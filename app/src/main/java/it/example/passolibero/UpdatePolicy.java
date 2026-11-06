package it.example.passolibero;

/** Validazioni pure di un aggiornamento prima di scaricare o installare APK. */
public final class UpdatePolicy {
    public static final String REPOSITORY = "VittorioS/app-running";
    public static final String DEBUG_TAG = "debug-pr-2";

    private UpdatePolicy() { }

    public static boolean newer(long installed, long offered) {
        return installed >= 0 && offered > installed && offered <= Integer.MAX_VALUE;
    }

    public static String assetName(String channel, long code) {
        if (code <= 0 || code > Integer.MAX_VALUE
                || (!"debug".equals(channel) && !"stable".equals(channel))) {
            throw new IllegalArgumentException("Canale o versione non validi");
        }
        return "passolibero-" + channel + "-v" + code + ".apk";
    }

    public static boolean validSha256(String sha) {
        return sha != null && sha.matches("[a-fA-F0-9]{64}");
    }

    public static boolean trustedAssetUrl(String url, String tag, String asset) {
        if (url == null || tag == null || asset == null
                || !tag.matches("[a-zA-Z0-9._-]+")
                || !asset.matches("[a-zA-Z0-9._-]+")) return false;
        return url.equals("https://github.com/" + REPOSITORY
                + "/releases/download/" + tag + "/" + asset);
    }
}
