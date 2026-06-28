package com.example.chaticalmusic.model;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class PlaybackState {
    private boolean is_playing;
    private long current_position_ms;
    private long last_updated_system_time;
    private String current_track_url;
    private String current_track_title;
    private String current_track_artist;
    private String current_track_art_url;

    // Required default constructor for Firebase
    public PlaybackState() {
    }

    public PlaybackState(boolean is_playing, long current_position_ms, long last_updated_system_time, String current_track_url, String current_track_title, String current_track_artist, String current_track_art_url) {
        this.is_playing = is_playing;
        this.current_position_ms = current_position_ms;
        this.last_updated_system_time = last_updated_system_time;
        this.current_track_url = current_track_url;
        this.current_track_title = current_track_title;
        this.current_track_artist = current_track_artist;
        this.current_track_art_url = current_track_art_url;
    }

    public boolean isIs_playing() {
        return is_playing;
    }

    public void setIs_playing(boolean is_playing) {
        this.is_playing = is_playing;
    }

    public long getCurrent_position_ms() {
        return current_position_ms;
    }

    public void setCurrent_position_ms(long current_position_ms) {
        this.current_position_ms = current_position_ms;
    }

    public long getLast_updated_system_time() {
        return last_updated_system_time;
    }

    public void setLast_updated_system_time(long last_updated_system_time) {
        this.last_updated_system_time = last_updated_system_time;
    }

    public String getCurrent_track_url() {
        return current_track_url;
    }

    public void setCurrent_track_url(String current_track_url) {
        this.current_track_url = current_track_url;
    }

    public String getCurrent_track_title() {
        return current_track_title;
    }

    public void setCurrent_track_title(String current_track_title) {
        this.current_track_title = current_track_title;
    }

    public String getCurrent_track_artist() {
        return current_track_artist;
    }

    public void setCurrent_track_artist(String current_track_artist) {
        this.current_track_artist = current_track_artist;
    }

    public String getCurrent_track_art_url() {
        return current_track_art_url;
    }

    public void setCurrent_track_art_url(String current_track_art_url) {
        this.current_track_art_url = current_track_art_url;
    }
}
