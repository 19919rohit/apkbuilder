package neunix.pagevibe;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ThemeEditorActivity extends AppCompatActivity {

    public static final String EXTRA_THEME_ID = "extra_theme_id"; // null/absent = creating new

    private static final int[] PALETTE = {
            0xFF000000, 0xFF080808, 0xFF151515, 0xFF2A2A2A, 0xFF666666,
            0xFFAAAAAA, 0xFFFFFFFF, 0xFFF2F2F2, 0xFFFFEE00, 0xFFFF9944,
            0xFFFF4444, 0xFFFF6EC7, 0xFF9C27B0, 0xFF6A4CFF, 0xFF4488FF,
            0xFF2266DD, 0xFF1BC5C5, 0xFF44DD88, 0xFF2E7D32, 0xFF8D6E63
    };

    private ThemeManager themeManager;
    private View rootView;
    private String editingThemeId = null;

    private int backgroundColor, cardColor, dividerColor, textPrimaryColor,
            accentColor, buttonTextColor, outlineColor;
    private String selectedFont = "sans-serif";
    private float selectedScale = ThemeManager.TEXT_SCALE_MEDIUM;

    private EditText nameInput;
    private View dotBackground, dotCard, dotDivider, dotTextPrimary, dotAccent, dotButtonText, dotOutline;
    private TextView previewSampleText, previewSubText;

    private LinearLayout fontChoiceRow;
    private TextView sizeSmall, sizeMedium, sizeLarge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_editor);
        rootView = findViewById(android.R.id.content);
        themeManager = new ThemeManager(this);

        nameInput          = findViewById(R.id.themeNameInput);
        dotBackground       = findViewById(R.id.dotBackground);
        dotCard             = findViewById(R.id.dotCard);
        dotDivider           = findViewById(R.id.dotDivider);
        dotTextPrimary        = findViewById(R.id.dotTextPrimary);
        dotAccent               = findViewById(R.id.dotAccent);
        dotButtonText             = findViewById(R.id.dotButtonText);
        dotOutline                 = findViewById(R.id.dotOutline);
        previewSampleText            = findViewById(R.id.previewSampleText);
        previewSubText                = findViewById(R.id.previewSubText);
        fontChoiceRow                  = findViewById(R.id.fontChoiceRow);
        sizeSmall                       = findViewById(R.id.sizeSmall);
        sizeMedium                       = findViewById(R.id.sizeMedium);
        sizeLarge                         = findViewById(R.id.sizeLarge);

        editingThemeId = getIntent().getStringExtra(EXTRA_THEME_ID);
        loadInitialValues();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.rowBackground).setOnClickListener(v -> pickColor(dotBackground, c -> { backgroundColor = c; updatePreview(); }));
        findViewById(R.id.rowCard).setOnClickListener(v -> pickColor(dotCard, c -> { cardColor = c; updatePreview(); }));
        findViewById(R.id.rowDivider).setOnClickListener(v -> pickColor(dotDivider, c -> { dividerColor = c; updatePreview(); }));
        findViewById(R.id.rowTextPrimary).setOnClickListener(v -> pickColor(dotTextPrimary, c -> { textPrimaryColor = c; updatePreview(); }));
        findViewById(R.id.rowAccent).setOnClickListener(v -> pickColor(dotAccent, c -> { accentColor = c; updatePreview(); }));
        findViewById(R.id.rowButtonText).setOnClickListener(v -> pickColor(dotButtonText, c -> { buttonTextColor = c; updatePreview(); }));
        findViewById(R.id.rowOutline).setOnClickListener(v -> pickColor(dotOutline, c -> { outlineColor = c; updatePreview(); }));

        buildFontChoices();
        buildSizeChoices();
        updatePreview();
        applyTheme();

        findViewById(R.id.btnSaveTheme).setOnClickListener(v -> save());
    }
    
    @Override
