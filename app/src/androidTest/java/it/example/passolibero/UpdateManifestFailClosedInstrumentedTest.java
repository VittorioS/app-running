package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import it.example.passolibero.application.update.UpdateManifestPort;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Issue #87: malformed metadata must never produce an updater offer.
 *
 * <p>Runs only on disposable API 30 emulators. All manifests are in memory; no GitHub requests,
 * APK downloads, installer intents, signing keys, or user preferences are accessed.
 */
@RunWith(AndroidJUnit4.class)
public final class UpdateManifestFailClosedInstrumentedTest {
    private static final String PACKAGE = "it.example.passolibero";
    private static final String TAG = "debug-pr-2";
    private static final String HASH = "a".repeat(64);
    private static final String CERT = "b".repeat(64);
    private static final long INSTALLED = 100;
    private static final long OFFERED = 101;
    private static final String TRUSTED_MANIFEST =
            "https://github.com/VittorioS/app-running/releases/download/debug-pr-2/update.json";

    private static void disposableAndroid11Only() {
        assumeTrue("Android 11/API 30 required", Build.VERSION.SDK_INT == 30);
        assumeTrue("Never use live updater fixtures on personal devices",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    private static final class FakeManifest implements UpdateManifestPort {
        final String content;
        String requestedUrl;
        int reads;

        FakeManifest(String content) {
            this.content = content;
        }

        @Override public String read(String url) {
            requestedUrl = url;
            reads++;
            return content;
        }
    }

    private static JSONObject validManifest(long version) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("schema", 1);
        json.put("channel", TAG);
        json.put("packageName", PACKAGE);
        json.put("versionCode", version);
        json.put("apk", UpdatePolicy.assetName("debug", version));
        json.put("sha256", HASH);
        json.put("certificateSha256", CERT);
        return json;
    }

    private static UpdateManager.Offer check(FakeManifest fixture, long installed)
            throws Exception {
        return UpdateManager.check("debug", PACKAGE, installed, fixture);
    }

    private static void assertRejected(JSONObject manifest, String expectedMessage)
            throws Exception {
        FakeManifest fake = new FakeManifest(manifest.toString());
        try {
            check(fake, INSTALLED);
            fail("Invalid metadata must never produce an offer");
        } catch (IOException rejected) {
            assertEquals(expectedMessage, rejected.getMessage());
        }
        assertEquals(1, fake.reads);
        assertEquals(TRUSTED_MANIFEST, fake.requestedUrl);
    }

    @Test public void validMetadataProducesOnlyTrustedDeterministicOffer() throws Exception {
        disposableAndroid11Only();
        FakeManifest fake = new FakeManifest(validManifest(OFFERED).toString());
        UpdateManager.Offer offer = check(fake, INSTALLED);
        assertNotNull(offer);
        assertEquals("debug", offer.channel);
        assertEquals(TAG, offer.tag);
        assertEquals(OFFERED, offer.versionCode);
        assertEquals("passolibero-debug-v101.apk", offer.name);
        assertEquals(HASH, offer.sha256);
        assertEquals(CERT, offer.certificateSha256);
        assertEquals("https://github.com/VittorioS/app-running/releases/download/"
                + TAG + "/" + offer.name, offer.url);
        assertEquals(TRUSTED_MANIFEST, fake.requestedUrl);
        assertEquals(1, fake.reads);
    }

    @Test public void malformedJsonIsRejectedWithoutOffer() throws Exception {
        disposableAndroid11Only();
        FakeManifest fake = new FakeManifest("{\"schema\":1");
        try {
            check(fake, INSTALLED);
            fail("Malformed JSON must not be accepted");
        } catch (JSONException expected) {
            assertNotNull(expected);
        }
        assertEquals(1, fake.reads);
        assertEquals(TRUSTED_MANIFEST, fake.requestedUrl);
    }

    @Test public void wrongOrMissingSchemaIsRejected() throws Exception {
        disposableAndroid11Only();
        JSONObject wrong = validManifest(OFFERED);
        wrong.put("schema", 2);
        assertRejected(wrong, "Canale o pacchetto dell'aggiornamento non validi");
        JSONObject missing = validManifest(OFFERED);
        missing.remove("schema");
        assertRejected(missing, "Canale o pacchetto dell'aggiornamento non validi");
    }

    @Test public void wrongChannelAndPackageAreRejected() throws Exception {
        disposableAndroid11Only();
        JSONObject channel = validManifest(OFFERED);
        channel.put("channel", "stable");
        assertRejected(channel, "Canale o pacchetto dell'aggiornamento non validi");
        JSONObject pkg = validManifest(OFFERED);
        pkg.put("packageName", "invalid.package");
        assertRejected(pkg, "Canale o pacchetto dell'aggiornamento non validi");
    }

    @Test public void apkNameMustMatchOfferedVersion() throws Exception {
        disposableAndroid11Only();
        JSONObject wrong = validManifest(OFFERED);
        wrong.put("apk", "passolibero-debug-v102.apk");
        assertRejected(wrong, "Il nome dell'APK non corrisponde alla versione");
    }

    @Test public void malformedAndMissingApkHashesAreRejected() throws Exception {
        disposableAndroid11Only();
        JSONObject shortHash = validManifest(OFFERED);
        shortHash.put("sha256", "abc");
        assertRejected(shortHash, "Hash dell'APK o del certificato non valido");
        JSONObject missingHash = validManifest(OFFERED);
        missingHash.remove("sha256");
        assertRejected(missingHash, "Hash dell'APK o del certificato non valido");
    }

    @Test public void malformedAndMissingCertificateHashesAreRejected() throws Exception {
        disposableAndroid11Only();
        JSONObject shortHash = validManifest(OFFERED);
        shortHash.put("certificateSha256", "wrong-pin");
        assertRejected(shortHash, "Hash dell'APK o del certificato non valido");
        JSONObject missingHash = validManifest(OFFERED);
        missingHash.remove("certificateSha256");
        assertRejected(missingHash, "Hash dell'APK o del certificato non valido");
    }

    @Test public void sameOrOlderVersionNeverProducesAnOffer() throws Exception {
        disposableAndroid11Only();
        for (long version : new long[] {INSTALLED, INSTALLED - 1}) {
            FakeManifest fake = new FakeManifest(validManifest(version).toString());
            assertNull(check(fake, INSTALLED));
            assertEquals(1, fake.reads);
        }
    }

    @Test public void versionBeyondAndroidLimitNeverProducesAnOffer() throws Exception {
        disposableAndroid11Only();
        JSONObject json = validManifest(OFFERED);
        json.put("versionCode", (long) Integer.MAX_VALUE + 1L);
        FakeManifest fake = new FakeManifest(json.toString());
        assertNull(check(fake, INSTALLED));
        assertEquals(1, fake.reads);
    }

    @Test public void invalidRequestedChannelIsRejectedBeforeNetwork() throws Exception {
        disposableAndroid11Only();
        FakeManifest fake = new FakeManifest(validManifest(OFFERED).toString());
        try {
            UpdateManager.check("untrusted", PACKAGE, INSTALLED, fake);
            fail("Unknown channel must not reach metadata transport");
        } catch (IOException rejected) {
            assertEquals("Canale non valido", rejected.getMessage());
        }
        assertEquals(0, fake.reads);
    }

    @Test public void missingManifestProducesNoOffer() throws Exception {
        disposableAndroid11Only();
        FakeManifest missing = new FakeManifest(null);
        assertNull(check(missing, INSTALLED));
        assertEquals(1, missing.reads);
        assertTrue(missing.requestedUrl.startsWith("https://github.com/"));
    }
}
