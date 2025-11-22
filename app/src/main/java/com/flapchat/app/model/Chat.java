package com.flapchat.app.model;

import android.os.Parcel;
import android.os.Parcelable;

public class Chat implements Parcelable {

    private String id;
    private String userId;           // chat with
    private String lastMessage;
    private long lastTimestamp;

    private int unreadCount;
    private String wallpaper;        // custom bg per chat

    public Chat() {}

    protected Chat(Parcel in) {
        id = in.readString();
        userId = in.readString();
        lastMessage = in.readString();
        lastTimestamp = in.readLong();
        unreadCount = in.readInt();
        wallpaper = in.readString();
    }

    public static final Creator<Chat> CREATOR = new Creator<Chat>() {
        @Override
        public Chat createFromParcel(Parcel in) { return new Chat(in); }

        @Override
        public Chat[] newArray(int size) { return new Chat[size]; }
    };

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(userId);
        dest.writeString(lastMessage);
        dest.writeLong(lastTimestamp);
        dest.writeInt(unreadCount);
        dest.writeString(wallpaper);
    }

    // getters & setters
    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }

    public void setUserId(String userId) { this.userId = userId; }

    public String getLastMessage() { return lastMessage; }

    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public long getLastTimestamp() { return lastTimestamp; }

    public void setLastTimestamp(long lastTimestamp) { this.lastTimestamp = lastTimestamp; }

    public int getUnreadCount() { return unreadCount; }

    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    public String getWallpaper() { return wallpaper; }

    public void setWallpaper(String wallpaper) { this.wallpaper = wallpaper; }
}