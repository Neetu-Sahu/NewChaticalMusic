package com.example.chaticalmusic.model;

import com.google.gson.annotations.SerializedName;

public class JamendoTrack {

    @SerializedName("id")
    private String jamendoId;

    @SerializedName("name")
    private String trackTitle;

    @SerializedName("artist_name")
    private String trackArtist;

    @SerializedName("audio")
    private String streamUrl;

    @SerializedName("image")
    private String albumArtUrl;

    // Getters and Setters
    public String getJamendoId() {
        return jamendoId;
    }

    public void setJamendoId(String jamendoId) {
        this.jamendoId = jamendoId;
    }

    public String getTrackTitle() {
        return trackTitle;
    }

    public void setTrackTitle(String trackTitle) {
        this.trackTitle = trackTitle;
    }

    public String getTrackArtist() {
        return trackArtist;
    }

    public void setTrackArtist(String trackArtist) {
        this.trackArtist = trackArtist;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    public String getAlbumArtUrl() {
        return albumArtUrl;
    }

    public void setAlbumArtUrl(String albumArtUrl) {
        this.albumArtUrl = albumArtUrl;
    }
}
