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
import com.example.chaticalmusic.adapter.MemberAdapter;
import com.example.chaticalmusic.adapter.QueueAdapter;
import com.example.chaticalmusic.model.ChatMessage;
import com.example.chaticalmusic.model.Member;
import com.example.chaticalmusic.model.QueueTrack;
import com.example.chaticalmusic.service.MusicPlaybackService;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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
    private ImageView mHostAvatar;
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
    private TextView mTypingIndicatorText;
    private View mReplyDraftBar;
    private TextView mReplyDraftName;
    private TextView mReplyDraftText;
    private ImageButton mCancelReplyBtn;

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
    private String mPhotoUrl;
    private String mCurrentHostId;
    private boolean mIsHost;
    private boolean mIsCoDj;
    private boolean mIsSeekBarTracking = false;
    private ChatMessage mMessageToReply;

    // Media3 Playback
    private MediaController mMediaController;
    private ListenableFuture<MediaController> mMediaControllerFuture;

    // Firebase
    private DatabaseReference mRoomRef;
    private DatabaseReference mRoomMetaRef;
    private DatabaseReference mCoDjsRef;
    private DatabaseReference mChatRef;
    private DatabaseReference mQueueRef;
    private DatabaseReference mPresenceRef;
    private DatabaseReference mTypingRef;

    private ValueEventListener mRoomMetaListener;
    private ValueEventListener mPresenceListener;
    private ValueEventListener mQueueListener;
    private ValueEventListener mChatListener;
    private ValueEventListener mTypingListener;
    private ValueEventListener mCoDjsListener;

    // AUX request system
    private Button mRequestAuxBtn;
    private DatabaseReference mAuxRequestsRef;
    private ValueEventListener mAuxRequestsListener;
    private boolean mHasRequestedAux = false;
    private int mAuxRequestsCount = 0;
    private int mActiveMembersCount = 1;
    private final List<String> mRequestingUids = new ArrayList<>();
    private final List<String> mCoDjUids = new ArrayList<>();

    // Adapters
    private QueueAdapter mQueueAdapter;
    private ChatAdapter mChatAdapter;

    private final Handler mUpdateHandler = new Handler(Looper.getMainLooper());
    private final Handler mTypingHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTypingRunnable = () -> {
        if (mTypingRef != null) mTypingRef.child(mUid).setValue(false);
    };

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
        mPhotoUrl = prefs.getString("photo_url", "");

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
        mHostAvatar = findViewById(R.id.host_avatar);
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
        mTypingIndicatorText = findViewById(R.id.typing_indicator_text);
        mReplyDraftBar = findViewById(R.id.reply_draft_bar);
        mReplyDraftName = findViewById(R.id.reply_draft_name);
        mReplyDraftText = findViewById(R.id.reply_draft_text);
        mCancelReplyBtn = findViewById(R.id.cancel_reply_btn);

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
        mCoDjsRef = mRoomMetaRef.child(FirebasePaths.CO_DJS);
        mChatRef = mRoomRef.child(FirebasePaths.CHAT_MESSAGES);
        mQueueRef = mRoomRef.child(FirebasePaths.QUEUE);
        mPresenceRef = mRoomRef.child(FirebasePaths.PRESENCE);
        mEmojiExplosionsRef = mRoomRef.child(FirebasePaths.EMOJI_EXPLOSIONS);
        mPlaybackStateRef = mRoomRef.child(FirebasePaths.PLAYBACK_STATE);
        mAuxRequestsRef = mRoomRef.child(FirebasePaths.AUX_REQUESTS);
        mTypingRef = mRoomRef.child(FirebasePaths.TYPING);

        // Remove status automatically on disconnect
        mAuxRequestsRef.child(mUid).onDisconnect().removeValue();
        mTypingRef.child(mUid).onDisconnect().removeValue();
    }

    private void setupAdapters() {
        mQueueAdapter = new QueueAdapter(mUid, this);
        mQueueRecycler.setLayoutManager(new LinearLayoutManager(this));
        mQueueRecycler.setAdapter(mQueueAdapter);

        mChatAdapter = new ChatAdapter(mUid, this::onMessageLongClick);
        mChatRecycler.setLayoutManager(new LinearLayoutManager(this));
        mChatRecycler.setAdapter(mChatAdapter);
    }

    private void onMessageLongClick(ChatMessage message) {
        CharSequence[] options = new CharSequence[]{"Reply", "Mention @" + message.getSender_name()};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Message Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) startReplyMode(message);
                    else if (which == 1) startMentionMode(message);
                })
                .show();
    }

    private void startMentionMode(ChatMessage message) {
        String mention = "@" + message.getSender_name() + " ";
        mChatInput.setText(mChatInput.getText().toString() + mention);
        mChatInput.setSelection(mChatInput.getText().length());
        mChatInput.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(mChatInput, 0);
    }

    private void startReplyMode(ChatMessage message) {
        mMessageToReply = message;
        mReplyDraftName.setText("Replying to " + message.getSender_name());
        mReplyDraftText.setText(message.getMessage_text());
        mReplyDraftBar.setVisibility(View.VISIBLE);
        mChatInput.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(mChatInput, 0);
    }

    private void cancelReplyMode() {
        mMessageToReply = null;
        mReplyDraftBar.setVisibility(View.GONE);
    }

    private void setupListeners() {
        // Play / Pause Click
        mPlayPauseBtn.setOnClickListener(v -> {
            if (mMediaController != null && (mIsHost || mIsCoDj)) {
                if (mMediaController.isPlaying()) mMediaController.pause();
                else mMediaController.play();
            }
        });

        // Skip Click
        mSkipBtn.setOnClickListener(v -> {
            if (mMediaController != null && (mIsHost || mIsCoDj)) {
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

        mCancelReplyBtn.setOnClickListener(v -> cancelReplyMode());

        // Member list click
        mMemberCountText.setOnClickListener(v -> showMembersDialog());

        // Request AUX click listener
        mRequestAuxBtn.setOnClickListener(v -> {
            DatabaseReference reqRef = mAuxRequestsRef.child(mUid);
            if (mHasRequestedAux) {
                reqRef.removeValue().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) Toast.makeText(RoomActivity.this, "AUX request canceled", Toast.LENGTH_SHORT).show();
                });
            } else {
                java.util.Map<String, Object> requestData = new java.util.HashMap<>();
                requestData.put("uid", mUid);
                requestData.put("display_name", mDisplayName);
                reqRef.setValue(requestData).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) Toast.makeText(RoomActivity.this, "Hand raised! Requesting AUX...", Toast.LENGTH_SHORT).show();
                });
            }
        });

        // Seek Bar listener
        mTrackSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && (mIsHost || mIsCoDj)) {
                    if (mTimeElapsedText != null) mTimeElapsedText.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mIsSeekBarTracking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mIsSeekBarTracking = false;
                if (mMediaController != null && (mIsHost || mIsCoDj)) {
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
                if (mHasRequestedAux) {
                    mRequestAuxBtn.setText("AUX Requested \ud83d\ude4b");
                    mRequestAuxBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x66FFFFFF));
                } else {
                    mRequestAuxBtn.setText("Request AUX");
                    mRequestAuxBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1DB954));
                }
                updateMemberCountText();
                mRequestingUids.clear();
                for (DataSnapshot child : snapshot.getChildren()) mRequestingUids.add(child.getKey());
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mAuxRequestsRef.addValueEventListener(mAuxRequestsListener);

        // 2. Room Meta / Host Listener
        mRoomMetaListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String hostId = snapshot.child(FirebasePaths.HOST_ID).getValue(String.class);
                    Boolean needsNewHost = snapshot.child(FirebasePaths.NEEDS_NEW_HOST).getValue(Boolean.class);
                    String roomCode = snapshot.child(FirebasePaths.ROOM_CODE).getValue(String.class);
                    mRoomCodeText.setText("Code: " + (roomCode != null ? roomCode : "None"));
                    
                    if (hostId != null) {
                        mCurrentHostId = hostId;
                        mIsHost = mUid.equals(hostId);
                        updateControlsVisibility();
                        mRequestAuxBtn.setVisibility(mIsHost ? View.GONE : View.VISIBLE);
                        mAuxRequestsRef.child(hostId).removeValue();
                        updateMemberCountText();

                        FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(hostId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                                    String name = userSnapshot.child("display_name").getValue(String.class);
                                    String photo = userSnapshot.child("photo_url").getValue(String.class);
                                    mHostNameSubtitle.setText("DJ: " + (name != null ? name : "User_" + hostId.substring(0, 4)));
                                    Glide.with(RoomActivity.this).load(photo).placeholder(R.drawable.ic_music_placeholder).circleCrop().into(mHostAvatar);
                                }
                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {}
                            });

                        mHostWarningBanner.setVisibility(needsNewHost != null && needsNewHost ? View.VISIBLE : View.GONE);
                        if (needsNewHost != null && needsNewHost) mHostControlsContainer.setVisibility(View.GONE);
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
                for (DataSnapshot child : snapshot.getChildren()) {
                    QueueTrack track = child.getValue(QueueTrack.class);
                    if (track != null) {
                        track.setTrack_key(child.getKey());
                        tracks.add(track);
                    }
                }
                java.util.Collections.sort(tracks, (o1, o2) -> Long.compare(o1.getAdded_at(), o2.getAdded_at()));
                mQueueEmptyBanner.setVisibility(tracks.isEmpty() ? View.VISIBLE : View.GONE);
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
                for (DataSnapshot child : snapshot.getChildren()) {
                    ChatMessage msg = child.getValue(ChatMessage.class);
                    if (msg != null) messages.add(msg);
                }
                mChatAdapter.setMessages(messages);
                if (mChatAdapter.getItemCount() > 0) mChatRecycler.scrollToPosition(mChatAdapter.getItemCount() - 1);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mChatRef.limitToLast(50).addValueEventListener(mChatListener);

        // 5. Emoji Explosions Listener
        mEmojiExplosionsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String type = snapshot.child("emoji_type").getValue(String.class);
                Integer count = snapshot.child("count").getValue(Integer.class);
                Long ts = snapshot.child("timestamp").getValue(Long.class);
                if (ts != null && System.currentTimeMillis() - ts < 5000) spawnFloatingEmojis(type, count != null ? count : 0);
            }
            @Override public void onChildChanged(@NonNull DataSnapshot s, @Nullable String p) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot s) {}
            @Override public void onChildMoved(@NonNull DataSnapshot s, @Nullable String p) {}
            @Override public void onCancelled(@NonNull DatabaseError e) {}
        };
        mEmojiExplosionsRef.limitToLast(10).addChildEventListener(mEmojiExplosionsListener);

        // 6. Playback State Listener
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

        // 7. Typing Indicator Listener
        mTypingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> typingUids = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean isTyping = child.getValue(Boolean.class);
                    if (isTyping != null && isTyping && !child.getKey().equals(mUid)) typingUids.add(child.getKey());
                }
                fetchTypingUserNames(typingUids);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mTypingRef.addValueEventListener(mTypingListener);

        // 8. Co-DJs Listener
        mCoDjsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mCoDjUids.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean isCoDj = child.getValue(Boolean.class);
                    if (isCoDj != null && isCoDj) mCoDjUids.add(child.getKey());
                }
                mIsCoDj = mCoDjUids.contains(mUid);
                updateControlsVisibility();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mCoDjsRef.addValueEventListener(mCoDjsListener);

        mChatInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mTypingRef != null) {
                    mTypingRef.child(mUid).setValue(true);
                    mTypingHandler.removeCallbacks(mTypingRunnable);
                    mTypingHandler.postDelayed(mTypingRunnable, 3000);
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void updateControlsVisibility() {
        boolean hasControl = mIsHost || mIsCoDj;
        mTrackSeekBar.setEnabled(hasControl);
        mTrackSeekBar.setAlpha(hasControl ? 1.0f : 0.4f);
        mHostControlsContainer.setVisibility(hasControl ? View.VISIBLE : View.GONE);
    }

    private void fetchTypingUserNames(List<String> uids) {
        if (uids.isEmpty()) { mTypingIndicatorText.setVisibility(View.GONE); return; }
        final List<String> names = new ArrayList<>();
        final int[] count = {0};
        for (String uid : uids) {
            FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(uid).child("display_name")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String n = snapshot.getValue(String.class);
                        names.add(n != null ? n : "Someone");
                        count[0]++;
                        if (count[0] == uids.size()) showTypingText(names);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) { count[0]++; }
                });
        }
    }

    private void showTypingText(List<String> names) {
        StringBuilder sb = new StringBuilder();
        if (names.size() == 1) sb.append(names.get(0)).append(" is typing...");
        else if (names.size() == 2) sb.append(names.get(0)).append(" and ").append(names.get(1)).append(" are typing...");
        else sb.append(names.size()).append(" people are typing...");
        mTypingIndicatorText.setText(sb.toString());
        mTypingIndicatorText.setVisibility(View.VISIBLE);
    }

    private void sendChatMessage(String text) {
        if (!TextUtils.isEmpty(text)) {
            ChatMessage msg = mMessageToReply != null ? 
                new ChatMessage(mUid, mDisplayName, mPhotoUrl, text, System.currentTimeMillis(), true, mMessageToReply.getMessage_text(), mMessageToReply.getSender_name()) :
                new ChatMessage(mUid, mDisplayName, mPhotoUrl, text, System.currentTimeMillis());
            mChatRef.push().setValue(msg);
            mChatInput.setText("");
            cancelReplyMode();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken token = new SessionToken(this, new ComponentName(this, MusicPlaybackService.class));
        mMediaControllerFuture = new MediaController.Builder(this, token).buildAsync();
        mMediaControllerFuture.addListener(() -> {
            try {
                mMediaController = mMediaControllerFuture.get();
                onMediaControllerConnected();
            } catch (ExecutionException | InterruptedException e) { e.printStackTrace(); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void onMediaControllerConnected() {
        Bundle args = new Bundle();
        args.putString("ROOM_ID", mRoomId);
        args.putString("UID", mUid);
        mMediaController.sendCustomCommand(new SessionCommand("JOIN_ROOM", Bundle.EMPTY), args);
        mMediaController.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) { updatePlaybackUi(); }
            @Override public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) { updatePlaybackUi(); }
        });
        updatePlaybackUi();
        mUpdateHandler.post(mUpdateProgressRunnable);
    }

    private void updatePlaybackUi() {
        if (mMediaController == null) return;
        mPlayPauseBtn.setImageResource(mMediaController.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        if (mMediaController.isPlaying()) startEqualizerAnimation(); else stopEqualizerAnimation();
        MediaItem item = mMediaController.getCurrentMediaItem();
        if (item != null && item.mediaMetadata != null) {
            mTrackTitle.setText(item.mediaMetadata.title != null ? item.mediaMetadata.title : "Unknown Title");
            mTrackArtist.setText(item.mediaMetadata.artist != null ? item.mediaMetadata.artist : "Unknown Artist");
            Glide.with(this).load(item.mediaMetadata.artworkUri).placeholder(R.drawable.ic_music_placeholder).transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade()).into(mTrackArt);
            long d = mMediaController.getDuration();
            mTrackSeekBar.setMax(d > 0 ? (int) d : 0);
            mTrackSeekBar.setProgress((int) mMediaController.getCurrentPosition());
            if (mTimeElapsedText != null) mTimeElapsedText.setText(formatTime(mMediaController.getCurrentPosition()));
            if (mTimeDurationText != null) mTimeDurationText.setText(formatTime(d > 0 ? d : 0));
        } else {
            mTrackTitle.setText("No Song Playing");
            mTrackArtist.setText("Choose a song to start");
            mTrackArt.setImageResource(R.drawable.ic_music_placeholder);
            mTrackSeekBar.setProgress(0);
        }
    }

    private void startEqualizerAnimation() {
        View b1 = findViewById(R.id.eq_bar1), b2 = findViewById(R.id.eq_bar2), b3 = findViewById(R.id.eq_bar3);
        if (b1 == null || (mEqAnimator1 != null && mEqAnimator1.isRunning())) return;
        b1.post(() -> b1.setPivotY(b1.getHeight())); b2.post(() -> b2.setPivotY(b2.getHeight())); b3.post(() -> b3.setPivotY(b3.getHeight()));
        mEqAnimator1 = android.animation.ObjectAnimator.ofFloat(b1, "scaleY", 0.2f, 1.0f); mEqAnimator1.setDuration(400); mEqAnimator1.setRepeatCount(-1); mEqAnimator1.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        mEqAnimator2 = android.animation.ObjectAnimator.ofFloat(b2, "scaleY", 0.3f, 1.0f); mEqAnimator2.setDuration(550); mEqAnimator2.setRepeatCount(-1); mEqAnimator2.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        mEqAnimator3 = android.animation.ObjectAnimator.ofFloat(b3, "scaleY", 0.1f, 1.0f); mEqAnimator3.setDuration(450); mEqAnimator3.setRepeatCount(-1); mEqAnimator3.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        mEqAnimator1.start(); mEqAnimator2.start(); mEqAnimator3.start();
        findViewById(R.id.equalizer_container).setVisibility(View.VISIBLE);
    }

    private void stopEqualizerAnimation() {
        if (mEqAnimator1 != null) { mEqAnimator1.cancel(); mEqAnimator2.cancel(); mEqAnimator3.cancel(); }
        if (findViewById(R.id.equalizer_container) != null) findViewById(R.id.equalizer_container).setVisibility(View.GONE);
    }

    private void updateSyncStatus(DataSnapshot snapshot) {
        if (mSyncStatusText == null || mIsHost || snapshot == null || !snapshot.exists()) {
            if (mSyncStatusText != null) mSyncStatusText.setVisibility(mIsHost ? View.GONE : View.VISIBLE);
            return;
        }
        Long pos = snapshot.child(FirebasePaths.CURRENT_POSITION_MS).getValue(Long.class);
        Long st = snapshot.child(FirebasePaths.LAST_UPDATED_SYSTEM_TIME).getValue(Long.class);
        if (pos == null || st == null || mMediaController == null) return;
        long target = pos + (System.currentTimeMillis() - st);
        long drift = Math.abs(mMediaController.getCurrentPosition() - target);
        mSyncStatusText.setText(drift > 1200 ? "Syncing..." : "Synced");
        mSyncStatusText.setTextColor(drift > 1200 ? 0xFFFFBF00 : 0xFF1DB954);
    }

    @Override
    protected void onStop() {
        mUpdateHandler.removeCallbacks(mUpdateProgressRunnable);
        if (mMediaController != null) mMediaController.sendCustomCommand(new SessionCommand("LEAVE_ROOM", Bundle.EMPTY), Bundle.EMPTY);
        if (mMediaControllerFuture != null) MediaController.releaseFuture(mMediaControllerFuture);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mPresenceRef != null) mPresenceRef.removeEventListener(mPresenceListener);
        if (mRoomMetaRef != null) mRoomMetaRef.removeEventListener(mRoomMetaListener);
        if (mQueueRef != null) mQueueRef.removeEventListener(mQueueListener);
        if (mChatRef != null) mChatRef.removeEventListener(mChatListener);
        if (mEmojiExplosionsRef != null) mEmojiExplosionsRef.removeEventListener(mEmojiExplosionsListener);
        if (mPlaybackStateRef != null) mPlaybackStateRef.removeEventListener(mPlaybackStateListener);
        if (mAuxRequestsRef != null) mAuxRequestsRef.removeEventListener(mAuxRequestsListener);
        if (mTypingRef != null) mTypingRef.removeEventListener(mTypingListener);
        if (mCoDjsRef != null) mCoDjsRef.removeEventListener(mCoDjsListener);
        stopEqualizerAnimation();
        super.onDestroy();
    }

    @Override
    public void onVoteClick(QueueTrack track, boolean isUpvote) {
        if (mRoomId == null || track == null || track.getTrack_key() == null) return;
        mQueueRef.child(track.getTrack_key()).runTransaction(new Transaction.Handler() {
            @NonNull @Override public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                if (currentData.getValue() == null) return Transaction.success(currentData);
                int up = currentData.child(FirebasePaths.UPVOTES).getValue(Integer.class) != null ? currentData.child(FirebasePaths.UPVOTES).getValue(Integer.class) : 0;
                int down = currentData.child(FirebasePaths.DOWNVOTES).getValue(Integer.class) != null ? currentData.child(FirebasePaths.DOWNVOTES).getValue(Integer.class) : 0;
                String currentVote = currentData.child(FirebasePaths.VOTED_USERS).child(mUid).getValue(String.class);
                String targetVote = isUpvote ? "up" : "down";
                if (currentVote == null) { if (isUpvote) up++; else down++; currentData.child(FirebasePaths.VOTED_USERS).child(mUid).setValue(targetVote); }
                else if (currentVote.equals(targetVote)) return Transaction.abort();
                else { if (isUpvote) { up++; down = Math.max(0, down - 1); } else { down++; up = Math.max(0, up - 1); } currentData.child(FirebasePaths.VOTED_USERS).child(mUid).setValue(targetVote); }
                currentData.child(FirebasePaths.UPVOTES).setValue(up); currentData.child(FirebasePaths.DOWNVOTES).setValue(down);
                return Transaction.success(currentData);
            }
            @Override public void onComplete(@Nullable DatabaseError e, boolean c, @Nullable DataSnapshot d) {}
        });
    }

    private void handleEmojiTap(String type) {
        if ("fire".equals(type)) mLocalFireCount++; else if ("heart".equals(type)) mLocalHeartCount++; else if ("wow".equals(type)) mLocalWowCount++;
        mEmojiBatchHandler.removeCallbacks(mEmojiBatchRunnable); mEmojiBatchHandler.postDelayed(mEmojiBatchRunnable, 500); mIsEmojiTimerRunning = true;
    }

    private void flushEmojiBatches() {
        if (mRoomId == null || mEmojiExplosionsRef == null) return;
        long ts = System.currentTimeMillis();
        if (mLocalFireCount > 0) { writeEmojiBatch("fire", mLocalFireCount, ts); mLocalFireCount = 0; }
        if (mLocalHeartCount > 0) { writeEmojiBatch("heart", mLocalHeartCount, ts); mLocalHeartCount = 0; }
        if (mLocalWowCount > 0) { writeEmojiBatch("wow", mLocalWowCount, ts); mLocalWowCount = 0; }
    }

    private void writeEmojiBatch(String type, int count, long timestamp) {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>(); map.put("emoji_type", type); map.put("count", count); map.put("sender_uid", mUid); map.put("timestamp", timestamp);
        mEmojiExplosionsRef.push().setValue(map);
    }

    private void spawnFloatingEmojis(String type, int count) {
        String symbol = "fire".equals(type) ? "\ud83d\udd25" : "heart".equals(type) ? "\u2764\ufe0f" : "\ud83d\ude2e";
        int w = mEmojiOverlayContainer.getWidth(), h = mEmojiOverlayContainer.getHeight();
        if (w <= 0) { w = getResources().getDisplayMetrics().widthPixels; h = getResources().getDisplayMetrics().heightPixels; }
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < count; i++) {
            if (mActiveEmojiCount >= 20) break;
            mActiveEmojiCount++; TextView tv = new TextView(this); tv.setText(symbol); tv.setTextSize(28);
            tv.setX(rnd.nextInt(Math.max(1, w - 120))); tv.setY(h - 180);
            mEmojiOverlayContainer.addView(tv);
            tv.animate().translationYBy(-500 * getResources().getDisplayMetrics().density).alpha(0.0f).setDuration(1500).setListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator a) { mEmojiOverlayContainer.removeView(tv); mActiveEmojiCount = Math.max(0, mActiveEmojiCount - 1); }
            }).start();
        }
    }

    private void showMembersDialog() {
        BottomSheetDialog bs = new BottomSheetDialog(this, R.style.FullScreenBottomSheetTheme);
        View v = getLayoutInflater().inflate(R.layout.dialog_members, null); bs.setContentView(v);
        RecyclerView r = v.findViewById(R.id.members_recycler); v.findViewById(R.id.close_members_btn).setOnClickListener(view -> bs.dismiss());
        MemberAdapter adapter = new MemberAdapter(m -> {
            if (m.getUid().equals(mUid)) Toast.makeText(this, "You are the DJ", Toast.LENGTH_SHORT).show();
            else if (mIsHost) showMemberManagementDialog(m, bs);
            else Toast.makeText(this, m.getDisplayName() + " is listening.", Toast.LENGTH_SHORT).show();
        });
        r.setAdapter(adapter);
        mPresenceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> uids = new ArrayList<>(); for (DataSnapshot c : snapshot.getChildren()) if (c.getKey() != null) uids.add(c.getKey());
                List<Member> list = new ArrayList<>(); int[] cnt = {0};
                for (String uid : uids) {
                    FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                            String name = userSnapshot.child("display_name").getValue(String.class);
                            String photo = userSnapshot.child("photo_url").getValue(String.class);
                            list.add(new Member(uid, uid.equals(mUid) ? "You (" + name + ")" : (name != null ? name : "User_" + uid.substring(0,4)), photo, uid.equals(mCurrentHostId), mCoDjUids.contains(uid), mRequestingUids.contains(uid)));
                            if (++cnt[0] == uids.size()) adapter.setMembers(list);
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) { if (++cnt[0] == uids.size()) adapter.setMembers(list); }
                    });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
        bs.show();
    }

    private void showMemberManagementDialog(Member member, BottomSheetDialog parent) {
        List<String> opts = new ArrayList<>(); opts.add("Give DJ / Host");
        if (member.isCoDj()) opts.add("Remove Co-DJ"); else opts.add("Make Co-DJ");
        if (member.hasRequestedAux()) opts.add("Dismiss Request"); opts.add("Cancel");
        final CharSequence[] items = opts.toArray(new CharSequence[0]);
        new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Manage " + member.getDisplayName()).setItems(items, (dialog, which) -> {
            String opt = items[which].toString();
            if (opt.equals("Give DJ / Host")) { mRoomMetaRef.child(FirebasePaths.HOST_ID).setValue(member.getUid()); mAuxRequestsRef.child(member.getUid()).removeValue(); mCoDjsRef.child(member.getUid()).removeValue(); parent.dismiss(); }
            else if (opt.equals("Make Co-DJ")) { mCoDjsRef.child(member.getUid()).setValue(true); mAuxRequestsRef.child(member.getUid()).removeValue(); parent.dismiss(); }
            else if (opt.equals("Remove Co-DJ")) { mCoDjsRef.child(member.getUid()).removeValue(); parent.dismiss(); }
            else if (opt.equals("Dismiss Request")) { mAuxRequestsRef.child(member.getUid()).removeValue(); parent.dismiss(); }
        }).show();
    }

    private void updateMemberCountText() {
        String text = mActiveMembersCount + (mActiveMembersCount == 1 ? " Member" : " Members");
        if (mIsHost && mAuxRequestsCount > 0) text += " (" + mAuxRequestsCount + " \ud83d\ude4b)";
        mMemberCountText.setText(text);
    }

    private String formatTime(long ms) {
        long s = ms / 1000; return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60);
    }
}
