package neunix.pagevibe;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns PdfCore + PdfTextExtractor and is the SINGLE authoritative source for
 * "what page is the user actually looking at right now".
 *
 * Root cause of the old "read aloud reads the wrong page" bug: CurlView's
 * PageProvider.updatePage() is called for texture preparation purposes —
 * including background prefetch of neighbour pages and the actively-curling
 * page mid-animation — not only when the user has settled on a new page.
 * The old code mutated a single `currentPage` field directly inside that
 * callback, so any caller reading `currentPage` (like the TTS button) could
 * see a transient, in-flight value instead of the real settled page.
 *
 * Fix: this class exposes TWO different page numbers —
 *  - getRenderTargetPage(): used only by the texture pipeline (CurlView)
 *  - getSettledPage(): the authoritative page for UI, bookmarks, and TTS,
 *    updated only via explicit navigation calls (navigateToPage, slider
 *    commit, swipe-settle callback) — never from prefetch/animation churn.
 */
public class PdfReaderController {

    public interface Listener {
        void onPdfOpened(int totalPages, String title);
        void onPdfOpenFailed(String message);
        void onSettledPageChanged(int page);
    }

    private static final String PREFS_NAME    = "pagevibe_prefs";
    private static final String KEY_LAST_PAGE = "last_page_";

