package neunix.pagevibe;

import android.content.Context;
import android.graphics.RectF;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import io.legere.pdfiumandroid.PdfDocument;
import io.legere.pdfiumandroid.PdfPage;
import io.legere.pdfiumandroid.PdfTextPage;
import io.legere.pdfiumandroid.PdfiumCore;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Text/word extraction backed by PDFium (io.legere pdfiumandroid binding)
 * — the same native engine PdfCore.kt uses for rendering.
 *
 * TOC/bookmarks/document-metadata are intentionally NOT sourced from this
 * library (see extractOutline()/getTitle() below) — the app has its own
 * TOC/bookmark system and doesn't need this library's embedded-outline or
 * metadata surface, which was also the part of the 2.x API that isn't
 * stable across versions.
 *
 * ROBUSTNESS MODEL: every public method returns a safe, non-null default
 * (empty string / empty list / null) rather than throw, and every native
 * call is wrapped in catch(Throwable) — native failures can surface as
 * Error subtypes (OutOfMemoryError etc.), not just Exception. A page that
 * fails once is "poisoned" and never retried for the life of this open
 * document.
 */
public class PdfTextExtractor {

    private static final String TAG = "PdfTextExtractor";

    // Hard ceiling on characters processed per page. A maliciously bloated
    // "single page with millions of characters" PDF is a known technique
    // for hanging text-extraction code; capping bounds the worst case.
    private static final int MAX_CHARS_PER_PAGE = 200_000;

    // PDFium's native char-box API reports coordinates in PDF space,
    // which has its origin at the BOTTOM-LEFT of the page with Y
    // increasing upward — the opposite of Android's top-left canvas
    // convention. Without flipping, a word near the top of the page gets
    // placed near the bottom margin (usually blank — "highlight on empty
    // area of page"), and every word's true line is inverted, which is
    // also why the wrong word appeared highlighted. This MUST be true
    // for a page-point-space RectF straight from the native layer.
    private static final boolean FLIP_VERTICAL = true;

    // Small bounded cache of full-page word data. TTS and search both
    // read the same page's word data, often within moments of each other
    // (e.g. pressing Next/Prev repeatedly on the same page), so caching
    // avoids re-walking every character on the page from scratch each
    // time. Capped small and cleared on open()/close() so it can never
    // grow unbounded or serve stale data from a previous document.
    private static final int WORD_DATA_CACHE_SIZE = 4;

    // =========================================================
    // MODELS — unchanged shapes for TocEntry/SearchResult/WordBox/
    // PageWordData, so downstream callers keep working. MatchGroup is
    // new (see findMatchGroups()).
    // =========================================================

    public static class TocEntry {
        public final String title;
        public final int    page;
        public final int    depth;
        public TocEntry(String title, int page, int depth) {
            this.title = title; this.page = page; this.depth = depth;
        }
    }

    public static class SearchResult {
        public final int    page;
        public final String snippet;
        public final int    charOffset;
        public SearchResult(int page, String snippet, int charOffset) {
            this.page = page; this.snippet = snippet; this.charOffset = charOffset;
        }
    }

    public static class WordBox {
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;
        public final String word;
        public final int id;
        public final int charStart;
        public final int charEnd;

        public WordBox(float left, float top, float right, float bottom, String word,
                       int id, int charStart, int charEnd) {
            this.left = left; this.top = top;
            this.right = right; this.bottom = bottom;
            this.word = word;
            this.id = id;
            this.charStart = charStart;
            this.charEnd = charEnd;
        }

        public WordBox(float left, float top, float right, float bottom, String word) {
            this(left, top, right, bottom, word, -1, -1, -1);
        }
    }

    public static class PageWordData {
        public final List<WordBox> words;
        public final String        text;
        public PageWordData(List<WordBox> words, String text) {
            this.words = words;
            this.text  = text;
        }
    }

