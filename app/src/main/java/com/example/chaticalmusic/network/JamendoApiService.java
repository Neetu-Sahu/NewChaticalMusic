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
            @Query("namesearch") String namesearch,
            @Query("tags") String tags,
            @Query("fuzzytags") String fuzzytags,
            @Query("boost") String boost,
            @Query("order") String order,
            @Query("audioformat") String audioformat,
            @Query("include") String include,
            @Query("imagesize") String imagesize,
            @Query("type") String type
    );

    @GET("tracks/")
    Call<JamendoResponse> getPopularTracks(
            @Query("client_id") String clientId,
            @Query("format") String format,
            @Query("limit") int limit,
            @Query("audioformat") String audioformat,
            @Query("boost") String boost,
            @Query("imagesize") String imagesize
    );
}
