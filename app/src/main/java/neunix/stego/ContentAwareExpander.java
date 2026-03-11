package neunix.stego;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class ContentAwareExpander {

    /**
     * Expand a bitmap intelligently based on payload factor.
     * Original image stays centered. Canvas only grows as needed.
     *
     * @param original Original bitmap
     * @param factor   1 = 25%, 2 = 50%, 4 = 100% expansion factor
     * @return Expanded bitmap
     */
    public static Bitmap expand(Bitmap original, int factor) {

        int origWidth = original.getWidth();
        int origHeight = original.getHeight();

        // Calculate scale factor
        double scaleMultiplier;
        switch (factor) {
            case 1: scaleMultiplier = 1.25; break; // +25%
            case 2: scaleMultiplier = 1.5; break;  // +50%
            case 4: scaleMultiplier = 2.0; break;  // +100%
            default: scaleMultiplier = 1.0;
        }

        int newWidth = (int)(origWidth * scaleMultiplier);
        int newHeight = (int)(origHeight * scaleMultiplier);

        // Only create new canvas if it actually grows
        if (newWidth == origWidth && newHeight == origHeight) return original;

        Bitmap expanded = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(expanded);

        // Use Paint for proper filtering
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);

        // Fill background with average color
        int avgColor = getAverageColor(original);
        canvas.drawColor(avgColor);

        // Draw original image centered
        float left = (newWidth - origWidth) / 2f;
        float top = (newHeight - origHeight) / 2f;
        canvas.drawBitmap(original, left, top, paint);

        return expanded;
    }

    /**
     * Get average color of the bitmap using sampled pixels
     */
    private static int getAverageColor(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        long r=0, g=0, b=0;
        int count = 0;

        int step = Math.max(1, Math.min(width, height)/100); // sampling every few pixels
        for (int x=0; x<width; x+=step){
            for (int y=0; y<height; y+=step){
                int pixel = bmp.getPixel(x,y);
                r += Color.red(pixel);
                g += Color.green(pixel);
                b += Color.blue(pixel);
                count++;
            }
        }
        return Color.rgb((int)(r/count), (int)(g/count), (int)(b/count));
    }
}