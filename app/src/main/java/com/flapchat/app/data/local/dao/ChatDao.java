package com.flapchat.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Update;
import androidx.room.Query;
import androidx.room.OnConflictStrategy;

import com.flapchat.app.data.local.entities.ChatEntity;

import java.util.List;

@Dao
public interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertChat(ChatEntity chat);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChats(List<ChatEntity> chats);

    @Update
    void updateChat(ChatEntity chat);

    @Query("SELECT * FROM chats ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    List<ChatEntity> getAllChats();

    @Query("SELECT * FROM chats WHERE chatId = :chatId LIMIT 1")
    ChatEntity getChatById(String chatId);

    @Query("UPDATE chats SET lastMessage = :lastMessage, lastMessageTimestamp = :timestamp WHERE chatId = :chatId")
    void updateLastMessage(String chatId, String lastMessage, long timestamp);

    @Query("UPDATE chats SET unreadCount = unreadCount + 1 WHERE chatId = :chatId")
    void incrementUnread(String chatId);

    @Query("UPDATE chats SET unreadCount = 0 WHERE chatId = :chatId")
    void clearUnread(String chatId);

    @Query("UPDATE chats SET isPinned = :pin WHERE chatId = :chatId")
    void setPin(String chatId, boolean pin);
}