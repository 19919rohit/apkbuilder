package com.flapchat.app.data.repository;

import androidx.lifecycle.LiveData;

import com.flapchat.app.data.local.dao.MessageDao;
import com.flapchat.app.data.local.entities.MessageEntity;
import com.flapchat.app.data.remote.ApiClient;
import com.flapchat.app.data.remote.ChatService;
import com.flapchat.app.model.Message;

import java.util.List;

import retrofit2.Call;

public class MessageRepository {

    private final MessageDao messageDao;
    private final ChatService chatService;

    public MessageRepository(MessageDao messageDao) {
        this.messageDao = messageDao;
        this.chatService = ApiClient.getService(ChatService.class);
    }

    /** LOCAL — observe messages */
    public LiveData<List<MessageEntity>> getMessagesLive(String chatId) {
        return messageDao.getMessages(chatId);
    }

    /** LOCAL — insert new message */
    public void insertMessage(MessageEntity msg) {
        AppExecutors.database().execute(() -> messageDao.insertMessage(msg));
    }

    /** LOCAL — update status */
    public void updateStatus(String msgId, String status) {
        AppExecutors.database().execute(() -> messageDao.updateStatus(msgId, status));
    }

    /** REMOTE — send message */
    public Call<Message> sendMessageRemote(Message msg) {
        return chatService.sendMessage(msg);
    }

    /** REMOTE — sync messages */
    public Call<List<Message>> fetchMessagesRemote(String chatId) {
        return chatService.getMessages(chatId);
    }
}