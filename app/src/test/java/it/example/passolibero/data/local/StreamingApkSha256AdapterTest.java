package it.example.passolibero.data.local;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.Test;

/** ADR-002/#33 G1: characterize streaming APK SHA-256 with fake file IO. */
public final class StreamingApkSha256AdapterTest {
    @Test
    public void computesExactDigestAcrossMultiple64KiBChunksAndClosesSource()
            throws Exception {
        byte[] input = new byte[200_011];
        for (int index = 0; index < input.length; index++) {
            input[index] = (byte) (index * 37 + 11);
        }
        File selected = new File("synthetic-apk-not-on-disk");
        int[] calls = {0};
        int[] maximumRequested = {0};
        boolean[] closed = {false};
        StreamingApkSha256Adapter subject =
                new StreamingApkSha256Adapter(
                        file -> {
                            assertSame(selected, file);
                            calls[0]++;
                            return new ByteArrayInputStream(input) {
                                @Override
                                public synchronized int read(byte[] buffer, int offset, int length) {
                                    maximumRequested[0] = Math.max(maximumRequested[0], length);
                                    return super.read(buffer, offset, Math.min(length, 4_097));
                                }

                                @Override
                                public void close() throws IOException {
                                    closed[0] = true;
                                    super.close();
                                }
                            };
                        });

        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(input), subject.digest(selected));
        assertEquals(1, calls[0]);
        assertEquals(64 * 1024, maximumRequested[0]);
        assertTrue("The APK input stream must always close", closed[0]);
    }

    @Test
    public void emptyInputReturnsStandardSha256InsteadOfAPlaceholder() throws Exception {
        StreamingApkSha256Adapter subject =
                new StreamingApkSha256Adapter(file -> new ByteArrayInputStream(new byte[0]));

        assertArrayEquals(
                MessageDigest.getInstance("SHA-256").digest(new byte[0]),
                subject.digest(new File("unused")));
    }

    @Test
    public void zeroByteReadDoesNotTerminateDigestBeforeRealBytes() throws Exception {
        byte[] input = "abc".getBytes(StandardCharsets.US_ASCII);
        StreamingApkSha256Adapter subject =
                new StreamingApkSha256Adapter(
                        file ->
                                new ByteArrayInputStream(input) {
                                    boolean zeroFirst = true;

                                    @Override
                                    public synchronized int read(
                                            byte[] buffer, int offset, int length) {
                                        if (zeroFirst) {
                                            zeroFirst = false;
                                            return 0;
                                        }
                                        return super.read(buffer, offset, length);
                                    }
                                });

        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(input), subject.digest(new File("unused")));
    }

    @Test
    public void preservesOriginalReadExceptionAndClosesStream() throws Exception {
        IOException failure = new IOException("simulated storage read failure");
        boolean[] closed = {false};
        StreamingApkSha256Adapter subject =
                new StreamingApkSha256Adapter(
                        file ->
                                new InputStream() {
                                    boolean first = true;

                                    @Override
                                    public int read() throws IOException {
                                        throw failure;
                                    }

                                    @Override
                                    public int read(byte[] buffer, int offset, int length)
                                            throws IOException {
                                        if (first) {
                                            first = false;
                                            buffer[offset] = 42;
                                            return 1;
                                        }
                                        throw failure;
                                    }

                                    @Override
                                    public void close() {
                                        closed[0] = true;
                                    }
                                });

        try {
            subject.digest(new File("unused"));
            fail("A partial APK must never pass integrity validation");
        } catch (IOException caught) {
            assertSame(failure, caught);
        }
        assertTrue(closed[0]);
    }

    @Test
    public void preservesFailureToOpenWithoutReadingOrCreatingFiles() throws Exception {
        IOException failure = new IOException("APK file missing");
        int[] attempts = {0};
        StreamingApkSha256Adapter subject =
                new StreamingApkSha256Adapter(
                        file -> {
                            attempts[0]++;
                            throw failure;
                        });

        try {
            subject.digest(new File("not-created.apk"));
            fail("An unreadable APK cannot be accepted");
        } catch (IOException caught) {
            assertSame(failure, caught);
        }
        assertEquals(1, attempts[0]);
    }
}
