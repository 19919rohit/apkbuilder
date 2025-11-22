package com.flapchat.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

public class ChatWallpaperManager {

    public static void setWallpaper(ImageView imageView, String filePath) {
        Bitmap bitmap = BitmapFactory.decodeFile(filePath);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
    }
}