package neunix.stego;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.Random;

public final class ContentAwareExpander {

    private ContentAwareExpander() {}

    /**
     * Expands image using content-aware mirrored edge blending.
     *
     * @param original Original bitmap (ARGB_8888 recommended)
     * @param scaleFactor 1.0f = no change, 2.0f = 400% area
     */
    public static Bitmap expand(Bitmap original, float scaleFactor) {

        if (original == null) return null;
        if (scaleFactor <= 1.0f) return original;

        int w = original.getWidth();
        int h = original.getHeight();

        int newW = Math.max(w + 2, Math.round(w * scaleFactor));
        int newH = Math.max(h + 2, Math.round(h * scaleFactor));

        Bitmap expanded = Bitmap.createBitmap(
                newW,
                newH,
                Bitmap.Config.ARGB_8888
        );

        int[] origPixels = new int[w * h];
        original.getPixels(origPixels, 0, w, 0, 0, w, h);

        int[] newPixels = new int[newW * newH];

        int offsetX = (newW - w) / 2;
        int offsetY = (newH - h) / 2;

        // 1️⃣ Copy original to center
        for (int y = 0; y < h; y++) {
            System.arraycopy(
                    origPixels,
                    y * w,
                    newPixels,
                    (y + offsetY) * newW + offsetX,
                    w
            );
        }

        // 2️⃣ Expand Left + Right
        if (offsetX > 0) {
            expandHorizontal(
                    newPixels,
                    origPixels,
                    w, h,
                    newW, newH,
                    offsetX, offsetY
            );
        }

        // 3️⃣ Expand Top + Bottom
        if (offsetY > 0) {
            expandVertical(
                    newPixels,
                    newW, newH,
                    offsetX, offsetY,
                    w, h
            );
        }

        expanded.setPixels(newPixels, 0, newW, 0, 0, newW, newH);

        return expanded;
    }

    private static void expandHorizontal(
            int[] newPixels,
            int[] origPixels,
            int w, int h,
            int newW, int newH,
            int offsetX, int offsetY
    ) {

        Random random = new Random();

        for (int y = 0; y < h; y++) {

            int leftEdgeColor = origPixels[y * w];
            int rightEdgeColor = origPixels[y * w + (w - 1)];

            for (int x = 0; x < offsetX; x++) {

                float factor = (float) x / offsetX;

                int blended = blendWithNoise(
                        leftEdgeColor,
                        factor,
                        random
                );

                newPixels[(y + offsetY) * newW + x] = blended;
            }

            for (int x = offsetX + w; x < newW; x++) {

                float factor =
                        (float) (x - (offsetX + w)) / offsetX;

                int blended = blendWithNoise(
                        rightEdgeColor,
                        factor,
                        random
                );

                newPixels[(y + offsetY) * newW + x] = blended;
            }
        }
    }

    private static void expandVertical(
            int[] newPixels,
            int newW, int newH,
            int offsetX, int offsetY,
            int w, int h
    ) {

        Random random = new Random();

        for (int x = 0; x < newW; x++) {

            int topEdgeColor = newPixels[offsetY * newW + x];
            int bottomEdgeColor = newPixels[(offsetY + h - 1) * newW + x];

            for (int y = 0; y < offsetY; y++) {

                float factor = (float) y / offsetY;

                newPixels[y * newW + x] =
                        blendWithNoise(
                                topEdgeColor,
                                factor,
                                random
                        );
            }

            for (int y = offsetY + h; y < newH; y++) {

                float factor =
                        (float) (y - (offsetY + h)) / offsetY;

                newPixels[y * newW + x] =
                        blendWithNoise(
                                bottomEdgeColor,
                                factor,
                                random
                        );
            }
        }
    }

    /**
     * Blend base color with adaptive noise.
     * factor → 0 near original edge, 1 far from edge.
     */
    private static int blendWithNoise(
            int baseColor,
            float factor,
            Random random
    ) {

        int r = Color.red(baseColor);
        int g = Color.green(baseColor);
        int b = Color.blue(baseColor);

        int noiseStrength = (int) (factor * 20); // reduced noise

        if (noiseStrength > 0) {

            r = clamp(
                    r + random.nextInt(noiseStrength * 2 + 1)
                            - noiseStrength
            );

            g = clamp(
                    g + random.nextInt(noiseStrength * 2 + 1)
                            - noiseStrength
            );

            b = clamp(
                    b + random.nextInt(noiseStrength * 2 + 1)
                            - noiseStrength
            );
        }

        return Color.argb(255, r, g, b);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}