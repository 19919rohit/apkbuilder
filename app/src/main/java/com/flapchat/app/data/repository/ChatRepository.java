package com.flapchat.app.data.repository;

import androidx.lifecycle.LiveData;

import com.flapchat.app.data.local.dao.ChatDao;
import com.flapchat.app.data.local.entities.ChatEntity;
import com.flapchat.app.data.remote.ChatService;
import com.flapchat.app.data.remote.ApiClient;
import com.flapchat.app.model.Chat;

import java.util.List;

import retrofit2.Call;

public class ChatRepository {

    private final ChatDao chatDao;
    private final ChatService chatService;

    public ChatRepository(ChatDao chatDao) {
        this.chatDao = chatDao;
        this.chatService = ApiClient.getService(ChatService.class);
    }

    /** LOCAL — observe chats */
    public LiveData<List<ChatEntity>> getAllChatsLive() {
        return chatDao.getAllChats();
    }

    /** LOCAL — insert chat */
    public void insertChat(ChatEntity chat) {
        AppExecutors.database().execute(() -> chatDao.insertChat(chat));
    }

    /** REMOTE — fetch chats from server */
    public Call<List<Chat>> fetchChatsRemote(String userId) {
        return chatService.getUserChats(userId);
    }

    /** LOCAL — delete chat */
    public void deleteChat(String chatId) {
        AppExecutors.database().execute(() -> chatDao.deleteChat(chatId));
    }

}