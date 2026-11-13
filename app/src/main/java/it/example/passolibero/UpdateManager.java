package it.example.passolibero;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;

import it.example.passolibero.application.update.UpdateManifestPort;
import it.example.passolibero.data.remote.GithubReleaseMetadataAdapter;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/** Download e verifica locale degli update. Nessuna installazione silenziosa. */
public final class UpdateManager {
    public static final String CHANNEL_DEBUG = "debug";
    public static final String CHANNEL_STABLE = "stable";
    private static final String BASE =
            "https://github.com/VittorioS/app-running/releases/download/";
    private static final String DEBUG_TAG = "debug-pr-2";
    private static final String STABLE_TAG = "stable";
    private UpdateManager() { }

    public static final class Offer {
        public final String channel, tag, name, url, sha256, certificateSha256;
        public final long versionCode, size;

        Offer(String channel, String tag, String name, String sha256,
              String certificateSha256, long code, long size) {
            this.channel = channel;
            this.tag = tag;
            this.name = name;
            this.url = BASE + tag + "/" + name;
            this.sha256 = sha256.toLowerCase(Locale.ROOT);
            this.certificateSha256 = certificateSha256.toLowerCase(Locale.ROOT);
            this.versionCode = code;
            this.size = size;
        }
    }

    /** La sorgente deve essere un tag e un nome APK deterministici, non URL arbitrari dal JSON. */
    public static Offer check(String channel, String packageName, long installedCode)
            throws Exception {
        String tag = CHANNEL_DEBUG.equals(channel) ? DEBUG_TAG
                : CHANNEL_STABLE.equals(channel) ? STABLE_TAG
                : null;
        if (tag == null) throw new IOException("Canale non valido");

        // Il tag è già stato scelto da una whitelist: nessun URL dal manifest.
        UpdateManifestPort manifest = new GithubReleaseMetadataAdapter();
        String json = manifest.read(BASE + tag + "/update.json");
        if (json == null) return null; // HTTP 404, nessuna release per il canale.
        JSONObject data = new JSONObject(json);
        if (data.optInt("schema", -1) != 1
                || !("it.example.passolibero").equals(data.optString("packageName"))
                || !packageName.equals(data.optString("packageName"))
                || !tag.equals(data.optString("channel"))) {
            throw new IOException("Canale o pacchetto dell'aggiornamento non validi");
        }
        long offered = data.optLong("versionCode", -1);
        if (!UpdatePolicy.newer(installedCode, offered)) return null;
        String filename = UpdatePolicy.assetName(channel, offered);
        if (!filename.equals(data.optString("apk"))) {
            throw new IOException("Il nome dell'APK non corrisponde alla versione");
        }
        String digest = data.optString("sha256");
        String cert = data.optString("certificateSha256");
        if (!UpdatePolicy.validSha256(digest) || !UpdatePolicy.validSha256(cert)) {
            throw new IOException("Hash dell'APK o del certificato non valido");
        }
        String url = BASE + tag + "/" + filename;
        if (!UpdatePolicy.trustedAssetUrl(url, tag, filename)) {
            throw new IOException("Origine aggiornamento non attendibile");
        }
        return new Offer(channel, tag, filename, digest, cert, offered, -1);
    }

    public static long enqueue(Context context, Offer offer) throws IOException {
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) throw new IOException("Cartella download non disponibile");
        File destination = new File(directory, offer.name);
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Impossibile rimuovere il vecchio file");
        }
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(offer.url));
        request.setTitle("PassoLibero - " + offer.channel + " " + offer.versionCode);
        request.setDescription("Download aggiornamento firmato");
        request.setMimeType("application/vnd.android.package-archive");
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS,
                offer.name);
        DownloadManager downloads = context.getSystemService(DownloadManager.class);
        if (downloads == null) throw new IOException("DownloadManager non disponibile");
        return downloads.enqueue(request);
    }

    public static int downloadStatus(Context context, long id) {
        DownloadManager downloads = context.getSystemService(DownloadManager.class);
        if (downloads == null || id <= 0) return DownloadManager.STATUS_FAILED;
        try (android.database.Cursor cursor =
                     downloads.query(new DownloadManager.Query().setFilterById(id))) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            }
        }
        return DownloadManager.STATUS_FAILED;
    }

    public static void verify(Context context, Offer offer) throws Exception {
        if (AppPrefs.get(context).getBoolean("active", false)) {
            throw new IOException("Termina la sessione prima di installare un aggiornamento");
        }
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) throw new IOException("Cartella download non disponibile");
        File apk = new File(directory, offer.name);
        if (!apk.isFile() || apk.length() < 100_000 || apk.length() > 300_000_000) {
            throw new IOException("File APK mancante o dimensione non valida");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream stream = new FileInputStream(apk)) {
            byte[] bytes = new byte[64 * 1024];
            int count;
            while ((count = stream.read(bytes)) >= 0) {
                if (count > 0) digest.update(bytes, 0, count);
            }
        }
        if (!hex(digest.digest()).equals(offer.sha256)) {
            throw new IOException("Hash SHA-256 APK diverso: download non sicuro");
        }
        PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(
                apk.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo installed = pm.getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (archive == null || archive.signingInfo == null
                || installed.signingInfo == null
                || !context.getPackageName().equals(archive.packageName)
                || archive.getLongVersionCode() != offer.versionCode
                || archive.getLongVersionCode() <= installed.getLongVersionCode()) {
            throw new IOException("Pacchetto o versione non compatibili");
        }
        Signature[] expected = installed.signingInfo.getApkContentsSigners();
        Signature[] actual = archive.signingInfo.getApkContentsSigners();
        if (expected == null || actual == null || expected.length != actual.length
                || expected.length != 1 || !Arrays.equals(expected[0].toByteArray(),
                actual[0].toByteArray())) {
            throw new IOException("Firma dell'APK diversa da quella installata");
        }
        MessageDigest certDigest = MessageDigest.getInstance("SHA-256");
        if (!hex(certDigest.digest(actual[0].toByteArray())).equals(offer.certificateSha256)) {
            throw new IOException("Certificato dell'APK diverso da quello dichiarato");
        }
    }

    public static boolean canInstall(Context context) {
        return context.getPackageManager().canRequestPackageInstalls();
    }

    public static Intent installPermissionIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
    }

    public static Intent installIntent(Context context, long downloadId) throws IOException {
        DownloadManager downloads = context.getSystemService(DownloadManager.class);
        if (downloads == null || downloadId <= 0) throw new IOException("Download non trovato");
        Uri uri = downloads.getUriForDownloadedFile(downloadId);
        if (uri == null || !"content".equals(uri.getScheme())) {
            throw new IOException("Non è possibile condividere il file APK con Android");
        }
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(digits[(b >>> 4) & 15]);
            result.append(digits[b & 15]);
        }
        return result.toString();
    }
}
