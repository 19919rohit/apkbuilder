package neunix.pagevibe;

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
 * Per-PDF notes — an independent, ordered list of note ENTRIES per
 * document (keyed exactly like PdfHighlightManager), so every book's
 * notes are fully separate. Each entry has its own text and colour and
 * can be appended/edited independently over time.
 */
public class PdfNotesManager {

    private static final String PREFS_NAME = "pagevibe_prefs";
    private static final String KEY_PREFIX = "notes_";

    private final Context context;

    public PdfNotesManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static class NoteEntry {
        public final String id;
        public final String text;
        public final String colorHex;
        public final long createdAt;
        public final long updatedAt;

        public NoteEntry(String id, String text, String colorHex, long createdAt, long updatedAt) {
            this.id = id; this.text = text; this.colorHex = colorHex;
            this.createdAt = createdAt; this.updatedAt = updatedAt;
        }
    }

    public List<NoteEntry> getEntries(Uri pdfUri) {
        List<NoteEntry> result = new ArrayList<>();
        if (pdfUri == null) return result;
        JSONArray arr = loadArray(pdfUri);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            result.add(new NoteEntry(
                    obj.optString("id", UUID.randomUUID().toString()),
                    obj.optString("text", ""),
                    obj.optString("color", "#FFEE00"),
                    obj.optLong("createdAt", 0L),
                    obj.optLong("updatedAt", 0L)));
        }
        return result;
    }

    public int countForDocument(Uri pdfUri) {
        return loadArray(pdfUri).length();
    }

    public void addEntry(Uri pdfUri, String text, String colorHex) {
        if (pdfUri == null || text == null || text.trim().isEmpty()) return;
        JSONArray arr = loadArray(pdfUri);
        try {
            JSONObject obj = new JSONObject();
            long now = System.currentTimeMillis();
            obj.put("id", UUID.randomUUID().toString());
            obj.put("text", text.trim());
            obj.put("color", colorHex != null ? colorHex : "#FFEE00");
            obj.put("createdAt", now);
            obj.put("updatedAt", now);
            arr.put(obj);
            saveArray(pdfUri, arr);
        } catch (JSONException ignored) {}
    }

    public void updateEntry(Uri pdfUri, String entryId, String newText, String newColorHex) {
        if (pdfUri == null || entryId == null) return;
        JSONArray arr = loadArray(pdfUri);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null || !entryId.equals(obj.optString("id", null))) continue;
            try {
                if (newText != null) obj.put("text", newText.trim());
                if (newColorHex != null) obj.put("color", newColorHex);
                obj.put("updatedAt", System.currentTimeMillis());
            } catch (JSONException ignored) {}
            break;
        }
        saveArray(pdfUri, arr);
    }

    public void deleteEntry(Uri pdfUri, String entryId) {
        if (pdfUri == null || entryId == null) return;
        JSONArray arr = loadArray(pdfUri);
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            if (!entryId.equals(obj.optString("id", null))) filtered.put(obj);
        }
        saveArray(pdfUri, filtered);
    }

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