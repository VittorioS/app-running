package it.example.passolibero.application.update;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

/** APK byte digest boundary. Callers must still validate size, expected hash and signing. */
public interface ApkSha256Port {
    byte[] digest(File apk) throws IOException, NoSuchAlgorithmException;
}
