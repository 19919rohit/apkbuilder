package neunix.pagevibe;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.rowNotifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationSettingsActivity.class)));

        findViewById(R.id.rowAbout).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));
    }
}