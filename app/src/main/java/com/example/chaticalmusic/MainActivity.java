package com.example.chaticalmusic;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chaticalmusic.model.PlaybackState;
import com.example.chaticalmusic.model.Room;
import com.example.chaticalmusic.model.NotificationModel;
import com.example.chaticalmusic.model.PublicRoomItem;
import com.example.chaticalmusic.adapter.PublicRoomsAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.ChildEventListener;

import com.example.chaticalmusic.model.JamendoResponse;
import com.example.chaticalmusic.model.JamendoTrack;
import com.example.chaticalmusic.network.JamendoApiService;
import com.example.chaticalmusic.network.RetrofitClient;
import com.example.chaticalmusic.service.MusicPlaybackService;
import com.google.common.util.concurrent.ListenableFuture;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.net.Uri;
import com.bumptech.glide.Glide;
import java.util.concurrent.ExecutionException;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.content.ComponentName;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private android.widget.ProgressBar mProgressBar;
    private android.widget.TextView mBtnCreateHeader;
    private android.widget.TextView mBtnJoinHeader;
    private android.widget.ImageView mBtnRoomSearch;

    // Featured Song Components
    private androidx.cardview.widget.CardView mFeaturedCard;
    private android.widget.ImageView mFeaturedImage;
    private android.widget.TextView mFeaturedTitle;
    private android.widget.TextView mFeaturedLabel;
    private JamendoTrack mFeaturedTrack;

    // Mini Player components
    private android.view.View mMiniPlayerBar;
    private android.widget.ImageView mMiniArt;
    private android.widget.TextView mMiniTitle;
    private android.widget.TextView mMiniArtist;
    private android.widget.ImageButton mMiniPlayPause;

    // Media Session components
    private MediaController mMediaController;
    private ListenableFuture<MediaController> mControllerFuture;

    // Navigation Items
    private android.widget.LinearLayout mNavLobby;
    private android.widget.LinearLayout mNavSearch;
    private android.widget.LinearLayout mNavDm;
    private android.widget.LinearLayout mNavProfile;

    // Public Rooms List Fields
    private RecyclerView mPublicRoomsRecycler;
    private android.widget.TextView mNoPublicRoomsText;
    private PublicRoomsAdapter mPublicRoomsAdapter;
    private final List<PublicRoomItem> mPublicRoomsList = new ArrayList<>();
    private DatabaseReference mRoomsRef;
    private ChildEventListener mRoomsChildListener;
    private LoveAnimationHelper mLoveAnimationHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.header_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
            lp.topMargin = systemBars.top;
            v.setLayoutParams(lp);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottom_nav_container), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        mProgressBar = findViewById(R.id.main_progress_bar);
        
        mBtnCreateHeader = findViewById(R.id.btn_create_room_header);
        mBtnJoinHeader = findViewById(R.id.btn_join_room_header);
        mBtnRoomSearch = findViewById(R.id.btn_room_search);

        // Featured Card Initialization
        mFeaturedCard = findViewById(R.id.featured_card);
        mFeaturedImage = findViewById(R.id.featured_image);
        mFeaturedTitle = findViewById(R.id.featured_title);
        mFeaturedLabel = findViewById(R.id.featured_label);

        // Mini Player Initialization
        mMiniPlayerBar = findViewById(R.id.mini_player_bar);
        mMiniArt = findViewById(R.id.mini_art);
        mMiniTitle = findViewById(R.id.mini_title);
        mMiniArtist = findViewById(R.id.mini_artist);
        mMiniPlayPause = findViewById(R.id.mini_play_pause);

        mNavLobby = findViewById(R.id.nav_lobby);
        mNavSearch = findViewById(R.id.nav_search);
        mNavDm = findViewById(R.id.nav_dm);
        mNavProfile = findViewById(R.id.nav_profile);

        mLoveAnimationHelper = new LoveAnimationHelper(findViewById(R.id.love_animation_container));
        mLoveAnimationHelper.start();

        mBtnCreateHeader.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, CreateRoomActivity.class));
        });

        mBtnJoinHeader.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, JoinRoomActivity.class));
        });

        // Navigation Click Listeners
        mNavLobby.setOnClickListener(v -> {
            // Already in Lobby/Main
            Toast.makeText(this, "Lobby", Toast.LENGTH_SHORT).show();
        });

        mNavSearch.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, UserSearchActivity.class));
        });

        mNavDm.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, DmListActivity.class));
        });

        mNavProfile.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ProfileActivity.class));
        });

        mBtnRoomSearch.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, RoomSearchActivity.class));
        });

        setupNotificationListener();

        mFeaturedCard.setOnClickListener(v -> {
            if (mFeaturedTrack != null) {
                playFeaturedTrack();
            } else {
                // If no featured track loaded yet or user wants to search, go to search
                Intent intent = new Intent(MainActivity.this, SearchActivity.class);
                intent.putExtra("SELECT_MUSIC_TAB", true);
                startActivity(intent);
            }
        });

        mMiniPlayPause.setOnClickListener(v -> {
            if (mMediaController != null) {
                if (mMediaController.isPlaying()) {
                    mMediaController.pause();
                } else {
                    mMediaController.play();
                }
            }
        });

        mMiniPlayerBar.setOnClickListener(v -> {
            if (mMediaController != null && mMediaController.getCurrentMediaItem() != null) {
                FullPlayerBottomSheet sheet = FullPlayerBottomSheet.newInstance();
                sheet.setMediaController(mMediaController);
                sheet.show(getSupportFragmentManager(), "full_player");
            }
        });

        loadFeaturedSong();

        // Public Rooms RecyclerView Setup
        mPublicRoomsRecycler = findViewById(R.id.public_rooms_recycler);
        mNoPublicRoomsText = findViewById(R.id.no_public_rooms_text);

        mPublicRoomsAdapter = new PublicRoomsAdapter(item -> {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS)
                    .child(uid).child("recent_rooms").child(item.getRoomId()).setValue(com.google.firebase.database.ServerValue.TIMESTAMP);

            Intent intent = new Intent(MainActivity.this, RoomActivity.class);
            intent.putExtra("ROOM_ID", item.getRoomId());
            intent.putExtra("ROOM_NAME", item.getRoomName());
            startActivity(intent);
        });

        mPublicRoomsRecycler.setLayoutManager(new LinearLayoutManager(this));
        mPublicRoomsRecycler.setAdapter(mPublicRoomsAdapter);

        updateEmptyText();
        setupPublicRoomsListener();
        saveFcmToken();
    }

    private void saveFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String token = task.getResult();
                        String uid = FirebaseAuth.getInstance().getUid();
                        if (uid != null) {
                            FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(uid).child("fcm_token").setValue(token);
                        }
                    }
                });
    }

    private void setupNotificationListener() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DatabaseReference notificationsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.NOTIFICATIONS).child(uid);
        notificationsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                NotificationModel notification = snapshot.getValue(NotificationModel.class);
                if (notification != null && !notification.isRead()) {
                    if ("follow_request".equals(notification.getType())) {
                        showFriendRequestPopup(notification, snapshot.getRef());
                    } else if ("request_accepted".equals(notification.getType())) {
                        Toast.makeText(MainActivity.this, "Request accepted by " + notification.getSender_name(), Toast.LENGTH_SHORT).show();
                        snapshot.getRef().child("read").setValue(true);
                    }
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showFriendRequestPopup(NotificationModel notification, DatabaseReference ref) {
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("New Friend Request")
                .setMessage(notification.getSender_name() + " sent you a friend request!")
                .setPositiveButton("View", (d, which) -> {
                    ref.child("read").setValue(true);
                    startActivity(new Intent(MainActivity.this, FollowRequestsActivity.class));
                })
                .setNegativeButton("Ignore", (d, which) -> ref.child("read").setValue(true))
                .create();
        
        dialog.show();
    }

    private void setLoadingState(boolean isLoading) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(isLoading ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void createRoom(String roomName, boolean isPrivate) {
        if (!isNetworkAvailable()) {
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("Connection Error")
                .setMessage("Could not connect — check your internet.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Authentication error. Please restart the app.", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS);
        String roomId = roomsRef.push().getKey();

        if (roomId == null) {
            Toast.makeText(this, "Error generating Room ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show centered circular ProgressBar and disable buttons
        setLoadingState(true);

        // Generate 6-character alphanumeric code
        String roomCode = generateRoomCode();

        // Create models
        Room roomMeta = new Room(roomName, uid, isPrivate, System.currentTimeMillis(), false, roomCode);
        PlaybackState playbackState = new PlaybackState(false, 0L, System.currentTimeMillis(), "", "", "", "");

        // Set values together
        Map<String, Object> roomData = new HashMap<>();
        roomData.put(FirebasePaths.ROOM_META, roomMeta);
        roomData.put(FirebasePaths.PLAYBACK_STATE, playbackState);

        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final boolean[] completed = {false};

        Runnable timeoutRunnable = () -> {
            completed[0] = true;
            setLoadingState(false);
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("Connection Error")
                .setMessage("Could not connect — check your internet.")
                .setPositiveButton("OK", null)
                .show();
        };
        handler.postDelayed(timeoutRunnable, 5000);

        roomsRef.child(roomId).setValue(roomData).addOnCompleteListener(task -> {
            if (completed[0]) return;
            completed[0] = true;
            handler.removeCallbacks(timeoutRunnable);
            setLoadingState(false);

            if (task.isSuccessful()) {
                FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS)
                        .child(uid).child("recent_rooms").child(roomId).setValue(com.google.firebase.database.ServerValue.TIMESTAMP);
                Intent intent = new Intent(MainActivity.this, RoomActivity.class);
                intent.putExtra("ROOM_ID", roomId);
                intent.putExtra("ROOM_NAME", roomName);
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Failed to create room", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager = 
                (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.net.Network network = connectivityManager.getActiveNetwork();
                if (network != null) {
                    android.net.NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                    return capabilities != null && (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || 
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
                }
            } else {
                android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        }
        return false;
    }

    private void joinRoom(String roomInput) {
        if (!isNetworkAvailable()) {
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("Connection Error")
                .setMessage("Could not connect — check your internet.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        // Show centered circular ProgressBar and disable buttons
        setLoadingState(true);

        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS);
        
        // 1. Try querying by room name first
        Query nameQuery = roomsRef.orderByChild(FirebasePaths.ROOM_META + "/room_name").equalTo(roomInput);

        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final boolean[] completed = {false};

        Runnable timeoutRunnable = () -> {
            if (completed[0]) return;
            completed[0] = true;
            setLoadingState(false);
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("Connection Error")
                .setMessage("Could not connect — check your internet.")
                .setPositiveButton("OK", null)
                .show();
        };
        handler.postDelayed(timeoutRunnable, 5000);

        nameQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (completed[0]) return;

                if (dataSnapshot.exists() && dataSnapshot.hasChildren()) {
                    completed[0] = true;
                    handler.removeCallbacks(timeoutRunnable);
                    setLoadingState(false);
                    for (DataSnapshot roomSnapshot : dataSnapshot.getChildren()) {
                        String roomId = roomSnapshot.getKey();
                        String actualRoomName = roomSnapshot.child(FirebasePaths.ROOM_META + "/room_name").getValue(String.class);
                        
                        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                        FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS)
                                .child(uid).child("recent_rooms").child(roomId).setValue(com.google.firebase.database.ServerValue.TIMESTAMP);

                        Intent intent = new Intent(MainActivity.this, RoomActivity.class);
                        intent.putExtra("ROOM_ID", roomId);
                        intent.putExtra("ROOM_NAME", actualRoomName != null ? actualRoomName : roomInput);
                        startActivity(intent);
                        return;
                    }
                } else {
                    // 2. Try querying by room code next
                    Query codeQuery = roomsRef.orderByChild(FirebasePaths.ROOM_META + "/" + FirebasePaths.ROOM_CODE).equalTo(roomInput.toUpperCase().trim());
                    codeQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot codeSnapshot) {
                            if (completed[0]) return;
                            completed[0] = true;
                            handler.removeCallbacks(timeoutRunnable);
                            setLoadingState(false);

                            if (codeSnapshot.exists() && codeSnapshot.hasChildren()) {
                                for (DataSnapshot roomSnapshot : codeSnapshot.getChildren()) {
                                    String roomId = roomSnapshot.getKey();
                                    String actualRoomName = roomSnapshot.child(FirebasePaths.ROOM_META + "/room_name").getValue(String.class);
                                    
                                    String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                                    FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS)
                                            .child(uid).child("recent_rooms").child(roomId).setValue(com.google.firebase.database.ServerValue.TIMESTAMP);

                                    Intent intent = new Intent(MainActivity.this, RoomActivity.class);
                                    intent.putExtra("ROOM_ID", roomId);
                                    intent.putExtra("ROOM_NAME", actualRoomName != null ? actualRoomName : roomInput);
                                    startActivity(intent);
                                    return;
                                }
                            } else {
                                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Room Not Found")
                                    .setMessage("No room found with that name or code. Check and try again.")
                                    .setPositiveButton("OK", null)
                                    .show();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            if (completed[0]) return;
                            completed[0] = true;
                            handler.removeCallbacks(timeoutRunnable);
                            setLoadingState(false);
                            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("Connection Error")
                                .setMessage("Could not connect — check your internet.")
                                .setPositiveButton("OK", null)
                                .show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                if (completed[0]) return;
                completed[0] = true;
                handler.removeCallbacks(timeoutRunnable);
                setLoadingState(false);
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("Connection Error")
                    .setMessage("Could not connect — check your internet.")
                    .setPositiveButton("OK", null)
                    .show();
            }
        });
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.util.Random rnd = new java.util.Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void setupPublicRoomsListener() {
        mRoomsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS);
        mRoomsChildListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                processRoomSnapshot(snapshot);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                processRoomSnapshot(snapshot);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String roomId = snapshot.getKey();
                if (roomId != null) {
                    removeRoomById(roomId);
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mRoomsRef.addChildEventListener(mRoomsChildListener);
    }

    private void processRoomSnapshot(DataSnapshot snapshot) {
        String roomId = snapshot.getKey();
        if (roomId == null) return;

        Boolean isPrivate = snapshot.child(FirebasePaths.ROOM_META + "/is_private").getValue(Boolean.class);
        if (isPrivate != null && isPrivate) {
            // Room is private, so remove it if it was previously public
            removeRoomById(roomId);
            return;
        }

        String roomName = snapshot.child(FirebasePaths.ROOM_META + "/room_name").getValue(String.class);
        if (roomName == null || roomName.isEmpty()) {
            removeRoomById(roomId);
            return;
        }

        String songTitle = snapshot.child(FirebasePaths.PLAYBACK_STATE + "/current_track_title").getValue(String.class);
        int memberCount = (int) snapshot.child(FirebasePaths.PRESENCE).getChildrenCount();

        // Update or add in list
        boolean found = false;
        for (int i = 0; i < mPublicRoomsList.size(); i++) {
            PublicRoomItem item = mPublicRoomsList.get(i);
            if (item.getRoomId().equals(roomId)) {
                item.setRoomName(roomName);
                item.setCurrentTrackTitle(songTitle);
                item.setMemberCount(memberCount);
                found = true;
                break;
            }
        }

        if (!found) {
            mPublicRoomsList.add(new PublicRoomItem(roomId, roomName, songTitle, memberCount));
        }

        mPublicRoomsAdapter.setRooms(mPublicRoomsList);
        updateEmptyText();
    }

    private void removeRoomById(String roomId) {
        for (int i = 0; i < mPublicRoomsList.size(); i++) {
            if (mPublicRoomsList.get(i).getRoomId().equals(roomId)) {
                mPublicRoomsList.remove(i);
                mPublicRoomsAdapter.setRooms(mPublicRoomsList);
                updateEmptyText();
                return;
            }
        }
    }

    private void updateEmptyText() {
        if (mNoPublicRoomsText != null) {
            mNoPublicRoomsText.setVisibility(mPublicRoomsList.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    private void loadFeaturedSong() {
        String clientId = BuildConfig.JAMENDO_CLIENT_ID;
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = "56d30cce";
        }

        JamendoApiService apiService = RetrofitClient.getClient().create(JamendoApiService.class);
        apiService.getPopularTracks(
                clientId,
                "json",
                10,
                "mp31",
                "popularity_week",
                "600"
        ).enqueue(new Callback<JamendoResponse>() {
            @Override
            public void onResponse(Call<JamendoResponse> call, Response<JamendoResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<JamendoTrack> tracks = response.body().getResults();
                    if (tracks != null && !tracks.isEmpty()) {
                        mFeaturedTrack = tracks.get(new java.util.Random().nextInt(tracks.size()));
                        updateFeaturedUI();
                    }
                } else {
                    android.util.Log.e("MainActivity", "Featured Song Error: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<JamendoResponse> call, Throwable t) {
                android.util.Log.e("MainActivity", "Featured Song Failure: " + t.getMessage());
            }
        });
    }

    private void updateFeaturedUI() {
        if (mFeaturedTrack == null || mFeaturedTitle == null || mFeaturedLabel == null || mFeaturedImage == null) return;
        mFeaturedTitle.setText(mFeaturedTrack.getTrackTitle());
        mFeaturedLabel.setText("FEATURED • " + mFeaturedTrack.getTrackArtist());
        Glide.with(this)
                .load(mFeaturedTrack.getAlbumArtUrl())
                .placeholder(R.drawable.gradient_player_bg)
                .into(mFeaturedImage);
    }

    private void playFeaturedTrack() {
        if (mMediaController == null) {
            Toast.makeText(this, "Playback system not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        MediaItem mediaItem = new MediaItem.Builder()
                .setMediaId(mFeaturedTrack.getJamendoId())
                .setUri(Uri.parse(mFeaturedTrack.getStreamUrl()))
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(mFeaturedTrack.getTrackTitle())
                        .setArtist(mFeaturedTrack.getTrackArtist())
                        .setArtworkUri(Uri.parse(mFeaturedTrack.getAlbumArtUrl()))
                        .build())
                .build();

        mMediaController.setMediaItem(mediaItem);
        mMediaController.prepare();
        mMediaController.play();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.start();

        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicPlaybackService.class));
        mControllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        mControllerFuture.addListener(() -> {
            try {
                mMediaController = mControllerFuture.get();
                mMediaController.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        updateMiniPlayer();
                    }
                    @Override
                    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                        updateMiniPlayer();
                    }
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        updateMiniPlayer();
                    }
                });
                updateMiniPlayer();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    protected void onStop() {
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.stop();
        if (mMediaController != null) {
            mMediaController.release();
            mMediaController = null;
        }
        if (mControllerFuture != null) {
            MediaController.releaseFuture(mControllerFuture);
        }
        super.onStop();
    }

    private void updateMiniPlayer() {
        if (mMediaController == null || mMiniPlayerBar == null) {
            if (mMiniPlayerBar != null) mMiniPlayerBar.setVisibility(android.view.View.GONE);
            return;
        }

        MediaItem mediaItem = mMediaController.getCurrentMediaItem();
        if (mediaItem != null && mediaItem.mediaMetadata != null) {
            mMiniPlayerBar.setVisibility(android.view.View.VISIBLE);
            if (mMiniTitle != null) mMiniTitle.setText(mediaItem.mediaMetadata.title);
            if (mMiniArtist != null) mMiniArtist.setText(mediaItem.mediaMetadata.artist);
            if (mMiniArt != null) {
                Glide.with(this)
                        .load(mediaItem.mediaMetadata.artworkUri)
                        .placeholder(R.drawable.ic_music_placeholder)
                        .into(mMiniArt);
            }

            if (mMiniPlayPause != null) {
                if (mMediaController.isPlaying()) {
                    mMiniPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                } else {
                    mMiniPlayPause.setImageResource(android.R.drawable.ic_media_play);
                }
            }
        } else {
            mMiniPlayerBar.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.stop();
        if (mRoomsRef != null && mRoomsChildListener != null) {
            mRoomsRef.removeEventListener(mRoomsChildListener);
        }
        super.onDestroy();
    }
}