package it.example.passolibero.platform.update;

import android.content.SharedPreferences;
import it.example.passolibero.application.update.RestoreSavedDownload;
import java.util.Objects;

/** Reads the existing persisted download keys without writing, deleting or enqueuing anything. */
public final class SavedDownloadPreferencesAdapter implements RestoreSavedDownload.Reader {
    private final SharedPreferences preferences;

    public SavedDownloadPreferencesAdapter(SharedPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    @Override
    public RestoreSavedDownload.Snapshot readForChannel(String selectedChannel) {
        long id = preferences.getLong("update_download_id", 0L);
        if (id <= 0 || !selectedChannel.equals(preferences.getString("update_channel", ""))) {
            return null;
        }
        return new RestoreSavedDownload.Snapshot(
                id,
                preferences.getLong("update_code", 0L),
                preferences.getString("update_sha256", ""),
                preferences.getString("update_cert_sha256", ""));
    }
}
