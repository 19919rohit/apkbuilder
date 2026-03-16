package neunix.stego;

import android.graphics.Bitmap;

/*
=========================================================
 CONTENT AWARE EXPANDER
---------------------------------------------------------
 Expands carrier images when payload exceeds capacity.

 Features
 - API compatible with EmbedActivity.expand()
 - RAM efficient tile processing
 - Safe pixel limit protection
 - Center expansion
=========================================================
*/

public class ContentAwareExpander {

    private ContentAwareExpander(){}

    /* =====================================================
       SAFETY LIMIT
       ===================================================== */

    private static final long MAX_PIXELS = 20_000_000; // 20MP limit

    /* tile processing height to reduce memory spikes */
    private static final int TILE_HEIGHT = 256;


    /* =====================================================
       MAIN API USED BY EmbedActivity
       ===================================================== */

    public static Bitmap expand(Bitmap original, int factor){

        if(original == null) return null;

        if(factor <= 0){
            return original;
        }

        int w = original.getWidth();
        int h = original.getHeight();

        int newW = w;
        int newH = h;

        switch(factor){

            case 1: // Expand 25%
                newW = (int)(w * 1.25);
                newH = (int)(h * 1.25);
                break;

            case 2: // Expand 50%
                newW = (int)(w * 1.5);
                newH = (int)(h * 1.5);
                break;

            case 4: // Expand 100%
                newW = w * 2;
                newH = h * 2;
                break;

            default:
                return original;
        }

        return expandToSize(original,newW,newH);
    }


    /* =====================================================
       AUTO EXPAND BASED ON PAYLOAD SIZE
       ===================================================== */

    public static Bitmap expandToFitPayload(Bitmap original, int payloadBytes){

        int w = original.getWidth();
        int h = original.getHeight();

        long payloadBits = (long)payloadBytes * 8;
        long capacityBits = (long)w * h * 3;

        if(payloadBits <= capacityBits){
            return original;
        }

        double scale = Math.sqrt((double)payloadBits / capacityBits);

        int newW = (int)Math.ceil(w * scale);
        int newH = (int)Math.ceil(h * scale);

        if((long)newW * newH > MAX_PIXELS){
            throw new RuntimeException("Payload too large for expansion");
        }

        return expandToSize(original,newW,newH);
    }


    /* =====================================================
       EXPAND BITMAP TO SPECIFIC SIZE
       ===================================================== */

    public static Bitmap expandToSize(Bitmap original, int newW, int newH){

        int origW = original.getWidth();
        int origH = original.getHeight();

        if(newW <= origW && newH <= origH){
            return original;
        }

        if((long)newW * newH > MAX_PIXELS){
            throw new RuntimeException("Expansion exceeds safe pixel limit");
        }

        Bitmap expanded =
                Bitmap.createBitmap(newW,newH,Bitmap.Config.ARGB_8888);

        int offsetX = (newW - origW)/2;
        int offsetY = (newH - origH)/2;

        int[] srcRow = new int[origW];
        int[] dstRow = new int[newW];

        for(int tileY = 0; tileY < newH; tileY += TILE_HEIGHT){

            int endY = Math.min(tileY + TILE_HEIGHT,newH);

            for(int y = tileY; y < endY; y++){

                int srcY = clamp(y-offsetY,0,origH-1);

                original.getPixels(srcRow,0,origW,0,srcY,origW,1);

                for(int x = 0; x < newW; x++){

                    int srcX = clamp(x-offsetX,0,origW-1);

                    dstRow[x] = srcRow[srcX];
                }

                expanded.setPixels(dstRow,0,newW,0,y,newW,1);
            }
        }

        return expanded;
    }


    /* =====================================================
       CLAMP HELPER
       ===================================================== */

    private static int clamp(int value,int min,int max){

        if(value < min) return min;

        if(value > max) return max;

        return value;
    }

}