package neunix.pagevibe.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private ThemeManager themeManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        themeManager = new ThemeManager(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.rowThemes).setOnClickListener(v ->
                startActivity(new Intent(this, ThemesActivity.class)));

        findViewById(R.id.rowNotifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationSettingsActivity.class)));

        findViewById(R.id.rowBatteryOptimization).setOnClickListener(v -> requestBatteryExemption());

        findViewById(R.id.rowShareApp).setOnClickListener(v -> shareApp());

        findViewById(R.id.rowFeedback).setOnClickListener(v -> sendFeedback());

        findViewById(R.id.rowAbout).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        findViewById(R.id.rowLicenses).setOnClickListener(v ->
                startActivity(new Intent(this, LicenseActivity.class)));

        applyTheme();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyTheme();
    }

    private void applyTheme() {
        ThemeApplier.apply(findViewById(android.R.id.content), themeManager.getActiveTheme());
    }

    private void requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "Not needed on this Android version", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, "Already optimized for reliable notifications", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Throwable t) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Throwable ignored) {
                Toast.makeText(this, "Could not open battery settings on this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void shareApp() {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT,
                    "Check out PageVibe — a PDF reader with page-curl animation, read-aloud, and a Page Basket for combining pages from different PDFs.");
            startActivity(Intent.createChooser(shareIntent, "Share PageVibe"));
        } catch (Throwable ignored) {}
    }

    private void sendFeedback() {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:neunixstudios@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "PageVibe Feedback");
            startActivity(Intent.createChooser(intent, "Send feedback"));
        } catch (Throwable ignored) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
        }
    }
}