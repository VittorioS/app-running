package it.example.passolibero;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Locale;

/** Foreground Service: solo start esplicito, nessun monitoraggio fuori sessione. */
public final class TrackingService extends Service implements LocationListener, SensorEventListener {
    public static final String START = "it.example.passolibero.START";
    public static final String STOP = "it.example.passolibero.STOP";
    public static final String LABEL_WALK = "it.example.passolibero.LABEL_WALK";
    public static final String LABEL_RUN = "it.example.passolibero.LABEL_RUN";
    private static final String CHANNEL = "running_tracking";
    private static final int NOTIFICATION_ID = 501;
    private static final String TAG = "PassoLibero";

    private HandlerThread workerThread;
    private Handler work;
    private SharedPreferences prefs;
    private TrackerDb db;
    private LocationManager locationManager;
    private SensorManager sensors;
    private Sensor stepSensor;
    private VoiceCoach voice;
    private final MovementClassifier classifier = new MovementClassifier();
    private final ArrayDeque<Long> recentStepTimes = new ArrayDeque<>();
    private AdaptiveProfile profile;
    private RunState state;
    private Location lastAccepted;
    // Il filtro anti-rumore non deve perdere i microspostamenti GPS.
    private final DistanceAccumulator distanceAccumulator = new DistanceAccumulator();
    private double counterLast = Double.NaN;
    private long lastSavedDbElapsed, lastNotificationMs, lastPointWall;
    private long lastUiSavedElapsed = -1;
    private MovementClassifier.Kind trainingLabel;
    private long trainingUntilElapsed;
    private boolean initialized;
    private boolean acquiredForeground;

