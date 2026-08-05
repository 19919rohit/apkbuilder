package neunix.pagevibe.app;

import android.content.Context;
import android.graphics.RectF;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;

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
 * TOC (extractOutline) is sourced from PDFBox-Android, opened separately
 * and briefly from the same underlying file this instance already holds
 * — PDFium's binding here has no bookmark/outline API, and PDFBox is
 * already a project dependency (used for Page Basket export), so this
 * reuses it rather than adding a second engine's worth of surface area.
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

    private static final int MAX_CHARS_PER_PAGE = 200_000;
    private static final boolean FLIP_VERTICAL = true;
    private static final int WORD_DATA_CACHE_SIZE = 4;
    private static final float TTS_TOP_MARGIN_FRACTION    = 0.06f;
    private static final float TTS_BOTTOM_MARGIN_FRACTION  = 0.06f;

    private static final AtomicBoolean pdfBoxInitialized = new AtomicBoolean(false);

    // =========================================================
    // MODELS
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
    private File          currentFile; // kept only so extractOutline() can re-open the same file via PDFBox
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private final Object lock = new Object();

    private final Set<Integer> poisonedPages = ConcurrentHashMap.newKeySet();

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

        if (pdfBoxInitialized.compareAndSet(false, true)) {
            try { PDFBoxResourceLoader.init(appContext); } catch (Throwable ignored) {}
        }

        synchronized (lock) {
            ParcelFileDescriptor descriptor = null;
            try {
                descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfiumCore newCore = PdfiumFactory.createCore(appContext);
                PdfDocument newDoc = newCore.newDocument(descriptor);

                int pages = safePageCount(newDoc);
                if (pages <= 0) {
                    safeCloseDocument(newDoc);
                    throw new IllegalStateException("PDF opened but reports 0 pages");
                }

                this.pfd         = descriptor;
                this.core        = newCore;
                this.document     = newDoc;
                this.currentFile  = pdfFile;
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
            currentFile = null;
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
    // WORD BOX EXTRACTION
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
    
    public PageWordData extractPageWordDataForReading(int pageIndex) {
        PageWordData full = extractPageWordData(pageIndex);
        List<WordBox> filtered = new ArrayList<>();
        StringBuilder canonical = new StringBuilder();

        for (WordBox wb : full.words) {
            float centerY = (wb.top + wb.bottom) / 2f;
            if (centerY < TTS_TOP_MARGIN_FRACTION || centerY > (1f - TTS_BOTTOM_MARGIN_FRACTION)) continue;

            if (canonical.length() > 0) canonical.append(' ');
            int start = canonical.length();
            canonical.append(wb.word);
            int end = canonical.length();
            filtered.add(new WordBox(wb.left, wb.top, wb.right, wb.bottom, wb.word, filtered.size(), start, end));
        }

        return new PageWordData(filtered, canonical.toString());
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

            String text = data.text;
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

    public List<WordBox> findQueryBoxes(int pageIndex, String query) {
        List<WordBox> all     = extractWordBoxes(pageIndex);
        List<WordBox> matches = new ArrayList<>();
        if (query == null || query.isEmpty()) return matches;

        String lowerQuery = query.toLowerCase().trim();
        String[] queryWords = lowerQuery.split("\\s+");

        if (queryWords.length == 1) {
            for (WordBox wb : all) {
                if (wb.word.toLowerCase().contains(lowerQuery)) {
                    matches.add(wb);
                }
            }
            return matches;
        }

        for (int i = 0; i <= all.size() - queryWords.length; i++) {
            boolean match = true;
            for (int j = 0; j < queryWords.length; j++) {
                if (!all.get(i + j).word.toLowerCase().contains(queryWords[j])) {
                    match = false; break;
                }
            }
            if (match) {
                WordBox first = all.get(i);
                WordBox last  = all.get(i + queryWords.length - 1);
                float l = first.left;
                float t = Math.min(first.top, last.top);
                float r = last.right;
                float b = Math.max(first.bottom, last.bottom);
                matches.add(new WordBox(l, t, r, b, query));
            }
        }
        return matches;
    }

    // =========================================================
    // TOC — real embedded-outline extraction via PDFBox.
    //
    // FIXED: previously always returned an empty list (a deliberate
    // stub, see history), which meant PdfTocController always fell back
    // to its own evenly-spaced page-milestone builder — hence "only page
    // numbers show, no real contents." Since PDFium's binding has no
    // bookmark API and PDFBox is already a project dependency (used for
    // Page Basket export), this reopens the same underlying file briefly
    // through PDFBox purely to read its outline tree, then closes it —
    // it does not stay open for the session, unlike the PDFium document.
    // =========================================================

    public List<TocEntry> extractOutline() {
        List<TocEntry> entries = new ArrayList<>();
        if (currentFile == null || !currentFile.exists()) return entries;

        synchronized (lock) {
            PDDocument outlineDoc = null;
            try {
                outlineDoc = PDDocument.load(currentFile);
                PDDocumentOutline outline = outlineDoc.getDocumentCatalog().getDocumentOutline();
                if (outline != null) {
                    collectOutlineItems(outlineDoc, outline, entries, 0);
                }
            } catch (Throwable t) {
                Log.e(TAG, "extractOutline failed: " + t.getMessage());
            } finally {
                if (outlineDoc != null) {
                    try { outlineDoc.close(); } catch (Throwable ignored) {}
                }
            }
        }
        return entries;
    }

    private void collectOutlineItems(PDDocument doc, PDOutlineNode node, List<TocEntry> out, int depth) {
        if (depth > 12) return; // guard against a malicious/cyclic outline tree
        try {
            PDOutlineItem item = node.getFirstChild();
            while (item != null) {
                String title = item.getTitle();
                if (title == null || title.trim().isEmpty()) title = "Section";

                int pageNum = 0;
                try {
                    if (item.getDestination() != null) {
                        PDPage destPage = item.findDestinationPage(doc);
                        if (destPage != null) {
                            int idx = doc.getPages().indexOf(destPage);
                            if (idx >= 0) pageNum = idx;
                        }
                    }
                } catch (Throwable ignored) {
                    // A single broken destination shouldn't drop the whole entry.
                }

                out.add(new TocEntry(title.trim(), Math.max(0, pageNum), depth));
                collectOutlineItems(doc, item, out, depth + 1);
                item = item.getNextSibling();
            }
        } catch (Throwable ignored) {
            // Never let a malformed outline node crash TOC building.
        }
    }

    // =========================================================
    // DOCUMENT INFO — intentionally disabled; PdfReaderController
    // already falls back to a cleaned-up filename whenever this
    // returns null.
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