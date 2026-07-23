package neunix.pagevibe;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_shell);

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

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {

                // If not on Home, go to Home first
                if (bottomNav.getSelectedItemId() != R.id.nav_home) {
                    bottomNav.setSelectedItemId(R.id.nav_home);
                    return;
                }

                // Already on Home -> show exit dialog
                showExitDialog();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingViewIntent(intent);
    }

    /**
     * External "open with PageVibe" intents launch the reader directly —
     * PdfActivity itself registers the file into the Library on a
     * successful open (see PdfActivity.onPdfOpened), so there's no need
     * to duplicate that bookkeeping here.
     */
    private void handleIncomingViewIntent(Intent incoming) {
        if (incoming == null || !Intent.ACTION_VIEW.equals(incoming.getAction())
                || incoming.getData() == null) return;

        Uri uri = incoming.getData();
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

    private void showExitDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Exit PageVibe")
                .setMessage("Do you want to exit the app?")
                .setPositiveButton("Exit", (d, which) -> finishAffinity())
                .setNegativeButton("Cancel", null)
                .create();

        DialogUtil.applyDestructiveConfirm(dialog);
        dialog.show();
    }

    public void switchToHomeTab() {
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_home);
    }

    public void switchToLibraryTab() {
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_library);
    }
}