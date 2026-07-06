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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

public class JoinRoomActivity extends AppCompatActivity {

    private EditText mRoomInput;
    private Button mBtnJoin;
    private ProgressBar mProgressBar;
    private LoveAnimationHelper mLoveAnimationHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_join_room);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.love_animation_container), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

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

        mRoomInput = findViewById(R.id.room_input);
        mBtnJoin = findViewById(R.id.btn_join_final);
        mProgressBar = findViewById(R.id.progress_bar);

        mLoveAnimationHelper = new LoveAnimationHelper(findViewById(R.id.love_animation_container));
        mLoveAnimationHelper.start();

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        mBtnJoin.setOnClickListener(v -> {
            String input = mRoomInput.getText().toString().trim();
            if (TextUtils.isEmpty(input)) {
                Toast.makeText(this, "Please enter a room name or code", Toast.LENGTH_SHORT).show();
            } else {
                joinRoom(input);
            }
        });
    }

    private void joinRoom(String roomInput) {
        mProgressBar.setVisibility(android.view.View.VISIBLE);
        mBtnJoin.setEnabled(false);

        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS);
        
        // Try querying by room name
        Query nameQuery = roomsRef.orderByChild(FirebasePaths.ROOM_META + "/room_name").equalTo(roomInput);
        nameQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists() && dataSnapshot.hasChildren()) {
                    mProgressBar.setVisibility(android.view.View.GONE);
                    mBtnJoin.setEnabled(true);
                    for (DataSnapshot roomSnapshot : dataSnapshot.getChildren()) {
                        navigateToRoom(roomSnapshot.getKey(), roomSnapshot.child(FirebasePaths.ROOM_META + "/room_name").getValue(String.class));
                        return;
                    }
                } else {
                    // Try querying by room code
                    String upperInput = roomInput.toUpperCase().trim();
                    Query codeQuery = roomsRef.orderByChild(FirebasePaths.ROOM_META + "/" + FirebasePaths.ROOM_CODE).equalTo(upperInput);
                    codeQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot codeSnapshot) {
                            mProgressBar.setVisibility(android.view.View.GONE);
                            mBtnJoin.setEnabled(true);

                            if (codeSnapshot.exists() && codeSnapshot.hasChildren()) {
                                for (DataSnapshot roomSnapshot : codeSnapshot.getChildren()) {
                                    navigateToRoom(roomSnapshot.getKey(), roomSnapshot.child(FirebasePaths.ROOM_META + "/room_name").getValue(String.class));
                                    return;
                                }
                            } else {
                                Toast.makeText(JoinRoomActivity.this, "Room not found. Check the name or 6-digit code.", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            mProgressBar.setVisibility(android.view.View.GONE);
                            mBtnJoin.setEnabled(true);
                            Toast.makeText(JoinRoomActivity.this, "Database error", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                mProgressBar.setVisibility(android.view.View.GONE);
                mBtnJoin.setEnabled(true);
                Toast.makeText(JoinRoomActivity.this, "Database error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void navigateToRoom(String roomId, String roomName) {
        Intent intent = new Intent(JoinRoomActivity.this, RoomActivity.class);
        intent.putExtra("ROOM_ID", roomId);
        intent.putExtra("ROOM_NAME", roomName);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.stop();
        super.onDestroy();
    }
}