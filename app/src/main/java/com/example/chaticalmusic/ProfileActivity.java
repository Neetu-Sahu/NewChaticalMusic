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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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
    private TextView mProfileName, mRhythmScore, mSharedHours, mProfileBio;
    private Button mLogoutBtn;
    private View mEditBioBtn, mEditAvatarBtn;
    
    private TextView mMiniPlayerTitle, mMiniPlayerTime;
    private MediaController mMediaController;
    private ListenableFuture<MediaController> mControllerFuture;
    private LoveAnimationHelper mLoveAnimationHelper;

    private String mUid;
    private DatabaseReference mUserRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        mUserRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(mUid);

        mHeaderAvatar = findViewById(R.id.header_avatar);
        mMainAvatar = findViewById(R.id.profile_main_avatar);
        mProfileName = findViewById(R.id.profile_name);
        mRhythmScore = findViewById(R.id.rhythm_score_text);
        mSharedHours = findViewById(R.id.shared_hours_text);
        mProfileBio = findViewById(R.id.profile_bio);
        mLogoutBtn = findViewById(R.id.logout_btn);
        
        mEditBioBtn = mProfileBio; // Tapping bio edits it
        mEditAvatarBtn = findViewById(R.id.profile_main_avatar).getParent() instanceof View ? (View)findViewById(R.id.profile_main_avatar).getParent() : findViewById(R.id.profile_main_avatar);

        mLoveAnimationHelper = new LoveAnimationHelper(findViewById(R.id.love_animation_container));
        mLoveAnimationHelper.start();

        mMiniPlayerTitle = findViewById(R.id.mini_player_title);
        mMiniPlayerTime = findViewById(R.id.mini_player_time);

        loadUserData();

        mEditBioBtn.setOnClickListener(v -> showEditBioDialog());
        mMainAvatar.setOnClickListener(v -> showAvatarSelectionDialog());

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
            startActivity(new Intent(this, SearchActivity.class));
            finish();
        });
        findViewById(R.id.nav_rooms).setOnClickListener(v -> finish());
    }

    private void loadUserData() {
        mUserRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String name = snapshot.child("display_name").getValue(String.class);
                String bio = snapshot.child("bio").getValue(String.class);
                String photoUrl = snapshot.child("photo_url").getValue(String.class);
                Long score = snapshot.child("rhythm_score").getValue(Long.class);
                Double hours = snapshot.child("shared_hours").getValue(Double.class);

                mProfileName.setText(name != null ? name : "User");
                mProfileBio.setText(bio != null ? bio : "Tap to set your melody bio...");
                
                if (score != null) mRhythmScore.setText(java.text.NumberFormat.getIntegerInstance().format(score));
                if (hours != null) mSharedHours.setText(String.format(Locale.getDefault(), "%.0fh", hours));

                Glide.with(ProfileActivity.this).load(photoUrl).placeholder(R.drawable.ic_user_placeholder).circleCrop().into(mHeaderAvatar);
                Glide.with(ProfileActivity.this).load(photoUrl).placeholder(R.drawable.ic_user_placeholder).circleCrop().into(mMainAvatar);
                
                // Save locally too
                getSharedPreferences("MusicalChatPrefs", MODE_PRIVATE).edit()
                        .putString("display_name", name)
                        .putString("photo_url", photoUrl)
                        .apply();
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
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
