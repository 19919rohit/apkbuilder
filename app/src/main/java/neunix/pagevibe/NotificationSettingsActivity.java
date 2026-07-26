package neunix.pageflow;

import android.os.Bundle;
import android.widget.CompoundButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class NotificationSettingsActivity extends AppCompatActivity {

    private NotificationPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);
        prefs = new NotificationPreferences(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        bindSwitch(R.id.switchDailyQuote, NotificationPreferences.CAT_DAILY_QUOTE);
        bindSwitch(R.id.switchReadingReminder, NotificationPreferences.CAT_READING_REMINDER);
        bindSwitch(R.id.switchContinueReading, NotificationPreferences.CAT_CONTINUE_READING);
        bindSwitch(R.id.switchStreak, NotificationPreferences.CAT_STREAK);
        bindSwitch(R.id.switchAnnouncements, NotificationPreferences.CAT_ANNOUNCEMENTS);
    }

    private void bindSwitch(int viewId, String category) {
        SwitchMaterial sw = findViewById(viewId);
        sw.setChecked(prefs.isCategoryEnabled(category));
        sw.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) ->
                prefs.setCategoryEnabled(category, isChecked));
    }
}