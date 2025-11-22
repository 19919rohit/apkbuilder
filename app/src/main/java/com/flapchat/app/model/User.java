package com.flapchat.app.model;

import android.os.Parcel;
import android.os.Parcelable;

public class User implements Parcelable {

    private String id;
    private String name;
    private String avatarUrl;
    private boolean online;
    private long lastSeen;

    public User() {}

    protected User(Parcel in) {
        id = in.readString();
        name = in.readString();
        avatarUrl = in.readString();
        online = in.readByte() != 0;
        lastSeen = in.readLong();
    }

    public static final Creator<User> CREATOR = new Creator<User>() {
        @Override
        public User createFromParcel(Parcel in) { return new User(in); }

        @Override
        public User[] newArray(int size) { return new User[size]; }
    };

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(avatarUrl);
        dest.writeByte((byte) (online ? 1 : 0));
        dest.writeLong(lastSeen);
    }

    // getters & setters
    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getAvatarUrl() { return avatarUrl; }

    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public boolean isOnline() { return online; }

    public void setOnline(boolean online) { this.online = online; }

    public long getLastSeen() { return lastSeen; }

    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
}