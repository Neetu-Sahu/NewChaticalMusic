package com.example.chaticalmusic;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chaticalmusic.model.PlaybackState;
import com.example.chaticalmusic.model.Room;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class CreateRoomActivity extends AppCompatActivity {

    private EditText mRoomNameInput;
    private SwitchCompat mPrivateRoomSwitch;
    private Button mBtnCreate;
    private ProgressBar mProgressBar;
    private LoveAnimationHelper mLoveAnimationHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_room);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.love_animation_container), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0); // Background can start from top
            return insets;
        });
        
        // Find back button container or first visible item
        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            ViewCompat.setOnApplyWindowInsetsListener(btnBack, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
                lp.topMargin = systemBars.top + (int)(16 * getResources().getDisplayMetrics().density);
                v.setLayoutParams(lp);
                return insets;
            });
        }


        mRoomNameInput = findViewById(R.id.room_name_input);
        mPrivateRoomSwitch = findViewById(R.id.private_room_switch);
        mBtnCreate = findViewById(R.id.btn_create_final);
        mProgressBar = findViewById(R.id.progress_bar);

        mLoveAnimationHelper = new LoveAnimationHelper(findViewById(R.id.love_animation_container));
        mLoveAnimationHelper.start();

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        mBtnCreate.setOnClickListener(v -> {
            String roomName = mRoomNameInput.getText().toString().trim();
            if (TextUtils.isEmpty(roomName)) {
                Toast.makeText(this, "Please enter a room name", Toast.LENGTH_SHORT).show();
            } else {
                createRoom(roomName, mPrivateRoomSwitch.isChecked());
            }
        });
    }

    private void createRoom(String roomName, boolean isPrivate) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS);
        String roomId = roomsRef.push().getKey();

        if (roomId == null) {
            Toast.makeText(this, "Error generating Room ID", Toast.LENGTH_SHORT).show();
            return;
        }

        mProgressBar.setVisibility(android.view.View.VISIBLE);
        mBtnCreate.setEnabled(false);

        String roomCode = generateRoomCode();
        Room roomMeta = new Room(roomName, uid, isPrivate, System.currentTimeMillis(), false, roomCode);
        PlaybackState playbackState = new PlaybackState(false, 0L, System.currentTimeMillis(), "", "", "", "");

        Map<String, Object> roomData = new HashMap<>();
        roomData.put(FirebasePaths.ROOM_META, roomMeta);
        roomData.put(FirebasePaths.PLAYBACK_STATE, playbackState);

        roomsRef.child(roomId).setValue(roomData).addOnCompleteListener(task -> {
            mProgressBar.setVisibility(android.view.View.GONE);
            mBtnCreate.setEnabled(true);

            if (task.isSuccessful()) {
                Intent intent = new Intent(CreateRoomActivity.this, RoomActivity.class);
                intent.putExtra("ROOM_ID", roomId);
                intent.putExtra("ROOM_NAME", roomName);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Failed to create room", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString().toUpperCase();
    }

    @Override
    protected void onDestroy() {
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.stop();
        super.onDestroy();
    }
}