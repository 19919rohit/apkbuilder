package com.flapchat.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;
import androidx.room.OnConflictStrategy;
import com.flapchat.app.data.local.entities.MessageEntity;

import java.util.List;

@Dao
public interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertMessage(MessageEntity message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessages(List<MessageEntity> messages);

    @Update
    void updateMessage(MessageEntity message);

    @Delete
    void deleteMessage(MessageEntity message);

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    void deleteMessageById(String messageId);

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    List<MessageEntity> getMessagesForChat(String chatId);

    @Query("SELECT * FROM messages WHERE localId = :localId LIMIT 1")
    MessageEntity getMessageByLocalId(long localId);

    @Query("SELECT * FROM messages WHERE messageId = :messageId LIMIT 1")
    MessageEntity getMessageByMessageId(String messageId);

    @Query("UPDATE messages SET status = :status WHERE messageId = :messageId")
    void updateMessageStatus(String messageId, String status);

    @Query("UPDATE messages SET reactionsJson = :json WHERE messageId = :messageId")
    void updateMessageReactions(String messageId, String json);

    @Query("UPDATE messages SET edited = 1, content = :newText WHERE messageId = :messageId")
    void editMessage(String messageId, String newText);
}