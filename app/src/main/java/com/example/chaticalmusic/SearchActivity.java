package com.example.chaticalmusic;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.chaticalmusic.adapter.SearchResultsAdapter;
import com.example.chaticalmusic.model.JamendoResponse;
import com.example.chaticalmusic.model.JamendoTrack;
import com.example.chaticalmusic.network.JamendoApiService;
import com.example.chaticalmusic.network.RetrofitClient;
import com.example.chaticalmusic.service.MusicPlaybackService;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.chip.Chip;
import com.example.chaticalmusic.adapter.RecentTrackAdapter;
import com.google.android.material.snackbar.Snackbar;

public class SearchActivity extends AppCompatActivity implements SearchResultsAdapter.Listener {

    private EditText mSearchInput;
    private RecyclerView mRecyclerView;
    private SearchResultsAdapter mAdapter;
    private android.widget.ProgressBar mProgressBar;

    private boolean isProgrammaticChange = false;
    private String mLastQuery = "";

    private enum SearchState {
        PRE_SEARCH,
        LOADING,
        RESULTS,
        EMPTY,
        ERROR
    }

    // Mini Player components
    private View mMiniPlayerBar;
    private ImageView mMiniArt;
    private TextView mMiniTitle;
    private TextView mMiniArtist;
    private ImageButton mMiniPlayPause;
    private ImageButton mMiniSkip;

