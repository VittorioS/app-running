package it.example.passolibero.application.update;

import java.io.IOException;

/**
 * Bounded remote update-manifest read boundary.
 *
 * <p>The caller is responsible for choosing its existing trusted release URL and for
 * validating all parsed metadata. A missing manifest (HTTP 404) returns null; other
 * HTTP, network and bounded-input failures propagate as IOException.
 */
public interface UpdateManifestPort {
    String read(String trustedManifestUrl) throws IOException;
}
