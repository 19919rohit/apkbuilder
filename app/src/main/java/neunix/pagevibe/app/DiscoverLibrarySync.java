package neunix.pagevibe.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/**
 * Wires a completed Discover download into LibraryManager, using the
 * Firestore book's OWN title as the display name and OWN coverUrl as the
 * cover — so a downloaded Discover book shows up in the Library with its
 * real cover art immediately, instead of waiting for the user to open it
 * (which would otherwise generate a rendered page-1 thumbnail via the
 * normal PdfActivity.onPdfOpened() flow).
 *
 * Runs at most once per book — a SharedPreferences flag prevents
 * re-fetching the same cover image over the network on every subsequent
 * app resume/progress-poll tick.
 */
public class DiscoverLibrarySync {

    private static final String PREFS_NAME = "pagevibe_prefs";
    private static final String KEY_PREFIX = "discover_lib_synced_";

    private DiscoverLibrarySync() {}

    public static void syncIfNeeded(Context context, DiscoverBook book, Uri downloadedFileUri) {
        if (book == null || downloadedFileUri == null) return;

        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String flagKey = KEY_PREFIX + book.getBookId();
        if (prefs.getBoolean(flagKey, false)) return; // already synced once

        new Thread(() -> {
            try {
                LibraryManager libraryManager = new LibraryManager(appContext);
                String fileName = book.getTitle() + ".pdf";

                libraryManager.addOrTouch(downloadedFileUri, fileName);
                libraryManager.setCustomName(downloadedFileUri, book.getTitle());

                if (!book.getCoverUrl().isEmpty()) {
                    libraryManager.setCoverFromRemoteUrl(downloadedFileUri, book.getCoverUrl());
                }

                prefs.edit().putBoolean(flagKey, true).apply();
            } catch (Throwable ignored) {
                // A failed cover fetch must never crash anything — the
                // book still opens fine, just without a custom cover
                // until the user opens it once (normal fallback path).
            }
        }, "DiscoverLibrarySync").start();
    }
}