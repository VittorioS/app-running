import it.example.passolibero.UpdateMetadataReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class UpdateMetadataReaderTest {
    private static void check(boolean yes, String msg) {
        if (!yes) throw new AssertionError(msg);
    }
    private static void rejects(byte[] bytes) {
        try {
            UpdateMetadataReader.readJson(new ByteArrayInputStream(bytes));
            throw new AssertionError("Metadati invalidi accettati");
        } catch (IOException expected) { }
    }
    public static void main(String[] args) throws Exception {
        String json = "{\"channel\":\"debug-pr-2\",\"versionCode\":10063,\"city\":\"Salerno\"}";
        check(json.equals(UpdateMetadataReader.readJson(new ByteArrayInputStream(
                json.getBytes(StandardCharsets.UTF_8)))), "JSON GitHub");
        check("àè".equals(UpdateMetadataReader.readJson(new ByteArrayInputStream(
                "àè".getBytes(StandardCharsets.UTF_8)))), "UTF-8");
        byte[] max = new byte[16_384];
        Arrays.fill(max, (byte) 'a');
        check(UpdateMetadataReader.readJson(new ByteArrayInputStream(max)).length()
                == 16_384, "Limite massimo accettato");
        rejects(new byte[16_385]);
        rejects(new byte[0]);
        System.out.println("PASS: metadati aggiornamento compatibili con API 30, limite 16 KB");
    }
}
