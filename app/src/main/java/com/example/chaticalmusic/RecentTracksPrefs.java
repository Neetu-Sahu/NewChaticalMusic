package com.example.chaticalmusic;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.chaticalmusic.model.JamendoTrack;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class RecentTracksPrefs {
    private static final String PREF_NAME = "recent_tracks_prefs";
    private static final String KEY_RECENT_TRACKS = "recent_tracks";
    private final Context mContext;

    public RecentTracksPrefs(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public void addTrack(JamendoTrack track) {
        if (track == null) return;
        saveTrack(track.getTrackTitle(), track.getTrackArtist(), track.getStreamUrl(), track.getAlbumArtUrl());
    }

    public void saveTrack(String trackTitle, String artistName, String streamUrl, String albumArtUrl) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Gson gson = new Gson();
        
        List<JamendoTrack> list = getRecentTracks();
        
        // Remove duplicates by streamUrl
        for (int i = 0; i < list.size(); i++) {
            JamendoTrack t = list.get(i);
            if (t.getStreamUrl() != null && t.getStreamUrl().equals(streamUrl)) {
                list.remove(i);
                break;
            }
        }
        
        JamendoTrack track = new JamendoTrack();
        track.setTrackTitle(trackTitle);
        track.setTrackArtist(artistName);
        track.setStreamUrl(streamUrl);
        track.setAlbumArtUrl(albumArtUrl);
        
        list.add(0, track);
        
        while (list.size() > 5) {
            list.remove(list.size() - 1);
        }
        
        String json = gson.toJson(list);
        prefs.edit().putString(KEY_RECENT_TRACKS, json).apply();
    }

    public List<JamendoTrack> getRecentTracks() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_RECENT_TRACKS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        
        Gson gson = new Gson();
        Type type = new TypeToken<List<JamendoTrack>>(){}.getType();
        try {
            List<JamendoTrack> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
