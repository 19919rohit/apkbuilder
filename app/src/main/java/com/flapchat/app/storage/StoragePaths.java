package com.flapchat.app.storage;

import android.os.Environment;
import java.io.File;

public class StoragePaths {

    public static File getChatUploadsDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), "FlapChat/chat-uploads");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getCacheDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), "FlapChat/cache");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
}