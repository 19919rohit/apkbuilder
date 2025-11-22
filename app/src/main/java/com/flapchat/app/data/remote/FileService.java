package com.flapchat.app.data.remote;

import com.flapchat.app.network.NetlifyEndpoints;
import com.flapchat.app.network.UniversalUploader;

import java.io.File;

public class FileService {

    public static String upload(File file) throws Exception {
        return UniversalUploader.uploadFile(NetlifyEndpoints.UPLOAD_FILE, file);
    }
}