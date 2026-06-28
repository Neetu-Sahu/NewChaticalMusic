package com.example.chaticalmusic.model;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class ChatMessage {
    private String sender_uid;
    private String sender_name;
    private String sender_photo_url;
    private String message_text;
    private long timestamp;
    private boolean is_reply;
    private String replied_to_text;
    private String replied_to_name;

    public ChatMessage() {
    }

    public ChatMessage(String sender_uid, String sender_name, String sender_photo_url, String message_text, long timestamp) {
        this(sender_uid, sender_name, sender_photo_url, message_text, timestamp, false, null, null);
    }

    public ChatMessage(String sender_uid, String sender_name, String sender_photo_url, String message_text, long timestamp,
                       boolean is_reply, String replied_to_text, String replied_to_name) {
        this.sender_uid = sender_uid;
        this.sender_name = sender_name;
        this.sender_photo_url = sender_photo_url;
        this.message_text = message_text;
        this.timestamp = timestamp;
        this.is_reply = is_reply;
        this.replied_to_text = replied_to_text;
        this.replied_to_name = replied_to_name;
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

    public String getSender_photo_url() {
        return sender_photo_url;
    }

    public void setSender_photo_url(String sender_photo_url) {
        this.sender_photo_url = sender_photo_url;
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

    public boolean isIs_reply() {
        return is_reply;
    }

    public void setIs_reply(boolean is_reply) {
        this.is_reply = is_reply;
    }

    public String getReplied_to_text() {
        return replied_to_text;
    }

    public void setReplied_to_text(String replied_to_text) {
        this.replied_to_text = replied_to_text;
    }

    public String getReplied_to_name() {
        return replied_to_name;
    }

    public void setReplied_to_name(String replied_to_name) {
        this.replied_to_name = replied_to_name;
    }
}
