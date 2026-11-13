package it.example.passolibero.data.remote;

import it.example.passolibero.UpdateMetadataReader;
import it.example.passolibero.application.update.UpdateManifestPort;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;

/**
 * Original GitHub release metadata HTTP transport, isolated from security validation.
 *
 * <p>The existing UpdateManager constructs the only trusted URL after checking the
 * selected channel. This adapter preserves HTTP headers, timeouts, redirect behavior,
 * the 16-KiB UTF-8 limit and all network error strings. It never downloads an APK.
 */
public final class GithubReleaseMetadataAdapter implements UpdateManifestPort {
    @FunctionalInterface
    public interface ConnectionSource {
        HttpURLConnection open(URL url) throws IOException;
    }

    private final ConnectionSource connections;

    public GithubReleaseMetadataAdapter() {
        this(url -> (HttpURLConnection) url.openConnection());
    }

    public GithubReleaseMetadataAdapter(ConnectionSource connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override
    public String read(String trustedManifestUrl) throws IOException {
        HttpURLConnection connection = connections.open(new URL(trustedManifestUrl));
        try {
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(12_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setInstanceFollowRedirects(true);

            int status = connection.getResponseCode();
            if (status == 404) return null;
            if (status != 200) throw new IOException("GitHub: HTTP " + status);
            try (InputStream input = connection.getInputStream()) {
                return UpdateMetadataReader.readJson(input);
            }
        } finally {
            connection.disconnect();
        }
    }
}
