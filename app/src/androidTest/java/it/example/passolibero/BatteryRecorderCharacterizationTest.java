package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * ADR-002/#35: characterize the debug-only recorder before moving it out of its Activity.
 *
 * <p>Each fixture uses isolated preferences and a uniquely named private CSV. No user history,
 * GPS coordinates, release APK, network request or automatic capture is involved.
 */
@RunWith(AndroidJUnit4.class)
public final class BatteryRecorderCharacterizationTest {
    private static final String CSV_FILE = "battery-debug-last.csv";
    private static final String CSV_HEADER =
            "utc_iso,elapsed_ms,actual_interval_ms,scenario,planned_seconds,"
                    + "manufacturer,model,android_api,app_version,"
                    + "session_active,movement,speed_kmh,gps_accuracy_m,gps_status,"
                    + "steps,cadence_spm,distance_m,voice_enabled,"
                    + "screen_interactive,screen_brightness_setting,network_transport,thermal_status,"
                    + "battery_level_pct,battery_charging,battery_temperature_c,battery_voltage_mv,"
                    + "phone_current_now_ua,phone_current_average_ua,"
                    + "phone_charge_counter_uah,phone_energy_counter_nwh,"
                    + "app_process_cpu_total_ms,app_process_cpu_delta_ms,app_process_pss_kb,"
                    + "app_uid_rx_delta_bytes,app_uid_tx_delta_bytes\n";

    private static void requireDisposableAndroid11() {
        assumeTrue("Android 11/API 30 only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Recorder fixture must never run on a personal phone",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue35-battery-" + UUID.randomUUID() + "-";

        SandboxContext(Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return getBaseContext().getSharedPreferences(prefix + name, mode);
        }

        @Override
        public File getFileStreamPath(String name) {
            return getBaseContext().getFileStreamPath(prefix + name);
        }

        @Override
        public FileOutputStream openFileOutput(String name, int mode)
                throws FileNotFoundException {
            return getBaseContext().openFileOutput(prefix + name, mode);
        }

        @Override
        public FileInputStream openFileInput(String name) throws FileNotFoundException {
            return getBaseContext().openFileInput(prefix + name);
        }

        @Override
        public boolean deleteFile(String name) {
            return getBaseContext().deleteFile(prefix + name);
        }

        void cleanUp() {
            deleteFile(CSV_FILE);
            getBaseContext().deleteSharedPreferences(prefix + "passolibero");
        }
    }

    @Test
    public void manualStartAndStopKeepCsvHeaderStatusAndPrivatePersistence() {
        requireDisposableAndroid11();
        SandboxContext context =
                new SandboxContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            assertFalse("Initially no recorder active", BatteryRecorder.isRunning());
            assertFalse(AppPrefs.get(context).getBoolean("battery_auto_debug", false));

            BatteryRecorder.start(context, 10, "Riferimento - nessuna sessione");
            assertTrue("Manual start begins sampling", BatteryRecorder.isRunning());
            assertTrue(BatteryRecorder.status(context).contains(" / 10 s"));

            BatteryRecorder.stop();
            assertFalse(BatteryRecorder.isRunning());
            assertTrue(BatteryRecorder.hasReport(context));
            assertEquals("Completato · CSV pronto per esportazione",
                    BatteryRecorder.status(context));

            String csv = BatteryRecorder.csv(context);
            assertNotNull(csv);
            assertTrue("Preserve the complete CSV column contract", csv.startsWith(CSV_HEADER));
            String header = csv.substring(0, csv.indexOf('\n'));
            assertFalse("The CSV must not export GPS latitude", header.contains("latitude"));
            assertFalse("The CSV must not export GPS longitude", header.contains("longitude"));
            assertTrue("The existing private report file is retained",
                    context.getFileStreamPath(CSV_FILE).isFile());
            assertFalse("Manual capture must not enable auto-capture",
                    AppPrefs.get(context).getBoolean("battery_auto_debug", false));
        } finally {
            BatteryRecorder.stop();
            context.cleanUp();
        }
    }

    @Test
    public void secondStartIsIgnoredWhileRecordingAndStopIsIdempotent() {
        requireDisposableAndroid11();
        SandboxContext context =
                new SandboxContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            BatteryRecorder.start(context, 10, "Riferimento - nessuna sessione");
            BatteryRecorder.start(context, 300, "Corsa - mappa visibile");

            assertTrue(BatteryRecorder.isRunning());
            assertTrue("Second start cannot change the first configured duration",
                    BatteryRecorder.status(context).contains(" / 10 s"));
            BatteryRecorder.stop();
            String completed = BatteryRecorder.csv(context);
            BatteryRecorder.stop();

            assertFalse(BatteryRecorder.isRunning());
            assertEquals("Second stop must not erase the report", completed,
                    BatteryRecorder.csv(context));
        } finally {
            BatteryRecorder.stop();
            context.cleanUp();
        }
    }

    @Test
    public void persistedCsvMatchesExportedCsvBytesAndUsesUtf8() throws IOException {
        requireDisposableAndroid11();
        SandboxContext context =
                new SandboxContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            BatteryRecorder.start(context, 10, "Riferimento - nessuna sessione");
            BatteryRecorder.stop();
            String exported = BatteryRecorder.csv(context);
            assertNotNull(exported);
            StringBuilder stored = new StringBuilder();
            try (FileInputStream input = context.openFileInput(CSV_FILE)) {
                byte[] chunk = new byte[4096];
                int got;
                while ((got = input.read(chunk)) != -1) {
                    stored.append(new String(chunk, 0, got, StandardCharsets.UTF_8));
                }
            }
            assertEquals("Export and private recorder use identical UTF-8 bytes",
                    exported, stored.toString());
            assertTrue(exported.startsWith(CSV_HEADER));
        } finally {
            BatteryRecorder.stop();
            context.cleanUp();
        }
    }
}
