package neunix.pagevibe;

import android.view.View;
import android.widget.Toast;

import androidx.appcompat.widget.TooltipCompat;

/**
 * Adds accessible, discoverable tooltips to icon-only buttons.
 * Uses TooltipCompat (long-press shows a system tooltip bubble on API 26+,
 * and falls back to a short Toast on older devices so the label is never
 * silently lost).
 */
public class TooltipUtil {

    public static void apply(View view, String label) {
        if (view == null || label == null) return;
        view.setContentDescription(label);
        TooltipCompat.setTooltipText(view, label);

        // Belt-and-braces fallback for very old devices/launchers where the
        // tooltip bubble doesn't render — a quick toast still tells the user
        // what the icon does.
        view.setOnLongClickListener(v -> {
            Toast.makeText(v.getContext(), label, Toast.LENGTH_SHORT).show();
            return true;
        });
    }
}