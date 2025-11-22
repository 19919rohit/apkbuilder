package com.flapchat.app.network;

public class NetlifyEndpoints {

    public static final String BASE_URL = "https://flapchat.netlify.app/.netlify/functions/";

    public static final String SEND_MESSAGE = BASE_URL + "sendMessage";
    public static final String FETCH_MESSAGES = BASE_URL + "fetchMessages";
    public static final String CREATE_ROOM = BASE_URL + "createRoom";
    public static final String UPDATE_TYPING = BASE_URL + "typingIndicator";
    public static final String UPDATE_PRESENCE = BASE_URL + "presenceUpdate";
    public static final String UPLOAD_FILE = BASE_URL + "uploadFile";
}