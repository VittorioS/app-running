package it.example.passolibero;

import static org.junit.Assert.assertSame;
import static org.junit.Assume.assumeTrue;

import android.app.DownloadManager;
import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import it.example.passolibero.application.update.ReadDownloadState;
import it.example.passolibero.platform.update.AndroidDownloadStateAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;

/** P8b/#34: Android 11 characterizations; no APK is downloaded or installed. */
@RunWith(AndroidJUnit4.class)
public final class AndroidDownloadStateAdapterInstrumentedTest {
    private static void requireDisposableAndroid11() {
        assumeTrue("Android 11/API 30 only", Build.VERSION.SDK_INT == 30);
        assumeTrue(
                "Never run updater fixture on a personal handset",
                Build.FINGERPRINT.contains("generic")
                        || Build.HARDWARE.contains("ranchu")
                        || Build.HARDWARE.contains("goldfish"));
    }

    @Test
    public void androidStatusesKeepExactlyTheExistingPollingBranches() {
        requireDisposableAndroid11();
        assertSame(
                ReadDownloadState.State.READY_TO_VERIFY,
                AndroidDownloadStateAdapter.fromStatus(DownloadManager.STATUS_SUCCESSFUL));
        assertSame(
                ReadDownloadState.State.FAILED,
                AndroidDownloadStateAdapter.fromStatus(DownloadManager.STATUS_FAILED));
        assertSame(
                ReadDownloadState.State.WAITING,
                AndroidDownloadStateAdapter.fromStatus(DownloadManager.STATUS_PENDING));
        assertSame(
                ReadDownloadState.State.WAITING,
                AndroidDownloadStateAdapter.fromStatus(DownloadManager.STATUS_RUNNING));
        assertSame(
                ReadDownloadState.State.WAITING,
                AndroidDownloadStateAdapter.fromStatus(DownloadManager.STATUS_PAUSED));
        assertSame(
                ReadDownloadState.State.WAITING,
                AndroidDownloadStateAdapter.fromStatus(0));
    }

    @Test
    public void invalidDownloadIdsStillFailClosedWithoutStartingDownloads() {
        requireDisposableAndroid11();
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ReadDownloadState read = new ReadDownloadState(new AndroidDownloadStateAdapter(app));
        assertSame(ReadDownloadState.State.FAILED, read.execute(0L));
        assertSame(ReadDownloadState.State.FAILED, read.execute(-1L));
    }
}
