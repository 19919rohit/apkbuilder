package com.flapchat.app.data.repository;

import com.flapchat.app.data.remote.ApiClient;
import com.flapchat.app.data.remote.PresenceService;
import com.flapchat.app.data.remote.ChatService;
import com.flapchat.app.model.User;

import retrofit2.Call;

public class UserRepository {

    private final ChatService chatService;
    private final PresenceService presenceService;

    public UserRepository() {
        this.chatService = ApiClient.getService(ChatService.class);
        this.presenceService = ApiClient.getService(PresenceService.class);
    }

    /** REMOTE — fetch user profile */
    public Call<User> getUserProfile(String userId) {
        return chatService.getUser(userId);
    }

    /** REMOTE — update profile */
    public Call<User> updateUserProfile(User user) {
        return chatService.updateUser(user);
    }

    /** REMOTE — presence online/offline */
    public Call<Void> setUserOnline(String userId) {
        return presenceService.setOnline(userId);
    }

    public Call<Void> setUserOffline(String userId) {
        return presenceService.setOffline(userId);
    }
}