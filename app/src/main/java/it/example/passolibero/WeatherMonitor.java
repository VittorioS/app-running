package it.example.passolibero;

import android.Manifest;
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
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Preferenze e notifica locale su previsioni orarie, senza nuove richieste GPS. */
public final class WeatherMonitor {
    static final int JOB_ID = 69142;
    private static final int NOTIFICATION_ID = 69143;
    private static final String CHANNEL = "favorable_running_weather";
    private static final long SIX_HOURS = 6 * 3600L;
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

    public static void updateSchedule(Context context) {
        Context app = context.getApplicationContext();
        JobScheduler scheduler = app.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        SharedPreferences p = AppPrefs.get(app);
        if (!p.getBoolean("weather_enabled", false) || !hasCity(p)) {
            scheduler.cancel(JOB_ID);
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(app, WeatherJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(60 * 60_000L, 15 * 60_000L)
                .build();
        scheduler.schedule(job);
    }

    static void checkAndNotify(Context context) throws java.io.IOException {
        SharedPreferences prefs = AppPrefs.get(context);
        if (!prefs.getBoolean("weather_enabled", false) || !hasCity(prefs)) return;
        double lat = latitude(prefs), lon = longitude(prefs);
        WeatherApi.Forecast forecast = WeatherApi.forecast(lat, lon);
        // Le preferenze possono cambiare mentre il download è in corso.
        if (!prefs.getBoolean("weather_enabled", false) || !hasCity(prefs)
                || latitude(prefs) != lat || longitude(prefs) != lon) return;
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