    /**
     * One occurrence of a search query on a page, as the exact set of
     * WordBox entries (in order) that make it up — one entry for a
     * single-word query, several for a phrase. Every word in every group
     * carries its own real, stable id from the page's canonical text, so
     * callers can mark "this occurrence is the active one" by id rather
     * than by fragile list position.
     */
    public static class MatchGroup {
        public final List<WordBox> words;
        public MatchGroup(List<WordBox> words) { this.words = words; }
    }

    // =========================================================
    // STATE
    // =========================================================

    private Context      appContext;
    private PdfiumCore   core;
    private PdfDocument  document;
    private ParcelFileDescriptor pfd;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // Native calls are not guaranteed thread-safe across concurrent
    // callers; every method below synchronizes on this. In practice all
    // callers already funnel through a single background executor
    // (PdfReaderController.getBgExecutor()), but this is defense-in-depth.
    private final Object lock = new Object();

    // Pages that threw once — never retried this session.
    private final Set<Integer> poisonedPages = ConcurrentHashMap.newKeySet();

    // LRU-ish cache of full-page word data, guarded by `lock`.
    private final LinkedHashMap<Integer, PageWordData> pageWordDataCache =
            new LinkedHashMap<Integer, PageWordData>(WORD_DATA_CACHE_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, PageWordData> eldest) {
                    return size() > WORD_DATA_CACHE_SIZE;
                }
            };

    // =========================================================
    // INIT
    // =========================================================

    public void init(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void open(File pdfFile) throws Exception {
        close();
        if (pdfFile == null || !pdfFile.exists() || pdfFile.length() <= 0L) {
            throw new IllegalStateException("PDF file missing or empty");
        }
        synchronized (lock) {
            ParcelFileDescriptor descriptor = null;
            try {
                descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                // Uses the Kotlin bridge — see PdfiumFactory.kt for why.
                PdfiumCore newCore = PdfiumFactory.createCore(appContext);
                PdfDocument newDoc = newCore.newDocument(descriptor);

                int pages = safePageCount(newDoc);
                if (pages <= 0) {
                    safeCloseDocument(newDoc);
                    throw new IllegalStateException("PDF opened but reports 0 pages");
                }

                this.pfd      = descriptor;
                this.core     = newCore;
                this.document = newDoc;
                poisonedPages.clear();
                pageWordDataCache.clear();
                initialized.set(true);
            } catch (Throwable t) {
                if (descriptor != null) {
                    try { descriptor.close(); } catch (Throwable ignored) {}
                }
                initialized.set(false);
                throw (t instanceof Exception) ? (Exception) t : new Exception(t);
            }
        }
    }

    public void close() {
        synchronized (lock) {
            if (document != null) safeCloseDocument(document);
            if (pfd != null) {
                try { pfd.close(); } catch (Throwable ignored) {}
            }
            document    = null;
            core        = null;
            pfd         = null;
            initialized.set(false);
            poisonedPages.clear();
            pageWordDataCache.clear();
        }
    }

    private void safeCloseDocument(PdfDocument doc) {
        try { doc.close(); } catch (Throwable t) {
            Log.e(TAG, "document.close() failed: " + t.getMessage());
        }
    }

    public boolean isOpen() { return initialized.get() && document != null; }

    private int safePageCount(PdfDocument doc) {
        try { return doc.getPageCount(); } catch (Throwable t) { return 0; }
    }

    // =========================================================
    // PAGE SIZE
    // =========================================================

    public float[] getPageSize(int pageIndex) {
        if (!isOpen() || pageIndex < 0 || poisonedPages.contains(pageIndex)) return null;
        synchronized (lock) {
            PdfPage page = null;
            try {
                if (pageIndex >= safePageCount(document)) return null;
                page = document.openPage(pageIndex);
                int w = page.getPageWidthPoint();
                int h = page.getPageHeightPoint();
                if (w <= 0 || h <= 0 || w > 20000 || h > 20000) return null;
                return new float[]{ (float) w, (float) h };
            } catch (Throwable t) {
                Log.e(TAG, "getPageSize(" + pageIndex + ") failed: " + t.getMessage());
                poisonedPages.add(pageIndex);
                return null;
            } finally {
                closePageQuietly(page);
            }
        }
    }

    // =========================================================
    // PLAIN TEXT EXTRACTION
    // =========================================================

    public String extractPageText(int pageIndex) {
        if (!isOpen() || pageIndex < 0 || poisonedPages.contains(pageIndex)) return "";
        synchronized (lock) {
            PdfPage page = null;
            PdfTextPage textPage = null;
            try {
                if (pageIndex >= safePageCount(document)) return "";
                page = document.openPage(pageIndex);
                textPage = page.openTextPage();

                int charCount = safeCharCount(textPage);
                if (charCount <= 0) return "";

                String text = safeGetText(textPage, 0, charCount);
                return text != null ? text.trim() : "";
            } catch (Throwable t) {
                Log.e(TAG, "extractPageText(" + pageIndex + ") failed: " + t.getMessage());
                poisonedPages.add(pageIndex);
                return "";
            } finally {
                closeTextPageQuietly(textPage);
                closePageQuietly(page);
            }
        }
    }

    // =========================================================
    // WORD BOX EXTRACTION — per-character boxes from PDFium, grouped into
    // words on whitespace boundaries.
    // =========================================================

    public List<WordBox> extractWordBoxes(int pageIndex) {
        List<WordBox> boxes = new ArrayList<>();
        if (!isOpen() || pageIndex < 0 || poisonedPages.contains(pageIndex)) return boxes;

        synchronized (lock) {
            PdfPage page = null;
            PdfTextPage textPage = null;
            try {
                if (pageIndex >= safePageCount(document)) return boxes;
                page = document.openPage(pageIndex);

                int rawW = safeGetPageWidth(page);
                int rawH = safeGetPageHeight(page);
                float pageW = (rawW > 0 && rawW <= 20000) ? rawW : 612f;
                float pageH = (rawH > 0 && rawH <= 20000) ? rawH : 792f;

                textPage = page.openTextPage();
                int charCount = safeCharCount(textPage);
                if (charCount <= 0) return boxes;

                String fullText = safeGetText(textPage, 0, charCount);
                if (fullText == null) return boxes;

                StringBuilder wordBuf = new StringBuilder();
                float wLeft = 0, wTop = 0, wRight = 0, wBottom = 0;
                boolean inWord = false;

                int n = Math.min(charCount, fullText.length());
                for (int i = 0; i < n; i++) {
                    char ch = fullText.charAt(i);
                    boolean isSpace = Character.isWhitespace(ch);

                    if (isSpace) {
                        if (inWord && wordBuf.length() > 0) {
                            boxes.add(makeWordBox(wordBuf, wLeft, wTop, wRight, wBottom, pageW, pageH));
                        }
                        wordBuf.setLength(0);
                        inWord = false;
                        continue;
                    }

                    RectF box = safeGetCharBox(textPage, i);
                    if (box == null) {
                        wordBuf.append(ch);
                        continue;
                    }

                    float rawLeft = box.left, rawRight = box.right;
                    float rawTop = box.top, rawBottom = box.bottom;

                    if (!isFinite(rawLeft) || !isFinite(rawRight)
                            || !isFinite(rawTop) || !isFinite(rawBottom)) {
                        wordBuf.append(ch);
                        continue;
                    }

                    // Normalise regardless of which field is numerically
                    // larger — defends against either axis convention.
                    float glyphLeft   = Math.min(rawLeft, rawRight);
                    float glyphRight  = Math.max(rawLeft, rawRight);
                    float glyphTop    = Math.min(rawTop, rawBottom);
                    float glyphBottom = Math.max(rawTop, rawBottom);

                    if (FLIP_VERTICAL) {
                        float flippedTop    = pageH - glyphBottom;
                        float flippedBottom = pageH - glyphTop;
                        glyphTop    = flippedTop;
                        glyphBottom = flippedBottom;
                    }

                    if (!inWord) {
                        wLeft = glyphLeft; wTop = glyphTop;
                        wRight = glyphRight; wBottom = glyphBottom;
                        inWord = true;
                    } else {
                        wLeft   = Math.min(wLeft, glyphLeft);
                        wRight  = Math.max(wRight, glyphRight);
                        wTop    = Math.min(wTop, glyphTop);
                        wBottom = Math.max(wBottom, glyphBottom);
                    }
                    wordBuf.append(ch);
                }

                if (inWord && wordBuf.length() > 0) {
                    boxes.add(makeWordBox(wordBuf, wLeft, wTop, wRight, wBottom, pageW, pageH));
                }

            } catch (Throwable t) {
                Log.e(TAG, "extractWordBoxes(" + pageIndex + ") failed: " + t.getMessage());
                poisonedPages.add(pageIndex);
            } finally {
                closeTextPageQuietly(textPage);
                closePageQuietly(page);
            }
        }
        return boxes;
    }

    private WordBox makeWordBox(StringBuilder wordBuf, float left, float top, float right, float bottom,
                                 float pageW, float pageH) {
        String word = wordBuf.toString().trim();
        wordBuf.setLength(0);
        return new WordBox(
                clamp01(left / pageW),
                clamp01(top / pageH),
                clamp01(right / pageW),
                clamp01(bottom / pageH),
                word.isEmpty() ? "?" : word);
    }

    /**
     * Builds this page's word list WITH a stable per-word identity: each
     * word gets a unique sequential id and an exact character range within
     * a freshly-built canonical text string (words joined by single
     * spaces, in order). This is THE single source of truth both TTS and
     * search now read from, so "which occurrence you navigated to" and
     * "which word gets highlighted" can never disagree — they're computed
     * from the exact same text. Cached per page (see class docs).
     */
    public PageWordData extractPageWordData(int pageIndex) {
        synchronized (lock) {
            PageWordData cached = pageWordDataCache.get(pageIndex);
            if (cached != null) return cached;
        }

        List<WordBox> raw = extractWordBoxes(pageIndex);
        List<WordBox> withIdentity = new ArrayList<>(raw.size());
        StringBuilder canonical = new StringBuilder();

        for (int i = 0; i < raw.size(); i++) {
            WordBox wb = raw.get(i);
            if (canonical.length() > 0) canonical.append(' ');
            int start = canonical.length();
            canonical.append(wb.word);
            int end = canonical.length();
            withIdentity.add(new WordBox(
                    wb.left, wb.top, wb.right, wb.bottom, wb.word,
                    i, start, end));
        }

        PageWordData data = new PageWordData(withIdentity, canonical.toString());

        synchronized (lock) {
            pageWordDataCache.put(pageIndex, data);
        }
        return data;
    }

    /**
     * Finds every occurrence of query on a page, each as the exact group
     * of WordBox entries that make it up, found by scanning the SAME
     * canonical text searchAll() scans — so occurrence N here is always
     * occurrence N in the SearchResult list for this page, guaranteed by
     * construction rather than by hoping two separate extractions agree.
     */
    public List<MatchGroup> findMatchGroups(int pageIndex, String query) {
        List<MatchGroup> groups = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return groups;

        PageWordData data = extractPageWordData(pageIndex);
        if (data.words.isEmpty() || data.text.isEmpty()) return groups;

        String lowerQuery = query.toLowerCase().trim();
        if (lowerQuery.isEmpty()) return groups;
        String lowerText = data.text.toLowerCase();

        int searchFrom = 0;
        int cursor = 0;
        while (true) {
            int idx = lowerText.indexOf(lowerQuery, searchFrom);
            if (idx < 0) break;
            int matchStart = idx;
            int matchEnd = idx + lowerQuery.length();

            while (cursor < data.words.size() && data.words.get(cursor).charEnd <= matchStart) {
                cursor++;
            }
            List<WordBox> groupWords = new ArrayList<>();
            int scan = cursor;
            while (scan < data.words.size() && data.words.get(scan).charStart < matchEnd) {
                WordBox wb = data.words.get(scan);
                if (wb.charEnd > matchStart) groupWords.add(wb);
                scan++;
            }
            if (!groupWords.isEmpty()) groups.add(new MatchGroup(groupWords));

            searchFrom = idx + Math.max(1, lowerQuery.length());
        }
        return groups;
    }

    // =========================================================
    // SEARCH — reads the SAME canonical text findMatchGroups() reads, so
    // result count/order/offsets always line up with the boxes drawn.
    // =========================================================

    public List<SearchResult> searchAll(String query, int totalPages) {
        List<SearchResult> results = new ArrayList<>();
        if (!isOpen() || query == null || query.trim().isEmpty()) return results;

        String lowerQuery = query.toLowerCase().trim();
        int safeTotalPages = Math.max(0, totalPages);

        for (int i = 0; i < safeTotalPages; i++) {
            if (poisonedPages.contains(i)) continue;

            PageWordData data;
            try {
                data = extractPageWordData(i);
            } catch (Throwable t) {
                poisonedPages.add(i);
                continue;
            }
            if (data == null || data.text == null || data.text.isEmpty()) continue;

            String text = data.text; // already single-space-joined — no extra normalising needed
            String lowerText = text.toLowerCase();

            int searchFrom = 0;
            while (true) {
                int idx = lowerText.indexOf(lowerQuery, searchFrom);
                if (idx < 0) break;

                int snippetStart = Math.max(0, idx - 20);
                int snippetEnd   = Math.min(text.length(), idx + query.length() + 40);
                String snippet   = "…" + text.substring(snippetStart, snippetEnd).trim() + "…";

                results.add(new SearchResult(i, snippet, idx));
                searchFrom = idx + Math.max(1, query.length());
            }
        }
        return results;
    }

    // =========================================================
    // TOC — INTENTIONALLY EMPTY. This app has its own TOC/bookmark
    // system (PdfTocController / PdfBookmarkController). PdfTocController
    // .buildFor() already falls back to auto-generated, evenly-spaced
    // page milestones whenever this returns an empty list.
    // =========================================================

    public List<TocEntry> extractOutline() {
        return new ArrayList<>();
    }

    // =========================================================
    // DOCUMENT INFO — INTENTIONALLY DISABLED. PdfReaderController already
    // falls back to a cleaned-up filename whenever this returns null.
    // =========================================================

    public String getTitle() {
        return null;
    }

    // =========================================================
    // SAFE NATIVE-CALL HELPERS
    // =========================================================

    private int safeCharCount(PdfTextPage textPage) {
        try {
            long count = textPage.textPageCountChars();
            if (count < 0) return 0;
            return (int) Math.min(count, MAX_CHARS_PER_PAGE);
        } catch (Throwable t) {
            return 0;
        }
    }

    private String safeGetText(PdfTextPage textPage, int start, int count) {
        try {
            return textPage.textPageGetText(start, count);
        } catch (Throwable t) {
            Log.e(TAG, "textPageGetText failed: " + t.getMessage());
            return null;
        }
    }

    private RectF safeGetCharBox(PdfTextPage textPage, int index) {
        try {
            return textPage.textPageGetCharBox(index);
        } catch (Throwable t) {
            return null;
        }
    }

    private int safeGetPageWidth(PdfPage page) {
        try { return page.getPageWidthPoint(); } catch (Throwable t) { return 0; }
    }

    private int safeGetPageHeight(PdfPage page) {
        try { return page.getPageHeightPoint(); } catch (Throwable t) { return 0; }
    }

    private void closePageQuietly(PdfPage page) {
        if (page == null) return;
        try { page.close(); } catch (Throwable t) {
            Log.e(TAG, "page.close() failed: " + t.getMessage());
        }
    }

    private void closeTextPageQuietly(PdfTextPage textPage) {
        if (textPage == null) return;
        try { textPage.close(); } catch (Throwable t) {
            Log.e(TAG, "textPage.close() failed: " + t.getMessage());
        }
    }

    private static boolean isFinite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v)) return 0f;
        return Math.max(0f, Math.min(1f, v));
    }
}