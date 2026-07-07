package com.example.chaticalmusic.model;

import com.google.firebase.database.IgnoreExtraProperties;
import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class ChatMessage {
    public static final int STATUS_SENT = 0;
    public static final int STATUS_DELIVERED = 1;
    public static final int STATUS_READ = 2;

    private String message_id;
    private String sender_uid;
    private String sender_name;
    private String sender_photo_url;
    private String message_text;
    private long timestamp;
    private int status; // 0: sent, 1: delivered, 2: read
    private boolean is_reply;
    private String replied_to_text;
    private String replied_to_name;
    private String request_type; // "dj" or "codj"
    private String request_uid;
    private Map<String, String> reactions; // userId -> emoji
    private boolean is_deleted;
    private Map<String, Boolean> deleted_for; // userId -> true

    public ChatMessage() {
        this.reactions = new HashMap<>();
        this.deleted_for = new HashMap<>();
    }

    public ChatMessage(String sender_uid, String sender_name, String sender_photo_url, String message_text, long timestamp) {
        this(sender_uid, sender_name, sender_photo_url, message_text, timestamp, false, null, null, null, null);
    }

    public ChatMessage(String sender_uid, String sender_name, String sender_photo_url, String message_text, long timestamp,
                       boolean is_reply, String replied_to_text, String replied_to_name) {
        this(sender_uid, sender_name, sender_photo_url, message_text, timestamp, is_reply, replied_to_text, replied_to_name, null, null);
    }

    public ChatMessage(String sender_uid, String sender_name, String sender_photo_url, String message_text, long timestamp,
                       boolean is_reply, String replied_to_text, String replied_to_name, String request_type, String request_uid) {
        this.sender_uid = sender_uid;
        this.sender_name = sender_name;
        this.sender_photo_url = sender_photo_url;
        this.message_text = message_text;
        this.timestamp = timestamp;
        this.is_reply = is_reply;
        this.replied_to_text = replied_to_text;
        this.replied_to_name = replied_to_name;
        this.request_type = request_type;
        this.request_uid = request_uid;
        this.status = STATUS_SENT;
        this.reactions = new HashMap<>();
    }

    public String getMessage_id() {
        return message_id;
    }

    public void setMessage_id(String message_id) {
        this.message_id = message_id;
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

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
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

    public String getRequest_type() {
        return request_type;
    }

    public void setRequest_type(String request_type) {
        this.request_type = request_type;
    }

    public String getRequest_uid() {
        return request_uid;
    }

    public void setRequest_uid(String request_uid) {
        this.request_uid = request_uid;
    }

    public Map<String, String> getReactions() {
        return reactions;
    }

    public void setReactions(Map<String, String> reactions) {
        this.reactions = reactions;
    }

    public boolean isIs_deleted() {
        return is_deleted;
    }

    public void setIs_deleted(boolean is_deleted) {
        this.is_deleted = is_deleted;
    }

    public Map<String, Boolean> getDeleted_for() {
        return deleted_for;
    }

    public void setDeleted_for(Map<String, Boolean> deleted_for) {
        this.deleted_for = deleted_for;
    }
}
