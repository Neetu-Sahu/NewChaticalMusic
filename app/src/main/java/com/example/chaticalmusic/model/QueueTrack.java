package com.example.chaticalmusic.model;

import com.google.firebase.database.IgnoreExtraProperties;
import java.util.HashMap;
import java.util.Map;

@IgnoreExtraProperties
public class QueueTrack {
    private String track_key; // For database identification
    private String track_title;
    private String track_artist;
    private String stream_url;
    private String album_art_url;
    private String added_by;
    private long added_at;
    private int upvotes;
    private int downvotes;
    private Map<String, String> voted_users;

    public QueueTrack() {
        this.upvotes = 0;
        this.downvotes = 0;
        this.voted_users = new HashMap<>();
    }

    public QueueTrack(String track_title, String track_artist, String stream_url, String album_art_url, String added_by, long added_at) {
        this.track_title = track_title;
        this.track_artist = track_artist;
        this.stream_url = stream_url;
        this.album_art_url = album_art_url;
        this.added_by = added_by;
        this.added_at = added_at;
        this.upvotes = 0;
        this.downvotes = 0;
        this.voted_users = new HashMap<>();
    }

    public String getTrack_key() {
        return track_key;
    }

    public void setTrack_key(String track_key) {
        this.track_key = track_key;
    }

    public String getTrack_title() {
        return track_title;
    }

    public void setTrack_title(String track_title) {
        this.track_title = track_title;
    }

    public String getTrack_artist() {
        return track_artist;
    }

    public void setTrack_artist(String track_artist) {
        this.track_artist = track_artist;
    }

    public String getStream_url() {
        return stream_url;
    }

    public void setStream_url(String stream_url) {
        this.stream_url = stream_url;
    }

    public String getAlbum_art_url() {
        return album_art_url;
    }

    public void setAlbum_art_url(String album_art_url) {
        this.album_art_url = album_art_url;
    }

    public String getAdded_by() {
        return added_by;
    }

    public void setAdded_by(String added_by) {
        this.added_by = added_by;
    }

    public long getAdded_at() {
        return added_at;
    }

    public void setAdded_at(long added_at) {
        this.added_at = added_at;
    }

    public int getUpvotes() {
        return upvotes;
    }

    public void setUpvotes(int upvotes) {
        this.upvotes = upvotes;
    }

    public int getDownvotes() {
        return downvotes;
    }

    public void setDownvotes(int downvotes) {
        this.downvotes = downvotes;
    }

    public Map<String, String> getVoted_users() {
        return voted_users;
    }

    public void setVoted_users(Map<String, String> voted_users) {
        this.voted_users = voted_users;
    }
}
