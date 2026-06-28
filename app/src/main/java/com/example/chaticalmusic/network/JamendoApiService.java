package com.example.chaticalmusic.network;

import com.example.chaticalmusic.model.JamendoResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface JamendoApiService {

    @GET("tracks/")
    Call<JamendoResponse> searchTracks(
            @Query("client_id") String clientId,
            @Query("format") String format,
            @Query("limit") int limit,
            @Query("search") String search,
            @Query("audioformat") String audioformat,
            @Query("include") String include,
            @Query("imagesize") String imagesize
    );
}
