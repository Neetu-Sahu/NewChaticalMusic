package com.example.chaticalmusic.model;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Room {
    private String room_name;
    private String host_id;
    private boolean is_private;
    private long created_at;
    private boolean needs_new_host;
    private long host_disconnected_at;
    private String room_code;
    private java.util.Map<String, Boolean> co_djs;

    // Required default constructor for Firebase
    public Room() {
    }

    public Room(String room_name, String host_id, boolean is_private, long created_at, boolean needs_new_host) {
        this.room_name = room_name;
        this.host_id = host_id;
        this.is_private = is_private;
        this.created_at = created_at;
        this.needs_new_host = needs_new_host;
    }

    public Room(String room_name, String host_id, boolean is_private, long created_at, boolean needs_new_host, String room_code) {
        this.room_name = room_name;
        this.host_id = host_id;
        this.is_private = is_private;
        this.created_at = created_at;
        this.needs_new_host = needs_new_host;
        this.room_code = room_code;
    }

    public String getRoom_name() {
        return room_name;
    }

    public void setRoom_name(String room_name) {
        this.room_name = room_name;
    }

    public String getHost_id() {
        return host_id;
    }

    public void setHost_id(String host_id) {
        this.host_id = host_id;
    }

    public boolean isIs_private() {
        return is_private;
    }

    public void setIs_private(boolean is_private) {
        this.is_private = is_private;
    }

    public long getCreated_at() {
        return created_at;
    }

    public void setCreated_at(long created_at) {
        this.created_at = created_at;
    }

    public boolean isNeeds_new_host() {
        return needs_new_host;
    }

    public void setNeeds_new_host(boolean needs_new_host) {
        this.needs_new_host = needs_new_host;
    }

    public long getHost_disconnected_at() {
        return host_disconnected_at;
    }

    public void setHost_disconnected_at(long host_disconnected_at) {
        this.host_disconnected_at = host_disconnected_at;
    }

    public String getRoom_code() {
        return room_code;
    }

    public void setRoom_code(String room_code) {
        this.room_code = room_code;
    }

    public java.util.Map<String, Boolean> getCo_djs() {
        return co_djs;
    }

    public void setCo_djs(java.util.Map<String, Boolean> co_djs) {
        this.co_djs = co_djs;
    }
}
