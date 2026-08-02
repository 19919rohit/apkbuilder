package neunix.pagevibe;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ThemesActivity extends AppCompatActivity {

    private ThemeManager themeManager;
    private final List<ThemeManager.AppTheme> themes = new ArrayList<>();
    private ThemeAdapter adapter;

    private final ActivityResultLauncher<Intent> editorLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> reload());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_themes);
        themeManager = new ThemeManager(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCreateTheme).setOnClickListener(v ->
                editorLauncher.launch(new Intent(this, ThemeEditorActivity.class)));

        RecyclerView recycler = findViewById(R.id.themesRecycler);
        adapter = new ThemeAdapter();
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        recycler.setAdapter(adapter);

        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        ThemeApplier.apply(findViewById(android.R.id.content), themeManager.getActiveTheme());
    }

    private void reload() {
        themes.clear();
        themes.addAll(themeManager.getAllThemes());
        adapter.notifyDataSetChanged();
    }

    private void applyTheme(ThemeManager.AppTheme theme) {
        themeManager.setActiveThemeId(theme.id);
        adapter.notifyDataSetChanged();
        ThemeApplier.apply(findViewById(android.R.id.content), theme);
    }

    private void showItemMenu(View anchor, ThemeManager.AppTheme theme) {
        View content = LayoutInflater.from(this).inflate(R.layout.popup_theme_item_menu, null);
        // Popup reflects the currently ACTIVE app theme, not the theme
        // this specific card represents — consistent with every other
        // theme-aware popup in the app.
        ThemeApplier.apply(content, themeManager.getActiveTheme());

        PopupWindow popup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(16f);

        content.findViewById(R.id.themeMenuEdit).setOnClickListener(v -> {
            popup.dismiss();
            Intent i = new Intent(this, ThemeEditorActivity.class);
            i.putExtra(ThemeEditorActivity.EXTRA_THEME_ID, theme.id);
            editorLauncher.launch(i);
        });
        content.findViewById(R.id.themeMenuDelete).setOnClickListener(v -> {
            popup.dismiss();
            confirmDelete(theme);
        });

        popup.showAsDropDown(anchor, 0, 8, Gravity.END);
    }

    private void confirmDelete(ThemeManager.AppTheme theme) {
        AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Delete theme?")
                .setMessage("\"" + theme.name + "\" will be permanently deleted.")
                .setPositiveButton("Delete", (d, w) -> {
                    themeManager.deleteCustomTheme(theme.id);
                    reload();
                })
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();
        DialogUtil.applyDestructiveConfirm(dialog);
        dialog.show();
    }

    private class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_theme_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            ThemeManager.AppTheme theme = themes.get(pos);
            boolean active = theme.id.equals(themeManager.getActiveTheme().id);

            h.name.setText(theme.name);
            h.kind.setText(theme.builtIn ? "Built-in" : "Custom");

            android.graphics.drawable.Drawable bg = h.previewBox.getBackground();
            if (bg instanceof android.graphics.drawable.GradientDrawable) {
                try { ((android.graphics.drawable.GradientDrawable) bg.mutate()).setColor(theme.backgroundColor); }
                catch (Throwable ignored) { h.previewBox.setBackgroundColor(theme.backgroundColor); }
            } else {
                h.previewBox.setBackgroundColor(theme.backgroundColor);
            }
            h.sample.setTextColor(theme.textPrimaryColor);
            try { h.sample.setTypeface(Typeface.create(theme.fontFamily, Typeface.BOLD)); } catch (Throwable ignored) {}
            h.accentDot.setBackgroundColor(theme.accentColor);

            h.activeCheck.setVisibility(active ? View.VISIBLE : View.GONE);
            h.overflow.setVisibility(theme.builtIn ? View.GONE : View.VISIBLE);

            h.previewBox.setOnClickListener(v -> applyTheme(theme));
            h.overflow.setOnClickListener(v -> showItemMenu(v, theme));
        }

        @Override public int getItemCount() { return themes.size(); }

        class VH extends RecyclerView.ViewHolder {
            android.widget.FrameLayout previewBox;
            TextView sample, name, kind;
            View accentDot;
            ImageView activeCheck;
            ImageButton overflow;

            VH(View v) {
                super(v);
                previewBox  = v.findViewById(R.id.themePreviewBox);
                sample      = v.findViewById(R.id.themePreviewSample);
                accentDot   = v.findViewById(R.id.themeAccentDot);
                activeCheck = v.findViewById(R.id.themeActiveCheck);
                overflow    = v.findViewById(R.id.themeOverflow);
                name        = v.findViewById(R.id.themeCardName);
                kind        = v.findViewById(R.id.themeCardKind);
            }
        }
    }
}