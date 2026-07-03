package com.example.chaticalmusic;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chaticalmusic.adapter.RecentTrackAdapter;
import com.example.chaticalmusic.adapter.SearchResultsAdapter;
import com.example.chaticalmusic.model.JamendoResponse;
import com.example.chaticalmusic.model.JamendoTrack;
import com.example.chaticalmusic.network.JamendoApiService;
import com.example.chaticalmusic.network.RetrofitClient;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchActivity extends AppCompatActivity implements SearchResultsAdapter.Listener {

    private EditText mSearchInput;
    private TextInputLayout mSearchInputLayout;
    private RecyclerView mRecyclerView;
    private SearchResultsAdapter mAdapter;
    private ProgressBar mProgressBar;

    private boolean isProgrammaticChange = false;

    private enum SearchState {
        PRE_SEARCH, LOADING, RESULTS, EMPTY, ERROR
    }

    private View mMiniPlayerBar;
    private ImageView mMiniArt;
    private TextView mMiniTitle;
    private TextView mMiniArtist;
    private ImageButton mMiniPlayPause;
    private ImageButton mMiniSkip;

    private MediaController mMediaController;
    private ListenableFuture<MediaController> mControllerFuture;
    private LoveAnimationHelper mLoveAnimationHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_search);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.search_toolbar), (v, insets) -> {
            androidx.core.graphics.Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
            lp.topMargin = statusBarInsets.top;
            v.setLayoutParams(lp);
            return insets;
        });

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.search_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mSearchInput = findViewById(R.id.search_input);
        mSearchInputLayout = findViewById(R.id.search_bar_layout);
        mRecyclerView = findViewById(R.id.search_results_recycler);
        mProgressBar = findViewById(R.id.search_progress_bar);

        mLoveAnimationHelper = new LoveAnimationHelper(findViewById(R.id.love_animation_container));

        mMiniPlayerBar = findViewById(R.id.mini_player_bar);
        mMiniArt = findViewById(R.id.mini_art);
        mMiniTitle = findViewById(R.id.mini_title);
        mMiniArtist = findViewById(R.id.mini_artist);
        mMiniPlayPause = findViewById(R.id.mini_play_pause);
        mMiniSkip = findViewById(R.id.mini_skip);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new SearchResultsAdapter(this);
        mRecyclerView.setAdapter(mAdapter);

        ChipGroup moodChipGroup = findViewById(R.id.mood_chip_group);
        if (moodChipGroup != null) {
            moodChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (!checkedIds.isEmpty()) {
                    int checkedId = checkedIds.get(0);
                    com.google.android.material.chip.Chip chip = findViewById(checkedId);
                    if (chip != null && chip.isChecked()) {
                        String label = chip.getText().toString();
                        mAdapter.setTracks(new ArrayList<>()); 
                        isProgrammaticChange = true;
                        mSearchInput.setText(label);
                        isProgrammaticChange = false;
                        performMusicSearch(label, true); 
                    }
                }
            });
        }

        mSearchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (!isProgrammaticChange && moodChipGroup != null) moodChipGroup.clearCheck();
                if (s.toString().trim().isEmpty()) updateSearchUI(SearchState.PRE_SEARCH, "");
            }
        });

        mSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                String query = mSearchInput.getText().toString().trim();
                if (!query.isEmpty()) {
                    if (moodChipGroup != null) moodChipGroup.clearCheck();
                    performMusicSearch(query, false);
                }
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        Button retryBtn = findViewById(R.id.retry_search_btn);
        if (retryBtn != null) retryBtn.setOnClickListener(view -> {
            String query = mSearchInput.getText().toString().trim();
            if (!query.isEmpty()) performMusicSearch(query, false);
        });

        mMiniPlayPause.setOnClickListener(v -> {
            if (mMediaController != null) {
                if (mMediaController.isPlaying()) mMediaController.pause();
                else mMediaController.play();
            }
        });

        mMiniSkip.setOnClickListener(v -> {
            if (mMediaController != null) mMediaController.seekToNextMediaItem();
        });

        mMiniPlayerBar.setOnClickListener(v -> {
            SharedPreferences sp = getSharedPreferences("chatical_prefs", MODE_PRIVATE);
            String currentRoomId = sp.getString("current_room_id", null);
            String intentRoomId = getIntent().getStringExtra("ROOM_ID");
            String roomId = (intentRoomId != null && !intentRoomId.isEmpty()) ? intentRoomId : currentRoomId;
            if (roomId != null && !roomId.isEmpty()) {
                android.content.Intent roomIntent = new android.content.Intent(this, RoomActivity.class);
                roomIntent.putExtra("ROOM_ID", roomId);
                startActivity(roomIntent);
            }
        });

        // Auto load music
        updateSearchUI(SearchState.PRE_SEARCH, "");
        performMusicSearch("", false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.start();
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, com.example.chaticalmusic.service.MusicPlaybackService.class));
        mControllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        mControllerFuture.addListener(() -> {
            try {
                mMediaController = mControllerFuture.get();
                mMediaController.addListener(new Player.Listener() {
                    @Override public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) { updateMiniPlayer(); }
                    @Override public void onIsPlayingChanged(boolean isPlaying) { updatePlayPauseButton(isPlaying); }
                    @Override public void onPlaybackStateChanged(int playbackState) { updateMiniPlayer(); }
                });
                updateMiniPlayer();
            } catch (ExecutionException | InterruptedException e) { e.printStackTrace(); }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onStop() {
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.stop();
        if (mMediaController != null) { mMediaController.release(); mMediaController = null; }
        if (mControllerFuture != null) MediaController.releaseFuture(mControllerFuture);
        super.onStop();
    }

    private void performMusicSearch(String query, boolean isTagSearch) {
        updateSearchUI(SearchState.LOADING, query);
        String clientId = BuildConfig.JAMENDO_CLIENT_ID;
        if (clientId == null || clientId.trim().isEmpty()) clientId = "ca9c0b20"; 

        JamendoApiService apiService = RetrofitClient.getClient().create(JamendoApiService.class);
        boolean isAutoLoad = (query == null || query.trim().isEmpty());
        
        Call<JamendoResponse> call;
        if (isAutoLoad) {
            call = apiService.getPopularTracks(clientId, "json", 50, "mp31", "popularity_month", "300");
        } else {
            String namesearch = isTagSearch ? null : query.toLowerCase();
            String fuzzyTagsParam = isTagSearch ? query.toLowerCase() : null;
            call = apiService.searchTracks(clientId, "json", 50, namesearch, null, fuzzyTagsParam, "popularity_week", "popularity_week", "mp31", "musicinfo", "300", "single");
        }

        call.enqueue(new Callback<JamendoResponse>() {
            @Override
            public void onResponse(Call<JamendoResponse> call, Response<JamendoResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<JamendoTrack> results = response.body().getResults();
                    if (results == null || results.isEmpty()) updateSearchUI(SearchState.EMPTY, query);
                    else { updateSearchUI(SearchState.RESULTS, query); mAdapter.setTracks(results); }
                } else { updateSearchUI(SearchState.ERROR, query); }
            }
            @Override public void onFailure(Call<JamendoResponse> call, Throwable t) { updateSearchUI(SearchState.ERROR, query); }
        });
    }

    private void updateSearchUI(SearchState state, String query) {
        View preSearchLayout = findViewById(R.id.pre_search_layout);
        View musicResultsRecycler = findViewById(R.id.search_results_recycler);
        View progressBar = findViewById(R.id.search_progress_bar);
        View emptyText = findViewById(R.id.search_empty_text);
        View errorLayout = findViewById(R.id.search_error_layout);

        if (preSearchLayout != null) preSearchLayout.setVisibility(View.GONE);
        if (musicResultsRecycler != null) musicResultsRecycler.setVisibility(View.GONE);
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (emptyText != null) emptyText.setVisibility(View.GONE);
        if (errorLayout != null) errorLayout.setVisibility(View.GONE);

        switch (state) {
            case PRE_SEARCH:
                if (preSearchLayout != null) { preSearchLayout.setVisibility(View.VISIBLE); loadRecentTracks(); }
                break;
            case LOADING:
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                break;
            case RESULTS:
                if (musicResultsRecycler != null) musicResultsRecycler.setVisibility(View.VISIBLE);
                break;
            case EMPTY:
                if (emptyText != null) { emptyText.setVisibility(View.VISIBLE); ((TextView) emptyText).setText("No results for \"" + query + "\""); }
                break;
            case ERROR:
                if (errorLayout != null) errorLayout.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void loadRecentTracks() {
        RecentTracksPrefs prefs = new RecentTracksPrefs(this);
        List<JamendoTrack> recentTracks = prefs.getRecentTracks();
        RecyclerView recentRecycler = findViewById(R.id.recent_recycler);
        TextView emptyText = findViewById(R.id.recent_empty_text);
        TextView recentHeader = findViewById(R.id.recent_header);
        if (recentTracks.isEmpty()) {
            if (recentRecycler != null) recentRecycler.setVisibility(View.GONE);
            if (recentHeader != null) recentHeader.setVisibility(View.GONE);
            if (emptyText != null) emptyText.setVisibility(View.VISIBLE);
        } else {
            if (emptyText != null) emptyText.setVisibility(View.GONE);
            if (recentHeader != null) recentHeader.setVisibility(View.VISIBLE);
            if (recentRecycler != null) {
                recentRecycler.setVisibility(View.VISIBLE);
                recentRecycler.setLayoutManager(new LinearLayoutManager(this));
                RecentTrackAdapter recentAdapter = new RecentTrackAdapter(this::playTrack);
                recentRecycler.setAdapter(recentAdapter);
                recentAdapter.setTracks(recentTracks);
            }
        }
    }

    private void playTrack(JamendoTrack track) {
        if (mMediaController == null) { Toast.makeText(this, "Playback system not ready", Toast.LENGTH_SHORT).show(); return; }
        List<JamendoTrack> tracks = mAdapter.getTracks();
        if (tracks.isEmpty()) tracks = new RecentTracksPrefs(this).getRecentTracks();
        int startIndex = 0;
        List<MediaItem> mediaItems = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            JamendoTrack t = tracks.get(i);
            String mediaId = t.getJamendoId();
            if (mediaId.equals(track.getJamendoId())) startIndex = i;
            mediaItems.add(new MediaItem.Builder().setMediaId(mediaId).setUri(android.net.Uri.parse(t.getStreamUrl())).setMediaMetadata(new MediaMetadata.Builder().setTitle(t.getTrackTitle()).setArtist(t.getTrackArtist()).setArtworkUri(android.net.Uri.parse(t.getAlbumArtUrl())).build()).build());
        }
        mMediaController.setMediaItems(mediaItems, startIndex, 0);
        mMediaController.prepare();
        mMediaController.play();
        new RecentTracksPrefs(this).addTrack(track);
    }

    private void updateMiniPlayer() {
        if (mMediaController == null || mMediaController.getCurrentMediaItem() == null) { mMiniPlayerBar.setVisibility(View.GONE); return; }
        mMiniPlayerBar.setVisibility(View.VISIBLE);
        MediaMetadata meta = mMediaController.getMediaMetadata();
        mMiniTitle.setText(meta.title != null ? meta.title.toString() : "Unknown");
        mMiniArtist.setText(meta.artist != null ? meta.artist.toString() : "Unknown");
        Glide.with(this).load(meta.artworkUri).placeholder(R.drawable.ic_music_placeholder).into(mMiniArt);
        updatePlayPauseButton(mMediaController.isPlaying());
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        mMiniPlayPause.setImageResource(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) { if (item.getItemId() == android.R.id.home) { finish(); return true; } return super.onOptionsItemSelected(item); }
    @Override public void onTrackPlay(JamendoTrack track) { playTrack(track); }
    @Override public void onAddToQueue(JamendoTrack track) {
        String roomId = getIntent().getStringExtra("ROOM_ID");
        if (roomId == null) return;
        DatabaseReference queueRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS).child(roomId).child(FirebasePaths.QUEUE);
        String key = queueRef.push().getKey();
        com.example.chaticalmusic.model.QueueTrack qt = new com.example.chaticalmusic.model.QueueTrack(
            track.getTrackTitle(), 
            track.getTrackArtist(), 
            track.getStreamUrl(), 
            track.getAlbumArtUrl(), 
            com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid(), 
            System.currentTimeMillis()
        );
        if (key != null) {
            qt.setTrack_key(track.getJamendoId()); // Store ID in key field
            queueRef.child(key).setValue(qt).addOnCompleteListener(task -> {
                if (task.isSuccessful()) { Toast.makeText(SearchActivity.this, "Added to room queue!", Toast.LENGTH_SHORT).show(); finish(); }
            });
        }
    }
}