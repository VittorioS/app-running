package it.example.passolibero.application.update;

import it.example.passolibero.UpdatePolicy;
import java.util.Objects;

/**
 * Android-free decision for restoring an existing updater download.
 *
 * <p>The legacy UpdatePolicy is a pure validator; it stays the single source for version, digest
 * format and asset naming while the updater migrates one boundary at a time. A restored download
 * still requires full APK and signing verification before any installer intent is issued.
 */
public final class RestoreSavedDownload {
    public enum Outcome {
        IGNORE,
        CLEAR,
        RESTORE
    }

    public interface Reader {
        Snapshot readForChannel(String selectedChannel);
    }

    public static final class Snapshot {
        public final long downloadId;
        public final long versionCode;
        public final String sha256;
        public final String certificateSha256;

        public Snapshot(long downloadId, long versionCode, String sha256, String certificateSha256) {
            this.downloadId = downloadId;
            this.versionCode = versionCode;
            this.sha256 = sha256;
            this.certificateSha256 = certificateSha256;
        }
    }

    public static final class Decision {
        public final Outcome outcome;
        public final Snapshot snapshot;
        public final String tag;
        public final String assetName;

        private Decision(Outcome outcome, Snapshot snapshot, String tag, String assetName) {
            this.outcome = outcome;
            this.snapshot = snapshot;
            this.tag = tag;
            this.assetName = assetName;
        }
    }

    private final Reader reader;

    public RestoreSavedDownload(Reader reader) {
        this.reader = Objects.requireNonNull(reader);
    }

    public Decision execute(String selectedChannel, long installedVersionCode) {
        Snapshot snapshot = reader.readForChannel(selectedChannel);
        if (snapshot == null) {
            return new Decision(Outcome.IGNORE, null, null, null);
        }
        if (!UpdatePolicy.newer(installedVersionCode, snapshot.versionCode)
                || !UpdatePolicy.validSha256(snapshot.sha256)
                || !UpdatePolicy.validSha256(snapshot.certificateSha256)) {
            return new Decision(Outcome.CLEAR, null, null, null);
        }
        try {
            String tag = "debug".equals(selectedChannel) ? UpdatePolicy.DEBUG_TAG : "stable";
            String assetName = UpdatePolicy.assetName(selectedChannel, snapshot.versionCode);
            return new Decision(Outcome.RESTORE, snapshot, tag, assetName);
        } catch (IllegalArgumentException invalid) {
            return new Decision(Outcome.CLEAR, null, null, null);
        }
    }
}
