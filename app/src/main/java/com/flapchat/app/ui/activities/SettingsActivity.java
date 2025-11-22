package com.flapchat.app.ui.activities;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.flapchat.app.R;
import com.flapchat.app.utils.ThemeManager;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // TODO: Provide options for theme, notification settings, chat wallpaper, etc.
        ThemeManager.applyCurrentTheme(this);
    }
}