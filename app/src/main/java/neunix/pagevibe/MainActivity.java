package neunix.pagevibe;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.messaging.FirebaseMessaging;

/**
 * Thin shell hosting three tabs — Home, Library, Basket — via a
 * persistent BottomNavigationView. Fragments are created once and kept
 * alive for the app session via add() + hide()/show() — never replace().
 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private final HomeFragment    homeFragment    = new HomeFragment();
    private final LibraryFragment libraryFragment = new LibraryFragment();
    private final BasketFragment  basketFragment  = new BasketFragment();

    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { /* no-op either way */ });

    @Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main_shell);

    NotificationScheduler.initialize(this);
    requestNotificationPermissionIfNeeded();
    subscribeToFcmTopics();
    // Logs only if the app was opened from a PageVibe notification.
    AnalyticsHelper.logIfTagged(this, getIntent());

    handleIncomingViewIntent(getIntent());

    bottomNav = findViewById(R.id.bottomNav);
    showTab(homeFragment);

    bottomNav.setOnItemSelectedListener(item -> {
        int id = item.getItemId();
        if (id == R.id.nav_home) {
            showTab(homeFragment);
            return true;
        }
        if (id == R.id.nav_library) {
            showTab(libraryFragment);
            return true;
        }
        if (id == R.id.nav_basket) {
            showTab(basketFragment);
            return true;
        }
        return false;
    });

    // Handle Back button
    getOnBackPressedDispatcher().addCallback(this,
            new androidx.activity.OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {

                    if (bottomNav.getSelectedItemId() != R.id.nav_home) {
                        bottomNav.setSelectedItemId(R.id.nav_home);
                    } else {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                }
            });
}

    @Override
    protected void onResume() {
        super.onResume();
        new NotificationPreferences(this).recordAppOpenedNow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        AnalyticsHelper.logIfTagged(this, intent);

        handleIncomingViewIntent(intent);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    /**
     * "all" is the broadcast topic every install is subscribed to for
     * rare FCM announcements (major updates, security notices) — this is
     * the only topic the app subscribes to; it never targets individual
     * users or segments, so there's nothing personally identifying about
     * this subscription.
     */
    private void subscribeToFcmTopics() {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all");
        } catch (Throwable ignored) {
            // FCM not configured yet (e.g. google-services.json not
            // present) — never let this block the rest of app startup.
        }
    }

    private void handleIncomingViewIntent(Intent incoming) {
        if (incoming == null || !Intent.ACTION_VIEW.equals(incoming.getAction())
                || incoming.getData() == null) return;

        Uri uri = incoming.getData();
        String name = FileUtils.getFileName(this, uri);

        ExternalPdfPersister.persistIfNeeded(this, uri, name, new ExternalPdfPersister.Callback() {
            @Override
            public void onPersisted(Uri persistedUri, String fileName) {
                runOnUiThread(() -> launchReader(persistedUri));
            }

            @Override
            public void onFailed(Uri originalUri, String originalName) {
                runOnUiThread(() -> launchReader(originalUri));
            }
        });
    }

    private void launchReader(Uri uri) {
        Intent i = new Intent(this, PdfActivity.class);
        i.setData(uri);
        startActivity(i);
    }

    private void showTab(Fragment target) {

    FragmentManager fm = getSupportFragmentManager();

    Fragment current = null;

    if (homeFragment.isVisible()) {
        current = homeFragment;
    } else if (libraryFragment.isVisible()) {
        current = libraryFragment;
    } else if (basketFragment.isVisible()) {
        current = basketFragment;
    }

    // Already showing requested fragment
    if (current == target) {
        return;
    }

    FragmentTransaction tx = fm.beginTransaction();

    if (homeFragment.isAdded()) {
        tx.hide(homeFragment);
    }

    if (libraryFragment.isAdded()) {
        tx.hide(libraryFragment);
    }

    if (basketFragment.isAdded()) {
        tx.hide(basketFragment);
    }

    if (target.isAdded()) {
        tx.show(target);
    }

    tx.commit();
}

    public void switchToHomeTab() {
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_home);
    }

    public void switchToLibraryTab() {
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_library);
    }
}