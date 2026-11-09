package it.example.passolibero;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Lettura limitata dei metadati update con le API disponibili da Android 11.
 * Non usare InputStream.readNBytes(int): introdotto soltanto in Android API 33.
 */
public final class UpdateMetadataReader {
    private static final int MAX_BYTES = 16_384;
    private UpdateMetadataReader() { }

    public static String readJson(InputStream input) throws IOException {
        if (input == null) throw new IOException("Risposta GitHub assente");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read, total = 0;
        while ((read = input.read(buffer)) != -1) {
            if (read == 0) {
                // Protegge anche da InputStream che restituiscono 0 senza EOF.
                int single = input.read();
                if (single == -1) break;
                if (total >= MAX_BYTES) throw new IOException("Metadati troppo grandi");
                output.write(single);
                total++;
                continue;
            }
            if (read > MAX_BYTES - total) throw new IOException("Metadati troppo grandi");
            output.write(buffer, 0, read);
            total += read;
        }
        if (total == 0) throw new IOException("Risposta GitHub vuota");
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