    private final Context context;
    private final Listener listener;
    private final Handler  uiHandler = new Handler(Looper.getMainLooper());

    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "PdfRender"));
    private final ExecutorService bgExecutor      = Executors.newSingleThreadExecutor(r -> new Thread(r, "PdfBg"));

    private PdfCore          core;
    private PdfTextExtractor extractor;
    private Uri               currentUri;
    private String            currentFileName = "";
    private int                totalPages   = 0;
    private int                bitmapWidth  = 0;
    private int                bitmapHeight = 0;

    private final AtomicBoolean coreReady = new AtomicBoolean(false);

    // The ONLY page number that TTS, bookmarks, and the page counter should
    // ever read. Updated exclusively through navigateToPage() / settle
    // callbacks — never from background prefetch or in-flight curl frames.
    private final AtomicInteger settledPage = new AtomicInteger(0);

    private List<Future<?>> prefetchFutures;

    public PdfReaderController(Context context, Listener listener) {
        this.context  = context.getApplicationContext();
        this.listener = listener;
    }

    // =========================================================
    // OPEN
    // =========================================================
    public void open(Uri uri) {
        closeCore();
        coreReady.set(false);
        bitmapWidth = 0; bitmapHeight = 0;
        currentUri = uri;

        renderExecutor.execute(() -> {
            try {
                PdfCore newCore = new PdfCore();
                newCore.open(context, uri);
                int pages = newCore.pageCount();
                if (pages <= 0) {
                    newCore.close();
                    uiHandler.post(() -> listener.onPdfOpenFailed("This PDF appears to be empty."));
                    return;
                }

                File cacheFile = FileUtils.getFileFromUri(context, uri);
                String fileName = FileUtils.getFileName(context, uri);

                PdfTextExtractor newExtractor = new PdfTextExtractor();
                newExtractor.init(context);
                newExtractor.open(cacheFile);

                String pdfTitle = newExtractor.getTitle();

                uiHandler.post(() -> {
                    core            = newCore;
                    extractor       = newExtractor;
                    currentFileName = fileName;
                    totalPages      = pages;
                    int restored    = restoreLastPage(uri);
                    settledPage.set(restored);
                    coreReady.set(true);

                    String title = (pdfTitle != null && !pdfTitle.trim().isEmpty())
                            ? pdfTitle.trim() : cleanFileName(fileName);
                    listener.onPdfOpened(pages, title);
                });

            } catch (Exception e) {
                uiHandler.post(() -> listener.onPdfOpenFailed("Could not open PDF: " + safeMessage(e)));
            }
        });
    }

    // =========================================================
    // PAGE PROVIDER — feeds CurlView, does NOT own settled page
    // =========================================================
    public final CurlView.PageProvider pageProvider = new CurlView.PageProvider() {
        @Override
        public int getPageCount() { return totalPages; }

        @Override
        public void updatePage(CurlPage page, int width, int height, int index) {
            if (bitmapWidth == 0 || bitmapHeight == 0) {
                bitmapWidth  = Math.max(width, 1);
                bitmapHeight = Math.max(height, 1);
                PdfCore snap = core;
                if (snap != null) snap.setScreenSize(bitmapWidth, bitmapHeight);
            }
            final int w = Math.max(width, 1), h = Math.max(height, 1);
            if (!coreReady.get() || core == null || index < 0 || index >= totalPages) {
                page.setTexture(blank(w, h), CurlPage.SIDE_BOTH);
                return;
            }
            try {
                // Force both sides to opaque white EVERY time, unconditionally
                // — never rely on CurlPage's own reset() default holding.
                // This is the second half of the grey/muddy-background fix:
                // CurlMesh multiplies each vertex's shading factor against
                // THIS blend color before drawing the page texture, so if
                // this were ever anything other than pure white (a stale
                // value from a skipped reset(), a library quirk, or future
                // code that forgets to reset it), the entire page would
                // render with a flat grey/tinted wash regardless of what
                // CurlRenderer's clear color is set to.
                page.setColor(0xFFFFFFFF, CurlPage.SIDE_FRONT);
                page.setColor(0xFFFFFFFF, CurlPage.SIDE_BACK);

                page.setTexture(safeCopy(core.renderPage(index, w, h), w, h), CurlPage.SIDE_FRONT);
                if (index + 1 < totalPages) {
                    page.setTexture(safeCopy(core.renderPage(index + 1, w, h), w, h), CurlPage.SIDE_BACK);
                } else {
                    Bitmap back = blank(w, h);
                    back.eraseColor(0xFFFFFFFF);
                    page.setTexture(back, CurlPage.SIDE_BACK);
                }
                // IMPORTANT: this callback intentionally does NOT touch
                // settledPage. Texture prep happens for prefetch and for
                // the actively-curling page — neither represents "the user
                // has settled on this page". Settlement is driven only by
                // navigateToPage() below, called from explicit user actions
                // (swipe release, slider commit, TOC tap, search jump).
            } catch (Exception e) {
                page.setTexture(blank(w, h), CurlPage.SIDE_BOTH);
            }
        }
    };

    // =========================================================
    // EXPLICIT NAVIGATION — the only path that updates settledPage
    // =========================================================
    public void navigateToPage(CurlView curlView, DrawingView drawingView,
                                java.util.Map<Integer, List<DrawingView.Stroke>> pageStrokes,
                                int page) {
        if (page < 0 || page >= totalPages) return;
        int previous = settledPage.get();
        if (page == previous) return;

        pageStrokes.put(previous, drawingView.detachStrokes());
        settledPage.set(page);
        drawingView.attachStrokes(pageStrokes.get(page));
        curlView.setCurrentIndex(page);

        PdfCore snap = core;
        if (snap != null && bitmapWidth > 0) {
            if (prefetchFutures != null) snap.cancelPrefetch(prefetchFutures);
            prefetchFutures = snap.prefetchAround(page, 2);
        }

        listener.onSettledPageChanged(page);
    }

    /**
     * Call this when CurlView itself reports a settled position change
     * (e.g. after a curl animation completes and the page actually flips,
     * as opposed to mid-drag). Wire this from CurlView's animation-complete
     * path if available; otherwise rely on navigateToPage for all
     * programmatic navigation (slider, TOC, search, bookmarks) which
     * covers the vast majority of real usage.
     */
    public void reportSettledFromGesture(int page,
                                          DrawingView drawingView,
                                          java.util.Map<Integer, List<DrawingView.Stroke>> pageStrokes) {
        int previous = settledPage.get();
        if (page == previous || page < 0 || page >= totalPages) return;
        pageStrokes.put(previous, drawingView.detachStrokes());
        settledPage.set(page);
        drawingView.attachStrokes(pageStrokes.get(page));

        PdfCore snap = core;
        if (snap != null && bitmapWidth > 0) {
            if (prefetchFutures != null) snap.cancelPrefetch(prefetchFutures);
            prefetchFutures = snap.prefetchAround(page, 2);
        }
        listener.onSettledPageChanged(page);
    }

    // =========================================================
    // ACCESSORS
    // =========================================================
    public int getSettledPage()      { return settledPage.get(); }
    public int getTotalPages()       { return totalPages; }
    public boolean isReady()         { return coreReady.get(); }
    public Uri getCurrentUri()       { return currentUri; }
    public PdfTextExtractor getExtractor() { return extractor; }
    public PdfCore getCore()         { return core; }
    public ExecutorService getBgExecutor() { return bgExecutor; }

    public void setScreenSize(int w, int h) {
        bitmapWidth = w; bitmapHeight = h;
        PdfCore snap = core;
        if (snap != null) snap.setScreenSize(w, h);
    }

    public void evictAroundSlider(int target) {
        PdfCore snap = core;
        if (snap != null) snap.evictExcept(target - 3, target + 3);
    }

    // =========================================================
    // PERSISTENCE
    // =========================================================
    public void saveLastPage() {
        if (currentUri == null || totalPages <= 0) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_LAST_PAGE + currentUri.hashCode(), settledPage.get())
                .apply();
    }

    private int restoreLastPage(Uri uri) {
        if (totalPages <= 0) return 0;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int saved = prefs.getInt(KEY_LAST_PAGE + uri.hashCode(), 0);
        return Math.max(0, Math.min(saved, totalPages - 1));
    }

    // =========================================================
    // CLEANUP
    // =========================================================
    public void closeCore() {
        coreReady.set(false);
        PdfCore old = core;
        core = null;
        if (extractor != null) { extractor.close(); extractor = null; }
        if (old != null) {
            if (!renderExecutor.isShutdown()) {
                renderExecutor.execute(() -> { try { old.close(); } catch (Exception ignored) {} });
            } else {
                new Thread(() -> { try { old.close(); } catch (Exception ignored) {} }, "PdfClose-Fallback").start();
            }
        }
    }

    public void shutdown() {
        closeCore();
        renderExecutor.shutdownNow();
        bgExecutor.shutdownNow();
    }

    // =========================================================
    // HELPERS
    // =========================================================
    private Bitmap blank(int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Bitmap.Config.ARGB_8888);
            b.eraseColor(android.graphics.Color.WHITE);
            return b;
        } catch (OutOfMemoryError e) {
            Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            b.eraseColor(android.graphics.Color.WHITE);
            return b;
        }
    }

    private Bitmap safeCopy(Bitmap src, int w, int h) {
        if (src == null || src.isRecycled()) return blank(w, h);
        try {
            Bitmap copy = src.copy(Bitmap.Config.ARGB_8888, false);
            return copy != null ? copy : src;
        } catch (OutOfMemoryError e) { return src; }
    }

    private String cleanFileName(String name) {
        if (name == null || name.isEmpty()) return "PDF";
        return name.replaceAll("(?i)\\.pdf$", "").replace("_", " ").trim();
    }

    private String safeMessage(Exception e) {
        if (e == null) return "Unknown error";
        String msg = e.getMessage();
        return (msg != null && !msg.isEmpty()) ? msg : e.getClass().getSimpleName();
    }
}