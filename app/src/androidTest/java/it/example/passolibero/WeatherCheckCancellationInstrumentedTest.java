package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import it.example.passolibero.application.weather.WeatherCheckCancellation;

import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class WeatherCheckCancellationInstrumentedTest {
    @Test
    public void stoppedFetchCannotNotifyAndTheNextRunStartsIndependently() throws Exception {
        Context app = ApplicationProvider.getApplicationContext();
        String prefsName = "test-weather-cancel-" + UUID.randomUUID();
        SharedPreferences preferences = app.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        AtomicInteger notificationAttempts = new AtomicInteger();
        Context context = new ContextWrapper(app) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return preferences;
            }

            @Override public int checkSelfPermission(String permission) {
                return PackageManager.PERMISSION_GRANTED;
            }

            @Override public Object getSystemService(String name) {
                if (Context.NOTIFICATION_SERVICE.equals(name)) {
                    notificationAttempts.incrementAndGet();
                    return null;
                }
                return super.getSystemService(name);
            }
        };
        WeatherLocations.City first = new WeatherLocations.City("Prima", 0, 0);
        WeatherLocations.City second = new WeatherLocations.City("Seconda", 1, 1);
        String firstCooldown = "weather_notified_at_" + WeatherLocations.key(first);
        String secondCooldown = "weather_notified_at_" + WeatherLocations.key(second);
        try {
            preferences.edit().putBoolean("weather_enabled", true)
                    .putString("weather_times", "08:00")
                    .putString("weather_cities", WeatherLocations.encode(Arrays.asList(first, second)))
                    .commit();
            long slot = System.currentTimeMillis() / 1_000L + 60L;
            WeatherApi.Forecast forecast = new WeatherApi.Forecast(
                    Collections.singletonList(new WeatherApi.Hour(slot, 18, 4)), ZoneOffset.UTC);
            AtomicInteger requests = new AtomicInteger();
            WeatherCheckCancellation stopped = new WeatherCheckCancellation();
            WeatherCheckCancellation next = new WeatherCheckCancellation();

            WeatherMonitor.checkAndNotify(context, stopped, (latitude, longitude) -> {
                requests.incrementAndGet();
                stopped.cancel();
                return forecast;
            });
            assertEquals(1, requests.get());
            assertEquals(0, notificationAttempts.get());
            assertFalse(preferences.contains(firstCooldown));
            assertFalse(preferences.contains(secondCooldown));

            WeatherMonitor.checkAndNotify(context, next, (latitude, longitude) -> {
                requests.incrementAndGet();
                return forecast;
            });
            assertEquals(3, requests.get());
            assertEquals(2, notificationAttempts.get());
            assertTrue(preferences.contains(firstCooldown));
            assertTrue(preferences.contains(secondCooldown));
        } finally {
            app.deleteSharedPreferences(prefsName);
        }
    }
}
