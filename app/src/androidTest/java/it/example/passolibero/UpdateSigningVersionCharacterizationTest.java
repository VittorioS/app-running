package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Environment;
import android.test.mock.MockPackageManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * ADR-002/#33: end-to-end read-only verification of the already installed emulator debug APK.
 *
 * <p>Only temporary app-cache copies and isolated preferences are used. No GitHub release,
 * signing secret, real user install, DownloadManager request or Android installer is involved.
 */
@RunWith(AndroidJUnit4.class)
public final class UpdateSigningVersionCharacterizationTest {
    private static final String WRONG_CERTIFICATE_SHA256 =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private static void emulatorApi30Only() {
        assumeTrue("Android 11/API 30 required", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Only disposable CI emulators may run update signer fixtures",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    /** Delegates to the genuine API 30 package parser; only the installed version is faked. */
    private static final class VersionOverridePackageManager extends MockPackageManager {
        private final PackageManager actual;
        private final boolean installedVersionOlder;

        VersionOverridePackageManager(PackageManager actual, boolean installedVersionOlder) {
            this.actual = actual;
            this.installedVersionOlder = installedVersionOlder;
        }

        @Override
        public PackageInfo getPackageArchiveInfo(String path, int flags) {
            return actual.getPackageArchiveInfo(path, flags);
        }

        @Override
        public PackageInfo getPackageInfo(String packageName, int flags)
                throws PackageManager.NameNotFoundException {
            PackageInfo real = actual.getPackageInfo(packageName, flags);
            if (!installedVersionOlder) return real;
            // Do not mutate PackageManager's real installed PackageInfo or signing metadata.
            PackageInfo simulated = new PackageInfo();
            simulated.packageName = real.packageName;
            simulated.signingInfo = real.signingInfo;
            simulated.setLongVersionCode(real.getLongVersionCode() - 1);
            return simulated;
        }
    }

    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue33-signing-" + UUID.randomUUID() + "-";
        private final File root;
        private final File downloads;
        private final PackageManager parser;

        SandboxContext(Context actual, boolean fakeOldInstalledVersion) {
            super(actual);
            root = new File(actual.getCacheDir(), prefix);
            downloads = new File(root, "download-fixtures");
            parser =
                    new VersionOverridePackageManager(
                            actual.getPackageManager(), fakeOldInstalledVersion);
        }

        @Override
        public PackageManager getPackageManager() {
            return parser;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return getBaseContext().getSharedPreferences(prefix + name, mode);
        }

        @Override
        public File getExternalFilesDir(String type) {
            assertEquals(Environment.DIRECTORY_DOWNLOADS, type);
            if (!downloads.isDirectory() && !downloads.mkdirs()) {
                throw new AssertionError("Cannot create isolated update-fixture directory");
            }
            return downloads;
        }

        void copyInstalledDebugApk(File target) throws IOException {
            String installedPath = getBaseContext().getApplicationInfo().sourceDir;
            try (InputStream input = new FileInputStream(installedPath);
                    FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
            assertTrue("Fixture must meet the existing minimum APK size", target.length() >= 100_000);
            assertTrue("Fixture must meet the existing maximum APK size", target.length() <= 300_000_000);
        }

        void cleanUp() {
            File[] copied = downloads.listFiles();
            if (copied != null) {
                for (File file : copied) {
                    assertTrue("Delete only our isolated APK copy", file.delete());
                }
            }
            if (downloads.exists()) assertTrue(downloads.delete());
            if (root.exists()) assertTrue(root.delete());
            getBaseContext().deleteSharedPreferences(prefix + "passolibero");
        }
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(Character.forDigit((value >>> 4) & 15, 16));
            hex.append(Character.forDigit(value & 15, 16));
        }
        return hex.toString();
    }

    private static String sha256File(File apk) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(apk)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return sha256Hex(digest.digest());
    }

    private static final class Fixture {
        final SandboxContext context;
        final UpdateManager.Offer expectedOffer;

