package com.flapchat.app.model;

import com.flapchat.app.model.Enums.ReactionType;

public class Reaction {

    private String messageId;
    private String userId;
    private ReactionType type;

    public Reaction(String messageId, String userId, ReactionType type) {
        this.messageId = messageId;
        this.userId = userId;
        this.type = type;
    }

    public String getMessageId() { return messageId; }

    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getUserId() { return userId; }

    public void setUserId(String userId) { this.userId = userId; }

    public ReactionType getType() { return type; }

    public void setType(ReactionType type) { this.type = type; }
}