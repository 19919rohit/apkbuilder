package com.flapchat.app.utils;

import android.widget.ImageView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.net.URL;
import android.os.AsyncTask;

public class ImageLoader {

    public static void loadImage(ImageView imageView, String url) {
        new AsyncTask<String, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(String... strings) {
                try {
                    return BitmapFactory.decodeStream(new URL(strings[0]).openStream());
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }.execute(url);
    }
}