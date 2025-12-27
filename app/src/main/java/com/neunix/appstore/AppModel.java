package com.neunix.appstore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AppModel {

    public String name;
    public String packageName;
    public String version;        // versionName (e.g. 1.2.3)
    public int versionCode;       // versionCode (e.g. 12)
    public String apk;
    public String icon;
    public String category;
    public String description;
    public List<String> screenshots = new ArrayList<>();

    /* ---------------- JSON PARSER ---------------- */

    public static AppModel fromJson(String json) throws Exception {
        JSONObject o = new JSONObject(json);

        AppModel app = new AppModel();
        app.name = o.getString("name");
        app.packageName = o.getString("package");
        app.version = o.getString("version");
        app.versionCode = o.getInt("versionCode");
        app.apk = o.getString("apk");
        app.icon = o.getString("icon");
        app.category = o.getString("category");
        app.description = o.getString("description");

        JSONArray shots = o.optJSONArray("screenshots");
        if (shots != null) {
            for (int i = 0; i < shots.length(); i++) {
                app.screenshots.add(shots.getString(i));
            }
        }

        return app;
    }

    /* ---------------- JSON EXPORT (HISTORY) ---------------- */

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name);
            o.put("package", packageName);
            o.put("version", version);
            o.put("versionCode", versionCode);
            o.put("apk", apk);
            o.put("icon", icon);
            o.put("category", category);
            o.put("description", description);
        } catch (Exception ignored) {}
        return o;
    }
}