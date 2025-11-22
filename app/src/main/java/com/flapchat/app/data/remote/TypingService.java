package com.flapchat.app.data.remote;

import com.flapchat.app.network.HttpClient;
import com.flapchat.app.network.NetlifyEndpoints;

import org.json.JSONObject;

public class TypingService {

    public static void updateTyping(String roomId, String userId, boolean isTyping) {

        try {
            JSONObject body = new JSONObject();
            body.put("roomId", roomId);
            body.put("userId", userId);
            body.put("typing", isTyping);

            HttpClient.postJson(NetlifyEndpoints.UPDATE_TYPING, body.toString());

        } catch (Exception ignored) {}
    }
}