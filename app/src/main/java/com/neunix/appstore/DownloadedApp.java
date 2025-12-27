package com.neunix.appstore;

/**
 * Represents an APK downloaded via DownloadManager.
 */
public class DownloadedApp {

    public String name;       // App display name
    public String localUri;   // Local URI of the downloaded APK file

    /**
     * Constructor.
     *
     * @param name     Name of the app
     * @param localUri Local URI of the downloaded APK
     */
    public DownloadedApp(String name, String localUri) {
        this.name = name;
        this.localUri = localUri;
    }

    @Override
    public String toString() {
        return "DownloadedApp{" +
                "name='" + name + '\'' +
                ", localUri='" + localUri + '\'' +
                '}';
    }
}