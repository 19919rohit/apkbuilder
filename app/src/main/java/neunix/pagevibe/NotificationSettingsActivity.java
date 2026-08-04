package neunix.pagevibe.app;

import android.os.Bundle;
import android.widget.CompoundButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import android.view.View;

public class NotificationSettingsActivity extends AppCompatActivity {

    private NotificationPreferences prefs;
    private ThemeManager themeManager;
    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);
        rootView = findViewById(android.R.id.content);
        themeManager = new ThemeManager(this);
        prefs = new NotificationPreferences(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        bindSwitch(R.id.switchDailyQuote, NotificationPreferences.CAT_DAILY_QUOTE);
        bindSwitch(R.id.switchReadingReminder, NotificationPreferences.CAT_READING_REMINDER);
        bindSwitch(R.id.switchContinueReading, NotificationPreferences.CAT_CONTINUE_READING);
        bindSwitch(R.id.switchStreak, NotificationPreferences.CAT_STREAK);
        bindSwitch(R.id.switchAnnouncements, NotificationPreferences.CAT_ANNOUNCEMENTS);
        applyTheme();
    }
    
    @Override
protected void onResume() {
    super.onResume();
    applyTheme();
}

    private void applyTheme() {
    if (rootView == null || themeManager == null) {
        return;
    }

    ThemeApplier.apply(rootView, themeManager.getActiveTheme());
}

    private void bindSwitch(int viewId, String category) {
        SwitchMaterial sw = findViewById(viewId);
        sw.setChecked(prefs.isCategoryEnabled(category));
        sw.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                prefs.setCategoryEnabled(category, isChecked));
    }
}