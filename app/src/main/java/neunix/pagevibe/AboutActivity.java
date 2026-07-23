package neunix.pagevibe;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextView txtAppName;
    private TextView txtPackageName;
    private TextView txtVersion;
    private View btnLicenses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // Bind views
        btnBack = findViewById(R.id.btnBack);
        txtAppName = findViewById(R.id.txtAppName);
        txtPackageName = findViewById(R.id.txtPackageName);
        txtVersion = findViewById(R.id.txtVersion);
        btnLicenses = findViewById(R.id.btnLicenses);

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // App name
        try {
            CharSequence appName = getPackageManager().getApplicationLabel(getApplicationInfo());
            txtAppName.setText(appName);
        } catch (Exception e) {
            txtAppName.setText(getString(R.string.app_name));
        }

        // Package name
        txtPackageName.setText(getPackageName());

        // Version (automatically from Gradle)
        txtVersion.setText("Version " + BuildConfig.VERSION_NAME);

        // Open licenses screen
        btnLicenses.setOnClickListener(v ->
                startActivity(new Intent(AboutActivity.this, LicenseActivity.class))
        );
    }
}