        Fixture(SandboxContext context, UpdateManager.Offer expectedOffer) {
            this.context = context;
            this.expectedOffer = expectedOffer;
        }
    }

    private static Fixture newFixture(boolean pretendOlderInstalledVersion) throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SandboxContext context = new SandboxContext(target, pretendOlderInstalledVersion);
        try {
            PackageManager real = target.getPackageManager();
            PackageInfo apkInfo =
                    real.getPackageArchiveInfo(
                            target.getApplicationInfo().sourceDir,
                            PackageManager.GET_SIGNING_CERTIFICATES);
            assertNotNull("The emulator debug APK must be parseable", apkInfo);
            assertNotNull("The actual signed debug APK must have signing metadata", apkInfo.signingInfo);
            Signature[] signers = apkInfo.signingInfo.getApkContentsSigners();
            assertNotNull(signers);
            assertEquals("Only a single-signing-certificate fixture is supported", 1, signers.length);
            assertEquals(target.getPackageName(), apkInfo.packageName);
            long version = apkInfo.getLongVersionCode();
            assertTrue("The real debug APK has a positive versionCode", version > 0);
            if (pretendOlderInstalledVersion) {
                assertTrue("Installed version must be simulated as an older positive version", version > 1);
            }
            String name = UpdatePolicy.assetName(UpdateManager.CHANNEL_DEBUG, version);
            File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
            context.copyInstalledDebugApk(file);
            UpdateManager.Offer offer =
                    new UpdateManager.Offer(
                            UpdateManager.CHANNEL_DEBUG,
                            UpdatePolicy.DEBUG_TAG,
                            name,
                            sha256File(file),
                            sha256Hex(MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray())),
                            version,
                            file.length());
            return new Fixture(context, offer);
        } catch (Exception | AssertionError error) {
            context.cleanUp();
            throw error;
        }
    }

    @Test
    public void sameVersionIsRejectedEvenWithRealValidSignedBytesAndMatchingHash()
            throws Exception {
        emulatorApi30Only();
        Fixture fixture = newFixture(false);
        try {
            try {
                UpdateManager.verify(fixture.context, fixture.expectedOffer);
                fail("An update with the currently installed version must not pass");
            } catch (IOException rejected) {
                assertEquals("Pacchetto o versione non compatibili", rejected.getMessage());
            }
        } finally {
            fixture.context.cleanUp();
        }
    }

    @Test
    public void matchingSignerAndPinPassVerificationWithOnlyVersionSimulatedOlder()
            throws Exception {
        emulatorApi30Only();
        Fixture fixture = newFixture(true);
        try {
            // The existing platform parser checks authentic signed debug APK bytes;
            // the mocked PackageManager changes only the apparent installed version.
            UpdateManager.verify(fixture.context, fixture.expectedOffer);
            assertFalse(
                    "Verification cannot silently activate a tracking session",
                    AppPrefs.get(fixture.context).getBoolean("active", false));
        } finally {
            fixture.context.cleanUp();
        }
    }

    @Test
    public void mismatchedDeclaredCertificatePinIsRejectedAfterSignerMatches()
            throws Exception {
        emulatorApi30Only();
        Fixture fixture = newFixture(true);
        try {
            UpdateManager.Offer wrongCertificate =
                    new UpdateManager.Offer(
                            fixture.expectedOffer.channel,
                            fixture.expectedOffer.tag,
                            fixture.expectedOffer.name,
                            fixture.expectedOffer.sha256,
                            WRONG_CERTIFICATE_SHA256,
                            fixture.expectedOffer.versionCode,
                            fixture.expectedOffer.size);
            try {
                UpdateManager.verify(fixture.context, wrongCertificate);
                fail("Matching APK signing certificate cannot bypass the declared pin");
            } catch (IOException rejected) {
                assertEquals(
                        "Certificato dell'APK diverso da quello dichiarato",
                        rejected.getMessage());
            }
        } finally {
            fixture.context.cleanUp();
        }
    }
}
