package neunix.stego;

import android.graphics.Bitmap;

public class ContentAwareExpander {

    private ContentAwareExpander() {}

    /**
     * Expands the image canvas while keeping the original image centered.
     * Extra area is filled using replicated edge pixels so pixel statistics remain natural.
     *
     * factor:
     * 0 = no expansion
     * 1 = 25%
     * 2 = 50%
     * 4 = 100%
     */
    public static Bitmap expand(Bitmap original, int factor) {

        if (original == null || factor <= 0) {
            return original;
        }

        // Ensure safe pixel format
        Bitmap src = original.copy(Bitmap.Config.ARGB_8888, false);

        int origW = src.getWidth();
        int origH = src.getHeight();

        int newW = origW * (factor + 1);
        int newH = origH * (factor + 1);

        Bitmap expanded = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888);

        int[] srcPixels = new int[origW * origH];
        src.getPixels(srcPixels, 0, origW, 0, 0, origW, origH);

        int[] outPixels = new int[newW * newH];

        int offsetX = (newW - origW) / 2;
        int offsetY = (newH - origH) / 2;

        for (int y = 0; y < newH; y++) {

            int srcY = clamp(y - offsetY, 0, origH - 1);

            for (int x = 0; x < newW; x++) {

                int srcX = clamp(x - offsetX, 0, origW - 1);

                outPixels[y * newW + x] = srcPixels[srcY * origW + srcX];
            }
        }

        expanded.setPixels(outPixels, 0, newW, 0, 0, newW, newH);

        return expanded;
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}