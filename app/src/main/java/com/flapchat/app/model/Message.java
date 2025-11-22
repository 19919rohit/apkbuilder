package com.flapchat.app.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.flapchat.app.model.Enums.MessageStatus;
import com.flapchat.app.model.Enums.MessageType;

import java.util.List;

public class Message implements Parcelable {

    private String id;
    private String chatId;
    private String senderId;
    private String content;
    private long timestamp;
    private MessageType type;
    private MessageStatus status;

    private List<Reaction> reactions;   // multiple reactions
    private String replyTo;             // messageId being replied to
    private String attachmentUrl;       // for images/videos/files
    private String thumbnailUrl;        // for video thumbnails

    public Message() {}

    protected Message(Parcel in) {
        id = in.readString();
        chatId = in.readString();
        senderId = in.readString();
        content = in.readString();
        timestamp = in.readLong();
        type = MessageType.valueOf(in.readString());
        status = MessageStatus.valueOf(in.readString());
        replyTo = in.readString();
        attachmentUrl = in.readString();
        thumbnailUrl = in.readString();
    }

    public static final Creator<Message> CREATOR = new Creator<Message>() {
        @Override
        public Message createFromParcel(Parcel in) { return new Message(in); }

        @Override
        public Message[] newArray(int size) { return new Message[size]; }
    };

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(chatId);
        dest.writeString(senderId);
        dest.writeString(content);
        dest.writeLong(timestamp);
        dest.writeString(type.name());
        dest.writeString(status.name());
        dest.writeString(replyTo);
        dest.writeString(attachmentUrl);
        dest.writeString(thumbnailUrl);
    }

    // getters & setters
    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getChatId() { return chatId; }

    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getSenderId() { return senderId; }

    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public long getTimestamp() { return timestamp; }

    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public MessageType getType() { return type; }

    public void setType(MessageType type) { this.type = type; }

    public MessageStatus getStatus() { return status; }

    public void setStatus(MessageStatus status) { this.status = status; }

    public List<Reaction> getReactions() { return reactions; }

    public void setReactions(List<Reaction> reactions) { this.reactions = reactions; }

    public String getReplyTo() { return replyTo; }

    public void setReplyTo(String replyTo) { this.replyTo = replyTo; }

    public String getAttachmentUrl() { return attachmentUrl; }

    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public String getThumbnailUrl() { return thumbnailUrl; }

    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
}