package it.example.passolibero.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;

/** ADR-002/#33: exact legacy HTTP and bounded metadata contracts with no live network. */
public final class GithubReleaseMetadataAdapterTest {
    private static final String MANIFEST =
            "https://github.com/VittorioS/app-running/releases/download/debug-pr-2/update.json";

    private static final class FakeConnection extends HttpURLConnection {
        int code = 200;
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        boolean disconnected;
        boolean streamClosed;
        int bodyReads;
        IOException codeFailure;
        IOException bodyFailure;

        FakeConnection(URL url) {
            super(url);
        }

        @Override
        public int getResponseCode() throws IOException {
            if (codeFailure != null) throw codeFailure;
            return code;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            bodyReads++;
            if (bodyFailure != null) throw bodyFailure;
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
    public void successKeepsTrustedUrlHeadersTimeoutsBodyAndCleanup() throws IOException {
        FakeConnection connection = new FakeConnection(new URL(MANIFEST));
        String metadata = "{\"schema\":1,\"name\":\"città\"}";
        connection.payload = metadata.getBytes(StandardCharsets.UTF_8);
        URL[] openedUrl = {null};
        GithubReleaseMetadataAdapter subject =
                new GithubReleaseMetadataAdapter(
                        url -> {
                            openedUrl[0] = url;
                            return connection;
                        });

        assertEquals(metadata, subject.read(MANIFEST));
        assertEquals(MANIFEST, openedUrl[0].toString());
        assertEquals(12_000, connection.getConnectTimeout());
        assertEquals(12_000, connection.getReadTimeout());
        assertEquals("application/json", connection.getRequestProperty("Accept"));
        assertEquals("no-cache", connection.getRequestProperty("Cache-Control"));
        assertTrue(connection.getInstanceFollowRedirects());
        assertEquals(1, connection.bodyReads);
        assertTrue("InputStream must be closed", connection.streamClosed);
        assertTrue("HTTP connection must always disconnect", connection.disconnected);
    }

    @Test
    public void missingMetadataReturnsNullWithoutReadingBody() throws IOException {
        FakeConnection connection = new FakeConnection(new URL(MANIFEST));
        connection.code = 404;
        assertNull(new GithubReleaseMetadataAdapter(url -> connection).read(MANIFEST));
        assertEquals(0, connection.bodyReads);
        assertTrue(connection.disconnected);
    }

    @Test
    public void nonSuccessHttpPreservesExactLegacyErrorAndClosesConnection()
            throws IOException {
        FakeConnection connection = new FakeConnection(new URL(MANIFEST));
        connection.code = 503;
        try {
            new GithubReleaseMetadataAdapter(url -> connection).read(MANIFEST);
            fail("Only a 200 response can be parsed as signed-update metadata");
        } catch (IOException caught) {
            assertEquals("GitHub: HTTP 503", caught.getMessage());
        }
        assertEquals(0, connection.bodyReads);
        assertTrue(connection.disconnected);
    }

    @Test
    public void connectionAndStreamFailuresPropagateWithoutReturningAnOffer()
            throws IOException {
        IOException connectFailure = new IOException("connection failed");
        FakeConnection connection = new FakeConnection(new URL(MANIFEST));
        connection.codeFailure = connectFailure;
        try {
            new GithubReleaseMetadataAdapter(url -> connection).read(MANIFEST);
            fail("HTTP connection failure must be reported");
        } catch (IOException caught) {
            assertSame(connectFailure, caught);
        }
        assertTrue(connection.disconnected);

        IOException readFailure = new IOException("body unavailable");
        FakeConnection second = new FakeConnection(new URL(MANIFEST));
        second.bodyFailure = readFailure;
        try {
            new GithubReleaseMetadataAdapter(url -> second).read(MANIFEST);
            fail("HTTP body failure must be reported");
        } catch (IOException caught) {
            assertSame(readFailure, caught);
        }
        assertTrue(second.disconnected);
    }

    @Test
    public void preservesExact16KiBLimitAndRejectsEmptyResponse() throws IOException {
        FakeConnection maximum = new FakeConnection(new URL(MANIFEST));
        maximum.payload = new byte[16_384];
        Arrays.fill(maximum.payload, (byte) 'a');
        assertEquals(16_384, new GithubReleaseMetadataAdapter(url -> maximum)
                .read(MANIFEST).length());
        assertTrue(maximum.disconnected);
        assertTrue(maximum.streamClosed);

        FakeConnection oversized = new FakeConnection(new URL(MANIFEST));
        oversized.payload = new byte[16_385];
        try {
            new GithubReleaseMetadataAdapter(url -> oversized).read(MANIFEST);
            fail("Oversized update metadata must be rejected before JSON validation");
        } catch (IOException caught) {
            assertEquals("Metadati troppo grandi", caught.getMessage());
        }
        assertTrue(oversized.disconnected);
        assertTrue(oversized.streamClosed);

        FakeConnection empty = new FakeConnection(new URL(MANIFEST));
        empty.payload = new byte[0];
        try {
            new GithubReleaseMetadataAdapter(url -> empty).read(MANIFEST);
            fail("Empty update metadata must be rejected");
        } catch (IOException caught) {
            assertEquals("Risposta GitHub vuota", caught.getMessage());
        }
        assertTrue(empty.disconnected);
        assertTrue(empty.streamClosed);
    }

    @Test
    public void refusedUrlOpenPropagatesWithoutReturningMetadata() throws IOException {
        IOException error = new IOException("connection not created");
        try {
            new GithubReleaseMetadataAdapter(
                            url -> {
                                throw error;
                            })
                    .read(MANIFEST);
            fail("The caller must not interpret an unavailable manifest as an update offer");
        } catch (IOException caught) {
            assertSame(error, caught);
        }
    }
}
