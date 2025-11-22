package com.flapchat.app.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.room.Index;

@Entity(
    tableName = "chats",
    indices = {@Index("chatId"), @Index("userId")}
)
public class ChatEntity {

    @PrimaryKey(autoGenerate = true)
    public long localId;

    @ColumnInfo(name = "chatId")
    public String chatId; // UUID from Supabase

    @ColumnInfo(name = "userId")
    public String userId; // The other person’s ID

    @ColumnInfo(name = "lastMessage")
    public String lastMessage;

    @ColumnInfo(name = "lastMessageTimestamp")
    public long lastMessageTimestamp;

    @ColumnInfo(name = "unreadCount")
    public int unreadCount;

    @ColumnInfo(name = "isPinned")
    public boolean isPinned;

    @ColumnInfo(name = "wallpaperPath")
    public String wallpaperPath; // For per-chat wallpaper

    @ColumnInfo(name = "extraMeta")
    public String extraMeta;
}