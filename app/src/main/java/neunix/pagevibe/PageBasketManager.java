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

public class PageBasketManager {

    private static final String PREFS_NAME = "pagevibe_prefs";
    private static final String KEY_BASKET = "page_basket_v1";

    private final Context context;

    public PageBasketManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static class BasketEntry {
        public final String id;
        public final Uri    sourceUri;
        public final String sourceName;
        public final int    pageIndex;
        public final long   addedAt;

        public BasketEntry(String id, Uri sourceUri, String sourceName, int pageIndex, long addedAt) {
            this.id = id;
            this.sourceUri = sourceUri;
            this.sourceName = sourceName;
            this.pageIndex = pageIndex;
            this.addedAt = addedAt;
        }
    }

    public List<BasketEntry> getAll() {
        List<BasketEntry> result = new ArrayList<>();
        JSONArray arr = loadArray();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                result.add(new BasketEntry(
                        obj.getString("id"),
                        Uri.parse(obj.getString("uri")),
                        obj.optString("name", "PDF"),
                        obj.getInt("page"),
                        obj.optLong("addedAt", 0L)
                ));
            } catch (JSONException ignored) {}
        }
        return result;
    }

    public int size() {
        return loadArray().length();
    }

    public boolean contains(Uri uri, int pageIndex) {
        if (uri == null) return false;
        JSONArray arr = loadArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            if (uri.toString().equals(obj.optString("uri", null))
                    && pageIndex == obj.optInt("page", -1)) {
                return true;
            }
        }
        return false;
    }

    public boolean addPage(Uri sourceUri, String sourceName, int pageIndex) {
        if (sourceUri == null) return false;
        if (contains(sourceUri, pageIndex)) return false;

        try {
            JSONArray arr = loadArray();
            JSONObject entry = new JSONObject();
            entry.put("id", UUID.randomUUID().toString());
            entry.put("uri", sourceUri.toString());
            entry.put("name", sourceName != null ? sourceName : "PDF");
            entry.put("page", pageIndex);
            entry.put("addedAt", System.currentTimeMillis());
            arr.put(entry);
            saveArray(arr);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    public void removeById(String entryId) {
        if (entryId == null) return;
        JSONArray arr = loadArray();
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            if (!entryId.equals(obj.optString("id", null))) {
                filtered.put(obj);
            }
        }
        saveArray(filtered);
    }

    /** Convenience for toggle-style removal from the reader screen, where
     *  the caller knows the source+page but not the stored entry id. */
    public void removeEntry(Uri uri, int pageIndex) {
        if (uri == null) return;
        JSONArray arr = loadArray();
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            boolean matches = uri.toString().equals(obj.optString("uri", null))
                    && pageIndex == obj.optInt("page", -1);
            if (!matches) filtered.put(obj);
        }
        saveArray(filtered);
    }

    public void clearAll() {
        saveArray(new JSONArray());
    }

    public void moveEntry(int fromIndex, int toIndex) {
        List<BasketEntry> all = getAll();
        if (fromIndex < 0 || fromIndex >= all.size()
                || toIndex < 0 || toIndex >= all.size()
                || fromIndex == toIndex) return;

        BasketEntry moved = all.remove(fromIndex);
        all.add(toIndex, moved);

        try {
            JSONArray arr = new JSONArray();
            for (BasketEntry e : all) {
                JSONObject obj = new JSONObject();
                obj.put("id", e.id);
                obj.put("uri", e.sourceUri.toString());
                obj.put("name", e.sourceName);
                obj.put("page", e.pageIndex);
                obj.put("addedAt", e.addedAt);
                arr.put(obj);
            }
            saveArray(arr);
        } catch (JSONException ignored) {}
    }

    private JSONArray loadArray() {
        try {
            return new JSONArray(prefs().getString(KEY_BASKET, "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void saveArray(JSONArray arr) {
        prefs().edit().putString(KEY_BASKET, arr.toString()).apply();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}