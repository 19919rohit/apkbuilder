package com.flapchat.app;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainApplication extends Application {

    private static final String TAG = "MainApplication";
    private static final String TOPIC_HELLO = "hello";

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        FirebaseApp.initializeApp(this);
        Log.d(TAG, "Firebase initialized");

        // Subscribe to "hello" topic for push notifications
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_HELLO)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Subscribed to topic: " + TOPIC_HELLO);
                    } else {
                        Log.e(TAG, "Failed to subscribe to topic: " + TOPIC_HELLO, task.getException());
                    }
                });

        // Initialize theme from preferences
        initTheme();
    }

    private void initTheme() {
        SharedPreferences prefs = getSharedPreferences("flapchat_prefs", MODE_PRIVATE);
        boolean darkMode = prefs.getBoolean("dark_mode", false);

        if (darkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            Log.d(TAG, "Dark theme enabled");
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            Log.d(TAG, "Light theme enabled");
        }
    }

    // Optional: global method to toggle theme
    public void toggleTheme(boolean enableDarkMode) {
        SharedPreferences prefs = getSharedPreferences("flapchat_prefs", MODE_PRIVATE);
        prefs.edit().putBoolean("dark_mode", enableDarkMode).apply();
        initTheme();
    }
}