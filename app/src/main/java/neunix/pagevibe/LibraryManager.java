package neunix.pagevibe;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for "every PDF ever opened in PageVibe" — the
 * Library. Unlike the old per-fragment "recent files" JSON (capped at 20,
 * silently dropping older entries), this never trims automatically; an
 * entry only disappears when the user explicitly deletes it. Home's
 * dashboard is just a view over this data (top 5 by lastOpenedAt), not a
 * separate store — so a rename or cover change here is instantly correct
 * everywhere else that reads it.
 */
public class LibraryManager {

    private static final String PREFS_NAME     = "pagevibe_prefs";
    private static final String KEY_LIBRARY    = "library_entries_v2";
    private static final String KEY_OLD_RECENT = "recent_files"; // one-time migration source

    private static final String COVERS_DIR = "covers";
    private static final int    COVER_MAX_DIMENSION = 900;

    private final Context context;

    public LibraryManager(Context context) {
        this.context = context.getApplicationContext();
        migrateIfNeeded();
    }

    // =========================================================
    // MODEL
    // =========================================================

    public static class Entry {
        public final Uri    uri;
        public final String originalName;
        public final String customName;   // nullable
        public final String coverPath;    // nullable, absolute file path
        public final long   addedAt;
        public final long   lastOpenedAt;
        public final long   coverUpdatedAt;

        public Entry(Uri uri, String originalName, String customName, String coverPath,
                     long addedAt, long lastOpenedAt, long coverUpdatedAt) {
            this.uri = uri;
            this.originalName = originalName;
            this.customName = customName;
            this.coverPath = coverPath;
            this.addedAt = addedAt;
            this.lastOpenedAt = lastOpenedAt;
            this.coverUpdatedAt = coverUpdatedAt;
        }
    }

    public static String displayName(Entry e) {
        if (e == null) return "PDF";
        if (e.customName != null && !e.customName.trim().isEmpty()) return e.customName.trim();
        return cleanFileName(e.originalName);
    }

    public static String cleanFileName(String name) {
        if (name == null || name.isEmpty()) return "PDF";
        return name.replaceAll("(?i)\\.pdf$", "").replace("_", " ").trim();
    }

    // =========================================================
    // READ
    // =========================================================

    public List<Entry> getAll() {
        List<Entry> result = new ArrayList<>();
        JSONArray arr = loadArray();
        for (int i = 0; i < arr.length(); i++) {
            Entry e = parseEntry(arr.optJSONObject(i));
            if (e != null) result.add(e);
        }
        return result;
    }

    public Entry findByUri(Uri uri) {
        if (uri == null) return null;
        for (Entry e : getAll()) {
            if (e.uri.equals(uri)) return e;
        }
        return null;
    }

    public int size() {
        return loadArray().length();
    }

    // =========================================================
    // WRITE
    // =========================================================

    /** Upsert — call on every successfully opened PDF. Updates
     *  lastOpenedAt on existing entries; creates a new entry otherwise. */
    public Entry addOrTouch(Uri uri, String originalName) {
        if (uri == null) return null;
        JSONArray arr = loadArray();
        long now = System.currentTimeMillis();
        boolean found = false;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            if (uri.toString().equals(obj.optString("uri", null))) {
                try { obj.put("lastOpenedAt", now); } catch (JSONException ignored) {}
                found = true;
                break;
            }
        }

        if (!found) {
            try {
                JSONObject entry = new JSONObject();
                entry.put("uri", uri.toString());
                entry.put("name", originalName != null ? originalName : "document.pdf");
                entry.put("addedAt", now);
                entry.put("lastOpenedAt", now);
                arr.put(entry);
            } catch (JSONException ignored) {}
        }

