package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import it.example.passolibero.application.update.RestoreSavedDownload;
import it.example.passolibero.platform.update.SavedDownloadPreferencesAdapter;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

/** P8b/#34: no APK download, user data, real updater or installer is touched. */
@RunWith(AndroidJUnit4.class)
public final class SavedDownloadPreferencesAdapterInstrumentedTest {
    private static void requireDisposableAndroid11() {
        assumeTrue("Android 11/API 30 only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Never touch personal updater preferences",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    @Test
    public void channelMismatchLeavesSavedKeysIntactAndDoesNotReadVersion() {
        requireDisposableAndroid11();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key = "issue34-restore-" + UUID.randomUUID();
        SharedPreferences preferences = context.getSharedPreferences(key, Context.MODE_PRIVATE);
        try {
            assertTrue(
                    preferences.edit()
                            .putLong("update_download_id", 21L)
                            .putString("update_channel", "stable")
                            .putString("update_code", "malformed-and-must-not-be-read")
                            .commit());
            SavedDownloadPreferencesAdapter reader =
                    new SavedDownloadPreferencesAdapter(preferences);
            assertNull(reader.readForChannel("debug"));
            assertEquals(21L, preferences.getLong("update_download_id", 0L));
            assertEquals("stable", preferences.getString("update_channel", ""));
        } finally {
            assertTrue(context.deleteSharedPreferences(key));
        }
    }

    @Test
    public void matchingChannelReadsAllExistingMetadataWithoutRewritingIt() {
        requireDisposableAndroid11();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key = "issue34-restore-" + UUID.randomUUID();
        SharedPreferences preferences = context.getSharedPreferences(key, Context.MODE_PRIVATE);
        try {
            assertTrue(
                    preferences.edit()
                            .putLong("update_download_id", 33L)
                            .putString("update_channel", "debug")
                            .putLong("update_code", 99L)
                            .putString("update_sha256", "a".repeat(64))
                            .putString("update_cert_sha256", "b".repeat(64))
                            .commit());
            SavedDownloadPreferencesAdapter reader =
                    new SavedDownloadPreferencesAdapter(preferences);
            RestoreSavedDownload.Snapshot snapshot = reader.readForChannel("debug");
            assertEquals(33L, snapshot.downloadId);
            assertEquals(99L, snapshot.versionCode);
            assertEquals("a".repeat(64), snapshot.sha256);
            assertEquals("b".repeat(64), snapshot.certificateSha256);
            assertEquals(33L, preferences.getLong("update_download_id", 0L));
        } finally {
            assertTrue(context.deleteSharedPreferences(key));
        }
    }
}
