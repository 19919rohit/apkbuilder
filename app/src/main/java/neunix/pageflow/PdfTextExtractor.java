package neunix.pageflow;

import android.content.Context;
import android.net.Uri;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps pdfbox-android for real per-page text extraction and outline (TOC) parsing.
 * All methods are blocking — call from a background thread.
 */
public class PdfTextExtractor {

    public static class TocEntry {
        public final String title;
        public final int    page;   // 0-based
        public final int    depth;  // 0 = top-level chapter

        public TocEntry(String title, int page, int depth) {
            this.title = title;
            this.page  = page;
            this.depth = depth;
        }
    }

    private PDDocument document;
    private boolean    initialized = false;

    // =========================================================
    // INIT — call once per PDF, reuse for all pages
    // =========================================================

    public void init(Context context) {
        PDFBoxResourceLoader.init(context);
    }

    public void open(File pdfFile) throws Exception {
        close();
        document    = PDDocument.load(pdfFile);
        initialized = true;
    }

    public void close() {
        if (document != null) {
            try { document.close(); } catch (Exception ignored) {}
            document    = null;
            initialized = false;
        }
    }

    public boolean isOpen() { return initialized && document != null; }

    // =========================================================
    // TEXT EXTRACTION — real per-page text
    // =========================================================

    /**
     * Returns the visible text on the given 0-based page index.
     * Returns empty string if page has no extractable text (scanned image PDF).
     */
    public String extractPageText(int pageIndex) {
        if (!isOpen()) return "";
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(pageIndex + 1); // PDFBox is 1-based
            stripper.setEndPage(pageIndex + 1);
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return text != null ? text.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Searches all pages for the query (case-insensitive).
     * Returns list of 0-based page indices that contain the query.
     * Also returns the character offset of the first match per page
     * so callers can highlight context.
     */
    public List<SearchResult> searchAll(String query, int totalPages) {
        List<SearchResult> results = new ArrayList<>();
        if (!isOpen() || query == null || query.isEmpty()) return results;

        String lower = query.toLowerCase();

        for (int i = 0; i < totalPages; i++) {
            String text = extractPageText(i);
            String lowerText = text.toLowerCase();
            int idx = lowerText.indexOf(lower);
            if (idx >= 0) {
                // Extract a short snippet around the match (up to 80 chars)
                int snippetStart = Math.max(0, idx - 20);
                int snippetEnd   = Math.min(text.length(), idx + query.length() + 40);
                String snippet   = "…" + text.substring(snippetStart, snippetEnd).trim() + "…";
                results.add(new SearchResult(i, snippet, idx));
            }
        }
        return results;
    }

    // =========================================================
    // TABLE OF CONTENTS — real PDF outline
    // =========================================================

    /**
     * Parses the PDF's embedded outline (bookmarks/TOC).
     * Returns empty list if the PDF has no outline.
     */
    public List<TocEntry> extractOutline() {
        List<TocEntry> entries = new ArrayList<>();
        if (!isOpen()) return entries;
        try {
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            if (outline != null) {
                collectOutlineItems(outline, entries, 0);
            }
        } catch (Exception ignored) {}
        return entries;
    }

    private void collectOutlineItems(PDOutlineNode node, List<TocEntry> out, int depth) {
        try {
            PDOutlineItem item = node.getFirstChild();
            while (item != null) {
                String title = item.getTitle();
                if (title == null || title.trim().isEmpty()) title = "Section";

                // Resolve destination page
                int pageNum = 0;
                try {
                    if (item.getDestination() != null) {
                        pageNum = document.getDocumentCatalog()
                                .getPages()
                                .indexOf(item.findDestinationPage(document));
                    } else if (item.getAction() != null) {
                        // GoTo action — best effort
                        pageNum = 0;
                    }
                } catch (Exception ignored) {}

                out.add(new TocEntry(title.trim(), Math.max(0, pageNum), depth));
                // Recurse into children
                collectOutlineItems(item, out, depth + 1);
                item = item.getNextSibling();
            }
        } catch (Exception ignored) {}
    }

    // =========================================================
    // DOCUMENT INFO
    // =========================================================

    public String getTitle() {
        if (!isOpen()) return null;
        try {
            PDDocumentInformation info = document.getDocumentInformation();
            return info != null ? info.getTitle() : null;
        } catch (Exception e) { return null; }
    }

    // =========================================================
    // SEARCH RESULT MODEL
    // =========================================================

    public static class SearchResult {
        public final int    page;       // 0-based
        public final String snippet;    // context text around match
        public final int    charOffset; // position of match in page text

        public SearchResult(int page, String snippet, int charOffset) {
            this.page       = page;
            this.snippet    = snippet;
            this.charOffset = charOffset;
        }
    }
}