package com.flapchat.app.data.remote;

import android.util.Log;

import com.flapchat.app.network.HttpClient;
import com.flapchat.app.network.NetlifyEndpoints;

import org.json.JSONObject;

public class NotificationService {

    public static void pushNewMessage(String userId, String messagePreview) {
        try {
            JSONObject body = new JSONObject();
            body.put("toUser", userId);
            body.put("preview", messagePreview);

            HttpClient.postJson(NetlifyEndpoints.SEND_MESSAGE, body.toString());
        } catch (Exception ignored) {}
    }
}