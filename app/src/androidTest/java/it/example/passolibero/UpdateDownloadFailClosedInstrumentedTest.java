package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.DownloadManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.os.Environment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * ADR-002/#33: fail-closed downloader/installer boundaries without an Android download.
 *
 * <p>Runs only on disposable Android 11 emulators. Uses an isolated private cache directory
 * and a ContextWrapper that deliberately makes DownloadManager unavailable. It never enqueues
 * a real request, requests installer permission, opens an APK, or touches personal storage.
 */
@RunWith(AndroidJUnit4.class)
public final class UpdateDownloadFailClosedInstrumentedTest {
    private static final String HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private static void disposableAndroid11Only() {
        assumeTrue("Android 11/API 30 only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Do not execute downloader fixtures on personal devices",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    private static UpdateManager.Offer syntheticOffer() {
        long version = 900_001L;
        return new UpdateManager.Offer(
                UpdateManager.CHANNEL_DEBUG,
                UpdatePolicy.DEBUG_TAG,
                UpdatePolicy.assetName(UpdateManager.CHANNEL_DEBUG, version),
                HASH,
                HASH,
                version,
                -1L);
    }

    private static final class NoDownloadContext extends ContextWrapper {
        private final File root;
        private final File downloads;
        private final boolean directoryAvailable;
        int directoryLookups;
        int downloadServiceLookups;

        NoDownloadContext(Context actual, boolean directoryAvailable) {
            super(actual);
            root = new File(actual.getCacheDir(), "issue33-download-" + UUID.randomUUID());
            downloads = new File(root, "downloads");
            this.directoryAvailable = directoryAvailable;
        }

        @Override
        public File getExternalFilesDir(String type) {
            directoryLookups++;
            assertEquals(Environment.DIRECTORY_DOWNLOADS, type);
            if (!directoryAvailable) return null;
            if (!downloads.isDirectory() && !downloads.mkdirs()) {
                throw new AssertionError("Unable to create synthetic download directory");
            }
            return downloads;
        }

        @Override
        public Object getSystemService(String name) {
            if (Context.DOWNLOAD_SERVICE.equals(name)) {
                downloadServiceLookups++;
                return null;
            }
            return getBaseContext().getSystemService(name);
        }

        void cleanUp() {
            File[] created = downloads.listFiles();
            if (created != null) {
                for (File file : created) {
                    assertTrue("Delete only isolated fixture files", file.delete());
                }
            }
            if (downloads.exists()) assertTrue("Remove isolated downloads", downloads.delete());
            if (root.exists()) assertTrue("Remove isolated cache root", root.delete());
        }

        File syntheticDestination() {
            return new File(downloads, syntheticOffer().name);
        }
    }

    @Test
    public void enqueueRejectsMissingPrivateDirectoryBeforeAccessingDownloadService()
            throws Exception {
        disposableAndroid11Only();
        NoDownloadContext context =
                new NoDownloadContext(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(), false);
        try {
            try {
                UpdateManager.enqueue(context, syntheticOffer());
                fail("An update must not be enqueued without private download storage");
            } catch (IOException rejected) {
                assertEquals("Cartella download non disponibile", rejected.getMessage());
            }
            assertEquals(1, context.directoryLookups);
            assertEquals(0, context.downloadServiceLookups);
            assertFalse(context.syntheticDestination().exists());
        } finally {
            context.cleanUp();
        }
    }

    @Test
    public void enqueueRejectsUnavailableDownloadManagerWithoutEnqueuingARequest()
            throws Exception {
        disposableAndroid11Only();
        NoDownloadContext context =
                new NoDownloadContext(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(), true);
        try {
            assertNull("No Android download service is available in this fixture",
                    context.getSystemService(DownloadManager.class));
            try {
                UpdateManager.enqueue(context, syntheticOffer());
                fail("The app must not report a download id without DownloadManager");
            } catch (IOException rejected) {
                assertEquals("DownloadManager non disponibile", rejected.getMessage());
            }
            assertTrue(context.directoryLookups >= 1);
            assertTrue(context.downloadServiceLookups >= 1);
            assertFalse("Never write a synthetic APK", context.syntheticDestination().exists());
        } finally {
            context.cleanUp();
        }
    }

    @Test
    public void missingManagerAndInvalidIdsCannotExposeDownloadedFileOrInstallerIntent()
            throws Exception {
        disposableAndroid11Only();
        NoDownloadContext context =
                new NoDownloadContext(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(), true);
        try {
            assertNull(context.getSystemService(DownloadManager.class));
            for (long id : new long[] {-1L, 0L, 123_456L}) {
                assertEquals(DownloadManager.STATUS_FAILED,
                        UpdateManager.downloadStatus(context, id));
                try {
                    UpdateManager.installIntent(context, id);
                    fail("An invalid or unavailable download must not expose an installer intent");
                } catch (IOException rejected) {
                    assertEquals("Download non trovato", rejected.getMessage());
                }
            }
            assertFalse("A failed lookup must not create an APK", context.syntheticDestination().exists());
        } finally {
            context.cleanUp();
        }
    }
}
