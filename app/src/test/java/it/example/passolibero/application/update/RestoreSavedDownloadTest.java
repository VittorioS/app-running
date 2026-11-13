package it.example.passolibero.application.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/** P8b/#34: characterize restore decisions with no Android, network or installer. */
public final class RestoreSavedDownloadTest {
    private static final String HASH = "a".repeat(64);
    private static final String CERT = "B".repeat(64);

    private static final class FakeReader implements RestoreSavedDownload.Reader {
        RestoreSavedDownload.Snapshot snapshot;
        String requestedChannel;
        int calls;

        @Override
        public RestoreSavedDownload.Snapshot readForChannel(String channel) {
            calls++;
            requestedChannel = channel;
            return snapshot;
        }
    }

    private static RestoreSavedDownload.Snapshot saved(
            long id, long version, String hash, String cert) {
        return new RestoreSavedDownload.Snapshot(id, version, hash, cert);
    }

    @Test
    public void missingOrOtherChannelIsIgnoredWithoutClearingSavedMetadata() {
        FakeReader reader = new FakeReader();
        RestoreSavedDownload.Decision decision =
                new RestoreSavedDownload(reader).execute("debug", 42L);
        assertSame(RestoreSavedDownload.Outcome.IGNORE, decision.outcome);
        assertNull(decision.snapshot);
        assertEquals("debug", reader.requestedChannel);
        assertEquals(1, reader.calls);
    }

    @Test
    public void validDebugDownloadPreservesIdMetadataTagAndAssetName() {
        FakeReader reader = new FakeReader();
        reader.snapshot = saved(91L, 43L, HASH, CERT);
        RestoreSavedDownload.Decision decision =
                new RestoreSavedDownload(reader).execute("debug", 42L);
        assertSame(RestoreSavedDownload.Outcome.RESTORE, decision.outcome);
        assertSame(reader.snapshot, decision.snapshot);
        assertEquals(91L, decision.snapshot.downloadId);
        assertEquals("debug-pr-2", decision.tag);
        assertEquals("passolibero-debug-v43.apk", decision.assetName);
        assertEquals(HASH, decision.snapshot.sha256);
        assertEquals(CERT, decision.snapshot.certificateSha256);
        assertEquals(1, reader.calls);
    }

    @Test
    public void validStableDownloadUsesTheExistingStableTag() {
        FakeReader reader = new FakeReader();
        reader.snapshot = saved(11L, 55L, HASH, CERT);
        RestoreSavedDownload.Decision decision =
                new RestoreSavedDownload(reader).execute("stable", 54L);
        assertSame(RestoreSavedDownload.Outcome.RESTORE, decision.outcome);
        assertEquals("stable", decision.tag);
        assertEquals("passolibero-stable-v55.apk", decision.assetName);
    }

    @Test
    public void staleOrOversizedVersionIsRejectedExactlyAsBefore() {
        FakeReader reader = new FakeReader();
        RestoreSavedDownload restore = new RestoreSavedDownload(reader);
        reader.snapshot = saved(11L, 42L, HASH, CERT);
        assertSame(
                RestoreSavedDownload.Outcome.CLEAR,
                restore.execute("stable", 42L).outcome);
        reader.snapshot = saved(11L, (long) Integer.MAX_VALUE + 1, HASH, CERT);
        assertSame(
                RestoreSavedDownload.Outcome.CLEAR,
                restore.execute("stable", 42L).outcome);
    }

    @Test
    public void missingOrMalformedApkAndCertificateHashAreRejected() {
        FakeReader reader = new FakeReader();
        RestoreSavedDownload restore = new RestoreSavedDownload(reader);
        reader.snapshot = saved(11L, 43L, "", CERT);
        assertSame(
                RestoreSavedDownload.Outcome.CLEAR,
                restore.execute("debug", 42L).outcome);
        reader.snapshot = saved(11L, 43L, HASH, "invalid");
        assertSame(
                RestoreSavedDownload.Outcome.CLEAR,
                restore.execute("debug", 42L).outcome);
    }

    @Test
    public void unexpectedChannelCannotSynthesizeAnApkName() {
        FakeReader reader = new FakeReader();
        reader.snapshot = saved(11L, 43L, HASH, CERT);
        RestoreSavedDownload.Decision decision =
                new RestoreSavedDownload(reader).execute("unknown", 42L);
        assertSame(RestoreSavedDownload.Outcome.CLEAR, decision.outcome);
        assertNull(decision.snapshot);
    }
}