    @Override public void onCreate() {
        super.onCreate();
        prefs = AppPrefs.get(this);
        db = new TrackerDb(this);
        profile = AppPrefs.loadProfile(prefs);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        workerThread = new HandlerThread("GPS-Tracking");
        workerThread.start();
        work = new Handler(workerThread.getLooper());
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Sessione sportiva", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (STOP.equals(action)) {
            work.post(this::finish);
            return START_NOT_STICKY;
        }
        if (LABEL_WALK.equals(action) || LABEL_RUN.equals(action)) {
            MovementClassifier.Kind label = LABEL_RUN.equals(action)
                    ? MovementClassifier.Kind.RUN : MovementClassifier.Kind.WALK;
            work.post(() -> {
                if (state == null) return;
                trainingLabel = label;
                trainingUntilElapsed = SystemClock.elapsedRealtime() + 30_000;
                state.gpsStatus = "Apprendimento " + (label == MovementClassifier.Kind.RUN ? "corsa" : "camminata") + " (30 s)";
                state.save(prefs, true);
            });
            return START_STICKY;
        }
        // Avvio esclusivamente mentre MainActivity è visibile (o riavvio del sistema di un servizio già attivo).
        if (!hasLocationPermission() || !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            prefs.edit().putString("error", "Attiva il GPS e consenti la posizione precisa.").apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH;
            }
            startForeground(NOTIFICATION_ID, notification("Tracciamento in corso"), type);
            acquiredForeground = true;
        } catch (RuntimeException failure) {
            Log.e(TAG, "Foreground Service non avviabile", failure);
            prefs.edit().putString("error", "Impossibile avviare il servizio: controlla i permessi.").apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        work.post(() -> {
            if (!initialized) initializeTracking();
        });
        return START_STICKY;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private Notification notification(String line) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(it.example.passolibero.R.drawable.ic_runner)
                .setContentTitle("PassoLibero • Sessione attiva")
                .setContentText(line).setContentIntent(pi).setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE).setOnlyAlertOnce(true)
                .build();
    }

    private void initializeTracking() {
        if (initialized) return;
        initialized = true;
        boolean restore = prefs.getBoolean("active", false) && prefs.getLong("active_id", 0) > 0;
        if (restore) {
            state = RunState.read(prefs);
            // elapsedRealtime() si azzera dopo il riavvio del telefono: niente durata negativa.
            if (state.startedElapsedMs <= 0 || state.startedElapsedMs > SystemClock.elapsedRealtime()) {
                long elapsedBasedOnWall = Math.max(0, System.currentTimeMillis() - state.startedWallMs);
                state.startedElapsedMs = Math.max(0, SystemClock.elapsedRealtime() - elapsedBasedOnWall);
            }
            state.movement = "UNKNOWN";
            state.gpsStatus = "Ripresa: attesa del GPS";
        } else {
            state = new RunState();
            state.startedWallMs = System.currentTimeMillis();
            state.startedElapsedMs = SystemClock.elapsedRealtime();
            state.checkpointInterval = AppPrefs.intervalM(prefs);
            state.goalM = AppPrefs.goalM(prefs);
            state.id = db.newSession(state.startedWallMs);
        }
        state.lastTickElapsedMs = SystemClock.elapsedRealtime();
        state.lastLocationValid = false; // Ripresa: nessun marker da una posizione vecchia.
        classifier.reset();
        lastAccepted = null; // Non collegare con una linea GPS il tratto perso durante un riavvio.
        distanceAccumulator.reset();
        state.save(prefs, true);
        lastUiSavedElapsed = SystemClock.elapsedRealtime();
        syncVoiceCoach();

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    1_000L, 0f, this, workerThread.getLooper());
        } catch (SecurityException | IllegalArgumentException ex) {
            state.gpsStatus = "GPS non disponibile";
            state.save(prefs, true);
            Log.e(TAG, "Registrazione GPS fallita", ex);
        }
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            stepSensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            if (stepSensor == null) stepSensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (stepSensor != null) sensors.registerListener(this, stepSensor,
                    SensorManager.SENSOR_DELAY_NORMAL, work);
        }
        work.post(tick);
    }

    /** Elapsed realtime evita problemi con cambiamenti dell'orologio e a schermo bloccato. */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (state == null) return;
            long now = SystemClock.elapsedRealtime();
            long dt = Math.max(0, Math.min(10_000, now - state.lastTickElapsedMs));
            state.lastTickElapsedMs = now;
            state.goalM = AppPrefs.goalM(prefs);
            state.cadenceSpm = cadenceSpm(now);
            if (lastAccepted != null && now - lastAccepted.getElapsedRealtimeNanos() / 1_000_000 > 15_000) {
                state.gpsStatus = "GPS temporaneamente assente";
                state.movement = "UNKNOWN";
            }
            switch (state.movement) {
                case "RUN": state.runMs += dt; state.movingMs += dt; break;
                case "WALK": state.walkMs += dt; state.movingMs += dt; break;
                case "STILL": state.stillMs += dt; break;
                default: break;
            }
            syncVoiceCoach();
            persist(now, false, false);
            if (now - lastNotificationMs >= 30_000 && acquiredForeground) {
                lastNotificationMs = now;
                getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,
                        notification(RunMath.km(state.distanceM) + " km • " + RunMath.duration(now - state.startedElapsedMs)));
            }
            work.postDelayed(this, 1_000);
        }
    };

    /** Initialize the speech engine only while checkpoint voice is enabled. */
    private void syncVoiceCoach() {
        boolean enabled = AppPrefs.voiceEnabled(prefs);
        if (enabled && voice == null) {
            voice = new VoiceCoach(this);
        } else if (!enabled && voice != null) {
            voice.close();
            voice = null;
        }
    }

    /** Coalesce duplicate UI snapshots, without changing GPS sampling or classification. */
    private void persist(long now, boolean forceDb, boolean freshGps) {
        if (state == null) return;
        if (forceDb || BatteryPolicy.shouldPublishState(lastUiSavedElapsed, now, freshGps)) {
            state.save(prefs, true);
            lastUiSavedElapsed = now;
        }
        // Database crash-recovery snapshot cadence stays unchanged.
        if (forceDb || now - lastSavedDbElapsed >= 5_000) {
            db.snapshot(state, now - state.startedElapsedMs, false);
            lastSavedDbElapsed = now;
        }
    }

    @Override public void onLocationChanged(Location location) {
        // Callback richiesta sullo stesso Looper del worker.
        if (state == null || !location.hasAccuracy()) return;
        long now = SystemClock.elapsedRealtime();
        long ageMs = Math.abs(now - location.getElapsedRealtimeNanos() / 1_000_000);
        float acc = location.getAccuracy();
        if (ageMs > 10_000 || acc > 25 || acc <= 0) {
            state.gpsStatus = "Segnale GPS impreciso";
            return;
        }
        double delta = 0;
        double pointSpeed = Double.NaN;
        double dt = 0;
        if (lastAccepted != null) {
            dt = (location.getElapsedRealtimeNanos() - lastAccepted.getElapsedRealtimeNanos()) / 1e9;
            if (dt <= 0) return;
            if (dt <= 20) {
                double meters = RunMath.distanceMeters(lastAccepted.getLatitude(), lastAccepted.getLongitude(),
                        location.getLatitude(), location.getLongitude());
                double maxPhysical = Math.max(10, dt * 10 + Math.max(acc, lastAccepted.getAccuracy()));
                if (meters > maxPhysical) {
                    state.gpsStatus = "Salto GPS ignorato";
                    // Non lasciare un'ancora antica che blocca anche i fix successivi.
                    lastAccepted = new Location(location);
                    distanceAccumulator.reset();
                    return;
                }
                pointSpeed = meters / dt * 3.6;
            } else {
                // Dopo un lungo buco nel GPS non collegare due posizioni lontane.
                distanceAccumulator.reset();
            }
        }
        if (location.hasSpeed() && (!location.hasSpeedAccuracy() || location.getSpeedAccuracyMetersPerSecond() < 1.8f)) {
            pointSpeed = Math.max(0, location.getSpeed() * 3.6);
        }
        if (!Double.isFinite(pointSpeed) || pointSpeed > 40) {
            state.gpsStatus = "Velocità non affidabile";
            lastAccepted = new Location(location);
            distanceAccumulator.reset();
            return;
        }
        long ageStep = state.lastStepElapsedMs <= 0 ? Long.MAX_VALUE
                : now - state.lastStepElapsedMs;
        trimSteps(now);
        int recentSteps = recentStepTimes.size();
        // Non azzerare i movimenti inferiori a 2,5 m a ogni fix: sommali
        // finche' superano il rumore GPS. Se il GPS indica una sosta reale,
        // aggiorna l'ancora senza sommare la deriva della posizione.
        boolean stationary = pointSpeed < 0.7 && ageStep > 8_000;
        delta = distanceAccumulator.accept(location.getLatitude(), location.getLongitude(),
                acc, !stationary);

        double threshold = profile.threshold(AppPrefs.manualThreshold(prefs), AppPrefs.adaptiveEnabled(prefs));
        MovementClassifier.Kind movement = classifier.update(now, pointSpeed, true,
                recentSteps, ageStep, threshold);
        state.speedKmh = classifier.smoothedSpeedKmh();
        state.movement = movement.name();
        state.gpsStatus = "GPS ±" + Math.round(acc) + " m";
        state.gpsAccuracy = acc;
        state.lastLatitude = location.getLatitude();
        state.lastLongitude = location.getLongitude();
        state.lastLocationValid = true;
        state.distanceM += delta;
        if (movement == MovementClassifier.Kind.WALK) state.walkM += delta;
        else if (movement == MovementClassifier.Kind.RUN) state.runM += delta;

        // Punti del percorso quando ci si sposta; a riposo al massimo ogni 15 s.
        if (delta > 0 || location.getTime() - lastPointWall >= 15_000) {
            db.point(state.id, location.getTime(), location.getLatitude(), location.getLongitude(),
                    acc, state.speedKmh, delta, movement);
            lastPointWall = location.getTime();
        }
        lastAccepted = new Location(location);

        int cadence = cadenceSpm(now);
        state.cadenceSpm = cadence;
        if (trainingLabel != null && now <= trainingUntilElapsed && acc <= 12) {
            profile.observe(trainingLabel == MovementClassifier.Kind.RUN, state.speedKmh);
            if ((profile.walkSamples + profile.runSamples) % 5 == 0) AppPrefs.saveProfile(prefs, profile);
        } else {
            trainingLabel = null;
            double manual = AppPrefs.manualThreshold(prefs);
            // Solo esempi estremi confermati anche dai passi: non rinforzare errori al confine.
            if (AppPrefs.adaptiveEnabled(prefs) && acc <= 10 && recentSteps >= 10) {
                boolean sampleAdded = false;
                if (state.speedKmh <= manual - 1.5 && state.speedKmh >= 2.0 && cadence >= 65 && cadence <= 145) {
                    profile.observe(false, state.speedKmh);
                    sampleAdded = true;
                } else if (state.speedKmh >= manual + 2 && cadence >= 145 && cadence <= 220) {
                    profile.observe(true, state.speedKmh);
                    sampleAdded = true;
                }
                // Avoid rewriting the profile when no training sample was added.
                if (sampleAdded && (profile.walkSamples + profile.runSamples) % 15 == 0)
                    AppPrefs.saveProfile(prefs, profile);
            }
        }
        handleCheckpoint(now);
        persist(now, false, true);
    }

    private void handleCheckpoint(long now) {
        int interval = AppPrefs.intervalM(prefs);
        if (interval <= 0) return;
        if (state.checkpointInterval != interval) {
            state.checkpointInterval = interval;
            state.checkpointIndex = (int) Math.floor(state.distanceM / interval);
            return;
        }
        int reached = (int) Math.floor(state.distanceM / interval);
        if (reached <= state.checkpointIndex) return;
        state.checkpointIndex = reached; // Prima si salva, così non si annuncia due volte dopo crash.
        state.save(prefs, true);
        if (AppPrefs.voiceEnabled(prefs) && voice != null) {
            double distance = reached * interval;
            int distanceInt = (int) distance;
            String marker;
            if (distanceInt == 1000) marker = "Un chilometro";
            else if (distanceInt % 1000 == 0) marker = (distanceInt / 1000) + " chilometri";
            else marker = distanceInt + " metri";
            long elapsed = now - state.startedElapsedMs;
            String phrase = marker + " completati. Tempo " + spokenDuration(elapsed)
                    + ". Ritmo medio " + spokenPace(elapsed, state.distanceM) + ".";
            voice.speak(phrase);
        }
    }

    private static String spokenDuration(long ms) {
        long seconds = Math.max(0, ms / 1000);
        long minutes = seconds / 60, rem = seconds % 60;
        return minutes + " minuti e " + rem + " secondi";
    }

    private static String spokenPace(long ms, double meters) {
        if (meters < 50) return "non disponibile";
        long sec = Math.round(ms / meters);
        return (sec / 60) + " minuti e " + (sec % 60) + " secondi al chilometro";
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (state == null) return;
        long stamp = event.timestamp / 1_000_000;
        int newSteps = 0;
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            newSteps = Math.max(1, Math.round(event.values[0]));
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            if (Double.isFinite(counterLast)) {
                newSteps = (int) RunMath.clamp(event.values[0] - counterLast, 0, 50);
            }
            counterLast = event.values[0];
        }
        for (int i = 0; i < newSteps; i++) recentStepTimes.addLast(stamp);
        if (newSteps > 0) {
            state.steps += newSteps;
            state.lastStepElapsedMs = stamp;
            trimSteps(stamp);
        }
    }

    private void trimSteps(long now) {
        while (!recentStepTimes.isEmpty() && now - recentStepTimes.peekFirst() > 20_000)
            recentStepTimes.removeFirst();
    }

    private int cadenceSpm(long now) {
        trimSteps(now);
        if (recentStepTimes.size() < 4) return 0;
        long span = Math.max(4_000, now - recentStepTimes.peekFirst());
        return (int) Math.round((recentStepTimes.size() - 1) * 60_000.0 / span);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    @Override public void onProviderDisabled(String provider) {
        if (state != null) { state.gpsStatus = "GPS disattivato"; state.save(prefs, true); }
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    private void finish() {
        if (state == null) {
            // STOP arrivato prima che il worker abbia creato la sessione.
            if (prefs.getBoolean("active", false)) {
                state = RunState.read(prefs);
            } else { stopSelf(); return; }
        }
        long now = SystemClock.elapsedRealtime();
        if (state.lastTickElapsedMs > 0) {
            long dt = Math.min(10_000, Math.max(0, now - state.lastTickElapsedMs));
            if ("WALK".equals(state.movement)) { state.walkMs += dt; state.movingMs += dt; }
            if ("RUN".equals(state.movement)) { state.runMs += dt; state.movingMs += dt; }
            if ("STILL".equals(state.movement)) state.stillMs += dt;
        }
        db.snapshot(state, Math.max(0, now - state.startedElapsedMs), true);
        state.save(prefs, false);
        AppPrefs.saveProfile(prefs, profile);
        stopSensors();
        if (voice != null) { voice.close(); voice = null; }
        state = null;
        initialized = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void stopSensors() {
        try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
        sensors.unregisterListener(this);
    }

    @Override public void onDestroy() {
        // Distruzione inattesa: non cancellare o completare una sessione.
        // I dati restano nel DB e il flag active ne consente il recupero con START_STICKY.
        if (work != null) {
            work.removeCallbacks(tick);
            work.post(() -> {
                stopSensors();
                if (state != null) persist(SystemClock.elapsedRealtime(), true, false);
                if (voice != null) voice.close();
                db.close();
            });
            workerThread.quitSafely();
        }
        super.onDestroy();
    }
}
