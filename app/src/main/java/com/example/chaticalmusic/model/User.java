package com.example.chaticalmusic.model;

public class User {
    private String uid;
    private String username;
    private String display_name;
    private String photo_url;
    private String bio;
    private int followers_count;
    private int following_count;

    public User() {}

    public User(String uid, String username, String display_name, String photo_url) {
        this.uid = uid;
        this.username = username;
        this.display_name = display_name;
        this.photo_url = photo_url;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getDisplay_name() { return display_name; }
    public void setDisplay_name(String display_name) { this.display_name = display_name; }

    public String getPhoto_url() { return photo_url; }
    public void setPhoto_url(String photo_url) { this.photo_url = photo_url; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public int getFollowers_count() { return followers_count; }
    public void setFollowers_count(int followers_count) { this.followers_count = followers_count; }

    public int getFollowing_count() { return following_count; }
    public void setFollowing_count(int following_count) { this.following_count = following_count; }
}