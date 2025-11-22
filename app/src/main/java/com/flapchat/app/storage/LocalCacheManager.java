package com.flapchat.app.storage;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

public class LocalCacheManager {
    private static LruCache<String, Bitmap> imageCache;

    public static void init() {
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        imageCache = new LruCache<>(cacheSize);
    }

    public static void putImage(String key, Bitmap bitmap) {
        if (getImage(key) == null) {
            imageCache.put(key, bitmap);
        }
    }

    public static Bitmap getImage(String key) {
        return imageCache.get(key);
    }
}