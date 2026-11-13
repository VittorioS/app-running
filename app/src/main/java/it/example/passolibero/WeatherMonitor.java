package it.example.passolibero;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import it.example.passolibero.application.weather.ScheduleNextWeatherCheck;

import java.time.Instant;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Meteo programmato in orari scelti. Un allarme INESATTO alla volta,
 * poi un JobScheduler vincolato alla rete. Nessun GPS o servizio permanente.
 */
public final class WeatherMonitor {
    static final int JOB_ID = 69142;
    static final int ALARM_REQUEST_CODE = 69144;
    private static final int NOTIFICATION_ID = 69143;
    private static final String CHANNEL = "favorable_running_weather";
    private WeatherMonitor() { }

    /** Migra senza perdere la citta' usata dalle precedenti versioni dell'app. */
    public static List<WeatherLocations.City> cities(SharedPreferences prefs) {
        if (prefs.contains("weather_cities"))
            return WeatherLocations.decode(prefs.getString("weather_cities", ""));
        List<WeatherLocations.City> old = new java.util.ArrayList<>();
        if (prefs.contains("weather_lat_bits") && prefs.contains("weather_lon_bits")) {
            WeatherLocations.City city = WeatherLocations.legacy(
                    prefs.getString("weather_city", ""),
                    Double.longBitsToDouble(prefs.getLong("weather_lat_bits", 0)),
                    Double.longBitsToDouble(prefs.getLong("weather_lon_bits", 0)));
            if (city != null) old.add(city);
        }
        return old;
    }

    public static boolean hasCity(SharedPreferences prefs) {
        return !cities(prefs).isEmpty();
    }

    static WeatherRules rules(SharedPreferences prefs) {
        return new WeatherRules(
                WeatherRules.optional(prefs.getString("weather_min_c", "")),
                WeatherRules.optional(prefs.getString("weather_max_c", "")),
                WeatherRules.optional(prefs.getString("weather_max_wind", "")));
    }

    public static boolean isActive(SharedPreferences prefs) {
        return prefs.getBoolean("weather_enabled", false) && hasCity(prefs)
                && !WeatherSchedule.decode(prefs.getString("weather_times", "")).isEmpty();
    }

    /** Cancella il vecchio job periodico e programma soltanto il prossimo orario. */
    public static void updateSchedule(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences p = AppPrefs.get(app);
        JobScheduler jobs = app.getSystemService(JobScheduler.class);
        if (jobs != null) jobs.cancel(JOB_ID);
        AlarmManager alarms = app.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        PendingIntent intent = alarmIntent(app);
        alarms.cancel(intent);
        if (!isActive(p)) return;
        List<String> times = WeatherSchedule.decode(p.getString("weather_times", ""));
        long next = new ScheduleNextWeatherCheck(Clock.systemDefaultZone())
                .nextEpochMillis(times);
        if (next <= 0) return;
        // setAndAllowWhileIdle può ritardare in Doze: niente permessi exact alarm.
        try {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, intent);
        } catch (SecurityException error) {
            // Ultima alternativa sempre inesatta per produttori con policy restrittive.
            alarms.set(AlarmManager.RTC_WAKEUP, next, intent);
        }
    }

    static PendingIntent alarmIntent(Context context) {
        Intent intent = new Intent(context, WeatherAlarmReceiver.class);
        intent.setAction("it.example.passolibero.CHECK_WEATHER");
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Called by alarm receiver; request network availability without spinning. */
    static void scheduleNetworkCheck(Context context) {
        if (!isActive(AppPrefs.get(context))) return;
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, WeatherJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(15 * 60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        jobs.schedule(job);
    }

    /** Un controllo per ogni localita', con cooldown e notifiche indipendenti. */
    static void checkAndNotify(Context context) throws java.io.IOException {
        SharedPreferences prefs = AppPrefs.get(context);
        if (!isActive(prefs)) return;
        WeatherRules rules;
        try { rules = rules(prefs); }
        catch (IllegalArgumentException ex) { return; }
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        List<WeatherLocations.City> target = cities(prefs);
        int successful = 0;
        java.io.IOException firstFailure = null;
        for (WeatherLocations.City city : target) {
            // La lista potrebbe essere stata modificata mentre era in corso il job.
            if (!isActive(prefs)) return;
            if (!WeatherLocations.contains(cities(prefs), city)) continue;

            WeatherApi.Forecast forecast;
            try {
                forecast = WeatherApi.forecast(city.latitude, city.longitude);
                successful++;
            } catch (java.io.IOException error) {
                android.util.Log.w("PassoLiberoWeather", "Meteo non disponibile per "
                        + city.title, error);
                if (firstFailure == null) firstFailure = error;
                continue; // Un errore di rete per una citta' non blocca le altre.
            }
            if (!WeatherLocations.contains(cities(prefs), city) || !isActive(prefs)) continue;
            long now = System.currentTimeMillis() / 1000L;
            String key = WeatherLocations.key(city);
            String oldCity = prefs.getString("weather_city", "");
            boolean legacy = !oldCity.isEmpty() && oldCity.equals(city.title);
            long last = prefs.getLong("weather_notified_at_" + key,
                    legacy ? prefs.getLong("weather_notified_at", 0) : 0);
            long lastSlot = prefs.getLong("weather_notified_slot_" + key,
                    legacy ? prefs.getLong("weather_notified_slot", -1) : -1);
            for (WeatherApi.Hour hour : forecast.hours) {
                if (rules.matches(hour.temperatureC, hour.windKmh)
                        && WeatherRules.shouldNotify(now, hour.epochSec, last, lastSlot)) {
                    notifyFavorable(context, city, forecast, hour);
                    prefs.edit().putLong("weather_notified_at_" + key, now)
                            .putLong("weather_notified_slot_" + key, hour.epochSec).apply();
                    break;
                }
            }
        }
        if (successful == 0 && firstFailure != null) throw firstFailure;
    }

    private static void notifyFavorable(Context context, WeatherLocations.City city,
                                        WeatherApi.Forecast forecast, WeatherApi.Hour hour) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "Meteo favorevole per correre", NotificationManager.IMPORTANCE_DEFAULT));
        String time = DateTimeFormatter.ofPattern("EEE d MMM 'alle' HH:mm", Locale.ITALY)
                .withZone(forecast.zone).format(Instant.ofEpochSecond(hour.epochSec));
        String message = String.format(Locale.ITALY,
                "%s: previsti %.0f °C e vento %.0f km/h. Condizioni meteo perfette per correre!",
                time, hour.temperatureC, hour.windKmh);
        Intent open = new Intent(context, WeatherSettingsActivity.class);
        PendingIntent intent = PendingIntent.getActivity(context, 810, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_runner)
                .setContentTitle("Meteo favorevole a " + city.title)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message
                        + " Previsioni Open-Meteo: da verificare prima di uscire."))
                .setContentIntent(intent)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .build();
        manager.notify("weather_" + WeatherLocations.key(city),
                NOTIFICATION_ID, notification);
    }
}
