package com.flapchat.app.utils;

import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

import com.flapchat.app.model.Enums.MessageStatus;
import com.flapchat.app.model.Enums.MessageType;
import com.flapchat.app.model.Reaction;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Converters {

    private static final Gson gson = new Gson();

    // -----------------------------
    // Date <-> Long
    // -----------------------------
    @TypeConverter
    public static Long fromDate(Date date) {
        return date == null ? null : date.getTime();
    }

    @TypeConverter
    public static Date toDate(Long value) {
        return value == null ? null : new Date(value);
    }

    // -----------------------------
    // MessageType Enum
    // -----------------------------
    @TypeConverter
    public static String fromMessageType(MessageType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    public static MessageType toMessageType(String value) {
        return value == null ? null : MessageType.valueOf(value);
    }

    // -----------------------------
    // MessageStatus Enum
    // -----------------------------
    @TypeConverter
    public static String fromMessageStatus(MessageStatus status) {
        return status == null ? null : status.name();
    }

    @TypeConverter
    public static MessageStatus toMessageStatus(String value) {
        return value == null ? null : MessageStatus.valueOf(value);
    }

    // -----------------------------
    // Reactions List<Reaction>
    // -----------------------------
    @TypeConverter
    public static String fromReactions(List<Reaction> reactions) {
        return reactions == null ? null : gson.toJson(reactions);
    }

    @TypeConverter
    public static List<Reaction> toReactions(String json) {
        if (json == null) return null;
        Type type = new TypeToken<List<Reaction>>() {}.getType();
        return gson.fromJson(json, type);
    }

    // -----------------------------
    // List<String> Attachments
    // -----------------------------
    @TypeConverter
    public static String fromStringList(List<String> list) {
        return list == null ? null : gson.toJson(list);
    }

    @TypeConverter
    public static List<String> toStringList(String json) {
        if (json == null) return null;
        Type type = new TypeToken<List<String>>() {}.getType();
        return gson.fromJson(json, type);
    }

    // -----------------------------
    // Metadata Map<String, String>
    // -----------------------------
    @TypeConverter
    public static String fromMetadata(Map<String, String> map) {
        return map == null ? null : gson.toJson(map);
    }

    @TypeConverter
    public static Map<String, String> toMetadata(String json) {
        if (json == null) return null;
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        return gson.fromJson(json, type);
    }

    // -----------------------------
    // Reaction (single emoji reaction)
    // -----------------------------
    @TypeConverter
    public static String fromReaction(Reaction reaction) {
        return reaction == null ? null : gson.toJson(reaction);
    }

    @TypeConverter
    public static Reaction toReaction(String json) {
        if (json == null) return null;
        return gson.fromJson(json, Reaction.class);
    }
}