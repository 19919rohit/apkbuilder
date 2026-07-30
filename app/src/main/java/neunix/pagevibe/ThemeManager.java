package neunix.pagevibe;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Single source of truth for every theme — the two built-in themes
 * (Dark Almond, Light) plus any number of user-created custom themes.
 * Custom themes persist as one JSON array in SharedPreferences, same
 * pattern as LibraryManager/PageBasketManager.
 */
public class ThemeManager {

    private static final String PREFS_NAME       = "pagevibe_prefs";
    private static final String KEY_CUSTOM_THEMES = "custom_themes_v1";
    private static final String KEY_ACTIVE_THEME  = "active_theme_id";

    public static final String BUILTIN_DARK_ALMOND = "builtin_dark_almond";
    public static final String BUILTIN_LIGHT        = "builtin_light";

    // Font family values are real Android generic type-family names —
    // resolved directly via Typeface.create(name, style), which ships
    // inside every Android system image. Zero APK size cost.
    public static final String[] FONT_VALUES = {
            "sans-serif", "serif", "monospace", "sans-serif-condensed", "sans-serif-medium"
    };
    public static final String[] FONT_DISPLAY_NAMES = {
            "Default", "Serif", "Monospace", "Condensed", "Medium"
    };

    public static final float TEXT_SCALE_SMALL  = 0.9f;
    public static final float TEXT_SCALE_MEDIUM = 1.0f;
    public static final float TEXT_SCALE_LARGE  = 1.15f;

    private final Context context;

    public ThemeManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // =========================================================
    // MODEL
    // =========================================================

    public static class AppTheme {
        public final String  id;
        public final String  name;
        public final boolean builtIn;
        public final int     backgroundColor;
        public final int     cardColor;
        public final int     textPrimaryColor;
        public final int     textSecondaryColor;
        public final int     accentColor;
        public final String  fontFamily;
        public final float   textScale;

        public AppTheme(String id, String name, boolean builtIn,
                         int backgroundColor, int cardColor,
                         int textPrimaryColor, int textSecondaryColor,
                         int accentColor, String fontFamily, float textScale) {
            this.id = id;
            this.name = name;
            this.builtIn = builtIn;
            this.backgroundColor = backgroundColor;
            this.cardColor = cardColor;
            this.textPrimaryColor = textPrimaryColor;
            this.textSecondaryColor = textSecondaryColor;
            this.accentColor = accentColor;
            this.fontFamily = fontFamily != null ? fontFamily : "sans-serif";
            this.textScale = textScale > 0 ? textScale : TEXT_SCALE_MEDIUM;
        }
    }

    // =========================================================
    // BUILT-IN THEMES
    // =========================================================

    private static AppTheme darkAlmond() {
        return new AppTheme(
                BUILTIN_DARK_ALMOND, "Dark Almond", true,
                0xFF080808, 0xFF151515,
                0xFFFFFFFF, 0xFF8A8A8A,
                0xFF4488FF, "sans-serif", TEXT_SCALE_MEDIUM);
    }

    private static AppTheme light() {
        return new AppTheme(
                BUILTIN_LIGHT, "Light", true,
                0xFFFFFFFF, 0xFFF2F2F2,
                0xFF111111, 0xFF666666,
                0xFF2266DD, "sans-serif", TEXT_SCALE_MEDIUM);
    }

    // =========================================================
    // READ
    // =========================================================

    public List<AppTheme> getAllThemes() {
        List<AppTheme> result = new ArrayList<>();
        result.add(darkAlmond());
        result.add(light());
        result.addAll(getCustomThemes());
        return result;
    }

    public List<AppTheme> getCustomThemes() {
        List<AppTheme> result = new ArrayList<>();
        JSONArray arr = loadArray();
        for (int i = 0; i < arr.length(); i++) {
            AppTheme t = parseTheme(arr.optJSONObject(i));
            if (t != null) result.add(t);
        }
        return result;
    }

    public AppTheme findById(String id) {
        if (id == null) return darkAlmond();
        for (AppTheme t : getAllThemes()) {
            if (t.id.equals(id)) return t;
        }
        return darkAlmond();
    }

    /** Falls back safely to Dark Almond if the stored active id no
     *  longer exists (e.g. its custom theme was deleted). */
    public AppTheme getActiveTheme() {
        String id = prefs().getString(KEY_ACTIVE_THEME, BUILTIN_DARK_ALMOND);
        return findById(id);
    }

    // =========================================================
    // WRITE
    // =========================================================

    public void setActiveThemeId(String id) {
        prefs().edit().putString(KEY_ACTIVE_THEME, id).apply();
    }

    /** Upserts by id — pass a null/empty id to create a brand-new theme. */
    public String saveCustomTheme(AppTheme theme) {
        String id = (theme.id == null || theme.id.trim().isEmpty())
                ? UUID.randomUUID().toString() : theme.id;

        JSONArray arr = loadArray();
        JSONArray rebuilt = new JSONArray();
        boolean replaced = false;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            if (id.equals(obj.optString("id", null))) {
                rebuilt.put(toJson(id, theme));
                replaced = true;
            } else {
                rebuilt.put(obj);
            }
        }
        if (!replaced) rebuilt.put(toJson(id, theme));

        saveArray(rebuilt);
        return id;
    }

    public void deleteCustomTheme(String id) {
        if (id == null) return;
        JSONArray arr = loadArray();
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            if (!id.equals(obj.optString("id", null))) filtered.put(obj);
        }
        saveArray(filtered);

        // If the deleted theme was active, fall back to the safe default
        // rather than leaving the app pointed at a theme that no longer
        // exists.
        if (id.equals(prefs().getString(KEY_ACTIVE_THEME, BUILTIN_DARK_ALMOND))) {
            setActiveThemeId(BUILTIN_DARK_ALMOND);
        }
    }

    // =========================================================
    // PERSISTENCE
    // =========================================================

    private AppTheme parseTheme(JSONObject obj) {
        if (obj == null) return null;
        try {
            return new AppTheme(
                    obj.getString("id"),
                    obj.optString("name", "Custom Theme"),
                    false,
                    obj.getInt("background"),
                    obj.getInt("card"),
                    obj.getInt("textPrimary"),
                    obj.getInt("textSecondary"),
                    obj.getInt("accent"),
                    obj.optString("font", "sans-serif"),
                    (float) obj.optDouble("textScale", TEXT_SCALE_MEDIUM));
        } catch (JSONException e) {
            return null;
        }
    }

    private JSONObject toJson(String id, AppTheme theme) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", id);
            obj.put("name", theme.name);
            obj.put("background", theme.backgroundColor);
            obj.put("card", theme.cardColor);
            obj.put("textPrimary", theme.textPrimaryColor);
            obj.put("textSecondary", theme.textSecondaryColor);
            obj.put("accent", theme.accentColor);
            obj.put("font", theme.fontFamily);
            obj.put("textScale", theme.textScale);
            return obj;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private JSONArray loadArray() {
        try { return new JSONArray(prefs().getString(KEY_CUSTOM_THEMES, "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    private void saveArray(JSONArray arr) {
        prefs().edit().putString(KEY_CUSTOM_THEMES, arr.toString()).apply();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}