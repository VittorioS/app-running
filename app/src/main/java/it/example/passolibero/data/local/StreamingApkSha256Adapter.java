package it.example.passolibero.data.local;

import it.example.passolibero.application.update.ApkSha256Port;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Stream the exact APK bytes to SHA-256 using the legacy 64-KiB buffer.
 *
 * <p>File size and expected hash are validated by UpdateManager before and after this read. No APK
 * is downloaded, trusted, or installed by this adapter.
 */
public final class StreamingApkSha256Adapter implements ApkSha256Port {
    @FunctionalInterface
    public interface InputSource {
        InputStream open(File apk) throws IOException;
    }

    private final InputSource source;

    public StreamingApkSha256Adapter() {
        this(FileInputStream::new);
    }

    public StreamingApkSha256Adapter(InputSource source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public byte[] digest(File apk) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = source.open(apk)) {
            byte[] bytes = new byte[64 * 1024];
            int count;
            while ((count = stream.read(bytes)) >= 0) {
                if (count > 0) digest.update(bytes, 0, count);
            }
        }
        return digest.digest();
    }
}
