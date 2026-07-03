package com.example.chaticalmusic.model;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Conversation {
    private String chat_id;
    private String target_uid;
    private String last_message;
    private long last_timestamp;
    private int unread_count;
    private String target_name;
    private String target_photo;
    private long muted_until;

    public Conversation() {
    }

    public String getChat_id() {
        return chat_id;
    }

    public void setChat_id(String chat_id) {
        this.chat_id = chat_id;
    }

    public String getTarget_uid() {
        return target_uid;
    }

    public void setTarget_uid(String target_uid) {
        this.target_uid = target_uid;
    }

    public String getLast_message() {
        return last_message;
    }

    public void setLast_message(String last_message) {
        this.last_message = last_message;
    }

    public long getLast_timestamp() {
        return last_timestamp;
    }

    public void setLast_timestamp(long last_timestamp) {
        this.last_timestamp = last_timestamp;
    }

    public int getUnread_count() {
        return unread_count;
    }

    public void setUnread_count(int unread_count) {
        this.unread_count = unread_count;
    }

    public String getTarget_name() {
        return target_name;
    }

    public void setTarget_name(String target_name) {
        this.target_name = target_name;
    }

    public String getTarget_photo() {
        return target_photo;
    }

    public void setTarget_photo(String target_photo) {
        this.target_photo = target_photo;
    }

    public long getMuted_until() {
        return muted_until;
    }

    public void setMuted_until(long muted_until) {
        this.muted_until = muted_until;
    }
}
