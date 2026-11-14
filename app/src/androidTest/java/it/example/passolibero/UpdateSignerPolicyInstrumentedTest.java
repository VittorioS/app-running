package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.pm.Signature;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.IOException;
import java.security.MessageDigest;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Issue #87: synthetic signer/pin checks exercising the exact comparison used by verify().
 *
 * <p>No APK files, package installation, keys, network access, or user data. Run on an
 * ephemeral Android 11/API 30 emulator, not on a personal handset.
 */
@RunWith(AndroidJUnit4.class)
public final class UpdateSignerPolicyInstrumentedTest {
    private static void disposableAndroid11Only() {
        assumeTrue("Android 11/API 30 required", Build.VERSION.SDK_INT == 30);
        assumeTrue("Never run signing fixtures on personal devices",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    private static Signature signer(int firstByte) {
        return new Signature(new byte[] {(byte) firstByte, 2, 3, 4, 5});
    }

    private static String fingerprint(Signature signature) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    private static void assertRejected(Signature[] installed, Signature[] candidate, String pin,
                                       String message) throws Exception {
        try {
            UpdateManager.verifySigningIdentity(installed, candidate, pin);
            fail("Untrusted signing identity must not pass verification");
        } catch (IOException rejected) {
            assertEquals(message, rejected.getMessage());
        }
    }

    @Test public void matchingSignerAndPinnedFingerprintAreAccepted() throws Exception {
        disposableAndroid11Only();
        Signature installed = signer(1);
        Signature candidateWithSameBytes = signer(1);
        UpdateManager.verifySigningIdentity(
                new Signature[] {installed}, new Signature[] {candidateWithSameBytes},
                fingerprint(candidateWithSameBytes));
    }

    @Test public void differentSigningCertificateIsRejected() throws Exception {
        disposableAndroid11Only();
        Signature installed = signer(1);
        Signature other = signer(9);
        assertRejected(new Signature[] {installed}, new Signature[] {other},
                fingerprint(other), "Firma dell'APK diversa da quella installata");
    }

    @Test public void validFormatButIncorrectPinnedFingerprintIsRejected() throws Exception {
        disposableAndroid11Only();
        Signature signer = signer(1);
        assertRejected(new Signature[] {signer}, new Signature[] {signer},
                "0".repeat(64), "Certificato dell'APK diverso da quello dichiarato");
    }

    @Test public void absentOrMultipleSignersAreRejected() throws Exception {
        disposableAndroid11Only();
        Signature signer = signer(1);
        String correctPin = fingerprint(signer);
        String mismatch = "Firma dell'APK diversa da quella installata";
        assertRejected(null, new Signature[] {signer}, correctPin, mismatch);
        assertRejected(new Signature[] {signer}, null, correctPin, mismatch);
        assertRejected(new Signature[0], new Signature[0], correctPin, mismatch);
        assertRejected(new Signature[] {signer},
                new Signature[] {signer, signer(2)}, correctPin, mismatch);
        assertRejected(new Signature[] {signer, signer(2)},
                new Signature[] {signer, signer(2)}, correctPin, mismatch);
    }
}
