package neunix.pagevibe;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.WeakHashMap;

/**
 * Recursively walks a view tree and applies a theme to any view whose
 * android:tag contains one or more comma-separated "theme:*" tokens.
 * See prior delivery for the full tag reference (bg/card/divider/
 * textPrimary/textSecondary/accent/buttonBg/buttonText/outline).
 *
 * setBackgroundColorPreservingShape() and setOutlinePreservingShape() are
 * now PUBLIC static — the only change in this file — so
 * ThemeEditorActivity's live preview can reuse the exact same "mutate the
 * GradientDrawable in place" logic the real app uses, instead of
 * duplicating it. That guarantees the editor's preview and the actual
 * themed app can never visually disagree about how the coloring math
 * works.
 */
public class ThemeApplier {

    private ThemeApplier() {}

    private static final WeakHashMap<TextView, Float> BASE_SIZES_SP = new WeakHashMap<>();

    public static void apply(View root, ThemeManager.AppTheme theme) {
        if (root == null || theme == null) return;
        applyRecursive(root, theme);
    }

    public static void applyToSingleView(View itemRoot, ThemeManager.AppTheme theme) {
        apply(itemRoot, theme);
    }

    private static void applyRecursive(View view, ThemeManager.AppTheme theme) {
        Object tagObj = view.getTag();
        if (tagObj instanceof String) {
            String raw = (String) tagObj;
            for (String token : raw.split(",")) {
                applySingleTag(view, token.trim(), theme);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRecursive(group.getChildAt(i), theme);
            }
        }
    }

    private static void applySingleTag(View view, String tag, ThemeManager.AppTheme theme) {
        switch (tag) {
            case "theme:bg":
                setBackgroundColorPreservingShape(view, theme.backgroundColor);
                break;
            case "theme:card":
                setBackgroundColorPreservingShape(view, theme.cardColor);
                break;
            case "theme:divider":
                view.setBackgroundColor(theme.dividerColor);
                break;
            case "theme:textPrimary":
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    tv.setTextColor(theme.textPrimaryColor);
                    tv.setTypeface(resolveTypeface(theme.fontFamily));
                    applyTextScale(tv, theme.textScale);
                }
                break;
            case "theme:textSecondary":
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    tv.setTextColor(theme.textSecondaryColor);
                    tv.setTypeface(resolveTypeface(theme.fontFamily));
                    applyTextScale(tv, theme.textScale);
                }
                break;
            case "theme:accent":
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    tv.setTextColor(theme.accentColor);
                    tv.setTypeface(resolveTypeface(theme.fontFamily));
                    applyTextScale(tv, theme.textScale);
                } else if (view instanceof ImageView) {
                    ((ImageView) view).setColorFilter(theme.accentColor);
                }
                break;
            case "theme:buttonBg":
                setBackgroundColorPreservingShape(view, theme.accentColor);
                break;
            case "theme:buttonText":
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    tv.setTextColor(theme.buttonTextColor);
                    tv.setTypeface(resolveTypeface(theme.fontFamily));
                    applyTextScale(tv, theme.textScale);
                }
                break;
            case "theme:outline":
                setOutlinePreservingShape(view, theme.outlineColor);
                break;
            default:
                break;
        }
    }

    private static void applyTextScale(TextView tv, float scale) {
        float baseSp = getOrCacheBaseSizeSp(tv);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp * scale);
    }

    private static float getOrCacheBaseSizeSp(TextView tv) {
        Float cached = BASE_SIZES_SP.get(tv);
        if (cached != null) return cached;

        float currentPx = tv.getTextSize();
        float scaledDensity = tv.getResources().getDisplayMetrics().scaledDensity;
        float sp = scaledDensity > 0 ? currentPx / scaledDensity : 14f;
        BASE_SIZES_SP.put(tv, sp);
        return sp;
    }

    public static void setBackgroundColorPreservingShape(View view, int color) {
        Drawable bg = view.getBackground();
        if (bg instanceof GradientDrawable) {
            try {
                ((GradientDrawable) bg.mutate()).setColor(color);
                return;
            } catch (Throwable ignored) {
                // Fall through to the flat-color fallback below.
            }
        }
        view.setBackgroundColor(color);
    }

    public static void setOutlinePreservingShape(View view, int strokeColor) {
        Drawable bg = view.getBackground();
        if (bg instanceof GradientDrawable) {
            try {
                float density = view.getResources().getDisplayMetrics().density;
                int strokeWidthPx = Math.round(1.5f * density);
                ((GradientDrawable) bg.mutate()).setStroke(strokeWidthPx, strokeColor);
            } catch (Throwable ignored) {
                // No safe fallback for a non-GradientDrawable background —
                // silently skip rather than replace the whole background.
            }
        }
    }

    private static Typeface resolveTypeface(String fontFamily) {
        try {
            return Typeface.create(fontFamily, Typeface.NORMAL);
        } catch (Throwable t) {
            return Typeface.DEFAULT;
        }
    }
}