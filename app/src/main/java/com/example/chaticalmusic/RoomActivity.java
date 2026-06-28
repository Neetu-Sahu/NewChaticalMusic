package com.example.chaticalmusic;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chaticalmusic.adapter.ChatAdapter;
import com.example.chaticalmusic.adapter.QueueAdapter;
import com.example.chaticalmusic.model.ChatMessage;
import com.example.chaticalmusic.model.QueueTrack;
import com.example.chaticalmusic.service.MusicPlaybackService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.MutableData;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class RoomActivity extends AppCompatActivity implements QueueAdapter.OnVoteClickListener {

    // UI Bindings
    private TextView mRoomNameTitle;
    private TextView mHostNameSubtitle;
    private LinearLayout mRoomCodeContainer;
    private TextView mRoomCodeText;
    private TextView mMemberCountText;
    private TextView mTrackTitle;
    private TextView mTrackArtist;
    private TextView mHostWarningBanner;
    private TextView mQueueEmptyBanner;
    private ImageView mTrackArt;
    private SeekBar mTrackSeekBar;
    private TextView mTimeElapsedText;
    private TextView mTimeDurationText;
    private LinearLayout mHostControlsContainer;
    private ImageButton mPlayPauseBtn;
    private ImageButton mSkipBtn;
    private Button mAddSongBtn;
    private TextView mEmojiFireBtn;
    private TextView mEmojiHeartBtn;
    private TextView mEmojiWowBtn;
    private EditText mChatInput;
    private ImageButton mChatSendBtn;
    private RecyclerView mQueueRecycler;
    private RecyclerView mChatRecycler;

    // Guest Sync indicator
    private TextView mSyncStatusText;
    private DatabaseReference mPlaybackStateRef;
    private ValueEventListener mPlaybackStateListener;
    private DataSnapshot mLastPlaybackStateSnapshot;

    // Equalizer animators
    private android.animation.ObjectAnimator mEqAnimator1;
    private android.animation.ObjectAnimator mEqAnimator2;
    private android.animation.ObjectAnimator mEqAnimator3;

    // Room parameters
    private String mRoomId;
    private String mRoomName;
    private String mUid;
    private String mDisplayName;
    private boolean mIsHost;
    private boolean mIsSeekBarTracking = false;

    // Media3 Playback
    private MediaController mMediaController;
    private ListenableFuture<MediaController> mMediaControllerFuture;

    // Firebase
    private DatabaseReference mRoomRef;
    private DatabaseReference mRoomMetaRef;
    private DatabaseReference mChatRef;
    private DatabaseReference mQueueRef;
    private DatabaseReference mPresenceRef;

    private ValueEventListener mRoomMetaListener;
    private ValueEventListener mPresenceListener;
    private ValueEventListener mQueueListener;
    private ValueEventListener mChatListener;

    // AUX request system
    private Button mRequestAuxBtn;
    private DatabaseReference mAuxRequestsRef;
    private ValueEventListener mAuxRequestsListener;
    private boolean mHasRequestedAux = false;
    private int mAuxRequestsCount = 0;
    private int mActiveMembersCount = 1;
    private final List<String> mRequestingUids = new ArrayList<>();

    // Adapters
    private QueueAdapter mQueueAdapter;
    private ChatAdapter mChatAdapter;

    private final Handler mUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable mUpdateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mMediaController != null) {
                long position = mMediaController.getCurrentPosition();
                long duration = mMediaController.getDuration();
                if (duration < 0) duration = 0;

                if (mMediaController.isPlaying() && !mIsSeekBarTracking) {
                    if (duration > 0) {
                        mTrackSeekBar.setMax((int) duration);
                        mTrackSeekBar.setProgress((int) position);
                    } else {
                        mTrackSeekBar.setProgress(0);
                    }
                }

                if (!mIsSeekBarTracking && mTimeElapsedText != null) {
                    mTimeElapsedText.setText(formatTime(position));
                }
                if (mTimeDurationText != null) {
                    mTimeDurationText.setText(formatTime(duration));
                }

                // Periodically update the sync status indicator for guests
                updateSyncStatus(mLastPlaybackStateSnapshot);
            }
            mUpdateHandler.postDelayed(this, 1000);
        }
    };

    // Emoji batching & animations
    private FrameLayout mEmojiOverlayContainer;
    private DatabaseReference mEmojiExplosionsRef;
    private ChildEventListener mEmojiExplosionsListener;

    private int mLocalFireCount = 0;
    private int mLocalHeartCount = 0;
    private int mLocalWowCount = 0;
    private int mActiveEmojiCount = 0;

    private final Handler mEmojiBatchHandler = new Handler(Looper.getMainLooper());
    private boolean mIsEmojiTimerRunning = false;
    private final Runnable mEmojiBatchRunnable = new Runnable() {
        @Override
        public void run() {
            mIsEmojiTimerRunning = false;
            flushEmojiBatches();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_room);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.header_bar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottom_input_container), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        mRoomId = getIntent().getStringExtra("ROOM_ID");
        mRoomName = getIntent().getStringExtra("ROOM_NAME");

        if (mRoomId == null) {
            Toast.makeText(this, "Invalid Room ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Get stored credentials
        SharedPreferences prefs = getSharedPreferences("MusicalChatPrefs", MODE_PRIVATE);
        mUid = prefs.getString("user_uid", "UnknownUid");
        mDisplayName = prefs.getString("display_name", "Anonymous");

        initViews();
        setupFirebase();
        setupAdapters();
        setupListeners();
    }

    private void initViews() {
        mRoomNameTitle = findViewById(R.id.room_name_title);
        mHostNameSubtitle = findViewById(R.id.host_name_subtitle);
        mMemberCountText = findViewById(R.id.member_count_text);
        mTrackTitle = findViewById(R.id.track_title);
        mTrackArtist = findViewById(R.id.track_artist);
        mHostWarningBanner = findViewById(R.id.host_warning_banner);
        mQueueEmptyBanner = findViewById(R.id.queue_empty_banner);
        mTrackArt = findViewById(R.id.track_art);
        mTrackSeekBar = findViewById(R.id.track_seekbar);
        mTimeElapsedText = findViewById(R.id.track_time_elapsed);
        mTimeDurationText = findViewById(R.id.track_time_duration);
        mHostControlsContainer = findViewById(R.id.host_controls_container);
        mPlayPauseBtn = findViewById(R.id.play_pause_btn);
        mSkipBtn = findViewById(R.id.skip_btn);
        mAddSongBtn = findViewById(R.id.add_to_queue_helper_btn);
        mEmojiFireBtn = findViewById(R.id.emoji_fire_btn);
        mEmojiHeartBtn = findViewById(R.id.emoji_heart_btn);
        mEmojiWowBtn = findViewById(R.id.emoji_wow_btn);
        mChatInput = findViewById(R.id.chat_input);
        mChatSendBtn = findViewById(R.id.chat_send_btn);
        mQueueRecycler = findViewById(R.id.queue_recycler);
        mChatRecycler = findViewById(R.id.chat_recycler);
        mEmojiOverlayContainer = findViewById(R.id.emoji_overlay_container);
        mSyncStatusText = findViewById(R.id.sync_status_text);
        mRoomCodeContainer = findViewById(R.id.room_code_container);
        mRoomCodeText = findViewById(R.id.room_code_text);
        mRequestAuxBtn = findViewById(R.id.request_aux_btn);

        mRoomCodeContainer.setOnClickListener(v -> {
            CharSequence codeText = mRoomCodeText.getText();
            if (codeText != null && codeText.toString().contains(": ")) {
                String code = codeText.toString().substring(codeText.toString().indexOf(": ") + 2);
                if (!code.isEmpty() && !code.equals("Loading...") && !code.equals("None")) {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Room Code", code);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(RoomActivity.this, "Room code " + code + " copied to clipboard!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        mRoomNameTitle.setText(mRoomName != null ? mRoomName : "MusicalChat Room");

        // Disable seekBar by default until host status is confirmed
        mTrackSeekBar.setEnabled(false);
        mTrackSeekBar.setAlpha(0.4f);
    }

    private void setupFirebase() {
        mRoomRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS).child(mRoomId);
        mRoomMetaRef = mRoomRef.child(FirebasePaths.ROOM_META);
        mChatRef = mRoomRef.child(FirebasePaths.CHAT_MESSAGES);
        mQueueRef = mRoomRef.child(FirebasePaths.QUEUE);
        mPresenceRef = mRoomRef.child(FirebasePaths.PRESENCE);
        mEmojiExplosionsRef = mRoomRef.child(FirebasePaths.EMOJI_EXPLOSIONS);
        mPlaybackStateRef = mRoomRef.child(FirebasePaths.PLAYBACK_STATE);
        mAuxRequestsRef = mRoomRef.child(FirebasePaths.AUX_REQUESTS);

        // Remove AUX request automatically on disconnect
        mAuxRequestsRef.child(mUid).onDisconnect().removeValue();
    }

    private void setupAdapters() {
        mQueueAdapter = new QueueAdapter(mUid, this);
        mQueueRecycler.setLayoutManager(new LinearLayoutManager(this));
        mQueueRecycler.setAdapter(mQueueAdapter);

        mChatAdapter = new ChatAdapter(mUid);
        mChatRecycler.setLayoutManager(new LinearLayoutManager(this));
        mChatRecycler.setAdapter(mChatAdapter);
    }

    private void setupListeners() {
        // Play / Pause Click
        mPlayPauseBtn.setOnClickListener(v -> {
            if (mMediaController != null && mIsHost) {
                if (mMediaController.isPlaying()) {
                    mMediaController.pause();
                } else {
                    mMediaController.play();
                }
            }
        });

        // Skip Click
        mSkipBtn.setOnClickListener(v -> {
            if (mMediaController != null && mIsHost) {
                mMediaController.sendCustomCommand(new SessionCommand("SKIP_TRACK", Bundle.EMPTY), Bundle.EMPTY);
            }
        });

        // Add Song Click
        mAddSongBtn.setOnClickListener(v -> {
            Intent intent = new Intent(RoomActivity.this, SearchActivity.class);
            intent.putExtra("ROOM_ID", mRoomId);
            startActivity(intent);
        });

        // Chat Send Click
        mChatSendBtn.setOnClickListener(v -> sendChatMessage(mChatInput.getText().toString().trim()));

        // Emoji clicks
        mEmojiFireBtn.setOnClickListener(v -> handleEmojiTap("fire"));
        mEmojiHeartBtn.setOnClickListener(v -> handleEmojiTap("heart"));
        mEmojiWowBtn.setOnClickListener(v -> handleEmojiTap("wow"));

        // Member list click
        mMemberCountText.setOnClickListener(v -> showMembersDialog());

        // Request AUX click listener
        mRequestAuxBtn.setOnClickListener(v -> {
            DatabaseReference reqRef = mAuxRequestsRef.child(mUid);
            if (mHasRequestedAux) {
                reqRef.removeValue().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(RoomActivity.this, "AUX request canceled", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                java.util.Map<String, Object> requestData = new java.util.HashMap<>();
                requestData.put("uid", mUid);
                requestData.put("display_name", mDisplayName);
                reqRef.setValue(requestData).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(RoomActivity.this, "Hand raised! Requesting AUX...", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        // Seek Bar listener
        mTrackSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mIsHost) {
                    if (mTimeElapsedText != null) {
                        mTimeElapsedText.setText(formatTime(progress));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsSeekBarTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mIsSeekBarTracking = false;
                if (mMediaController != null && mIsHost) {
                    int progress = seekBar.getProgress();
                    mMediaController.seekTo(progress);
                    java.util.Map<String, Object> seekUpdates = new java.util.HashMap<>();
                    seekUpdates.put(FirebasePaths.CURRENT_POSITION_MS, (long) progress);
                    seekUpdates.put("last_updated_system_time", System.currentTimeMillis());
                    mRoomRef.child(FirebasePaths.PLAYBACK_STATE).updateChildren(seekUpdates);
                }
            }
        });

        // 1. Presence Listener (Count)
        mPresenceListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mActiveMembersCount = (int) snapshot.getChildrenCount();
                updateMemberCountText();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mPresenceRef.addValueEventListener(mPresenceListener);

        // AUX Requests Listener
        mAuxRequestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mAuxRequestsCount = (int) snapshot.getChildrenCount();
                mHasRequestedAux = snapshot.hasChild(mUid);

                // Update Request AUX button state
                if (mHasRequestedAux) {
                    mRequestAuxBtn.setText("AUX Requested ✋");
                    mRequestAuxBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x66FFFFFF));
                } else {
                    mRequestAuxBtn.setText("Request AUX");
                    mRequestAuxBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1DB954));
                }

                // Update Member Count Text
                updateMemberCountText();

                // Store request status for active member rendering
                mRequestingUids.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    mRequestingUids.add(child.getKey());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mAuxRequestsRef.addValueEventListener(mAuxRequestsListener);

        // 2. Room Meta / Host Listener (Step 6)
        mRoomMetaListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String hostId = snapshot.child(FirebasePaths.HOST_ID).getValue(String.class);
                    Boolean needsNewHost = snapshot.child(FirebasePaths.NEEDS_NEW_HOST).getValue(Boolean.class);
                    String roomCode = snapshot.child(FirebasePaths.ROOM_CODE).getValue(String.class);

                    if (roomCode != null && !roomCode.isEmpty()) {
                        mRoomCodeText.setText("Code: " + roomCode);
                    } else {
                        mRoomCodeText.setText("Code: None");
                    }

                    if (hostId != null) {
                        mIsHost = mUid.equals(hostId);
                        mTrackSeekBar.setEnabled(mIsHost);
                        mTrackSeekBar.setAlpha(mIsHost ? 1.0f : 0.4f);
                        mRequestAuxBtn.setVisibility(mIsHost ? View.GONE : View.VISIBLE);
                        mAuxRequestsRef.child(hostId).removeValue();
                        updateMemberCountText();

                        // Fetch host's display name from Firebase users node
                        FirebaseDatabase.getInstance().getReference("users")
                            .child(hostId)
                            .child("display_name")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                    String preferredName = userSnapshot.getValue(String.class);
                                    if (preferredName != null && !preferredName.trim().isEmpty()) {
                                        mHostNameSubtitle.setText("DJ: " + preferredName);
                                    } else {
                                        String defaultHostName = "User_" + (hostId.length() >= 4 ? hostId.substring(0, 4) : hostId);
                                        mHostNameSubtitle.setText("DJ: " + defaultHostName);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    String defaultHostName = "User_" + (hostId.length() >= 4 ? hostId.substring(0, 4) : hostId);
                                    mHostNameSubtitle.setText("DJ: " + defaultHostName);
                                }
                            });

                        if (needsNewHost != null && needsNewHost) {
                            mHostWarningBanner.setVisibility(View.VISIBLE);
                            mHostControlsContainer.setVisibility(View.GONE);
                        } else {
                            mHostWarningBanner.setVisibility(View.GONE);
                            mHostControlsContainer.setVisibility(mIsHost ? View.VISIBLE : View.GONE);
                        }
                        updateSyncStatus(mLastPlaybackStateSnapshot);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mRoomMetaRef.addValueEventListener(mRoomMetaListener);

        // 3. Queue Listener
        mQueueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<QueueTrack> tracks = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        QueueTrack track = child.getValue(QueueTrack.class);
                        if (track != null) {
                            track.setTrack_key(child.getKey());
                            tracks.add(track);
                        }
                    }
                }
                // Sort by added_at ascending
                java.util.Collections.sort(tracks, new java.util.Comparator<QueueTrack>() {
                    @Override
                    public int compare(QueueTrack o1, QueueTrack o2) {
                        return Long.compare(o1.getAdded_at(), o2.getAdded_at());
                    }
                });
                if (tracks.isEmpty()) {
                    mQueueEmptyBanner.setVisibility(View.VISIBLE);
                } else {
                    mQueueEmptyBanner.setVisibility(View.GONE);
                }
                mQueueAdapter.setTracks(tracks);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mQueueRef.addValueEventListener(mQueueListener);

        // 4. Live Chat Listener
        mChatListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<ChatMessage> messages = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        ChatMessage msg = child.getValue(ChatMessage.class);
                        if (msg != null) {
                            messages.add(msg);
                        }
                    }
                }
                mChatAdapter.setMessages(messages);
                if (mChatAdapter.getItemCount() > 0) {
                    mChatRecycler.scrollToPosition(mChatAdapter.getItemCount() - 1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mChatRef.limitToLast(50).addValueEventListener(mChatListener);

        // 5. Emoji Explosions Listener
        mEmojiExplosionsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                if (snapshot.exists()) {
                    String type = snapshot.child("emoji_type").getValue(String.class);
                    Integer countVal = snapshot.child("count").getValue(Integer.class);
                    int count = countVal != null ? countVal : 0;
                    Long timestamp = snapshot.child("timestamp").getValue(Long.class);

                    if (timestamp != null && System.currentTimeMillis() - timestamp < 5000) {
                        spawnFloatingEmojis(type, count);
                    }
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mEmojiExplosionsRef.limitToLast(10).addChildEventListener(mEmojiExplosionsListener);

        // 6. Playback State Listener (Guest Sync Status)
        mPlaybackStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mLastPlaybackStateSnapshot = snapshot;
                updateSyncStatus(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mPlaybackStateRef.addValueEventListener(mPlaybackStateListener);
    }

    private void sendChatMessage(String text) {
        if (!TextUtils.isEmpty(text)) {
            ChatMessage msg = new ChatMessage(mUid, mDisplayName, text, System.currentTimeMillis());
            mChatRef.push().setValue(msg);
            mChatInput.setText("");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Connect to MediaSessionService
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicPlaybackService.class));
        mMediaControllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        mMediaControllerFuture.addListener(() -> {
            try {
                mMediaController = mMediaControllerFuture.get();
                onMediaControllerConnected();
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
                Toast.makeText(RoomActivity.this, "Failed to connect to player service", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void onMediaControllerConnected() {
        // Send JOIN_ROOM custom command
        Bundle args = new Bundle();
        args.putString("ROOM_ID", mRoomId);
        args.putString("UID", mUid);
        mMediaController.sendCustomCommand(new SessionCommand("JOIN_ROOM", Bundle.EMPTY), args);

        // Listen to playback state changes
        mMediaController.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlaybackUi();
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                updatePlaybackUi();
            }
        });

        updatePlaybackUi();
        mUpdateHandler.post(mUpdateProgressRunnable);
    }

    private void updatePlaybackUi() {
        if (mMediaController == null) return;

        mPlayPauseBtn.setImageResource(mMediaController.isPlaying() ?
                android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

        if (mMediaController.isPlaying()) {
            startEqualizerAnimation();
        } else {
            stopEqualizerAnimation();
        }

        MediaItem currentItem = mMediaController.getCurrentMediaItem();
        if (currentItem != null && currentItem.mediaMetadata != null) {
            String title = currentItem.mediaMetadata.title != null ? currentItem.mediaMetadata.title.toString() : "Unknown Title";
            String artist = currentItem.mediaMetadata.artist != null ? currentItem.mediaMetadata.artist.toString() : "Unknown Artist";
            Uri artUri = currentItem.mediaMetadata.artworkUri;

            mTrackTitle.setText(title);
            mTrackArtist.setText(artist);

            Glide.with(this)
                    .load(artUri)
                    .placeholder(R.drawable.ic_music_placeholder)
                    .error(R.drawable.ic_music_placeholder)
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                    .into(mTrackArt);

            long duration = mMediaController.getDuration();
            long position = mMediaController.getCurrentPosition();
            if (duration > 0) {
                mTrackSeekBar.setMax((int) duration);
                mTrackSeekBar.setProgress((int) position);
            } else {
                mTrackSeekBar.setProgress(0);
            }
            if (mTimeElapsedText != null) {
                mTimeElapsedText.setText(formatTime(position));
            }
            if (mTimeDurationText != null) {
                mTimeDurationText.setText(formatTime(duration > 0 ? duration : 0));
            }
        } else {
            mTrackTitle.setText("No Song Playing");
            mTrackArtist.setText("Choose a song to start");
            mTrackArt.setImageResource(R.drawable.ic_music_placeholder);
            mTrackSeekBar.setProgress(0);
            if (mTimeElapsedText != null) {
                mTimeElapsedText.setText("00:00");
            }
            if (mTimeDurationText != null) {
                mTimeDurationText.setText("00:00");
            }
        }
    }

    private void startEqualizerAnimation() {
        View bar1 = findViewById(R.id.eq_bar1);
        View bar2 = findViewById(R.id.eq_bar2);
        View bar3 = findViewById(R.id.eq_bar3);

        if (bar1 == null || bar2 == null || bar3 == null) return;

        // If already running, just make sure visible and return
        if (mEqAnimator1 != null && mEqAnimator1.isRunning()) {
            View container = findViewById(R.id.equalizer_container);
            if (container != null) {
                container.setVisibility(View.VISIBLE);
            }
            return;
        }

        // Set pivot to bottom so it scales upwards
        bar1.post(() -> bar1.setPivotY(bar1.getHeight()));
        bar2.post(() -> bar2.setPivotY(bar2.getHeight()));
        bar3.post(() -> bar3.setPivotY(bar3.getHeight()));

        mEqAnimator1 = android.animation.ObjectAnimator.ofFloat(bar1, "scaleY", 0.2f, 1.0f);
        mEqAnimator1.setDuration(400);
        mEqAnimator1.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        mEqAnimator1.setRepeatMode(android.animation.ValueAnimator.REVERSE);

        mEqAnimator2 = android.animation.ObjectAnimator.ofFloat(bar2, "scaleY", 0.3f, 1.0f);
        mEqAnimator2.setDuration(550);
        mEqAnimator2.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        mEqAnimator2.setRepeatMode(android.animation.ValueAnimator.REVERSE);

        mEqAnimator3 = android.animation.ObjectAnimator.ofFloat(bar3, "scaleY", 0.1f, 1.0f);
        mEqAnimator3.setDuration(450);
        mEqAnimator3.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        mEqAnimator3.setRepeatMode(android.animation.ValueAnimator.REVERSE);

        mEqAnimator1.start();
        mEqAnimator2.start();
        mEqAnimator3.start();

        View container = findViewById(R.id.equalizer_container);
        if (container != null) {
            container.setVisibility(View.VISIBLE);
        }
    }

    private void stopEqualizerAnimation() {
        if (mEqAnimator1 != null) mEqAnimator1.cancel();
        if (mEqAnimator2 != null) mEqAnimator2.cancel();
        if (mEqAnimator3 != null) mEqAnimator3.cancel();
        View container = findViewById(R.id.equalizer_container);
        if (container != null) {
            container.setVisibility(View.GONE);
        }
    }

    private void updateSyncStatus(DataSnapshot snapshot) {
        if (mSyncStatusText == null) return;

        if (mIsHost) {
            mSyncStatusText.setVisibility(View.GONE);
            return;
        }

        mSyncStatusText.setVisibility(View.VISIBLE);
        if (snapshot == null || !snapshot.exists()) {
            mSyncStatusText.setText("Synced");
            mSyncStatusText.setTextColor(0xFF1DB954);
            return;
        }

        Long snapshotPositionMs = snapshot.child(FirebasePaths.CURRENT_POSITION_MS).getValue(Long.class);
        Long snapshotSystemTime = snapshot.child(FirebasePaths.LAST_UPDATED_SYSTEM_TIME).getValue(Long.class);
        String snapshotTrackUrl = snapshot.child(FirebasePaths.CURRENT_TRACK_URL).getValue(String.class);

        if (snapshotPositionMs == null || snapshotSystemTime == null || snapshotTrackUrl == null || snapshotTrackUrl.isEmpty()) {
            mSyncStatusText.setText("Synced");
            mSyncStatusText.setTextColor(0xFF1DB954);
            return;
        }

        if (mMediaController == null) return;

        long networkLatency = System.currentTimeMillis() - snapshotSystemTime;
        long targetPosition = snapshotPositionMs + networkLatency;
        long drift = Math.abs(mMediaController.getCurrentPosition() - targetPosition);

        if (drift > 1200) {
            mSyncStatusText.setText("Syncing...");
            mSyncStatusText.setTextColor(0xFFFFBF00); // Amber
        } else {
            mSyncStatusText.setText("Synced");
            mSyncStatusText.setTextColor(0xFF1DB954); // Green
        }
    }

    @Override
    protected void onStop() {
        mUpdateHandler.removeCallbacks(mUpdateProgressRunnable);

        if (mMediaController != null) {
            // Send LEAVE_ROOM custom command
            mMediaController.sendCustomCommand(new SessionCommand("LEAVE_ROOM", Bundle.EMPTY), Bundle.EMPTY);
        }

        if (mMediaControllerFuture != null) {
            MediaController.releaseFuture(mMediaControllerFuture);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // Remove Firebase Listeners
        if (mPresenceRef != null && mPresenceListener != null) {
            mPresenceRef.removeEventListener(mPresenceListener);
        }
        if (mRoomMetaRef != null && mRoomMetaListener != null) {
            mRoomMetaRef.removeEventListener(mRoomMetaListener);
        }
        if (mQueueRef != null && mQueueListener != null) {
            mQueueRef.removeEventListener(mQueueListener);
        }
        if (mChatRef != null && mChatListener != null) {
            mChatRef.removeEventListener(mChatListener);
        }
        if (mEmojiExplosionsRef != null && mEmojiExplosionsListener != null) {
            mEmojiExplosionsRef.removeEventListener(mEmojiExplosionsListener);
        }
        if (mPlaybackStateRef != null && mPlaybackStateListener != null) {
            mPlaybackStateRef.removeEventListener(mPlaybackStateListener);
        }
        if (mAuxRequestsRef != null && mAuxRequestsListener != null) {
            mAuxRequestsRef.removeEventListener(mAuxRequestsListener);
        }
        stopEqualizerAnimation();
        super.onDestroy();
    }

    @Override
    public void onVoteClick(QueueTrack track, boolean isUpvote) {
        if (mRoomId == null || track == null || track.getTrack_key() == null) return;

        DatabaseReference trackRef = mQueueRef.child(track.getTrack_key());
        trackRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.getValue() == null) {
                    return Transaction.success(currentData);
                }

                Integer upvotesObj = currentData.child(FirebasePaths.UPVOTES).getValue(Integer.class);
                Integer downvotesObj = currentData.child(FirebasePaths.DOWNVOTES).getValue(Integer.class);
                int upvotes = upvotesObj != null ? upvotesObj : 0;
                int downvotes = downvotesObj != null ? downvotesObj : 0;

                String currentVote = currentData.child(FirebasePaths.VOTED_USERS).child(mUid).getValue(String.class);
                String targetVote = isUpvote ? "up" : "down";

                if (currentVote == null) {
                    if (isUpvote) {
                        upvotes++;
                    } else {
                        downvotes++;
                    }
                    currentData.child(FirebasePaths.VOTED_USERS).child(mUid).setValue(targetVote);
                } else if (currentVote.equals(targetVote)) {
                    return Transaction.abort();
                } else {
                    if (isUpvote) {
                        upvotes++;
                        downvotes = Math.max(0, downvotes - 1);
                    } else {
                        downvotes++;
                        upvotes = Math.max(0, upvotes - 1);
                    }
                    currentData.child(FirebasePaths.VOTED_USERS).child(mUid).setValue(targetVote);
                }

                currentData.child(FirebasePaths.UPVOTES).setValue(upvotes);
                currentData.child(FirebasePaths.DOWNVOTES).setValue(downvotes);

                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {}
        });
    }

    private void handleEmojiTap(String type) {
        if ("fire".equals(type)) mLocalFireCount++;
        else if ("heart".equals(type)) mLocalHeartCount++;
        else if ("wow".equals(type)) mLocalWowCount++;

        mEmojiBatchHandler.removeCallbacks(mEmojiBatchRunnable);
        mEmojiBatchHandler.postDelayed(mEmojiBatchRunnable, 500);
        mIsEmojiTimerRunning = true;
    }

    private void flushEmojiBatches() {
        if (mRoomId == null || mEmojiExplosionsRef == null) return;
        long timestamp = System.currentTimeMillis();

        if (mLocalFireCount > 0) {
            writeEmojiBatch("fire", mLocalFireCount, timestamp);
            mLocalFireCount = 0;
        }
        if (mLocalHeartCount > 0) {
            writeEmojiBatch("heart", mLocalHeartCount, timestamp);
            mLocalHeartCount = 0;
        }
        if (mLocalWowCount > 0) {
            writeEmojiBatch("wow", mLocalWowCount, timestamp);
            mLocalWowCount = 0;
        }
    }

    private void writeEmojiBatch(String type, int count, long timestamp) {
        DatabaseReference batchRef = mEmojiExplosionsRef.push();
        java.util.HashMap<String, Object> batchMap = new java.util.HashMap<>();
        batchMap.put("emoji_type", type);
        batchMap.put("count", count);
        batchMap.put("sender_uid", mUid);
        batchMap.put("timestamp", timestamp);
        batchRef.setValue(batchMap);
    }

    private void spawnFloatingEmojis(String type, int count) {
        String emojiSymbol;
        if ("fire".equals(type)) emojiSymbol = "🔥";
        else if ("heart".equals(type)) emojiSymbol = "❤️";
        else if ("wow".equals(type)) emojiSymbol = "😮";
        else return;

        int containerWidth = mEmojiOverlayContainer.getWidth();
        int containerHeight = mEmojiOverlayContainer.getHeight();
        if (containerWidth <= 0 || containerHeight <= 0) {
            containerWidth = getResources().getDisplayMetrics().widthPixels;
            containerHeight = getResources().getDisplayMetrics().heightPixels;
        }

        java.util.Random random = new java.util.Random();

        for (int i = 0; i < count; i++) {
            if (mActiveEmojiCount >= 20) {
                break;
            }

            mActiveEmojiCount++;

            TextView emojiTv = new TextView(this);
            emojiTv.setText(emojiSymbol);
            emojiTv.setTextSize(28);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            int randomX = random.nextInt(Math.max(1, containerWidth - 120));
            emojiTv.setX(randomX);
            emojiTv.setY(containerHeight - 180);

            mEmojiOverlayContainer.addView(emojiTv, params);

            float density = getResources().getDisplayMetrics().density;
            float travelDistance = -500 * density;

            emojiTv.animate()
                    .translationYBy(travelDistance)
                    .alpha(0.0f)
                    .setDuration(1500)
                    .setListener(new android.animation.AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            mEmojiOverlayContainer.removeView(emojiTv);
                            mActiveEmojiCount = Math.max(0, mActiveEmojiCount - 1);
                        }
                    })
                    .start();
        }
    }

    private void showMembersDialog() {
        if (mRoomId == null) return;
        DatabaseReference presenceRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS)
                .child(mRoomId).child(FirebasePaths.PRESENCE);

        presenceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                final List<String> memberUids = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String uid = child.getKey();
                        if (uid != null) {
                            memberUids.add(uid);
                        }
                    }
                }

                if (memberUids.isEmpty()) {
                    Toast.makeText(RoomActivity.this, "No active members found", Toast.LENGTH_SHORT).show();
                    return;
                }

                final String[] displayNames = new String[memberUids.size()];
                final int[] resolvedCount = {0};

                for (int i = 0; i < memberUids.size(); i++) {
                    final int index = i;
                    String uid = memberUids.get(index);

                    if (uid.equals(mUid)) {
                        displayNames[index] = "You";
                        resolvedCount[0]++;
                        if (resolvedCount[0] == memberUids.size()) {
                            showResolvedMembersDialog(memberUids, displayNames);
                        }
                    } else {
                        FirebaseDatabase.getInstance().getReference("users")
                            .child(uid)
                            .child("display_name")
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                    String preferredName = userSnapshot.getValue(String.class);
                                    if (preferredName != null && !preferredName.trim().isEmpty()) {
                                        displayNames[index] = preferredName;
                                    } else {
                                        displayNames[index] = "User_" + (uid.length() >= 4 ? uid.substring(0, 4) : uid);
                                    }
                                    resolvedCount[0]++;
                                    if (resolvedCount[0] == memberUids.size()) {
                                        showResolvedMembersDialog(memberUids, displayNames);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    displayNames[index] = "User_" + (uid.length() >= 4 ? uid.substring(0, 4) : uid);
                                    resolvedCount[0]++;
                                    if (resolvedCount[0] == memberUids.size()) {
                                        showResolvedMembersDialog(memberUids, displayNames);
                                    }
                                }
                            });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(RoomActivity.this, "Failed to read members", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showResolvedMembersDialog(List<String> memberUids, String[] displayNames) {
        if (isFinishing() || isDestroyed()) return;

        final String[] itemNames = new String[displayNames.length];
        for (int i = 0; i < displayNames.length; i++) {
            String uid = memberUids.get(i);
            if (mRequestingUids.contains(uid)) {
                itemNames[i] = displayNames[i] + " ✋";
            } else {
                itemNames[i] = displayNames[i];
            }
        }

        CharSequence[] items = itemNames;
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(RoomActivity.this);
        builder.setTitle("Room DJ / Active Members");
        builder.setItems(items, (dialog, which) -> {
            String selectedUid = memberUids.get(which);
            if (selectedUid.equals(mUid)) {
                Toast.makeText(RoomActivity.this, "You are already DJ", Toast.LENGTH_SHORT).show();
                return;
            }

            if (mIsHost) {
                boolean hasRequested = mRequestingUids.contains(selectedUid);
                CharSequence[] options;
                if (hasRequested) {
                    options = new CharSequence[]{"Give AUX", "Dismiss Request", "Cancel"};
                } else {
                    options = new CharSequence[]{"Give AUX", "Cancel"};
                }

                new androidx.appcompat.app.AlertDialog.Builder(RoomActivity.this)
                        .setTitle("Manage Member: " + displayNames[which])
                        .setItems(options, (dialogInterface, index) -> {
                            if (index == 0) {
                                mRoomMetaRef.child(FirebasePaths.HOST_ID).setValue(selectedUid);
                                mAuxRequestsRef.child(selectedUid).removeValue();
                                Toast.makeText(RoomActivity.this, "Passed DJ controls!", Toast.LENGTH_SHORT).show();
                            } else if (hasRequested && index == 1) {
                                mAuxRequestsRef.child(selectedUid).removeValue();
                                Toast.makeText(RoomActivity.this, "Request dismissed", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            } else {
                Toast.makeText(RoomActivity.this, displayNames[which] + " is active in this room.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void updateMemberCountText() {
        String text = mActiveMembersCount + (mActiveMembersCount == 1 ? " Member" : " Members");
        if (mIsHost && mAuxRequestsCount > 0) {
            text += " (" + mAuxRequestsCount + " ✋)";
        }
        mMemberCountText.setText(text);
    }

    private String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds);
    }
}