    // Media Session components
    private MediaController mMediaController;
    private ListenableFuture<MediaController> mControllerFuture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_search);

        View searchRoot = findViewById(R.id.search_root);
        ViewCompat.setOnApplyWindowInsetsListener(searchRoot, (v, insets) -> {
            androidx.core.graphics.Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(v.getPaddingLeft(), statusBarInsets.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.search_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mSearchInput = findViewById(R.id.search_input);
        mRecyclerView = findViewById(R.id.search_results_recycler);
        mProgressBar = findViewById(R.id.search_progress_bar);

        // Initialize Mini Player components
        mMiniPlayerBar = findViewById(R.id.mini_player_bar);
        mMiniArt = findViewById(R.id.mini_art);
        mMiniTitle = findViewById(R.id.mini_title);
        mMiniArtist = findViewById(R.id.mini_artist);
        mMiniPlayPause = findViewById(R.id.mini_play_pause);
        mMiniSkip = findViewById(R.id.mini_skip);

        // Bottom insets for mini player bar (gesture nav)
        ViewCompat.setOnApplyWindowInsetsListener(mMiniPlayerBar, (v, insets) -> {
            androidx.core.graphics.Insets navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), navInsets.bottom + 8);
            return insets;
        });

        // Set up RecyclerView
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new SearchResultsAdapter(this);
        mRecyclerView.setAdapter(mAdapter);

        // Search action and Pre-search initialization
        ChipGroup moodChipGroup = findViewById(R.id.mood_chip_group);
        RecyclerView recentRecycler = findViewById(R.id.recent_recycler);
        if (recentRecycler != null) {
            recentRecycler.setLayoutManager(new LinearLayoutManager(this));
        }

        if (moodChipGroup != null) {
            moodChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (!checkedIds.isEmpty()) {
                    int checkedId = checkedIds.get(0);
                    com.google.android.material.chip.Chip chip = findViewById(checkedId);
                    if (chip != null && chip.isChecked()) {
                        String label = chip.getText().toString();
                        isProgrammaticChange = true;
                        mSearchInput.setText(label);
                        isProgrammaticChange = false;
                        performSearch();
                    }
                }
            });
        }

        mSearchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (!isProgrammaticChange) {
                    if (moodChipGroup != null) {
                        moodChipGroup.clearCheck();
                    }
                }
                if (s.toString().trim().isEmpty()) {
                    updateSearchUI(SearchState.PRE_SEARCH, "");
                }
            }
        });

        mSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                String query = mSearchInput.getText().toString().trim();
                if (!query.isEmpty()) {
                    if (moodChipGroup != null) {
                        moodChipGroup.clearCheck();
                    }
                    performSearch();
                }
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
                }
                return true;
            }
            return false;
        });

        Button retryBtn = findViewById(R.id.retry_search_btn);
        if (retryBtn != null) {
            retryBtn.setOnClickListener(view -> performSearch());
        }

        // Set initial UI state
        updateSearchUI(SearchState.PRE_SEARCH, "");

        // Mini player control actions
        mMiniPlayPause.setOnClickListener(v -> {
            if (mMediaController != null) {
                if (mMediaController.isPlaying()) {
                    mMediaController.pause();
                } else {
                    mMediaController.play();
                }
            }
        });

        mMiniSkip.setOnClickListener(v -> {
            if (mMediaController != null) {
                mMediaController.seekToNextMediaItem();
            }
        });

        // Whole bar tap: navigate to room or stub full player
        mMiniPlayerBar.setOnClickListener(v -> {
            SharedPreferences sp = getSharedPreferences("chatical_prefs", MODE_PRIVATE);
            String currentRoomId = sp.getString("current_room_id", null);
            String intentRoomId = getIntent().getStringExtra("ROOM_ID");
            String roomId = (intentRoomId != null && !intentRoomId.isEmpty()) ? intentRoomId : currentRoomId;
            if (roomId != null && !roomId.isEmpty()) {
                android.content.Intent roomIntent = new android.content.Intent(this, RoomActivity.class);
                roomIntent.putExtra("ROOM_ID", roomId);
                startActivity(roomIntent);
            } else {
                if (mMediaController != null && mMediaController.getMediaItemCount() > 0) {
                    FullPlayerBottomSheet sheet = FullPlayerBottomSheet.newInstance();
                    sheet.setMediaController(mMediaController);
                    sheet.show(getSupportFragmentManager(), "full_player");
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 1. Create ComponentName and SessionToken pointing to MusicPlaybackService
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicPlaybackService.class));
        
        // 2. Build the MediaController asynchronously
        mControllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        
        // 3. Register listener to receive the controller when ready
        mControllerFuture.addListener(() -> {
            try {
                mMediaController = mControllerFuture.get();
                mMediaController.addListener(new Player.Listener() {
                    @Override
                    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                        updateMiniPlayer();
                    }

                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        updatePlayPauseButton(isPlaying);
                    }

                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        updateMiniPlayer();
                    }
                });
                updateMiniPlayer();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
                Toast.makeText(SearchActivity.this, "Failed to connect playback controller", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onStop() {
        // Release MediaController if loaded
        if (mMediaController != null) {
            mMediaController.release();
            mMediaController = null;
        }
        if (mControllerFuture != null) {
            MediaController.releaseFuture(mControllerFuture);
        }
        super.onStop();
    }

    private void performSearch() {
        String query = mSearchInput.getText().toString().trim();
        mLastQuery = query;
        if (query.isEmpty()) {
            Toast.makeText(this, "Please enter a search query", Toast.LENGTH_SHORT).show();
            return;
        }

        updateSearchUI(SearchState.LOADING, query);

        JamendoApiService apiService = RetrofitClient.getClient().create(JamendoApiService.class);
        apiService.searchTracks(
                BuildConfig.JAMENDO_CLIENT_ID,
                "json",
                20,
                query,
                "mp31",
                "musicinfo",
                "300"
        ).enqueue(new Callback<JamendoResponse>() {
            @Override
            public void onResponse(Call<JamendoResponse> call, Response<JamendoResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    java.util.List<JamendoTrack> results = response.body().getResults();
                    if (results == null || results.isEmpty()) {
                        updateSearchUI(SearchState.EMPTY, query);
                    } else {
                        updateSearchUI(SearchState.RESULTS, query);
                        mAdapter.setTracks(results);
                    }
                } else {
                    updateSearchUI(SearchState.ERROR, query);
                }
            }

            @Override
            public void onFailure(Call<JamendoResponse> call, Throwable t) {
                updateSearchUI(SearchState.ERROR, query);
            }
        });
    }

    private void updateSearchUI(SearchState state, String query) {
        View preSearchLayout = findViewById(R.id.pre_search_layout);
        View resultsRecycler = findViewById(R.id.search_results_recycler);
        View progressBar = findViewById(R.id.search_progress_bar);
        View emptyText = findViewById(R.id.search_empty_text);
        View errorLayout = findViewById(R.id.search_error_layout);

        if (preSearchLayout != null) preSearchLayout.setVisibility(View.GONE);
        if (resultsRecycler != null) resultsRecycler.setVisibility(View.GONE);
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (emptyText != null) emptyText.setVisibility(View.GONE);
        if (errorLayout != null) errorLayout.setVisibility(View.GONE);

        switch (state) {
            case PRE_SEARCH:
                if (preSearchLayout != null) {
                    preSearchLayout.setVisibility(View.VISIBLE);
                    loadRecentTracks();
                }
                break;
            case LOADING:
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                break;
            case RESULTS:
                if (resultsRecycler != null) resultsRecycler.setVisibility(View.VISIBLE);
                break;
            case EMPTY:
                if (emptyText != null) {
                    TextView tv = (TextView) emptyText;
                    tv.setText("No results for \"" + query + "\"");
                    tv.setVisibility(View.VISIBLE);
                }
                break;
            case ERROR:
                if (errorLayout != null) {
                    TextView tv = errorLayout.findViewById(R.id.error_message_text);
                    if (tv != null) {
                        tv.setText("Connection error — tap to retry");
                    }
                    errorLayout.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    private void loadRecentTracks() {
        RecentTracksPrefs prefs = new RecentTracksPrefs(this);
        java.util.List<JamendoTrack> recentTracks = prefs.getRecentTracks();
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
                RecentTrackAdapter recentAdapter = new RecentTrackAdapter(track -> {
                    playTrack(track);
                });
                recentRecycler.setAdapter(recentAdapter);
                recentAdapter.setTracks(recentTracks);
            }
        }
    }

    private void playTrack(JamendoTrack track) {
        new RecentTracksPrefs(this).saveTrack(
                track.getTrackTitle(),
                track.getTrackArtist(),
                track.getStreamUrl(),
                track.getAlbumArtUrl()
        );
        loadRecentTracks();

        String roomId = getIntent().getStringExtra("ROOM_ID");
        if (roomId != null) {
            String uid = FirebaseAuth.getInstance().getCurrentUser() != null ?
                    FirebaseAuth.getInstance().getCurrentUser().getUid() : "Anonymous";

            DatabaseReference queueRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS)
                    .child(roomId).child(FirebasePaths.QUEUE);

            String trackId = queueRef.push().getKey();
            if (trackId != null) {
                java.util.HashMap<String, Object> trackMap = new java.util.HashMap<>();
                trackMap.put(FirebasePaths.TRACK_TITLE, track.getTrackTitle());
                trackMap.put(FirebasePaths.TRACK_ARTIST, track.getTrackArtist());
                trackMap.put(FirebasePaths.STREAM_URL, track.getStreamUrl());
                trackMap.put(FirebasePaths.ALBUM_ART_URL, track.getAlbumArtUrl());
                trackMap.put(FirebasePaths.ADDED_BY, uid);
                trackMap.put(FirebasePaths.ADDED_AT, System.currentTimeMillis());
                trackMap.put(FirebasePaths.UPVOTES, 0);
                trackMap.put(FirebasePaths.DOWNVOTES, 0);

                queueRef.child(trackId).setValue(trackMap).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(SearchActivity.this, "Added to room queue!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(SearchActivity.this, "Failed to add to queue", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            return;
        }

        if (mMediaController == null) {
            Toast.makeText(this, "Playback system not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        List<JamendoTrack> tracks = mAdapter.getTracks();
        List<MediaItem> mediaItems = new ArrayList<>();
        int startIndex = 0;
        boolean found = false;

        for (int i = 0; i < tracks.size(); i++) {
            JamendoTrack t = tracks.get(i);
            String mediaId = t.getJamendoId();
            if (mediaId == null || mediaId.isEmpty()) {
                mediaId = t.getStreamUrl();
            }
            MediaItem item = new MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setUri(Uri.parse(t.getStreamUrl()))
                    .setMediaMetadata(new MediaMetadata.Builder()
                            .setTitle(t.getTrackTitle())
                            .setArtist(t.getTrackArtist())
                            .setArtworkUri(Uri.parse(t.getAlbumArtUrl()))
                            .build())
                    .build();
            mediaItems.add(item);
            if (track.getStreamUrl() != null && track.getStreamUrl().equals(t.getStreamUrl())) {
                startIndex = i;
                found = true;
            }
        }

        if (found && !mediaItems.isEmpty()) {
            mMediaController.setMediaItems(mediaItems, startIndex, 0);
        } else {
            String mediaId = track.getJamendoId();
            if (mediaId == null || mediaId.isEmpty()) {
                mediaId = track.getStreamUrl();
            }
            MediaItem mediaItem = new MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setUri(Uri.parse(track.getStreamUrl()))
                    .setMediaMetadata(new MediaMetadata.Builder()
                            .setTitle(track.getTrackTitle())
                            .setArtist(track.getTrackArtist())
                            .setArtworkUri(Uri.parse(track.getAlbumArtUrl()))
                            .build())
                    .build();
            mMediaController.setMediaItem(mediaItem);
        }

        mMediaController.prepare();
        mMediaController.play();
    }

    private void updateMiniPlayer() {
        if (mMediaController == null) {
            mMiniPlayerBar.setVisibility(View.GONE);
            return;
        }

        MediaItem mediaItem = mMediaController.getCurrentMediaItem();
        if (mediaItem != null && mediaItem.mediaMetadata != null) {
            mMiniPlayerBar.setVisibility(View.VISIBLE);

            CharSequence title = mediaItem.mediaMetadata.title;
            CharSequence artist = mediaItem.mediaMetadata.artist;
            Uri artworkUri = mediaItem.mediaMetadata.artworkUri;

            mMiniTitle.setText(title != null ? title.toString() : "Unknown");
            mMiniArtist.setText(artist != null ? artist.toString() : "Unknown");

            Glide.with(this)
                    .load(artworkUri)
                    .placeholder(R.drawable.ic_music_placeholder)
                    .error(R.drawable.ic_music_placeholder)
                    .into(mMiniArt);

            updatePlayPauseButton(mMediaController.isPlaying());
        } else {
            mMiniPlayerBar.setVisibility(View.GONE);
        }
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        if (isPlaying) {
            mMiniPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            mMiniPlayPause.setImageResource(R.drawable.ic_play_arrow);
        }
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---- SearchResultsAdapter.Listener implementation ----

    @Override
    public void onTrackPlay(JamendoTrack track) {
        playTrack(track);
    }

    @Override
    public void onAddToQueue(JamendoTrack track) {
        // Try intent extra first, then SharedPreferences
        String roomId = getIntent().getStringExtra("ROOM_ID");
        if (roomId == null || roomId.isEmpty()) {
            SharedPreferences sp = getSharedPreferences("chatical_prefs", MODE_PRIVATE);
            roomId = sp.getString("current_room_id", null);
        }

        if (roomId == null || roomId.isEmpty()) {
            Snackbar.make(findViewById(R.id.search_root),
                    "Join a room first to use the queue.",
                    Snackbar.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ?
                FirebaseAuth.getInstance().getCurrentUser().getUid() : "Anonymous";

        DatabaseReference queueRef = FirebaseDatabase.getInstance()
                .getReference(FirebasePaths.ROOMS)
                .child(roomId)
                .child(FirebasePaths.QUEUE);

        String key = queueRef.push().getKey();
        if (key == null) return;

        java.util.HashMap<String, Object> trackMap = new java.util.HashMap<>();
        trackMap.put(FirebasePaths.TRACK_TITLE, track.getTrackTitle());
        trackMap.put(FirebasePaths.TRACK_ARTIST, track.getTrackArtist());
        trackMap.put(FirebasePaths.STREAM_URL, track.getStreamUrl());
        trackMap.put(FirebasePaths.ALBUM_ART_URL, track.getAlbumArtUrl());
        trackMap.put(FirebasePaths.ADDED_BY, uid);
        trackMap.put(FirebasePaths.ADDED_AT, System.currentTimeMillis());
        trackMap.put(FirebasePaths.UPVOTES, 0);
        trackMap.put(FirebasePaths.DOWNVOTES, 0);

        queueRef.child(key).setValue(trackMap).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Snackbar.make(findViewById(R.id.search_root),
                        "Added to queue.", Snackbar.LENGTH_SHORT).show();
            } else {
                Snackbar.make(findViewById(R.id.search_root),
                        "Failed to add — check connection.", Snackbar.LENGTH_SHORT).show();
            }
        });
    }
}
