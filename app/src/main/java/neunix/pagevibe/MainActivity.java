package neunix.pagevibe;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
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
        subscribeToFcmTopicsWithRetry();

        AnalyticsHelper.logIfTagged(this, getIntent());

        handleIncomingViewIntent(getIntent());

        bottomNav = findViewById(R.id.bottomNav);
        showTab(homeFragment);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home)    { showTab(homeFragment);    return true; }
            if (id == R.id.nav_library) { showTab(libraryFragment); return true; }
            if (id == R.id.nav_basket)  { showTab(basketFragment);  return true; }
            return false;
        });

        // Back press from Library or Basket returns to Home instead of
        // exiting the app directly — only exits (default system
        // behavior) when already on Home.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
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
        // Cheap to re-check every resume — if the earlier subscribe
        // attempt failed (no network, Play Services updating, etc.),
        // this keeps trying until it genuinely succeeds instead of
        // giving up after one silent failure.
        subscribeToFcmTopicsWithRetry();
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
     * FIXED: previously fire-and-forget, so a failed first attempt (no
     * network at first launch, Play Services mid-update, etc.) meant that
     * device was permanently NOT subscribed to "all" with no retry — the
     * exact kind of silent failure that explains "works on my phone, not
     * my friend's." Success is now persisted and checked before retrying,
     * so this becomes a no-op the moment it has genuinely succeeded once.
     */
    private void subscribeToFcmTopicsWithRetry() {
        NotificationPreferences prefs = new NotificationPreferences(this);
        if (prefs.isFcmTopicSubscribed()) return;

        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all")
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            prefs.setFcmTopicSubscribed(true);
                        }
                        // On failure, deliberately leave the flag false —
                        // the next onResume() will simply try again.
                    });
        } catch (Throwable ignored) {
            // FCM not configured (e.g. google-services.json missing) —
            // never block app startup on this.
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
        FragmentTransaction tx = fm.beginTransaction();
        if (!homeFragment.isAdded())    tx.add(R.id.fragmentContainer, homeFragment, "home");
        if (!libraryFragment.isAdded()) tx.add(R.id.fragmentContainer, libraryFragment, "library");
        if (!basketFragment.isAdded())  tx.add(R.id.fragmentContainer, basketFragment, "basket");
        tx.hide(homeFragment);
        tx.hide(libraryFragment);
        tx.hide(basketFragment);
        tx.show(target);
        tx.commitAllowingStateLoss();
    }

    public void switchToHomeTab() {
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_home);
    }

    public void switchToLibraryTab() {
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_library);
    }
}