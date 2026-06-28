package com.example.chaticalmusic.model;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class ChatMessage {
    private String sender_uid;
    private String sender_name;
    private String message_text;
    private long timestamp;

    public ChatMessage() {
    }

    public ChatMessage(String sender_uid, String sender_name, String message_text, long timestamp) {
        this.sender_uid = sender_uid;
        this.sender_name = sender_name;
        this.message_text = message_text;
        this.timestamp = timestamp;
    }

    public String getSender_uid() {
        return sender_uid;
    }

    public void setSender_uid(String sender_uid) {
        this.sender_uid = sender_uid;
    }

    public String getSender_name() {
        return sender_name;
    }

    public void setSender_name(String sender_name) {
        this.sender_name = sender_name;
    }

    public String getMessage_text() {
        return message_text;
    }

    public void setMessage_text(String message_text) {
        this.message_text = message_text;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
