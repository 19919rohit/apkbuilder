package com.flapchat.app.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;

@Entity(
    tableName = "messages",
    indices = {
        @Index("chatId"),
        @Index("senderId"),
        @Index("timestamp")
    }
)
public class MessageEntity {

    @PrimaryKey(autoGenerate = true)
    public long localId;  // Local DB id

    @ColumnInfo(name = "messageId")
    public String messageId;  // Server ID (Supabase)

    @ColumnInfo(name = "chatId")
    public String chatId;

    @ColumnInfo(name = "senderId")
    public String senderId;

    @ColumnInfo(name = "content")
    public String content;  // Text OR Markdown

    @ColumnInfo(name = "type")
    public String type; // "text", "image", "video", “file”, “markdown”, “codeblock”

    @ColumnInfo(name = "fileUrl")
    public String fileUrl;  // For images/videos/files

    @ColumnInfo(name = "thumbnailUrl")
    public String thumbnailUrl; // For videos & images

    @ColumnInfo(name = "status")
    public String status;  // sending, sent, delivered, read

    @ColumnInfo(name = "timestamp")
    public long timestamp;

    @ColumnInfo(name = "isOutgoing")
    public boolean isOutgoing; // To style UI

    @ColumnInfo(name = "replyToMessageId")
    public String replyToMessageId;

    @ColumnInfo(name = "reactionsJson")
    public String reactionsJson; // {"👍":3, "❤️":1}

    @ColumnInfo(name = "edited")
    public boolean edited;

    @ColumnInfo(name = "localPath")
    public String localPath; // For cached media

    @ColumnInfo(name = "extraMeta")
    public String extraMeta; // Future extra fields
}