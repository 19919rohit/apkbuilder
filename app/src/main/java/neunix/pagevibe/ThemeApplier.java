package neunix.pagevibe;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.content.res.ColorStateList;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.WeakHashMap;

/**
 * Recursively walks a view tree and applies a theme to any view whose
 * android:tag contains one or more comma-separated "theme:*" tokens.
 *
 * TEXT SIZE FIX: previously nothing here ever called setTextSize(), so
 * a theme's textScale had no visible effect anywhere in the app. Every
 * themed TextView now has its ORIGINAL xml-defined size captured exactly
 * once, in BASE_SIZES_SP (a WeakHashMap keyed by the view instance
 * itself, so it never leaks and never needs manual cleanup — entries
 * disappear automatically once a view is garbage collected, e.g. when a
 * Fragment's view is destroyed). Every subsequent apply() recomputes
 * size as baseSp * theme.textScale from that cached original — so
 * switching themes back and forth, or applying the same theme twenty
 * times, can never compound or drift the size.
 *
 * Supported tags (comma-separate multiple on one view, e.g.
 * "theme:buttonBg,theme:buttonText"):
 *   theme:bg            — background color
 *   theme:card           — background color (surface/card tone)
 *   theme:divider         — background color (thin separator Views)
 *   theme:textPrimary      — text color + font + size scale
 *   theme:textSecondary    — text color + font + size scale
 *   theme:accent            — TextView text color (+ size scale) OR
 *                             ImageView/ImageButton tint
 *   theme:buttonBg           — fills an accent-colored action button's
 *                              background (preserves rounded shape)
 *   theme:buttonText          — text color/size for text sitting on a
 *                              theme:buttonBg surface (contrast-correct
 *                              per theme, not hardcoded black/white)
 *   theme:outline              — sets a 1dp stroke color on a
 *                              GradientDrawable background, if present
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

        if (tv instanceof android.widget.EditText) {
            android.widget.EditText et = (android.widget.EditText) tv;

            et.setHintTextColor(theme.textSecondaryColor);

            Drawable bg = et.getBackground();
            if (bg instanceof GradientDrawable) {
                try {
                    GradientDrawable gd = (GradientDrawable) bg.mutate();
                    gd.setColor(theme.cardColor);

                    float density = et.getResources().getDisplayMetrics().density;
                    gd.setStroke(Math.round(1f * density), theme.outlineColor);
                } catch (Throwable ignored) {}
            }
        }
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
                
            case "theme:switch":
    if (view instanceof SwitchMaterial) {
        SwitchMaterial sw = (SwitchMaterial) view;

        ColorStateList thumb = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked}
                },
                new int[]{
                        theme.accentColor,
                        0xFFBDBDBD
                });

        ColorStateList track = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked}
                },
                new int[]{
                        (theme.accentColor & 0x00FFFFFF) | 0x66000000,
                        0xFF666666
                });

        sw.setThumbTintList(thumb);
        sw.setTrackTintList(track);
    }
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

    private static void setBackgroundColorPreservingShape(View view, int color) {
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

    private static void setOutlinePreservingShape(View view, int strokeColor) {
        Drawable bg = view.getBackground();
        if (bg instanceof GradientDrawable) {
            try {
                float density = view.getResources().getDisplayMetrics().density;
                int strokeWidthPx = Math.round(1f * density);
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