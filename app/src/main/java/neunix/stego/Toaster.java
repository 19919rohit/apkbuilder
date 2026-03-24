package neunix.stego;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class Toaster {

    private static final Map<String, Integer> countMap = new HashMap<>();
    private static long lastResetTime = 0;

    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static final int WINDOW_MS = 2000;
    private static final int MAX_REPEAT = 2;

    // ================= BASIC =================

    public static void show(Context ctx, String msg) {
        showInternal(ctx, msg, false);
    }

    // ================= ERROR (bypass limit) =================

    public static void error(Context ctx, String msg) {
        showInternal(ctx, msg, true);
    }

    // ================= CORE =================

    private static void showInternal(Context ctx, String msg, boolean priority) {

        long now = System.currentTimeMillis();

        if (now - lastResetTime > WINDOW_MS) {
            countMap.clear();
            lastResetTime = now;
        }

        int count = countMap.getOrDefault(msg, 0);

        if (priority || count < MAX_REPEAT) {

            handler.post(() ->
                    Toast.makeText(ctx.getApplicationContext(), msg, Toast.LENGTH_SHORT).show()
            );

            if (!priority) {
                countMap.put(msg, count + 1);
            }
        }
    }
}