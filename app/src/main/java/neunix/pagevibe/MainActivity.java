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
        
        FirebaseMessaging.getInstance()
        .subscribeToTopic("all")
        .addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                android.util.Log.d("PageVibe", "Subscribed to topic: all");
            } else {
                android.util.Log.e("PageVibe", "Failed to subscribe", task.getException());
            }
        });

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
     * External "Open with PageVibe" intents: the incoming content:// URI
     * may point at a TEMPORARY file (email attachment viewer cache,
     * browser download cache, etc.) that can vanish once the source app
     * closes. ExternalPdfPersister copies it into Documents/PageVibe/PDF
     * first, and PdfActivity is launched against that permanent copy —
     * so the file genuinely belongs to the device from that point on.
     * PdfActivity itself still registers into the Library on successful
     * open (see PdfActivity.onPdfOpened), so no duplicate bookkeeping
     * is needed here.
     */
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
                // Persistence failed (no permission, IO error, etc.) —
                // still open the original URI rather than blocking the
                // user from reading the file at all.
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