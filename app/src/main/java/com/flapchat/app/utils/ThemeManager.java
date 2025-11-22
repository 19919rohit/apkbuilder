package com.flapchat.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class ThemeManager {

    private static final String PREFS = "theme_prefs";
    private static final String KEY_THEME = "theme_mode";

    public static void setDarkMode(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_THEME, enabled).apply();
    }

    public static boolean isDarkMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_THEME, false);
    }
}