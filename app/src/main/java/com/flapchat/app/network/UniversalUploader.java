package com.flapchat.app.network;

import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class UniversalUploader {

    private static final String TAG = "UniversalUploader";

    public static String uploadFile(String urlString, File file) throws Exception {

        String boundary = "----FlapChatBoundary" + System.currentTimeMillis();
        String LINE = "\r\n";

        URL url = new URL(urlString);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(30000);
        conn.setConnectTimeout(30000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        OutputStream os = conn.getOutputStream();
        DataOutputStream dos = new DataOutputStream(os);

        // ----- File part -----
        dos.writeBytes("--" + boundary + LINE);
        dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"" + LINE);
        dos.writeBytes("Content-Type: application/octet-stream" + LINE);
        dos.writeBytes(LINE);

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[4096];
        int len;
        while ((len = fis.read(buffer)) != -1) {
            dos.write(buffer, 0, len);
        }

        fis.close();
        dos.writeBytes(LINE);

        // End boundary
        dos.writeBytes("--" + boundary + "--" + LINE);
        dos.flush();
        dos.close();

        InputStream is = conn.getResponseCode() < 400 ?
                conn.getInputStream() : conn.getErrorStream();

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        return sb.toString();
    }
}