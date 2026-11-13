package it.example.passolibero;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import it.example.passolibero.data.remote.GithubReleaseMetadataAdapter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;

/** ADR-002/#33: bounded HTTP metadata compatibility on a disposable Android 11 emulator. */
@RunWith(AndroidJUnit4.class)
public final class UpdateManifestHttpInstrumentedTest {
    private static final String MANIFEST =
            "https://github.com/VittorioS/app-running/releases/download/debug-pr-2/update.json";

    private static void disposableAndroid11Only() {
        assumeTrue("Android 11/API 30 required", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Never exercise updater code on a personal device in this fixture",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    private static final class FakeConnection extends HttpURLConnection {
        byte[] payload;
        boolean disconnected;
        boolean streamClosed;

        FakeConnection(URL url, byte[] payload) {
            super(url);
            this.payload = payload;
        }

        @Override
        public int getResponseCode() {
            return 200;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(payload) {
                @Override
                public void close() throws IOException {
                    streamClosed = true;
                    super.close();
                }
            };
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public void connect() {}
    }

    @Test
    public void api30ReadsUtf8MetadataViaInjectedConnectionWithoutNetwork() throws IOException {
        disposableAndroid11Only();
        String json = "{\"schema\":1,\"city\":\"Salernò\"}";
        FakeConnection connection =
                new FakeConnection(new URL(MANIFEST), json.getBytes(StandardCharsets.UTF_8));

        assertEquals(
                json,
                new GithubReleaseMetadataAdapter(url -> connection).read(MANIFEST));
        assertTrue(connection.disconnected);
        assertTrue(connection.streamClosed);
        assertEquals(12_000, connection.getConnectTimeout());
        assertEquals(12_000, connection.getReadTimeout());
    }

    @Test
    public void api30RejectsOversizedResponseAndClosesAllResources() throws IOException {
        disposableAndroid11Only();
        FakeConnection connection = new FakeConnection(new URL(MANIFEST), new byte[16_385]);
        try {
            new GithubReleaseMetadataAdapter(url -> connection).read(MANIFEST);
            fail("The 16-KiB metadata limit must hold on Android 11");
        } catch (IOException caught) {
            assertEquals("Metadati troppo grandi", caught.getMessage());
        }
        assertTrue(connection.disconnected);
        assertTrue(connection.streamClosed);
    }
}
