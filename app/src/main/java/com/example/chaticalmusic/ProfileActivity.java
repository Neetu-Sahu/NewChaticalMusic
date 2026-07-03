package com.example.chaticalmusic;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chaticalmusic.adapter.AvatarAdapter;
import com.example.chaticalmusic.service.MusicPlaybackService;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class ProfileActivity extends AppCompatActivity {

    private ImageView mHeaderAvatar, mMainAvatar;
    private TextView mProfileName, mProfileBio, mProfileStatus;
    private TextView mFollowersCount, mFollowingCount;
    private Button mLogoutBtn, mFollowBtn, mMessageBtn;
    private View mProfileActionsContainer;
    private RecyclerView mRecentRoomsRecycler;
    private com.example.chaticalmusic.adapter.PublicRoomsAdapter mRecentRoomsAdapter;
    
    private TextView mMiniPlayerTitle, mMiniPlayerTime;
    private MediaController mMediaController;
    private ListenableFuture<MediaController> mControllerFuture;
    private LoveAnimationHelper mLoveAnimationHelper;

    private String mMyUid;
    private String mTargetUid;
    private DatabaseReference mUserRef;
    private boolean mIsOwnProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profile_header), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
            lp.topMargin = systemBars.top;
            v.setLayoutParams(lp);
            return insets;
        });

        mMyUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        mTargetUid = getIntent().getStringExtra("TARGET_UID");
        if (mTargetUid == null) mTargetUid = mMyUid;
        
        mIsOwnProfile = mMyUid.equals(mTargetUid);
        mUserRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(mTargetUid);

        mHeaderAvatar = findViewById(R.id.header_avatar);
        mMainAvatar = findViewById(R.id.profile_main_avatar);
        mProfileName = findViewById(R.id.profile_name);
        mProfileBio = findViewById(R.id.profile_bio);
        mProfileStatus = findViewById(R.id.profile_status);
        mFollowersCount = findViewById(R.id.followers_count);
        mFollowingCount = findViewById(R.id.following_count);
        
        findViewById(R.id.followers_container).setOnClickListener(v -> {
            Intent intent = new Intent(this, FollowListActivity.class);
            intent.putExtra("UID", mTargetUid);
            intent.putExtra("TYPE", "followers");
            startActivity(intent);
        });
        
        findViewById(R.id.following_container).setOnClickListener(v -> {
            Intent intent = new Intent(this, FollowListActivity.class);
            intent.putExtra("UID", mTargetUid);
            intent.putExtra("TYPE", "following");
            startActivity(intent);
        });
        mLogoutBtn = findViewById(R.id.logout_btn);
        mFollowBtn = findViewById(R.id.btn_follow);
        mMessageBtn = findViewById(R.id.btn_message);
        mProfileActionsContainer = findViewById(R.id.profile_actions_container);
        
        mRecentRoomsRecycler = findViewById(R.id.recent_rooms_recycler);
        mRecentRoomsRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        mRecentRoomsAdapter = new com.example.chaticalmusic.adapter.PublicRoomsAdapter(item -> {
            Intent intent = new Intent(ProfileActivity.this, RoomActivity.class);
            intent.putExtra("ROOM_ID", item.getRoomId());
            intent.putExtra("ROOM_NAME", item.getRoomName());
            startActivity(intent);
        });
        mRecentRoomsRecycler.setAdapter(mRecentRoomsAdapter);

        mLoveAnimationHelper = new LoveAnimationHelper(findViewById(R.id.love_animation_container));
        mLoveAnimationHelper.start();

        mMiniPlayerTitle = findViewById(R.id.mini_player_title);
        mMiniPlayerTime = findViewById(R.id.mini_player_time);

        if (mIsOwnProfile) {
            mLogoutBtn.setVisibility(View.VISIBLE);
            mProfileActionsContainer.setVisibility(View.GONE);
            mProfileStatus.setVisibility(View.GONE);
            mProfileName.setOnClickListener(v -> showEditNameDialog());
            mProfileBio.setOnClickListener(v -> showEditBioDialog());
            mMainAvatar.setOnClickListener(v -> showAvatarSelectionDialog());
        } else {
            mLogoutBtn.setVisibility(View.GONE);
            mProfileActionsContainer.setVisibility(View.VISIBLE);
            mProfileStatus.setVisibility(View.VISIBLE);
            checkFollowStatus();
            setupOtherProfileActions();
        }

        loadUserData();
        loadFollowStats();
        loadRecentRooms();

        mLogoutBtn.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            getSharedPreferences("MusicalChatPrefs", MODE_PRIVATE).edit().clear().apply();
            Intent intent = new Intent(ProfileActivity.this, SplashActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.nav_lobby).setOnClickListener(v -> finish());
        findViewById(R.id.nav_search).setOnClickListener(v -> {
            startActivity(new Intent(this, UserSearchActivity.class));
            finish();
        });
        findViewById(R.id.nav_dm).setOnClickListener(v -> {
            startActivity(new Intent(this, DmListActivity.class));
            finish();
        });
    }

    private void showEditNameDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog bs = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_bio, null); // Reuse bio dialog layout for name
        bs.setContentView(view);

        TextView title = view.findViewById(R.id.dialog_title);
        if (title != null) title.setText("Edit Name");
        EditText input = view.findViewById(R.id.bio_input);
        input.setHint("Enter your name");
        input.setText(mProfileName.getText().toString());

        Button saveBtn = view.findViewById(R.id.btn_save_bio);
        if (saveBtn != null) saveBtn.setText("Save Name");

        saveBtn.setOnClickListener(v -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                mUserRef.child("display_name").setValue(newName);
                bs.dismiss();
            }
        });
        bs.show();
    }

    private void checkFollowStatus() {
        // 1. Check if I am following them
        FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS)
                .child(mMyUid).child(FirebasePaths.FOLLOWING).child(mTargetUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            mFollowBtn.setText("Following");
                            mFollowBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.melodify_card)));
                            mFollowBtn.setTextColor(getResources().getColor(R.color.white));
                        } else {
                            // 2. Check if I have already sent a request
                            FirebaseDatabase.getInstance().getReference(FirebasePaths.FOLLOW_REQUESTS)
                                    .child(mTargetUid).child(mMyUid)
                                    .addValueEventListener(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot reqSnap) {
                                            if (reqSnap.exists()) {
                                                mFollowBtn.setText("Requested");
                                                mFollowBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.white)));
                                                mFollowBtn.setTextColor(getResources().getColor(R.color.melodify_pink));
                                            } else {
                                                // 3. Check if they are following me (to show Follow Back)
                                                FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS)
                                                        .child(mMyUid).child(FirebasePaths.FOLLOWERS).child(mTargetUid)
                                                        .addListenerForSingleValueEvent(new ValueEventListener() {
                                                            @Override
                                                            public void onDataChange(@NonNull DataSnapshot followerSnap) {
                                                                if (followerSnap.exists()) {
                                                                    mFollowBtn.setText("Follow Back");
                                                                } else {
                                                                    mFollowBtn.setText("Follow");
                                                                }
                                                                mFollowBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.melodify_pink)));
                                                                mFollowBtn.setTextColor(getResources().getColor(R.color.melodify_background));
                                                            }
                                                            @Override public void onCancelled(@NonNull DatabaseError error) {}
                                                        });
                                            }
                                        }
                                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                                    });
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void setupOtherProfileActions() {
        mFollowBtn.setOnClickListener(v -> {
            String text = mFollowBtn.getText().toString();
            DatabaseReference myFollowingRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(mMyUid).child(FirebasePaths.FOLLOWING).child(mTargetUid);
            DatabaseReference targetFollowersRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(mTargetUid).child(FirebasePaths.FOLLOWERS).child(mMyUid);
            DatabaseReference requestRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.FOLLOW_REQUESTS).child(mTargetUid).child(mMyUid);

            if (text.equals("Following")) {
                // Unfollow
                myFollowingRef.removeValue();
                targetFollowersRef.removeValue();
                Toast.makeText(this, "Unfollowed", Toast.LENGTH_SHORT).show();
            } else if (text.equals("Requested")) {
                // Cancel request
                requestRef.removeValue();
                Toast.makeText(this, "Request cancelled", Toast.LENGTH_SHORT).show();
            } else if (text.equals("Follow Back")) {
                // Immediate follow back
                myFollowingRef.setValue(com.google.firebase.database.ServerValue.TIMESTAMP);
                targetFollowersRef.setValue(com.google.firebase.database.ServerValue.TIMESTAMP);
                
                NotificationHelper.sendNotification(this, mTargetUid, "new_follower");
                Toast.makeText(this, "Following back!", Toast.LENGTH_SHORT).show();
            } else {
                // Send Request
                requestRef.setValue(com.google.firebase.database.ServerValue.TIMESTAMP);
                NotificationHelper.sendNotification(this, mTargetUid, "follow_request");
                Toast.makeText(this, "Follow request sent!", Toast.LENGTH_SHORT).show();
            }
        });

        mMessageBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, DirectChatActivity.class);
            intent.putExtra("TARGET_UID", mTargetUid);
            intent.putExtra("TARGET_NAME", mProfileName.getText().toString());
            startActivity(intent);
        });
    }

    private void loadFollowStats() {
        FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(mTargetUid).child(FirebasePaths.FOLLOWERS)
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    mFollowersCount.setText(String.valueOf(snapshot.getChildrenCount()));
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(mTargetUid).child(FirebasePaths.FOLLOWING)
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    mFollowingCount.setText(String.valueOf(snapshot.getChildrenCount()));
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
    }

    private void loadRecentRooms() {
        mUserRef.child("recent_rooms").limitToLast(5).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<com.example.chaticalmusic.model.PublicRoomItem> recentRooms = new ArrayList<>();
                for (DataSnapshot roomSnap : snapshot.getChildren()) {
                    String roomId = roomSnap.getKey();
                    // We need to fetch room details from /rooms/<roomId>/room_meta
                    FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS).child(roomId).child(FirebasePaths.ROOM_META)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot metaSnap) {
                                    String name = metaSnap.child("room_name").getValue(String.class);
                                    if (name != null) {
                                        recentRooms.add(new com.example.chaticalmusic.model.PublicRoomItem(roomId, name, "", 0));
                                        mRecentRoomsAdapter.setRooms(recentRooms);
                                    }
                                }
                                @Override public void onCancelled(@NonNull DatabaseError error) {}
                            });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadUserData() {
        mUserRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.child("display_name").getValue(String.class);
                String bio = snapshot.child("bio").getValue(String.class);
                String photoUrl = snapshot.child("photo_url").getValue(String.class);
                Long lastActive = snapshot.child("last_active").getValue(Long.class);
                Boolean online = snapshot.child("online").getValue(Boolean.class);

                mProfileName.setText(name != null ? name : "User");
                mProfileBio.setText(bio != null ? bio : (mIsOwnProfile ? "Tap to set your melody bio..." : "No bio yet."));
                
                if (!mIsOwnProfile) {
                    if (online != null && online) {
                        mProfileStatus.setText("Online");
                        mProfileStatus.setTextColor(getResources().getColor(R.color.melodify_pink));
                    } else if (lastActive != null) {
                        mProfileStatus.setText("Last active: " + formatLastActive(lastActive));
                        mProfileStatus.setTextColor(getResources().getColor(R.color.melodify_text_secondary));
                    } else {
                        mProfileStatus.setVisibility(View.GONE);
                    }
                }

                if (!isFinishing()) {
                    Glide.with(ProfileActivity.this).load(photoUrl).placeholder(R.drawable.ic_user_placeholder).circleCrop().into(mHeaderAvatar);
                    Glide.with(ProfileActivity.this).load(photoUrl).placeholder(R.drawable.ic_user_placeholder).circleCrop().into(mMainAvatar);
                }
                
                if (mIsOwnProfile) {
                    getSharedPreferences("MusicalChatPrefs", MODE_PRIVATE).edit()
                            .putString("display_name", name)
                            .putString("photo_url", photoUrl)
                            .apply();
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private String formatLastActive(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60000) return "just now";
        if (diff < 3600000) return (diff / 60000) + "m ago";
        if (diff < 86400000) return (diff / 3600000) + "h ago";
        return (diff / 86400000) + "d ago";
    }

    private void showEditBioDialog() {
        BottomSheetDialog bs = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_bio, null);
        bs.setContentView(view);

        EditText input = view.findViewById(R.id.bio_input);
        String currentBio = mProfileBio.getText().toString();
        if (!currentBio.startsWith("Tap to set")) input.setText(currentBio);

        view.findViewById(R.id.btn_save_bio).setOnClickListener(v -> {
            String newBio = input.getText().toString().trim();
            if (!newBio.isEmpty()) {
                mUserRef.child("bio").setValue(newBio);
                bs.dismiss();
            }
        });
        bs.show();
    }

    private void showAvatarSelectionDialog() {
        BottomSheetDialog bs = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_select_avatar, null);
        bs.setContentView(view);

        RecyclerView recycler = view.findViewById(R.id.avatar_recycler);
        List<String> avatars = new ArrayList<>();
        // Using high quality diverse placeholders
        for (int i = 1; i <= 9; i++) {
            avatars.add("https://api.dicebear.com/7.x/avataaars/png?seed=" + i + "&backgroundColor=b6e3f4,c0aede,d1d4f9");
        }

        AvatarAdapter adapter = new AvatarAdapter(avatars, url -> {
            mUserRef.child("photo_url").setValue(url);
            bs.dismiss();
        });
        recycler.setAdapter(adapter);

        view.findViewById(R.id.btn_close_avatars).setOnClickListener(v -> bs.dismiss());
        bs.show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, MusicPlaybackService.class));
        mControllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        mControllerFuture.addListener(() -> {
            try {
                mMediaController = mControllerFuture.get();
                mMediaController.addListener(new Player.Listener() {
                    @Override public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) { updateMiniPlayer(); }
                    @Override public void onPlaybackStateChanged(int playbackState) { updateMiniPlayer(); }
                });
                updateMiniPlayer();
                startProgressUpdate();
            } catch (ExecutionException | InterruptedException e) { e.printStackTrace(); }
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }

    private void updateMiniPlayer() {
        if (mMediaController == null) return;
        MediaItem item = mMediaController.getCurrentMediaItem();
        if (item != null && item.mediaMetadata != null) {
            String title = item.mediaMetadata.title != null ? item.mediaMetadata.title.toString() : "Unknown";
            mMiniPlayerTitle.setText(String.format("\"%s\" — Melodify Solo", title));
        } else {
            mMiniPlayerTitle.setText("No Song Playing");
        }
    }

    private final android.os.Handler mHandler = new android.os.Handler();
    private final Runnable mProgressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mMediaController != null && mMediaController.isPlaying()) {
                long pos = mMediaController.getCurrentPosition();
                long dur = mMediaController.getDuration();
                mMiniPlayerTime.setText(String.format("%s / %s", formatTime(pos), formatTime(dur)));
            }
            mHandler.postDelayed(this, 1000);
        }
    };

    private void startProgressUpdate() { mHandler.post(mProgressRunnable); }

    private String formatTime(long ms) {
        if (ms < 0) return "00:00";
        long totalSeconds = ms / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    @Override
    protected void onStop() {
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.stop();
        if (mMediaController != null) mMediaController.release();
        if (mControllerFuture != null) MediaController.releaseFuture(mControllerFuture);
        mHandler.removeCallbacks(mProgressRunnable);
        super.onStop();
    }
}