protected void onResume() {
    super.onResume();
    applyTheme();
}

    private void loadInitialValues() {
        ThemeManager.AppTheme base;
        if (editingThemeId != null) {
            base = themeManager.findById(editingThemeId);
            nameInput.setText(base.name);
            ((TextView) findViewById(R.id.editorTitle)).setText("Edit Theme");
        } else {
            base = themeManager.getActiveTheme(); // sensible starting point
        }
        backgroundColor   = base.backgroundColor;
        cardColor         = base.cardColor;
        dividerColor       = base.dividerColor;
        textPrimaryColor    = base.textPrimaryColor;
        accentColor           = base.accentColor;
        buttonTextColor        = base.buttonTextColor;
        outlineColor             = base.outlineColor;
        selectedFont               = base.fontFamily;
        selectedScale                = base.textScale;

        dotBackground.setBackgroundColor(backgroundColor);
        dotCard.setBackgroundColor(cardColor);
        dotDivider.setBackgroundColor(dividerColor);
        dotTextPrimary.setBackgroundColor(textPrimaryColor);
        dotAccent.setBackgroundColor(accentColor);
        dotButtonText.setBackgroundColor(buttonTextColor);
        dotOutline.setBackgroundColor(outlineColor);
    }

    private interface ColorPicked { void onPicked(int color); }

    private void pickColor(View anchor, ColorPicked callback) {
        View content = LayoutInflater.from(this).inflate(R.layout.popup_color_swatches, null);
        GridLayout grid = content.findViewById(R.id.colorSwatchGrid);
        EditText hexInput = content.findViewById(R.id.customHexInput);
        View useHex = content.findViewById(R.id.btnUseHex);

        PopupWindow popup = new PopupWindow(content, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setElevation(16f);

        int swatchSize = dpToPx(30);
        int margin = dpToPx(4);
        for (int color : PALETTE) {
            View swatch = new View(this);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = swatchSize;
            lp.height = swatchSize;
            lp.setMargins(margin, margin, margin, margin);
            swatch.setLayoutParams(lp);
            swatch.setBackgroundColor(color);
            swatch.setOnClickListener(v -> {
                callback.onPicked(color);
                popup.dismiss();
            });
            grid.addView(swatch);
        }

        useHex.setOnClickListener(v -> {
            String hex = hexInput.getText().toString().trim();
            try {
                int parsed = Color.parseColor(hex.startsWith("#") ? hex : "#" + hex);
                callback.onPicked(parsed);
                popup.dismiss();
            } catch (Throwable t) {
                Toast.makeText(this, "Invalid hex color, try e.g. #4488FF", Toast.LENGTH_SHORT).show();
            }
        });

        popup.showAsDropDown(anchor, 0, 8, Gravity.END);
    }
    
    private void applyTheme() {
    if (rootView == null || themeManager == null)
        return;

    ThemeApplier.apply(
            rootView,
            themeManager.getActiveTheme()
    );

    updatePreview();
}

    private void buildFontChoices() {
        fontChoiceRow.removeAllViews();
        for (int i = 0; i < ThemeManager.FONT_VALUES.length; i++) {
            String value = ThemeManager.FONT_VALUES[i];
            String display = ThemeManager.FONT_DISPLAY_NAMES[i];

            TextView chip = new TextView(this);
            chip.setText(display);
            chip.setTextSize(12f);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));
            chip.setBackgroundResource(R.drawable.bg_button_dark);
            try { chip.setTypeface(Typeface.create(value, Typeface.NORMAL)); } catch (Throwable ignored) {}

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dpToPx(8));
            chip.setLayoutParams(lp);
            chip.setTag("theme:textPrimary");
            ThemeApplier.applyToSingleView(chip, themeManager.getActiveTheme());

            chip.setOnClickListener(v -> {
                selectedFont = value;
                refreshFontChoiceHighlight();
                updatePreview();
            });
            fontChoiceRow.addView(chip);
        }
        refreshFontChoiceHighlight();
    }

    private void refreshFontChoiceHighlight() {
        for (int i = 0; i < fontChoiceRow.getChildCount(); i++) {
            View child = fontChoiceRow.getChildAt(i);
            if (!(child instanceof TextView)) continue;
            boolean selected = ThemeManager.FONT_VALUES[i].equals(selectedFont);
            ((TextView) child).setTextColor(selected ? Color.parseColor("#4488FF") : Color.parseColor("#AAAAAA"));
        }
    }

    private void buildSizeChoices() {
        sizeSmall.setOnClickListener(v -> { selectedScale = ThemeManager.TEXT_SCALE_SMALL; refreshSizeHighlight(); updatePreview(); });
        sizeMedium.setOnClickListener(v -> { selectedScale = ThemeManager.TEXT_SCALE_MEDIUM; refreshSizeHighlight(); updatePreview(); });
        sizeLarge.setOnClickListener(v -> { selectedScale = ThemeManager.TEXT_SCALE_LARGE; refreshSizeHighlight(); updatePreview(); });
        refreshSizeHighlight();
    }

    private void refreshSizeHighlight() {
        sizeSmall.setTextColor(selectedScale == ThemeManager.TEXT_SCALE_SMALL ? Color.parseColor("#4488FF") : Color.parseColor("#AAAAAA"));
        sizeMedium.setTextColor(selectedScale == ThemeManager.TEXT_SCALE_MEDIUM ? Color.parseColor("#4488FF") : Color.parseColor("#AAAAAA"));
        sizeLarge.setTextColor(selectedScale == ThemeManager.TEXT_SCALE_LARGE ? Color.parseColor("#4488FF") : Color.parseColor("#AAAAAA"));
    }

    private void updatePreview() {
    View previewCard = findViewById(R.id.previewCard);

    android.graphics.drawable.Drawable bg = previewCard.getBackground();

    if (bg instanceof android.graphics.drawable.GradientDrawable) {
        try {
            android.graphics.drawable.GradientDrawable drawable =
                    (android.graphics.drawable.GradientDrawable) bg.mutate();
            drawable.setColor(cardColor);
        } catch (Throwable ignored) {
            previewCard.setBackgroundColor(cardColor);
        }
    } else {
        previewCard.setBackgroundColor(cardColor);
    }

    previewSampleText.setTextColor(textPrimaryColor);

    // 30sp is this preview's own base — scaled live exactly the same
    // way ThemeApplier scales real app text.
    previewSampleText.setTextSize(30f * selectedScale);

    try {
        previewSampleText.setTypeface(
                Typeface.create(selectedFont, Typeface.BOLD)
        );
    } catch (Throwable ignored) {}

    previewSubText.setTextColor(accentColor);
    previewSubText.setTextSize(11f * selectedScale);

    dotBackground.setBackgroundColor(backgroundColor);
    dotCard.setBackgroundColor(cardColor);
    dotDivider.setBackgroundColor(dividerColor);
    dotTextPrimary.setBackgroundColor(textPrimaryColor);
    dotAccent.setBackgroundColor(accentColor);
    dotButtonText.setBackgroundColor(buttonTextColor);
    dotOutline.setBackgroundColor(outlineColor);
}

    private void save() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Give your theme a name", Toast.LENGTH_SHORT).show();
            return;
        }

        ThemeManager.AppTheme theme = new ThemeManager.AppTheme(
                editingThemeId, name, false,
                backgroundColor, cardColor, dividerColor,
                textPrimaryColor, blendForSecondary(textPrimaryColor),
                accentColor, buttonTextColor, outlineColor,
                selectedFont, selectedScale);

        themeManager.saveCustomTheme(theme);
        Toast.makeText(this, "Theme saved", Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    /** Secondary text color is still auto-derived from the primary text
     *  pick — kept implicit (not its own row) to hold the picker count
     *  at a manageable 7 explicit decisions instead of 8. */
    private int blendForSecondary(int primary) {
        int r = Color.red(primary), g = Color.green(primary), b = Color.blue(primary);
        float factor = 0.6f;
        return Color.rgb(Math.round(r * factor), Math.round(g * factor), Math.round(b * factor));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}