package neunix.stego;

import android.graphics.Bitmap;

public class ContentAwareExpander {

    private ContentAwareExpander(){}

    // ================= SAFETY =================

    private static final long MAX_PIXELS = 20_000_000; // 20MP


    // ================= MAIN =================

    public static Bitmap expand(Bitmap original, int factor){

        if(original == null) return null;

        if(factor <= 0) return original;

        int w = original.getWidth();
        int h = original.getHeight();

        int newW = w;
        int newH = h;

        switch(factor){

            case 1: // +25%
                newW = (int)(w * 1.25);
                newH = (int)(h * 1.25);
                break;

            case 2: // +50%
                newW = (int)(w * 1.5);
                newH = (int)(h * 1.5);
                break;

            case 4: // +100%
                newW = w * 2;
                newH = h * 2;
                break;

            default:
                return original;
        }

        return expandToSize(original, newW, newH);
    }


    // ================= AUTO FIT =================

    public static Bitmap expandToFitPayload(Bitmap original, int payloadBytes){

        int w = original.getWidth();
        int h = original.getHeight();

        long payloadBits = (long) payloadBytes * 8;
        long capacityBits = (long) w * h * 3;

        if(payloadBits <= capacityBits){
            return original;
        }

        double scale = Math.sqrt((double) payloadBits / capacityBits);

        int newW = (int) Math.ceil(w * scale);
        int newH = (int) Math.ceil(h * scale);

        if((long)newW * newH > MAX_PIXELS){
            throw new RuntimeException("Payload too large");
        }

        return expandToSize(original, newW, newH);
    }


    // ================= CORE EXPANSION =================

    public static Bitmap expandToSize(Bitmap original, int newW, int newH){

        int origW = original.getWidth();
        int origH = original.getHeight();

        if(newW <= origW && newH <= origH){
            return original;
        }

        if((long)newW * newH > MAX_PIXELS){
            throw new RuntimeException("Expansion too large");
        }

        Bitmap expanded =
                Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888);

        int[] srcRow = new int[origW];
        int[] dstRow = new int[newW];

        for(int y = 0; y < newH; y++){

            // 🔥 deterministic mapping (no offset, no clamp)
            int srcY = (y < origH) ? y : (y % origH);

            original.getPixels(srcRow, 0, origW, 0, srcY, origW, 1);

            for(int x = 0; x < newW; x++){

                int srcX = (x < origW) ? x : (x % origW);

                dstRow[x] = srcRow[srcX];
            }

            expanded.setPixels(dstRow, 0, newW, 0, y, newW, 1);
        }

        return expanded;
    }
}