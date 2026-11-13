package it.example.passolibero;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import it.example.passolibero.data.local.StreamingApkSha256Adapter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.Test;
import org.junit.runner.RunWith;

/** ADR-002/#33: APK digest with fake file data on a disposable API 30 emulator. */
@RunWith(AndroidJUnit4.class)
public final class UpdateApkSha256InstrumentedTest {
    private static void disposableAndroid11Only() {
        assumeTrue("Android 11/API 30 required", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "No verification on personal APKs or phones",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    @Test
    public void api30ComputesSameDigestWithoutOpeningOrInstallingAnyRealApk()
            throws Exception {
        disposableAndroid11Only();
        byte[] bytes = "abc".getBytes(StandardCharsets.US_ASCII);
        File synthetic = new File("synthetic-digest-fixture-not-on-disk");
        boolean[] closed = {false};
        StreamingApkSha256Adapter subject =
                new StreamingApkSha256Adapter(
                        file -> {
                            assertSame(synthetic, file);
                            return new ByteArrayInputStream(bytes) {
                                @Override
                                public void close() throws IOException {
                                    closed[0] = true;
                                    super.close();
                                }
                            };
                        });

        assertArrayEquals(
                MessageDigest.getInstance("SHA-256").digest(bytes), subject.digest(synthetic));
        assertTrue(closed[0]);
    }

    @Test
    public void api30PropagatesReadFailureWithoutReturningPartialDigest()
            throws Exception {
        disposableAndroid11Only();
        IOException failure = new IOException("APK unavailable");
        StreamingApkSha256Adapter subject =
                new StreamingApkSha256Adapter(
                        file -> {
                            throw failure;
                        });

        try {
            subject.digest(new File("missing-fixture.apk"));
            fail("An unavailable APK must not be accepted");
        } catch (IOException caught) {
            assertSame(failure, caught);
        }
    }
}
