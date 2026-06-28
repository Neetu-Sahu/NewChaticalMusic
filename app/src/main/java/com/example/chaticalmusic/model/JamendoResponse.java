package com.example.chaticalmusic.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class JamendoResponse {

    @SerializedName("results")
    private List<JamendoTrack> results;

    public List<JamendoTrack> getResults() {
        return results;
    }

    public void setResults(List<JamendoTrack> results) {
        this.results = results;
    }
}
