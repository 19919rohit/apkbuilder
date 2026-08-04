package neunix.pagevibe.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists user text highlights, one JSON array per PDF (keyed by
 * uri.hashCode()) so every book's highlights are fully independent.
 *
 * A highlight is stored as a character range [charStart, charEnd) within
 * a PAGE's canonical word-joined text — the exact same text
 * PdfTextExtractor.extractPageWordData() deterministically rebuilds every
 * time it's called (words joined by single spaces, in reading order).
 * This is what keeps a highlight correctly anchored to its words even
 * across different zoom levels or screen sizes, and is what makes it
 * robust to line wraps: the range is logical (word N through word M),
 * never raw pixel coordinates.
 */
public class PdfHighlightManager {

    private static final String PREFS_NAME = "pagevibe_prefs";
    private static final String KEY_PREFIX = "highlights_";

    private final Context context;

    public PdfHighlightManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static class HighlightEntry {
        public final String id;
        public final int page;
        public final int charStart;
        public final int charEnd;
        public final String colorHex;
        public final long createdAt;

        public HighlightEntry(String id, int page, int charStart, int charEnd, String colorHex, long createdAt) {
            this.id = id; this.page = page;
            this.charStart = charStart; this.charEnd = charEnd;
            this.colorHex = colorHex; this.createdAt = createdAt;
        }
    }

    public void addHighlight(Uri pdfUri, int page, int charStart, int charEnd, String colorHex) {
        if (pdfUri == null) return;
        JSONArray arr = loadArray(pdfUri);
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", UUID.randomUUID().toString());
            obj.put("page", page);
            obj.put("charStart", charStart);
            obj.put("charEnd", charEnd);
            obj.put("color", colorHex);
            obj.put("createdAt", System.currentTimeMillis());
            arr.put(obj);
            saveArray(pdfUri, arr);
        } catch (JSONException ignored) {}
    }

    public List<HighlightEntry> getHighlightsForPage(Uri pdfUri, int page) {
        List<HighlightEntry> result = new ArrayList<>();
        if (pdfUri == null) return result;
        JSONArray arr = loadArray(pdfUri);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null || obj.optInt("page", -1) != page) continue;
            result.add(new HighlightEntry(
                    obj.optString("id", UUID.randomUUID().toString()),
                    obj.optInt("page", 0),
                    obj.optInt("charStart", 0),
                    obj.optInt("charEnd", 0),
                    obj.optString("color", "#FFEE00"),
                    obj.optLong("createdAt", 0L)));
        }
        return result;
    }

    public void removeHighlight(Uri pdfUri, String highlightId) {
        if (pdfUri == null || highlightId == null) return;
        JSONArray arr = loadArray(pdfUri);
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            if (!highlightId.equals(obj.optString("id", null))) filtered.put(obj);
        }
        saveArray(pdfUri, filtered);
    }

    /** Called when a PDF is removed from the Library so its highlights
     *  don't linger forever in SharedPreferences for a file nobody tracks. */
    public void clearForDocument(Uri pdfUri) {
        if (pdfUri == null) return;
        prefs().edit().remove(KEY_PREFIX + pdfUri.hashCode()).apply();
    }

    private JSONArray loadArray(Uri pdfUri) {
        try { return new JSONArray(prefs().getString(KEY_PREFIX + pdfUri.hashCode(), "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    private void saveArray(Uri pdfUri, JSONArray arr) {
        prefs().edit().putString(KEY_PREFIX + pdfUri.hashCode(), arr.toString()).apply();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}