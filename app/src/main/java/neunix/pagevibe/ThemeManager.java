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
 * Single source of truth for every theme — six built-in themes plus any
 * number of user-created custom themes. Custom themes persist as one
 * JSON array in SharedPreferences.
 */
public class ThemeManager {

    private static final String PREFS_NAME       = "pagevibe_prefs";
    private static final String KEY_CUSTOM_THEMES = "custom_themes_v2";
    private static final String KEY_ACTIVE_THEME  = "active_theme_id";

    public static final String BUILTIN_DARK_ALMOND = "builtin_dark_almond";
    public static final String BUILTIN_LIGHT       = "builtin_light";
    public static final String BUILTIN_SEPIA       = "builtin_sepia";
    public static final String BUILTIN_MATERIAL    = "builtin_material";
    public static final String BUILTIN_MIDNIGHT    = "builtin_midnight";
    public static final String BUILTIN_FOREST      = "builtin_forest";

    // 10 real Android generic type-family names — every one of these
    // resolves via Typeface.create(name, style) using families that ship
    // inside the Android system image. Zero additional APK size.
    public static final String[] FONT_VALUES = {
            "sans-serif", "sans-serif-light", "sans-serif-medium", "sans-serif-black",
            "sans-serif-condensed", "sans-serif-condensed-medium",
            "serif", "monospace", "casual", "cursive"
    };
    public static final String[] FONT_DISPLAY_NAMES = {
            "Default", "Light", "Medium", "Black",
            "Condensed", "Condensed Medium",
            "Serif", "Monospace", "Casual", "Cursive"
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
        public final int     dividerColor;
        public final int     textPrimaryColor;
        public final int     textSecondaryColor;
        public final int     accentColor;
        public final int     buttonTextColor;
        public final int     outlineColor;
        public final String  fontFamily;
        public final float   textScale;

        public AppTheme(String id, String name, boolean builtIn,
                         int backgroundColor, int cardColor, int dividerColor,
                         int textPrimaryColor, int textSecondaryColor,
                         int accentColor, int buttonTextColor, int outlineColor,
                         String fontFamily, float textScale) {
            this.id = id;
            this.name = name;
            this.builtIn = builtIn;
            this.backgroundColor = backgroundColor;
            this.cardColor = cardColor;
            this.dividerColor = dividerColor;
            this.textPrimaryColor = textPrimaryColor;
            this.textSecondaryColor = textSecondaryColor;
            this.accentColor = accentColor;
            this.buttonTextColor = buttonTextColor;
            this.outlineColor = outlineColor;
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
                0xFF080808, 0xFF151515, 0xFF2A2A2A,
                0xFFFFFFFF, 0xFF8A8A8A,
                0xFF4488FF, 0xFFFFFFFF, 0xFF4488FF,
                "sans-serif", TEXT_SCALE_MEDIUM);
    }

    private static AppTheme light() {
        return new AppTheme(
                BUILTIN_LIGHT, "Light", true,
                0xFFFFFFFF, 0xFFF2F2F2, 0xFFE0E0E0,
                0xFF111111, 0xFF666666,
                0xFF2266DD, 0xFFFFFFFF, 0xFF2266DD,
                "sans-serif", TEXT_SCALE_MEDIUM);
    }

    private static AppTheme sepia() {
        return new AppTheme(
                BUILTIN_SEPIA, "Sepia", true,
                0xFFF4ECD8, 0xFFEADFC4, 0xFFD8C9A8,
                0xFF5B4636, 0xFF8A7358,
                0xFF8B5E34, 0xFFFFF6E8, 0xFF8B5E34,
                "serif", TEXT_SCALE_MEDIUM);
    }

    private static AppTheme material() {
        return new AppTheme(
                BUILTIN_MATERIAL, "Material", true,
                0xFF121212, 0xFF1E1E1E, 0xFF2C2C2C,
                0xFFEDEDED, 0xFFA0A0A0,
                0xFFBB86FC, 0xFF1A1A1A, 0xFFBB86FC,
                "sans-serif-medium", TEXT_SCALE_MEDIUM);
    }

    private static AppTheme midnight() {
        return new AppTheme(
                BUILTIN_MIDNIGHT, "Midnight", true,
                0xFF0D1B2A, 0xFF1B263B, 0xFF223449,
                0xFFE0FBFC, 0xFF8FA9BE,
                0xFF00B4D8, 0xFF04101C, 0xFF00B4D8,
                "sans-serif", TEXT_SCALE_MEDIUM);
    }

    private static AppTheme forest() {
        return new AppTheme(
                BUILTIN_FOREST, "Forest", true,
                0xFF101B12, 0xFF16241A, 0xFF223A28,
                0xFFE8F5E9, 0xFF9BC0A2,
                0xFF4CAF50, 0xFFFFFFFF, 0xFF4CAF50,
                "sans-serif", TEXT_SCALE_MEDIUM);
    }

    private static List<AppTheme> builtInList() {
        List<AppTheme> list = new ArrayList<>();
        list.add(darkAlmond());
        list.add(light());
        list.add(sepia());
        list.add(material());
        list.add(midnight());
        list.add(forest());
        return list;
    }

    // =========================================================
    // READ
    // =========================================================

    public List<AppTheme> getAllThemes() {
        List<AppTheme> result = new ArrayList<>(builtInList());
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

        if (id.equals(prefs().getString(KEY_ACTIVE_THEME, BUILTIN_DARK_ALMOND))) {
            setActiveThemeId(BUILTIN_DARK_ALMOND);
        }
    }

    // =========================================================
    // PERSISTENCE — backward-compatible with any theme saved by the
    // previous (5-field) version of this class: missing keys fall back
    // to computed defaults instead of failing to parse.
    // =========================================================

    private AppTheme parseTheme(JSONObject obj) {
        if (obj == null) return null;
        try {
            int background   = obj.getInt("background");
            int card         = obj.getInt("card");
            int textPrimary  = obj.getInt("textPrimary");
            int accent       = obj.optInt("accent", 0xFF4488FF);
            int textSecondary = obj.optInt("textSecondary", blend(textPrimary, 0.6f));
            int divider       = obj.optInt("divider", blend(card, 0.7f));
            int buttonText     = obj.optInt("buttonText", contrastFor(accent));
            int outline         = obj.optInt("outline", accent);

            return new AppTheme(
                    obj.getString("id"),
                    obj.optString("name", "Custom Theme"),
                    false,
                    background, card, divider,
                    textPrimary, textSecondary,
                    accent, buttonText, outline,
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
            obj.put("divider", theme.dividerColor);
            obj.put("textPrimary", theme.textPrimaryColor);
            obj.put("textSecondary", theme.textSecondaryColor);
            obj.put("accent", theme.accentColor);
            obj.put("buttonText", theme.buttonTextColor);
            obj.put("outline", theme.outlineColor);
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

    // =========================================================
    // COLOR HELPERS — used both for backward-compat defaults above and
    // available to callers building a theme.
    // =========================================================

    private static int blend(int color, float factor) {
        int r = Math.round(android.graphics.Color.red(color) * factor);
        int g = Math.round(android.graphics.Color.green(color) * factor);
        int b = Math.round(android.graphics.Color.blue(color) * factor);
        return android.graphics.Color.rgb(r, g, b);
    }

    public static int contrastFor(int backgroundColor) {
        double luminance = (0.299 * android.graphics.Color.red(backgroundColor)
                + 0.587 * android.graphics.Color.green(backgroundColor)
                + 0.114 * android.graphics.Color.blue(backgroundColor)) / 255.0;
        return luminance > 0.55 ? 0xFF000000 : 0xFFFFFFFF;
    }
}