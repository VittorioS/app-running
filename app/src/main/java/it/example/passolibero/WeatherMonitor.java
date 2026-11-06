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

import java.time.Instant;
import java.time.ZoneId;
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

    public static boolean hasCity(SharedPreferences prefs) {
        return prefs.contains("weather_lat_bits") && prefs.contains("weather_lon_bits");
    }

    static double latitude(SharedPreferences prefs) {
        return Double.longBitsToDouble(prefs.getLong("weather_lat_bits", 0));
    }

    static double longitude(SharedPreferences prefs) {
        return Double.longBitsToDouble(prefs.getLong("weather_lon_bits", 0));
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
        long next = WeatherSchedule.nextEpochMillis(
                times, ZoneId.systemDefault(), System.currentTimeMillis());
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

    static void checkAndNotify(Context context) throws java.io.IOException {
        SharedPreferences prefs = AppPrefs.get(context);
        if (!isActive(prefs)) return;
        double lat = latitude(prefs), lon = longitude(prefs);
        WeatherApi.Forecast forecast = WeatherApi.forecast(lat, lon);
        if (!isActive(prefs) || latitude(prefs) != lat || longitude(prefs) != lon) return;
        WeatherRules rules;
        try { rules = rules(prefs); }
        catch (IllegalArgumentException ex) { return; }
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        long now = System.currentTimeMillis() / 1000L;
        long last = prefs.getLong("weather_notified_at", 0);
        long lastSlot = prefs.getLong("weather_notified_slot", -1);
        for (WeatherApi.Hour hour : forecast.hours) {
            if (rules.matches(hour.temperatureC, hour.windKmh)
                    && WeatherRules.shouldNotify(now, hour.epochSec, last, lastSlot)) {
                notifyFavorable(context, prefs.getString("weather_city", "Città"),
                        forecast, hour);
                prefs.edit().putLong("weather_notified_at", now)
                        .putLong("weather_notified_slot", hour.epochSec).apply();
                break;
            }
        }
    }

    private static void notifyFavorable(Context context, String city,
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
                .setContentTitle("Meteo favorevole a " + city)
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message
                        + " Previsioni Open-Meteo: da verificare prima di uscire."))
                .setContentIntent(intent)
                .setCategory(Notification.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }
}
