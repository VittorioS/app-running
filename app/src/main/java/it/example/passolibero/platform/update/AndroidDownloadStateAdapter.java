package it.example.passolibero.platform.update;

import android.app.DownloadManager;
import android.content.Context;
import it.example.passolibero.UpdateManager;
import it.example.passolibero.application.update.ReadDownloadState;
import java.util.Objects;

/**
 * Android download status adapter. All download, URI and installer behavior stays in the
 * existing UpdateManager; this adapter only maps its status to the application contract.
 */
public final class AndroidDownloadStateAdapter implements ReadDownloadState.Port {
    private final Context context;

    public AndroidDownloadStateAdapter(Context context) {
        this.context = Objects.requireNonNull(context);
    }

    @Override
    public ReadDownloadState.State read(long downloadId) {
        return fromStatus(UpdateManager.downloadStatus(context, downloadId));
    }

    public static ReadDownloadState.State fromStatus(int androidStatus) {
        if (androidStatus == DownloadManager.STATUS_SUCCESSFUL) {
            return ReadDownloadState.State.READY_TO_VERIFY;
        }
        if (androidStatus == DownloadManager.STATUS_FAILED) {
            return ReadDownloadState.State.FAILED;
        }
        // Preserve the original UI branch for pending, running, paused and unknown codes.
        return ReadDownloadState.State.WAITING;
    }
}
