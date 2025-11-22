package com.flapchat.app.data.remote;

import com.flapchat.app.network.HttpClient;
import com.flapchat.app.network.NetlifyEndpoints;

import org.json.JSONObject;

public class ChatService {

    public static String sendMessage(String roomId, String senderId, String content,
                                     String type, String metadataJson) throws Exception {

        JSONObject body = new JSONObject();
        body.put("roomId", roomId);
        body.put("senderId", senderId);
        body.put("content", content);
        body.put("type", type);
        body.put("metadata", metadataJson);

        return HttpClient.postJson(NetlifyEndpoints.SEND_MESSAGE, body.toString());
    }

    public static String fetchMessages(String roomId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("roomId", roomId);

        return HttpClient.postJson(NetlifyEndpoints.FETCH_MESSAGES, body.toString());
    }

    public static String createRoom(String user1, String user2) throws Exception {
        JSONObject body = new JSONObject();
        body.put("user1", user1);
        body.put("user2", user2);

        return HttpClient.postJson(NetlifyEndpoints.CREATE_ROOM, body.toString());
    }
}