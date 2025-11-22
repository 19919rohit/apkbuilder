package com.flapchat.app.data.remote;

import com.flapchat.app.network.HttpClient;
import com.flapchat.app.network.NetlifyEndpoints;

import org.json.JSONObject;

public class PresenceService {

    public static void updatePresence(String userId, boolean online) {
        try {
            JSONObject body = new JSONObject();
            body.put("userId", userId);
            body.put("online", online);

            HttpClient.postJson(NetlifyEndpoints.UPDATE_PRESENCE, body.toString());
        } catch (Exception ignored) {}
    }
}