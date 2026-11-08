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
    public static final String SPRINT_START = "it.example.passolibero.SPRINT_START";
    public static final String SPRINT_CANCEL = "it.example.passolibero.SPRINT_CANCEL";
    public static final String SPRINT_PREPARE_VOICE = "it.example.passolibero.SPRINT_PREPARE_VOICE";
    public static final String EXTRA_SPRINT_COUNTDOWN = "sprint_countdown";
    public static final String EXTRA_SPRINT_MODE = "sprint_mode";
    public static final String EXTRA_SPRINT_TARGET = "sprint_target";
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
    private Sensor inertialSensor;
    private final InertialGaitDetector inertialGait = new InertialGaitDetector();
    private VoiceCoach voice;
    private final ActivityEvidence activityEvidence = new ActivityEvidence();
    private final ArrayDeque<Long> recentStepTimes = new ArrayDeque<>();
    private AdaptiveProfile profile;
    private RunState state;
    private Location lastAccepted;
    // Il filtro anti-rumore non deve perdere i microspostamenti GPS.
    private final DistanceAccumulator distanceAccumulator = new DistanceAccumulator();
    private final MotionEvidencePolicy motionEvidence = new MotionEvidencePolicy();
    private final MotionTimeline motionTimeline = new MotionTimeline();
    private final SessionMotionVoiceEvents sessionVoice = new SessionMotionVoiceEvents();
    private boolean detectorSeen;
    private final StepCounterEvidence counterEvidence = new StepCounterEvidence();
    private boolean stepSensorAvailable;
    private long lastSavedDbElapsed, lastNotificationMs, lastPointWall;
    private long lastUiSavedElapsed = -1;
    private MovementClassifier.Kind trainingLabel;
    private long trainingUntilElapsed;
    private boolean initialized;
    private boolean acquiredForeground;
    private long poorSince = -1, recoverySince = -1;
    private boolean gpsIncident, gpsAskedWhileStill;
    private String motionBeforeGpsLoss = "UNKNOWN";
    private final SprintTrial sprint = new SprintTrial();
    private long lastNormalGpsFixMs = -1;
    private boolean sprintRecorded;
    private boolean sprintVoicePrepared, sprintVoicePending;
    private int sprintVoiceGeneration;
    private SprintProgressVoicePlan sprintProgressVoice;
    private long sprintProgressLastSpeakMs = -1;

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
        if (SPRINT_PREPARE_VOICE.equals(action)) {
            work.post(() -> {
                if (state != null && initialized && prefs.getBoolean("active", false)) {
                    sprintVoicePrepared = true;
                    syncVoiceCoach();
                }
            });
            return START_STICKY;
        }
        if (SPRINT_START.equals(action)) {
            final int countdown = intent.getIntExtra(EXTRA_SPRINT_COUNTDOWN, 5);
            final int target = intent.getIntExtra(EXTRA_SPRINT_TARGET, 10);
            final String modeName = intent.getStringExtra(EXTRA_SPRINT_MODE);
            work.post(() -> {
                if (state == null || !initialized || !prefs.getBoolean("active", false)
                        || sprint.busy() || sprintVoicePending) return;
                try {
                    SprintTrial.Mode mode = SprintTrial.Mode.valueOf(
                            modeName == null ? "" : modeName);
                    // Parametri gia' validati dalla UI, verificati ancora dal modello
                    // non appena parte il countdown.
                    if (countdown < 5 || countdown > 10
                            || (mode == SprintTrial.Mode.TIME && (target < 5 || target > 120))
                            || (mode == SprintTrial.Mode.DISTANCE && (target < 10 || target > 500)))
                        throw new IllegalArgumentException("Valori sprint invalidi");
                    sprintVoicePrepared = true;
                    sprintVoicePending = true;
                    final int token = ++sprintVoiceGeneration;
                    prefs.edit().putBoolean("sprint_voice_preparing", true)
                            .remove("sprint_voice_error").apply();
                    syncVoiceCoach();
                    // Il countdown parte quando il sintetizzatore italiano e' pronto:
                    // la prima cifra non viene persa durante l'avvio del motore.
                    final long deadline = SystemClock.elapsedRealtime() + 5_000L;
                    work.post(new Runnable() {
                        @Override public void run() {
                            if (token != sprintVoiceGeneration || !sprintVoicePending
                                    || state == null || !prefs.getBoolean("active", false)) return;
                            long now = SystemClock.elapsedRealtime();
                            if (voice != null && voice.isReady()) {
                                sprintVoicePending = false;
                                prefs.edit().putBoolean("sprint_voice_preparing", false).apply();
                                try {
                                    sprint.start(now, countdown, mode, target);
                                    sprintProgressVoice = new SprintProgressVoicePlan(mode, target);
                                    sprintProgressLastSpeakMs = -1;
                                    sprintRecorded = false;
                                    publishSprint();
                                    requestSprintGps(true);
                                    scheduleSpokenSprintCountdown(
                                            new SprintCountdownVoicePlan(now, countdown), token);
                                    if (mode == SprintTrial.Mode.TIME)
                                        scheduleTimedSprintProgress(token);
                                } catch (IllegalArgumentException | IllegalStateException ex) {
                                    Log.w(TAG, "Sprint non avviabile", ex);
                                    prefs.edit().putString("sprint_voice_error",
                                            "Impossibile avviare lo sprint. Riprova.").apply();
                                }
                            } else if (now < deadline) {
                                work.postDelayed(this, 100);
                            } else {
                                // Non avviare un countdown silenzioso a sorpresa.
                                sprintVoicePending = false;
                                prefs.edit().putBoolean("sprint_voice_preparing", false)
                                        .putString("sprint_voice_error",
                                                "Voce italiana non disponibile. Verifica la "
                                              + "sintesi vocale del telefono e riprova.").apply();
                            }
                        }
                    });
                } catch (IllegalArgumentException ex) {
                    Log.w(TAG, "Richiesta sprint non valida", ex);
                }
            });
            return START_STICKY;
        }
        if (SPRINT_CANCEL.equals(action)) {
            work.post(() -> {
                ++sprintVoiceGeneration;
                sprintProgressVoice = null;
                sprintProgressLastSpeakMs = -1;
                if (sprintVoicePending) {
                    sprintVoicePending = false;
                    prefs.edit().putBoolean("sprint_voice_preparing", false)
                            .putString("sprint_voice_error", "Preparazione sprint annullata.")
                            .apply();
                }
                if (voice != null) voice.stopSpeaking();
                if (state == null || !sprint.busy()) return;
                long now = SystemClock.elapsedRealtime();
                sprint.cancel(now);
                saveCompletedSprint(now);
                requestSprintGps(false);
                publishSprint();
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
        lastNormalGpsFixMs = -1;
        sprintRecorded = false;
        sprintVoicePending = false;
        ++sprintVoiceGeneration;
        sprintProgressVoice = null;
        sprintProgressLastSpeakMs = -1;
        prefs.edit().putBoolean("sprint_voice_preparing", false)
                .remove("sprint_voice_error").apply();
        // Uno sprint precedente non puo' proseguire dopo il riavvio del servizio.
        prefs.edit().putString("sprint_phase", "IDLE")
                .putLong("sprint_session_id", state.id).apply();
        state.lastTickElapsedMs = SystemClock.elapsedRealtime();
        state.lastLocationValid = false; // Ripresa: nessun marker da una posizione vecchia.
        prefs.edit().putBoolean("gps_ack_needed", false).putBoolean("gps_incident", false).apply();
        activityEvidence.reset();
        lastAccepted = null; // Non collegare con una linea GPS il tratto perso durante un riavvio.
        distanceAccumulator.reset();
        motionEvidence.reset();
        // Recover elapsed time not covered by the last durable snapshot as
        // UNKNOWN. Never infer RUN, WALK or STILL across a service restart.
        long accounted = state.runMs + state.walkMs + state.movingUnknownMs + state.stillMs + state.unknownMs;
        long elapsed = Math.max(0, state.lastTickElapsedMs - state.startedElapsedMs);
        if (restore && elapsed > accounted) state.unknownMs += elapsed - accounted;
        motionTimeline.restore(state.runMs, state.walkMs, state.stillMs, state.unknownMs, state.movingUnknownMs);
        // Non dedurre pause o riprese precedenti a un riavvio del servizio.
        sessionVoice.reset();
        motionTimeline.advance(restore ? state.lastTickElapsedMs : state.startedElapsedMs,
                MotionEvidencePolicy.Evidence.UNCERTAIN, MovementClassifier.Kind.UNKNOWN);
        inertialGait.reset();
        detectorSeen = false;
        counterEvidence.reset();
        recentStepTimes.clear();
        stepSensorAvailable = false;
        state.save(prefs, true);
        lastUiSavedElapsed = SystemClock.elapsedRealtime();
        syncVoiceCoach();

        requestSprintGps(false);
        if (checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
            stepSensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            if (stepSensor != null) stepSensorAvailable = sensors.registerListener(this, stepSensor,
                    SensorManager.SENSOR_DELAY_NORMAL, work);
            if (!stepSensorAvailable) {
                stepSensor = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (stepSensor != null) stepSensorAvailable = sensors.registerListener(this, stepSensor,
                        SensorManager.SENSOR_DELAY_NORMAL, work);
            }
        }
        // Only enable the higher-rate accelerometer when the real-time hardware
        // step detector is unavailable. Step Counter batching is not real-time.
        if (!stepSensorAvailable || stepSensor == null || stepSensor.getType() != Sensor.TYPE_STEP_DETECTOR) {
            inertialSensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (inertialSensor != null)
                sensors.registerListener(this, inertialSensor, SensorManager.SENSOR_DELAY_GAME, work);
        }
        if (BuildConfig.DEBUG) {
            // Dedicated debug code is invoked by reflection; release never ships it.
            try {
                Class<?> c = Class.forName(getPackageName() + ".BatteryAutoCapture");
                c.getMethod("onSessionStart", Context.class, long.class)
                        .invoke(null, getApplicationContext(), state.id);
            } catch (ReflectiveOperationException ex) { Log.w(TAG, "Debug recorder unavailable", ex); }
        }
        work.post(tick);
    }

    /** Elapsed realtime evita problemi con cambiamenti dell'orologio e a schermo bloccato. */
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (state == null) return;
            long now = SystemClock.elapsedRealtime();
            advanceSprint(now);
            state.lastTickElapsedMs = now;
            state.goalM = AppPrefs.goalM(prefs);
            state.cadenceSpm = cadenceSpm(now);
            if (lastAccepted != null && now - lastAccepted.getElapsedRealtimeNanos() / 1_000_000 > 15_000) {
                reportGpsQuality(false, now);
                state.gpsStatus = "GPS temporaneamente assente";
            }
            advanceTimeline(now);
            if (lastAccepted == null && now - state.startedElapsedMs > 15_000) reportGpsQuality(false, now);
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

    /** Attiva anche la voce sprint, indipendentemente dagli avvisi checkpoint. */
    private void syncVoiceCoach() {
        boolean enabled = AppPrefs.voiceEnabled(prefs) || gpsIncident
                || sessionVoice.hasSeenMovement()
                || sprintVoicePrepared || sprintVoicePending || sprint.busy();
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
        // Campioni sprint elaborati separatamente dal filtro per la sessione:
        // lettura nativa GNSS a basso intervallo, senza modificare distanza/classificazione.
        if (sprint.phase() == SprintTrial.Phase.COUNTDOWN
                || sprint.phase() == SprintTrial.Phase.ACTIVE) {
            long sprintNow = SystemClock.elapsedRealtime();
            advanceSprint(sprintNow);
            if (sprint.phase() == SprintTrial.Phase.ACTIVE && location.hasSpeed()
                    && location.hasSpeedAccuracy()) {
                boolean accepted = sprint.onFix(sprintNow,
                        location.getElapsedRealtimeNanos() / 1_000_000L,
                        location.getSpeed(), location.getSpeedAccuracyMetersPerSecond(),
                        location.getAccuracy());
                if (accepted && sprint.phase() == SprintTrial.Phase.ACTIVE
                        && sprintProgressVoice != null
                        && sprint.mode() == SprintTrial.Mode.DISTANCE) {
                    String message = sprintProgressVoice.onDistanceM(sprint.distanceM());
                    speakSprintProgress(message, sprintNow);
                }
                if (!sprint.busy()) {
                    sprintProgressVoice = null;
                    saveCompletedSprint(sprintNow);
                    requestSprintGps(false);
                    if (voice != null && AppPrefs.voiceEnabled(prefs))
                        voice.speak("Sprint terminato");
                }
                publishSprint();
            }
        }
        // I fix sprint ad alta frequenza sono gia' stati letti sopra.
        // Il tracciamento generale resta a circa 1 Hz, come prima dello sprint:
        // nessuna nuova pressione sulla classificazione RUN/WALK.
        long normalFixMs = location.getElapsedRealtimeNanos() / 1_000_000L;
        if (sprint.busy() && lastNormalGpsFixMs >= 0
                && normalFixMs >= lastNormalGpsFixMs
                && normalFixMs - lastNormalGpsFixMs < 900L) return;
        lastNormalGpsFixMs = normalFixMs;
        long now = SystemClock.elapsedRealtime();
        long ageMs = Math.abs(now - location.getElapsedRealtimeNanos() / 1_000_000);
        float acc = location.getAccuracy();
        if (!GpsQualityPolicy.isReliable(acc, ageMs)) {
            reportGpsQuality(false, now);
            state.gpsStatus = "Segnale GPS impreciso";
            persist(now, false, true);
            return;
        }
        reportGpsQuality(true, now);
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
        int stepsLastFiveSeconds = 0;
        for (long stepTime : recentStepTimes)
            if (now - stepTime <= 5_000L) stepsLastFiveSeconds++;
        double nativeSpeed = location.hasSpeed() ? location.getSpeed() : Double.NaN;
        double speedUncertainty = location.hasSpeedAccuracy()
                ? location.getSpeedAccuracyMetersPerSecond() : Double.NaN;
        long fixMs = location.getElapsedRealtimeNanos() / 1_000_000L;
        boolean freshActivityFix = MovementClassifier.isFreshMeasurement(now, fixMs);
        motionEvidence.observeGps(fixMs, location.getLatitude(), location.getLongitude(),
                acc, nativeSpeed, speedUncertainty);
        MotionEvidencePolicy.Evidence evidence = evaluateMotion(now);
        boolean moving = evidence == MotionEvidencePolicy.Evidence.MOVING;
        // Uncertainty is not proof of stillness. Permit conservative distance
        // accumulation for plausible slow movement; never force speed to zero.
        boolean plausibleSlow = evidence == MotionEvidencePolicy.Evidence.UNCERTAIN
                && Double.isFinite(pointSpeed) && pointSpeed >= 1.2 && pointSpeed < 20
                && Double.isFinite(nativeSpeed) && nativeSpeed >= 0.4
                && Double.isFinite(speedUncertainty) && speedUncertainty <= 0.6;
        boolean countDistance = moving || plausibleSlow;
        if (motionTimeline.visible() == MovementClassifier.Kind.STILL && !moving)
            countDistance = false;
        if (freshActivityFix) {
            delta = distanceAccumulator.accept(location.getLatitude(), location.getLongitude(),
                    acc, countDistance);
        } else {
            distanceAccumulator.reset();
        }

        double threshold = profile.threshold(AppPrefs.manualThreshold(prefs), AppPrefs.adaptiveEnabled(prefs));
        if (moving && Double.isFinite(pointSpeed) && freshActivityFix) {
            // This classifier distinguishes WALK/RUN only; the timeline owns STILL.
            activityEvidence.observe(now, fixMs, pointSpeed, recentSteps, threshold);
        }
        if (freshActivityFix) state.speedKmh = Math.max(0, pointSpeed);
        advanceTimeline(now);
        MovementClassifier.Kind movement = motionTimeline.visible();
        if (!"UNKNOWN".equals(state.movement)) motionBeforeGpsLoss = state.movement;
        if (gpsIncident && "STILL".equals(state.movement) && !gpsAskedWhileStill) {
            gpsAskedWhileStill = true;
            prefs.edit().putBoolean("gps_ack_needed", true).apply();
        }
        state.gpsStatus = freshActivityFix ? "GPS ±" + Math.round(acc) + " m" : "Posizione GPS in ritardo";
        state.gpsAccuracy = acc;
        // Mantieni fermo il marker quando il GPS oscilla senza movimento credibile.
        if (!state.lastLocationValid || delta > 0) {
            state.lastLatitude = location.getLatitude();
            state.lastLongitude = location.getLongitude();
            state.lastLocationValid = true;
        }
        state.distanceM += delta;
        if (movement == MovementClassifier.Kind.WALK) state.walkM += delta;
        else if (movement == MovementClassifier.Kind.RUN) state.runM += delta;
        // Statistica passiva: solo corsa confermata e fix GPS affidabile.
        // Non interviene su classificazione, velocita istantanea o distanza.
        state.maxRunSpeedKmh = RunPeakSpeedPolicy.update(state.maxRunSpeedKmh,
                movement.name(), moving, freshActivityFix, acc, delta, pointSpeed);

        // Punti del percorso quando ci si sposta; a riposo al massimo ogni 15 s.
        if (delta > 0 || location.getTime() - lastPointWall >= 15_000) {
            db.point(state.id, location.getTime(), state.lastLatitude, state.lastLongitude,
                    acc, state.speedKmh, delta, movement);
            lastPointWall = location.getTime();
        }
        lastAccepted = new Location(location);

        int cadence = cadenceSpm(now);
        state.cadenceSpm = cadence;
        if (trainingLabel != null && now <= trainingUntilElapsed && acc <= 12 && moving && freshActivityFix
                && (movement == MovementClassifier.Kind.RUN || movement == MovementClassifier.Kind.WALK)) {
            profile.observe(trainingLabel == MovementClassifier.Kind.RUN, state.speedKmh);
            if ((profile.walkSamples + profile.runSamples) % 5 == 0) AppPrefs.saveProfile(prefs, profile);
        } else {
            trainingLabel = null;
            double manual = AppPrefs.manualThreshold(prefs);
            // Solo esempi estremi confermati anche dai passi: non rinforzare errori al confine.
            if (AppPrefs.adaptiveEnabled(prefs) && moving && freshActivityFix && acc <= 10 && recentSteps >= 10
                    && (movement == MovementClassifier.Kind.RUN || movement == MovementClassifier.Kind.WALK)) {
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

    private MotionEvidencePolicy.Evidence evaluateMotion(long now) {
        trimSteps(now);
        int detectorSteps = 0;
        for (long step : recentStepTimes) if (step <= now && now - step <= 5_000L) detectorSteps++;
        long detectorAge = detectorSeen && state.lastStepElapsedMs > 0
                && stepSensor != null && stepSensor.getType() == Sensor.TYPE_STEP_DETECTOR
                ? now - state.lastStepElapsedMs : Long.MAX_VALUE;
        boolean detectorAvailable = stepSensorAvailable && stepSensor != null
                && stepSensor.getType() == Sensor.TYPE_STEP_DETECTOR;
        boolean counterProgress = counterEvidence.progressRecent(now);
        return motionEvidence.evaluate(now, detectorSteps, detectorAge,
                detectorAvailable, detectorSeen, counterProgress, inertialGait.gaitAt(now));
    }

    private void advanceTimeline(long now) {
        if (state == null) return;
        MovementClassifier.Kind freshActivity = activityEvidence.current(now);
        MovementClassifier.Kind previous = motionTimeline.visible();
        boolean cumulativeSteps = stepSensorAvailable && stepSensor != null
                && stepSensor.getType() == Sensor.TYPE_STEP_COUNTER;
        MotionEvidencePolicy.Evidence evidence = evaluateMotion(now);
        motionTimeline.advance(now, evidence, freshActivity,
                cumulativeSteps ? StepCounterEvidence.STOP_CONFIRM_MS : 0);
        MovementClassifier.Kind current = motionTimeline.visible();
        if (previous != current && (current == MovementClassifier.Kind.STILL
                || current == MovementClassifier.Kind.UNKNOWN)) {
            // A confirmed stop or loss of evidence invalidates the old gait.
            activityEvidence.onMotionState(current);
        }
        applyTimeline();
        // Solo stati confermati dal ledger esistente: non cambiare la logica
        // di movimento, i tempi della sessione o i filtri GPS.
        boolean speechReady = voice != null && voice.isReady();
        boolean sprintPriority = sprint.busy() || sprintVoicePending;
        String announcement = sessionVoice.observe(current, evidence,
                speechReady, sprintPriority);
        if (sessionVoice.hasSeenMovement() && voice == null) syncVoiceCoach();
        if (announcement != null && voice != null) voice.speak(announcement);
    }

    private void applyTimeline() {
        state.runMs = motionTimeline.runMs();
        state.walkMs = motionTimeline.walkMs();
        state.stillMs = motionTimeline.stillMs();
        state.unknownMs = motionTimeline.unknownMs();
        state.movingUnknownMs = motionTimeline.movingUnknownMs();
        state.movingMs = motionTimeline.movingMs();
        state.movement = motionTimeline.visible().name();
        if (motionTimeline.visible() == MovementClassifier.Kind.STILL) state.speedKmh = 0;
    }

    /** Ritorna sempre a 1 Hz finita la prova; 200 ms e' una richiesta, non una garanzia. */
    private void requestSprintGps(boolean fast) {
        if (locationManager == null || workerThread == null) return;
        try {
            locationManager.removeUpdates(this);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    fast ? 200L : 1_000L, 0f, this, workerThread.getLooper());
        } catch (SecurityException | IllegalArgumentException ex) {
            Log.w(TAG, "Frequenza GPS richiesta non disponibile", ex);
            if (fast) {
                // Ripristina comunque la lettura normale: lo sprint resta misurabile
                // ai campioni effettivamente forniti dal ricevitore.
                requestSprintGps(false);
            } else if (state != null) {
                state.gpsStatus = "GPS non disponibile";
                state.save(prefs, true);
            }
        }
    }

    /**
     * Una chiamata per ogni cifra all'istante previsto, non sui callback GPS.
     * Il token invalida i messaggi residui dopo annullamento e nuove prove.
     */
    private void scheduleSpokenSprintCountdown(SprintCountdownVoicePlan plan, int token) {
        for (int step = 0; step < plan.steps(); step++) {
            final int index = step;
            long delay = Math.max(0, plan.dueAt(index) - SystemClock.elapsedRealtime());
            work.postDelayed(() -> {
                if (token != sprintVoiceGeneration || state == null
                        || !prefs.getBoolean("active", false)) return;
                long now = SystemClock.elapsedRealtime();
                if (index == plan.steps() - 1) {
                    // La transizione e la parola "Via" sono gestite in
                    // advanceSprint(), quindi non si pronunciano due volte.
                    advanceSprint(now);
                } else if (sprint.phase() == SprintTrial.Phase.COUNTDOWN
                        && !plan.isStale(index, now) && voice != null && voice.isReady()) {
                    voice.speak(plan.phrase(index));
                }
            }, delay);
        }
    }

    /**
     * Cronometro dedicato alle soglie 20/40/60/80%: non dipende dai fix GPS.
     * Il token elimina tutti i callback pendenti dopo annullamento/nuovo sprint.
     */
    private void scheduleTimedSprintProgress(int token) {
        if (sprintProgressVoice == null || sprint.mode() != SprintTrial.Mode.TIME) return;
        final SprintProgressVoicePlan plan = sprintProgressVoice;
        for (int i = 1; i <= 4; i++) {
            final int milestone = i;
            final long dueMs = plan.dueAt(sprint.countdownEndMs(), milestone);
            long delay = Math.max(0L, dueMs - SystemClock.elapsedRealtime());
            work.postDelayed(() -> {
                if (token != sprintVoiceGeneration || sprintProgressVoice != plan
                        || state == null || !prefs.getBoolean("active", false)) return;
                long now = SystemClock.elapsedRealtime();
                // Non pronunciare un avviso vecchio per un callback in ritardo.
                // Gli sprint brevi hanno soglie ravvicinate: non creare una coda.
                if (now < dueMs || now - dueMs > 700L) return;
                advanceSprint(now);
                if (sprint.phase() != SprintTrial.Phase.ACTIVE) return;
                String message = plan.onElapsedMs(now - sprint.startedMs());
                speakSprintProgress(message, now);
            }, delay);
        }
    }

    private void speakSprintProgress(String message, long now) {
        if (message == null || sprint.phase() != SprintTrial.Phase.ACTIVE
                || voice == null || !voice.isReady()) return;
        // Evita accavallamenti serrati dovuti a misure GNSS irregolari.
        if (sprintProgressLastSpeakMs >= 0
                && now - sprintProgressLastSpeakMs < 850L) return;
        sprintProgressLastSpeakMs = now;
        voice.speak(message);
    }

    private void advanceSprint(long now) {
        if (!sprint.busy()) return;
        SprintTrial.Phase before = sprint.phase();
        sprint.advance(now);
        if (before == SprintTrial.Phase.COUNTDOWN
                && sprint.phase() == SprintTrial.Phase.ACTIVE
                && voice != null && voice.isReady())
            voice.speak("Via, sprint");
        if (!sprint.busy()) {
            sprintProgressVoice = null;
            saveCompletedSprint(now);
            requestSprintGps(false);
            if (voice != null && AppPrefs.voiceEnabled(prefs))
                voice.speak("Sprint terminato");
        }
        publishSprint();
    }

    /** Ogni prova conclusa viene scritta una sola volta nello storico. */
    private void saveCompletedSprint(long now) {
        if (state == null || sprintRecorded || sprint.busy()
                || (sprint.phase() != SprintTrial.Phase.FINISHED
                && sprint.phase() != SprintTrial.Phase.CANCELLED)) return;
        db.recordSprint(state.id, sprint, now);
        sprintRecorded = true;
    }

    private void publishSprint() {
        if (state == null) return;
        long now = SystemClock.elapsedRealtime();
        prefs.edit().putLong("sprint_session_id", state.id)
                .putString("sprint_phase", sprint.phase().name())
                .putString("sprint_mode", sprint.mode().name())
                .putInt("sprint_countdown", sprint.countdownSeconds())
                .putInt("sprint_target", sprint.target())
                .putLong("sprint_countdown_end", sprint.countdownEndMs())
                .putLong("sprint_started", sprint.startedMs())
                .putLong("sprint_elapsed", sprint.elapsedMs(now))
                .putLong("sprint_distance_bits", Double.doubleToLongBits(sprint.distanceM()))
                .putLong("sprint_peak_bits", Double.doubleToLongBits(sprint.peakSpeedKmh()))
                .putLong("sprint_avg_bits", Double.doubleToLongBits(sprint.averageSpeedKmh(now)))
                .putInt("sprint_samples", sprint.samples())
                .putString("sprint_finish_reason", sprint.finishReason())
                .apply();
    }

    private void reportGpsQuality(boolean good, long now) {
        if (state == null) return;
        if (good) {
            poorSince = -1;
            if (gpsIncident) {
                if (recoverySince < 0) recoverySince = now;
                if (GpsQualityPolicy.confirmedRecovery(recoverySince, now)) {
                    gpsIncident = false;
                    gpsAskedWhileStill = false;
                    recoverySince = -1;
                    prefs.edit().putBoolean("gps_incident", false)
                            .putBoolean("gps_ack_needed", false).apply();
                }
            }
            return;
        }
        recoverySince = -1;
        if (poorSince < 0) poorSince = now;
        if (!gpsIncident && GpsQualityPolicy.confirmedPoor(poorSince, now)) {
            gpsIncident = true;
            gpsAskedWhileStill = false;
            prefs.edit().putBoolean("gps_incident", true).apply();
            // Evita che l'avviso GPS interrompa le cifre del countdown.
            if (voice == null) voice = new VoiceCoach(this);
            if (!sprint.busy() && !sprintVoicePending)
                voice.speak("Segnale GPS impreciso");
        }
        if (gpsIncident && "STILL".equals(motionBeforeGpsLoss) && !gpsAskedWhileStill) {
            gpsAskedWhileStill = true;
            prefs.edit().putBoolean("gps_ack_needed", true).apply();
        }
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
        if (AppPrefs.voiceEnabled(prefs) && voice != null
                && !sprint.busy() && !sprintVoicePending) {
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
        long receivedMs = SystemClock.elapsedRealtime();
        if (stamp < 0 || stamp > receivedMs) return;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            inertialGait.observe(stamp, event.values[0], event.values[1], event.values[2]);
            return;
        }
        int newSteps = 0;
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            newSteps = Math.max(1, Math.round(event.values[0]));
            detectorSeen = true;
            for (int i = 0; i < newSteps; i++) recentStepTimes.addLast(stamp);
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            newSteps = counterEvidence.observe(stamp, event.values[0], receivedMs);
            if (newSteps > 0 && counterEvidence.canAmendPendingStop(receivedMs))
                motionTimeline.observeDelayedStep(counterEvidence.lastStepMs());
            // Batches have only the timestamp of the latest step. Do not
            // invent individual step timestamps or artificially high cadence.
        }
        if (newSteps > 0) {
            state.steps += newSteps;
            // Attribuzione per la UI, senza alterare la classificazione del movimento.
            // I passi arrivati mentre lo stato e' incerto restano solo nel totale.
            if ("RUN".equals(state.movement)) state.runSteps += newSteps;
            else if ("WALK".equals(state.movement)) state.walkSteps += newSteps;
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
        advanceTimeline(now);
        motionTimeline.finish();
        applyTimeline();
        ++sprintVoiceGeneration;
        sprintVoicePending = false;
        sprintProgressVoice = null;
        prefs.edit().putBoolean("sprint_voice_preparing", false).apply();
        if (sprint.busy()) {
            sprint.cancel(now);
            saveCompletedSprint(now);
            publishSprint();
        }
        if (BuildConfig.DEBUG) {
            try {
                Class.forName(getPackageName() + ".BatteryAutoCapture")
                        .getMethod("onSessionStop", Context.class, long.class)
                        .invoke(null, getApplicationContext(), state.id);
            } catch (ReflectiveOperationException ignored) { }
        }
        prefs.edit().putBoolean("gps_ack_needed", false).putBoolean("gps_incident", false).apply();
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
                if (state != null) {
                    advanceTimeline(SystemClock.elapsedRealtime());
                    motionTimeline.finish();
                    applyTimeline();
                    long now = SystemClock.elapsedRealtime();
                    ++sprintVoiceGeneration;
                    sprintVoicePending = false;
                    sprintProgressVoice = null;
                    prefs.edit().putBoolean("sprint_voice_preparing", false).apply();
                    if (sprint.busy()) {
                        sprint.cancel(now);
                        saveCompletedSprint(now);
                        publishSprint();
                    }
                    persist(now, true, false);
                }
                if (voice != null) voice.close();
                db.close();
            });
            workerThread.quitSafely();
        }
        super.onDestroy();
    }
}
