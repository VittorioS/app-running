package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * ADR-002/#33: fail-closed updater verification with disposable, unsigned synthetic files.
 *
 * <p>The test changes only unique cache files and isolated preferences on an API30 emulator.
 * It never downloads or installs an APK and never touches a real release or user history.
 */
@RunWith(AndroidJUnit4.class)
public final class UpdateVerificationGuardInstrumentedTest {
    private static final long VERSION_CODE = 900_001L;
    private static final String APK_NAME =
            UpdatePolicy.assetName(UpdateManager.CHANNEL_DEBUG, VERSION_CODE);
    private static final String ZERO_SHA256 =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private static void onDisposableAndroid11Emulator() {
        assumeTrue("Android 11/API 30 only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Never execute updater fixtures on a personal phone",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue33-verify-" + UUID.randomUUID() + "-";
        private final File root;
        private final File downloads;
        int directoryRequests;

        SandboxContext(Context base) {
            super(base);
            root = new File(base.getCacheDir(), prefix);
            downloads = new File(root, "test-downloads");
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return getBaseContext().getSharedPreferences(prefix + name, mode);
        }

        @Override
        public File getExternalFilesDir(String type) {
            directoryRequests++;
            assertEquals(Environment.DIRECTORY_DOWNLOADS, type);
            if (!downloads.isDirectory() && !downloads.mkdirs()) {
                throw new AssertionError("Unable to create isolated test downloads directory");
            }
            return downloads;
        }

        File fixture() {
            return new File(downloads, APK_NAME);
        }

        byte[] writeFakeApk(int size) throws IOException {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            byte[] bytes = new byte[size];
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) (index * 31 + 7);
            }
            try (FileOutputStream output = new FileOutputStream(new File(dir, APK_NAME))) {
                output.write(bytes);
            }
            return bytes;
        }

        void cleanUp() {
            File synthetic = fixture();
            if (synthetic.exists()) assertTrue("Delete only synthetic APK", synthetic.delete());
            if (downloads.exists()) assertTrue("Delete test downloads", downloads.delete());
            if (root.exists()) assertTrue("Delete test root", root.delete());
            getBaseContext().deleteSharedPreferences(prefix + "passolibero");
        }
    }

    private static UpdateManager.Offer offer(String apkHash) {
        return new UpdateManager.Offer(
                UpdateManager.CHANNEL_DEBUG,
                UpdatePolicy.DEBUG_TAG,
                APK_NAME,
                apkHash,
                ZERO_SHA256,
                VERSION_CODE,
                -1);
    }

    private static void assertRejected(
            SandboxContext context, UpdateManager.Offer update, String expectedMessage)
            throws Exception {
        try {
            UpdateManager.verify(context, update);
            fail("Verification must reject this synthetic, untrusted update");
        } catch (IOException rejected) {
            assertEquals(expectedMessage, rejected.getMessage());
        }
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder(64);
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    @Test
    public void activeTrackingSessionBlocksVerificationBeforeOpeningAnyApkDirectory()
            throws Exception {
        onDisposableAndroid11Emulator();
        SandboxContext context =
                new SandboxContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            assertTrue(AppPrefs.get(context).edit().putBoolean("active", true).commit());
            assertRejected(
                    context,
                    offer(ZERO_SHA256),
                    "Termina la sessione prima di installare un aggiornamento");
            assertEquals("The active guard must execute before file access", 0, context.directoryRequests);
            assertFalse(context.fixture().exists());
        } finally {
            context.cleanUp();
        }
    }

    @Test
    public void missingAndTooSmallApkAreRejectedBeforeHashing() throws Exception {
        onDisposableAndroid11Emulator();
        SandboxContext context =
                new SandboxContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            assertRejected(
                    context,
                    offer(ZERO_SHA256),
                    "File APK mancante o dimensione non valida");
            assertFalse(context.fixture().exists());

            context.writeFakeApk(99_999);
            assertRejected(
                    context,
                    offer(ZERO_SHA256),
                    "File APK mancante o dimensione non valida");
            assertEquals(99_999L, context.fixture().length());
        } finally {
            context.cleanUp();
        }
    }

    @Test
    public void validLengthWithWrongSha256IsRejectedBeforeApkParsing() throws Exception {
        onDisposableAndroid11Emulator();
        SandboxContext context =
                new SandboxContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            context.writeFakeApk(100_001);
            assertRejected(
                    context,
                    offer(ZERO_SHA256),
                    "Hash SHA-256 APK diverso: download non sicuro");
            assertTrue("The original failed file remains available for diagnostics",
                    context.fixture().isFile());
        } finally {
            context.cleanUp();
        }
    }

    @Test
    public void matchingDigestDoesNotMakeUnsignedNonApkBytesInstallable() throws Exception {
        onDisposableAndroid11Emulator();
        SandboxContext context =
                new SandboxContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        try {
            byte[] synthetic = context.writeFakeApk(100_001);
            assertRejected(
                    context,
                    offer(sha256Hex(synthetic)),
                    "Pacchetto o versione non compatibili");
            assertTrue(context.fixture().isFile());
        } finally {
            context.cleanUp();
        }
    }
}
