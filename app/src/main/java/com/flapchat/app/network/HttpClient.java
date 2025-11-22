package com.flapchat.app.network;

import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpClient {

    private static final int TIMEOUT = 15000;

    public static String postJson(String urlString, String jsonBody) throws Exception {
        URL url = new URL(urlString);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setReadTimeout(TIMEOUT);
        connection.setConnectTimeout(TIMEOUT);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        OutputStream os = connection.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));
        writer.write(jsonBody);
        writer.flush();
        writer.close();
        os.close();

        int code = connection.getResponseCode();

        InputStream is = (code < 400) ?
                connection.getInputStream() :
                connection.getErrorStream();

        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        return sb.toString();
    }

    public static String get(String urlString) throws Exception {
        URL url = new URL(urlString);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setReadTimeout(TIMEOUT);
        connection.setConnectTimeout(TIMEOUT);
        connection.setRequestMethod("GET");

        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder sb = new StringBuilder();

        String line;
        while ((line = br.readLine()) != null) sb.append(line);

        br.close();
        return sb.toString();
    }
}