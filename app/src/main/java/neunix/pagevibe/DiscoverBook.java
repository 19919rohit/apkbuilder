package neunix.pagevibe;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable model for one "discover_books" Firestore document. All
 * parsing happens once, here, via fromDocument() — every field has a
 * safe default so a single malformed or partially-missing document can
 * never crash list rendering; it just falls back to sane placeholder
 * text instead of being dropped or throwing.
 */
public class DiscoverBook {

    // Exact Firestore field names — kept as constants so a typo becomes
    // a compile-time-visible single source of truth instead of a silent
    // string mismatch scattered across the codebase.
    private static final String FIELD_TITLE           = "Title";
    private static final String FIELD_AUTHOR          = "Author";
    private static final String FIELD_DESCRIPTION     = "description";
    private static final String FIELD_CATEGORY        = "category";
    private static final String FIELD_LANGUAGE        = "language";
    private static final String FIELD_RATING          = "Rating";
    private static final String FIELD_DOWNLOADS       = "Downloads";
    private static final String FIELD_FEATURED        = "Featured";
    private static final String FIELD_PAGES           = "Pages";
    private static final String FIELD_DOWNLOAD_SIZE   = "downloadSize";
    private static final String FIELD_COVER_URL       = "coverUrl";
    private static final String FIELD_PDF_URL         = "pdfUrl";
    private static final String FIELD_SEARCH_KEYWORDS = "searchKeywords";

    private final String bookId; // Firestore document ID, e.g. "1342"
    private final String title;
    private final String author;
    private final String description;
    private final String category;
    private final String language;
    private final double rating;
    private final long downloads;
    private final boolean featured;
    private final long pages;
    private final String downloadSize;
    private final String coverUrl;
    private final String pdfUrl;
    private final List<String> searchKeywords;

    public DiscoverBook(String bookId, String title, String author, String description,
                         String category, String language, double rating, long downloads,
                         boolean featured, long pages, String downloadSize, String coverUrl,
                         String pdfUrl, List<String> searchKeywords) {
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.description = description;
        this.category = category;
        this.language = language;
        this.rating = rating;
        this.downloads = downloads;
        this.featured = featured;
        this.pages = pages;
        this.downloadSize = downloadSize;
        this.coverUrl = coverUrl;
        this.pdfUrl = pdfUrl;
        this.searchKeywords = searchKeywords != null ? searchKeywords : new ArrayList<>();
    }

    public static DiscoverBook fromDocument(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        try {
            return new DiscoverBook(
                    doc.getId(),
                    safeString(doc, FIELD_TITLE, "Untitled"),
                    safeString(doc, FIELD_AUTHOR, "Unknown Author"),
                    safeString(doc, FIELD_DESCRIPTION, ""),
                    safeString(doc, FIELD_CATEGORY, "General"),
                    safeString(doc, FIELD_LANGUAGE, ""),
                    safeDouble(doc, FIELD_RATING, 0.0),
                    safeLong(doc, FIELD_DOWNLOADS, 0L),
                    safeBoolean(doc, FIELD_FEATURED, false),
                    safeLong(doc, FIELD_PAGES, 0L),
                    safeString(doc, FIELD_DOWNLOAD_SIZE, ""),
                    safeString(doc, FIELD_COVER_URL, ""),
                    safeString(doc, FIELD_PDF_URL, ""),
                    safeStringList(doc, FIELD_SEARCH_KEYWORDS)
            );
        } catch (Throwable t) {
            // A single corrupt document must never take down the whole
            // Discover list.
            return null;
        }
    }

    // =========================================================
    // SAFE FIELD READERS
    // =========================================================

    private static String safeString(DocumentSnapshot doc, String field, String fallback) {
        try {
            String v = doc.getString(field);
            return (v != null && !v.trim().isEmpty()) ? v.trim() : fallback;
        } catch (Throwable t) { return fallback; }
    }

    private static double safeDouble(DocumentSnapshot doc, String field, double fallback) {
        try {
            Double v = doc.getDouble(field); // coerces stored Long OR Double
            return v != null ? v : fallback;
        } catch (Throwable t) { return fallback; }
    }

    private static long safeLong(DocumentSnapshot doc, String field, long fallback) {
        try {
            Long v = doc.getLong(field);
            return v != null ? v : fallback;
        } catch (Throwable t) { return fallback; }
    }

    private static boolean safeBoolean(DocumentSnapshot doc, String field, boolean fallback) {
        try {
            Boolean v = doc.getBoolean(field);
            return v != null ? v : fallback;
        } catch (Throwable t) { return fallback; }
    }

    @SuppressWarnings("unchecked")
    private static List<String> safeStringList(DocumentSnapshot doc, String field) {
        List<String> result = new ArrayList<>();
        try {
            Object raw = doc.get(field);
            if (raw instanceof List) {
                for (Object item : (List<Object>) raw) {
                    if (item != null) result.add(String.valueOf(item).trim());
                }
            } else if (raw instanceof String) {
                // Defensive: tolerate a single comma-separated string
                // instead of a real array field.
                for (String part : ((String) raw).split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) result.add(trimmed);
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public String getBookId() { return bookId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getLanguage() { return language; }
    public double getRating() { return rating; }
    public long getDownloads() { return downloads; }
    public boolean isFeatured() { return featured; }
    public long getPages() { return pages; }
    public String getDownloadSize() { return downloadSize; }
    public String getCoverUrl() { return coverUrl; }
    public String getPdfUrl() { return pdfUrl; }
    public List<String> getSearchKeywords() { return searchKeywords; }

    public boolean hasValidPdfUrl() {
        return pdfUrl != null && (pdfUrl.startsWith("http://") || pdfUrl.startsWith("https://"));
    }

    // =========================================================
    // EQUALITY — used by DiffUtil for "did the content actually change"
    // checks, so re-sorting/re-filtering doesn't force a full rebind of
    // unchanged cards.
    // =========================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiscoverBook)) return false;
        DiscoverBook other = (DiscoverBook) o;
        return Objects.equals(bookId, other.bookId)
                && Objects.equals(title, other.title)
                && Objects.equals(author, other.author)
                && Objects.equals(category, other.category)
                && Double.compare(rating, other.rating) == 0
                && downloads == other.downloads
                && featured == other.featured
                && Objects.equals(coverUrl, other.coverUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookId, title, author, category, rating, downloads, featured, coverUrl);
    }

    /** Human-friendly download count, e.g. "12.3K" for large numbers. */
    public String formatDownloads() {
        if (downloads >= 1_000_000) return String.format(Locale.getDefault(), "%.1fM", downloads / 1_000_000.0);
        if (downloads >= 1_000) return String.format(Locale.getDefault(), "%.1fK", downloads / 1_000.0);
        return String.valueOf(downloads);
    }
}