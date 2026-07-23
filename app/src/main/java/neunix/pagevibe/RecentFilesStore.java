package neunix.pagevibe;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Small shared helper for adding an entry to the app's "recent files"
 * list — the same SharedPreferences-backed list HomeFragment reads to
 * populate the library. Used by flows (like exporting a basket) that
 * need to surface a new file on Home without duplicating HomeFragment's
 * own in-memory list-management logic.
 */
public class RecentFilesStore {

    private static final String PREFS_NAME = "pagevibe_prefs";
    private static final String KEY_RECENT = "recent_files";
    private static final int    MAX_RECENT = 20;

    public static void addEntry(Context context, Uri uri, String name) {
        if (context == null || uri == null) return;
        try {
            SharedPreferences prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            JSONArray existing = new JSONArray(prefs.getString(KEY_RECENT, "[]"));
            JSONArray rebuilt = new JSONArray();

            JSONObject entry = new JSONObject();
            entry.put("uri", uri.toString());
            entry.put("name", name != null ? name : "PDF");
            entry.put("timestamp", System.currentTimeMillis());
            rebuilt.put(entry);

            for (int i = 0; i < existing.length() && rebuilt.length() < MAX_RECENT; i++) {
                JSONObject obj = existing.getJSONObject(i);
                if (!uri.toString().equals(obj.optString("uri"))) {
                    rebuilt.put(obj);
                }
            }
            prefs.edit().putString(KEY_RECENT, rebuilt.toString()).apply();
        } catch (Exception ignored) {}
    }
}