        saveArray(arr);
        return findByUri(uri);
    }

    public void setCustomName(Uri uri, String name) {
        if (uri == null) return;
        JSONArray arr = loadArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            if (uri.toString().equals(obj.optString("uri", null))) {
                try {
                    if (name == null || name.trim().isEmpty()) obj.remove("customName");
                    else obj.put("customName", name.trim());
                } catch (JSONException ignored) {}
                break;
            }
        }
        saveArray(arr);
    }

    /**
     * Decodes and downsamples the picked image, saves it into the app's
     * private covers directory (overwriting any previous cover for this
     * exact PDF), and records the new path + a fresh coverUpdatedAt
     * timestamp in one atomic update. The timestamp — not the path,
     * which is deterministic per-PDF and therefore unchanged on a
     * re-pick — is what lets every screen's thumbnail cache correctly
     * invalidate itself the next time it reads this entry.
     *
     * Safe to call from a background thread.
     */
    public String setCoverFromImage(Uri pdfUri, Uri pickedImageUri) {
        if (pdfUri == null || pickedImageUri == null) return null;
        try {
            Bitmap bitmap = decodeSampledBitmap(pickedImageUri, COVER_MAX_DIMENSION);
            if (bitmap == null) return null;

            File dir = new File(context.getFilesDir(), COVERS_DIR);
            if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            File outFile = new File(dir, Math.abs(pdfUri.hashCode()) + ".jpg");

            try (OutputStream out = new FileOutputStream(outFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 87, out);
            }
            if (!bitmap.isRecycled()) bitmap.recycle();

            String path = outFile.getAbsolutePath();
            applyCoverPath(pdfUri, path);
            return path;
        } catch (Throwable t) {
            return null;
        }
    }

    private void applyCoverPath(Uri uri, String path) {
        JSONArray arr = loadArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            if (uri.toString().equals(obj.optString("uri", null))) {
                try {
                    obj.put("coverPath", path);
                    obj.put("coverUpdatedAt", now);
                } catch (JSONException ignored) {}
                break;
            }
        }
        saveArray(arr);
    }

    private Bitmap decodeSampledBitmap(Uri imageUri, int maxDimension) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream boundsStream = context.getContentResolver().openInputStream(imageUri)) {
            BitmapFactory.decodeStream(boundsStream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while ((bounds.outWidth / sample) > maxDimension || (bounds.outHeight / sample) > maxDimension) {
            sample *= 2;
        }

        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = sample;
        try (InputStream dataStream = context.getContentResolver().openInputStream(imageUri)) {
            return BitmapFactory.decodeStream(dataStream, null, decodeOpts);
        }
    }

    public void removeEntry(Uri uri) {
        if (uri == null) return;
        JSONArray arr = loadArray();
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            if (uri.toString().equals(obj.optString("uri", null))) {
                deleteCoverFileIfAny(obj.optString("coverPath", null));
            } else {
                filtered.put(obj);
            }
        }
        saveArray(filtered);
    }

    public void clearAll() {
        JSONArray arr = loadArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null) deleteCoverFileIfAny(obj.optString("coverPath", null));
        }
        saveArray(new JSONArray());
    }

    private void deleteCoverFileIfAny(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            File f = new File(path);
            if (f.exists()) //noinspection ResultOfMethodCallIgnored
                f.delete();
        } catch (Throwable ignored) {}
    }

    // =========================================================
    // MIGRATION — one-time import from the old "recent_files" list, so
    // nobody's existing library silently disappears on update.
    // =========================================================

    private void migrateIfNeeded() {
        SharedPreferences prefs = prefs();
        if (prefs.contains(KEY_LIBRARY)) return; // already migrated (or fresh install with none needed)

        try {
            JSONArray oldArr = new JSONArray(prefs.getString(KEY_OLD_RECENT, "[]"));
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < oldArr.length(); i++) {
                JSONObject old = oldArr.optJSONObject(i);
                if (old == null) continue;
                JSONObject entry = new JSONObject();
                entry.put("uri", old.optString("uri"));
                entry.put("name", old.optString("name", "document.pdf"));
                long ts = old.optLong("timestamp", System.currentTimeMillis());
                entry.put("addedAt", ts);
                entry.put("lastOpenedAt", ts);
                newArr.put(entry);
            }
            prefs.edit().putString(KEY_LIBRARY, newArr.toString()).apply();
        } catch (JSONException e) {
            prefs.edit().putString(KEY_LIBRARY, "[]").apply();
        }
    }

    // =========================================================
    // PERSISTENCE
    // =========================================================

    private Entry parseEntry(JSONObject obj) {
        if (obj == null) return null;
        try {
            return new Entry(
                    Uri.parse(obj.getString("uri")),
                    obj.optString("name", "document.pdf"),
                    obj.has("customName") ? obj.optString("customName", null) : null,
                    obj.has("coverPath") ? obj.optString("coverPath", null) : null,
                    obj.optLong("addedAt", 0L),
                    obj.optLong("lastOpenedAt", 0L),
                    obj.optLong("coverUpdatedAt", 0L)
            );
        } catch (JSONException e) {
            return null;
        }
    }

    private JSONArray loadArray() {
        try {
            return new JSONArray(prefs().getString(KEY_LIBRARY, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void saveArray(JSONArray arr) {
        prefs().edit().putString(KEY_LIBRARY, arr.toString()).apply();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}