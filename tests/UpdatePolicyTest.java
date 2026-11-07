import it.example.passolibero.UpdatePolicy;

public final class UpdatePolicyTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(UpdatePolicy.newer(10060, 10061), "Nuova build");
        check(!UpdatePolicy.newer(10060, 10060), "Nessuna reinstallazione");
        check(!UpdatePolicy.newer(10060, 10059), "Nessun downgrade");
        check(!UpdatePolicy.newer(10060, Integer.MAX_VALUE + 1L), "Limite Android");
        String asset = UpdatePolicy.assetName("debug", 10061);
        check("passolibero-debug-v10061.apk".equals(asset), "Asset deterministico");
        check(UpdatePolicy.trustedAssetUrl(
            "https://github.com/VittorioS/app-running/releases/download/debug-pr-2/" + asset,
            "debug-pr-2", asset), "Solo repository ufficiale");
        check(!UpdatePolicy.trustedAssetUrl(
            "https://evil.test/VittorioS/app-running/releases/download/debug-pr-2/" + asset,
            "debug-pr-2", asset), "Host estraneo non valido");
        check(!UpdatePolicy.trustedAssetUrl(
            "http://github.com/VittorioS/app-running/releases/download/debug-pr-2/" + asset,
            "debug-pr-2", asset), "Solo HTTPS");
        check(!UpdatePolicy.trustedAssetUrl(
            "https://github.com/VittorioS/app-running/releases/download/debug-pr-2/" + asset,
            "../bad", asset), "Nessun path traversal");
        check(UpdatePolicy.validSha256("a".repeat(64)), "Hash valido");
        check(!UpdatePolicy.validSha256("xyz"), "Hash invalido");
        System.out.println("PASS: versionCode, hash e URL del canale update");
    }
}
