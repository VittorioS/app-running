package it.example.passolibero;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * ADR-002/#33: read-only signing and version characterization on a disposable Android 11 emulator.
 *
 * <p>Copies the CI-installed debug APK to a UUID-scoped cache directory; no test signing keys,
 * external downloads, installs, release APKs or user data are accessed or modified.
 */
@RunWith(AndroidJUnit4.class)
public final class UpdateSigningVersionCharacterizationTest {
    private static void onDisposableAndroid11() {
        assumeTrue("Android 11/API 30 only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Run signed-APK fixture only on disposable CI emulator",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    private static final class SandboxContext extends ContextWrapper {
        private final String prefix = "issue33-signing-" + UUID.randomUUID() + "-";
        private final File root;
        private final File downloads;

        SandboxContext(Context actual) {
            super(actual);
            root = new File(actual.getCacheDir(), prefix);
            downloads = new File(root, "download-fixtures");
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return getBaseContext().getSharedPreferences(prefix + name, mode);
        }

        @Override
        public File getExternalFilesDir(String type) {
            assertEquals(Environment.DIRECTORY_DOWNLOADS, type);
            if (!downloads.isDirectory() && !downloads.mkdirs()) {
                throw new AssertionError("Cannot create isolated download fixture directory");
            }
            return downloads;
        }

        File copyInstalledDebugApk(String name) throws IOException {
            File copied = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
            try (InputStream input =
                            new FileInputStream(getBaseContext().getApplicationInfo().sourceDir);
                    FileOutputStream output = new FileOutputStream(copied)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }
            assertTrue("The signed APK fixture must meet the existing minimum size",
                    copied.length() >= 100_000);
            assertTrue("The signed APK fixture must meet the existing maximum size",
                    copied.length() <= 300_000_000);
            return copied;
        }

        void cleanUp() {
            File[] files = downloads.listFiles();
            if (files != null) {
                for (File file : files) {
                    assertTrue("Delete only our cache copy", file.delete());
                }
            }
            if (downloads.exists()) assertTrue(downloads.delete());
            if (root.exists()) assertTrue(root.delete());
            getBaseContext().deleteSharedPreferences(prefix + "passolibero");
        }
    }

    private static String sha256Hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    private static String sha256File(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
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
        final File copy;
        final UpdateManager.Offer offer;
        final PackageInfo archive;

        Fixture(SandboxContext context, File copy, UpdateManager.Offer offer,
                PackageInfo archive) {
            this.context = context;
            this.copy = copy;
            this.offer = offer;
            this.archive = archive;
        }
    }

    private static Fixture newFixture() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SandboxContext context = new SandboxContext(target);
        try {
            PackageManager pm = target.getPackageManager();
            PackageInfo installed =
                    pm.getPackageInfo(target.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            assertNotNull(installed.signingInfo);
            Signature[] signer = installed.signingInfo.getApkContentsSigners();
            assertNotNull(signer);
            assertEquals(1, signer.length);
            assertTrue(installed.getLongVersionCode() > 0);
            String name =
                    UpdatePolicy.assetName(UpdateManager.CHANNEL_DEBUG, installed.getLongVersionCode());
            File copy = context.copyInstalledDebugApk(name);
            PackageInfo archive =
                    pm.getPackageArchiveInfo(
                            copy.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
            assertNotNull("The real signed debug APK must be parseable from the cache copy", archive);
            assertNotNull("APK signing info must be available on API 30", archive.signingInfo);
            Signature[] archiveSigners = archive.signingInfo.getApkContentsSigners();
            assertNotNull(archiveSigners);
            assertEquals(1, archiveSigners.length);
            assertEquals(target.getPackageName(), archive.packageName);
            assertEquals(installed.getLongVersionCode(), archive.getLongVersionCode());
            UpdateManager.Offer offer =
                    new UpdateManager.Offer(
                            UpdateManager.CHANNEL_DEBUG,
                            UpdatePolicy.DEBUG_TAG,
                            name,
                            sha256File(copy),
                            sha256Hex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(archiveSigners[0].toByteArray())),
                            archive.getLongVersionCode(),
                            copy.length());
            return new Fixture(context, copy, offer, archive);
        } catch (Exception | AssertionError error) {
            context.cleanUp();
            throw error;
        }
    }

    @Test
    public void sameVersionIsRejectedEvenForAuthenticallySignedApkWithCorrectHash()
            throws Exception {
        onDisposableAndroid11();
        Fixture fixture = newFixture();
        try {
            try {
                UpdateManager.verify(fixture.context, fixture.offer);
                fail("An APK with the already-installed version must be refused");
            } catch (IOException rejected) {
                assertEquals("Pacchetto o versione non compatibili", rejected.getMessage());
            }
        } finally {
            fixture.context.cleanUp();
        }
    }

    @Test
    public void realInstalledAndCopiedApkHaveIdenticalSignerAndFingerprint()
            throws Exception {
        onDisposableAndroid11();
        Fixture fixture = newFixture();
        try {
            PackageInfo installed =
                    fixture.context.getPackageManager().getPackageInfo(
                            fixture.context.getPackageName(),
                            PackageManager.GET_SIGNING_CERTIFICATES);
            Signature[] current = installed.signingInfo.getApkContentsSigners();
            Signature[] fromArchive = fixture.archive.signingInfo.getApkContentsSigners();
            assertEquals(1, current.length);
            assertEquals(1, fromArchive.length);
            assertArrayEquals(current[0].toByteArray(), fromArchive[0].toByteArray());
            String actualFingerprint =
                    sha256Hex(MessageDigest.getInstance("SHA-256").digest(fromArchive[0].toByteArray()));
            assertEquals(actualFingerprint, fixture.offer.certificateSha256);
            assertTrue(UpdatePolicy.validSha256(actualFingerprint));
        } finally {
            fixture.context.cleanUp();
        }
    }

    @Test
    public void changingSignedApkBytesIsRejectedByHashBeforeVersionOrSigning()
            throws Exception {
        onDisposableAndroid11();
        Fixture fixture = newFixture();
        try {
            try (RandomAccessFile writable = new RandomAccessFile(fixture.copy, "rw")) {
                int first = writable.read();
                writable.seek(0);
                writable.write(first ^ 0x01);
            }
            try {
                UpdateManager.verify(fixture.context, fixture.offer);
                fail("Mutated APK bytes must not pass digest verification");
            } catch (IOException rejected) {
                assertEquals("Hash SHA-256 APK diverso: download non sicuro",
                        rejected.getMessage());
            }
        } finally {
            fixture.context.cleanUp();
        }
    }
}
