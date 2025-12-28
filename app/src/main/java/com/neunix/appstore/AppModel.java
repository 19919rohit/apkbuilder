package com.neunix.appstore;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AppModel implements Serializable {

    public String name;
    public String packageName;
    public String version;      // versionName (e.g., 1.0)
    public int versionCode;     // versionCode (e.g., 1)
    public String apk;
    public String icon;
    public String category;
    public String description;
    public List<String> screenshots = new ArrayList<>();

    // ---------------- Constructors ----------------
    public AppModel() {
    }

    public AppModel(String name, String packageName, String version, String category,
                    String description, String icon, String apk) {
        this.name = name;
        this.packageName = packageName;
        this.version = version;
        this.category = category;
        this.description = description;
        this.icon = icon;
        this.apk = apk;
    }

    // ---------------- JSON Parser ----------------
    public static AppModel fromJson(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        AppModel app = new AppModel();
        app.name = o.getString("name");
        app.packageName = o.getString("package");
        app.version = o.getString("version");
        app.versionCode = o.optInt("versionCode", 1); // fallback 1
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

    // ---------------- JSON Export (History) ----------------
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
            // Optional: add screenshots as JSONArray
            JSONArray shots = new JSONArray();
            for (String s : screenshots) shots.put(s);
            o.put("screenshots", shots);
        } catch (Exception ignored) {}
        return o;
    }
}