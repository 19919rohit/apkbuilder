package neunix.pagevibe;

import android.app.AlertDialog;
import android.graphics.Color;
import android.widget.Button;

public class DialogUtil {

    private DialogUtil() {}

    /** Neutral dialogs (rename, save-as, etc.) — all buttons white. */
    public static void whitenButtons(AlertDialog dialog) {
        if (dialog == null) return;
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button neutral  = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (positive != null) positive.setTextColor(Color.WHITE);
            if (negative != null) negative.setTextColor(Color.WHITE);
            if (neutral  != null) neutral.setTextColor(Color.WHITE);
        });
    }

    /** Destructive/irreversible dialogs (delete, clear all) — positive
     *  action rendered in red so the weight of the action is visually
     *  obvious, negative/cancel stays a calm white. */
    public static void applyDestructiveConfirm(AlertDialog dialog) {
        if (dialog == null) return;
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (positive != null) positive.setTextColor(Color.parseColor("#FF5252"));
            if (negative != null) negative.setTextColor(Color.WHITE);
        });
    }
}