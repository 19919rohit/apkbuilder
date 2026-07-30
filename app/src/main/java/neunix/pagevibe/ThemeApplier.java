package neunix.pagevibe;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Recursively walks a view tree and applies a theme to any view whose
 * android:tag starts with "theme:" — an opt-in mechanism, not a forced
 * global retint. This lets any screen adopt live theming just by adding
 * one tag attribute per view in its XML and calling
 * ThemeApplier.apply(rootView, theme) once (typically in onResume()).
 *
 * Supported tags:
 *   theme:bg             — background color (preserves rounded-corner
 *                           drawables by mutating them instead of
 *                           replacing with a flat rectangle, if the
 *                           existing background is a GradientDrawable)
 *   theme:card            — same treatment as theme:bg, using cardColor
 *   theme:textPrimary      — text color + font family
 *   theme:textSecondary    — text color + font family
 *   theme:accent           — TextView text color OR ImageView/ImageButton
 *                            tint, whichever applies
 */
public class ThemeApplier {

    private ThemeApplier() {}

    public static void apply(View root, ThemeManager.AppTheme theme) {
        if (root == null || theme == null) return;
        applyRecursive(root, theme);
    }

    private static void applyRecursive(View view, ThemeManager.AppTheme theme) {
        Object tagObj = view.getTag();
        if (tagObj instanceof String) {
            applyToView(view, (String) tagObj, theme);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyRecursive(group.getChildAt(i), theme);
            }
        }
    }

    private static void applyToView(View view, String tag, ThemeManager.AppTheme theme) {
        switch (tag) {
            case "theme:bg":
                setBackgroundPreservingShape(view, theme.backgroundColor);
                break;
            case "theme:card":
                setBackgroundPreservingShape(view, theme.cardColor);
                break;
            case "theme:textPrimary":
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    tv.setTextColor(theme.textPrimaryColor);
                    tv.setTypeface(resolveTypeface(theme.fontFamily));
                }
                break;
            case "theme:textSecondary":
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    tv.setTextColor(theme.textSecondaryColor);
                    tv.setTypeface(resolveTypeface(theme.fontFamily));
                }
                break;
            case "theme:accent":
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(theme.accentColor);
                } else if (view instanceof ImageView) {
                    ((ImageView) view).setColorFilter(theme.accentColor);
                }
                break;
            default:
                break;
        }
    }

    /**
     * If the view's current background is a GradientDrawable (which is
     * how every rounded-corner card background in this app is defined),
     * mutate + recolor it in place — this preserves the rounded corners
     * exactly, since only the fill color changes, not the shape. Falls
     * back to a flat setBackgroundColor() for anything else.
     */
    private static void setBackgroundPreservingShape(View view, int color) {
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

    private static Typeface resolveTypeface(String fontFamily) {
        try {
            return Typeface.create(fontFamily, Typeface.NORMAL);
        } catch (Throwable t) {
            return Typeface.DEFAULT;
        }
    }
}