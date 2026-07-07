package com.example.chaticalmusic.model;

public class NotificationModel {
    private String id;
    private String type; // follow_request, request_accepted, new_follower, follow_back
    private String sender_uid;
    private String sender_name;
    private String sender_photo;
    private long timestamp;
    private boolean read;

    public NotificationModel() {}

    public NotificationModel(String type, String sender_uid, String sender_name, String sender_photo) {
        this.type = type;
        this.sender_uid = sender_uid;
        this.sender_name = sender_name;
        this.sender_photo = sender_photo;
        this.timestamp = System.currentTimeMillis();
        this.read = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public String getSender_uid() { return sender_uid; }
    public String getSender_name() { return sender_name; }
    public String getSender_photo() { return sender_photo; }
    public long getTimestamp() { return timestamp; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}