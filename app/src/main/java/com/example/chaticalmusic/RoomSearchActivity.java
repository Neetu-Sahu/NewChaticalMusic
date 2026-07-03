package com.example.chaticalmusic;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chaticalmusic.adapter.PublicRoomsAdapter;
import com.example.chaticalmusic.model.PublicRoomItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class RoomSearchActivity extends AppCompatActivity {

    private EditText mSearchInput;
    private RecyclerView mRecyclerView;
    private PublicRoomsAdapter mAdapter;
    private ProgressBar mProgressBar;
    private TextView mEmptyText;
    private LoveAnimationHelper mLoveAnimationHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_room_search);

        View root = findViewById(R.id.search_root);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.search_toolbar), (v, insets) -> {
            androidx.core.graphics.Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
            lp.topMargin = statusBarInsets.top;
            v.setLayoutParams(lp);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.search_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mSearchInput = findViewById(R.id.search_input);
        mRecyclerView = findViewById(R.id.room_search_results_recycler);
        mProgressBar = findViewById(R.id.search_progress_bar);
        mEmptyText = findViewById(R.id.search_empty_text);

        mLoveAnimationHelper = new LoveAnimationHelper(findViewById(R.id.love_animation_container));

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new PublicRoomsAdapter(item -> {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS)
                    .child(uid).child("recent_rooms").child(item.getRoomId()).setValue(com.google.firebase.database.ServerValue.TIMESTAMP);

            Intent intent = new Intent(RoomSearchActivity.this, RoomActivity.class);
            intent.putExtra("ROOM_ID", item.getRoomId());
            intent.putExtra("ROOM_NAME", item.getRoomName());
            startActivity(intent);
        });
        mRecyclerView.setAdapter(mAdapter);

        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                performRoomSearch(s.toString().trim());
            }
        });

        // Initial search to show some rooms
        performRoomSearch("");
    }

    private void performRoomSearch(String query) {
        mProgressBar.setVisibility(View.VISIBLE);
        FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mProgressBar.setVisibility(View.GONE);
                List<PublicRoomItem> roomResults = new ArrayList<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    String roomName = snapshot.child(FirebasePaths.ROOM_META + "/room_name").getValue(String.class);
                    if (roomName != null && (query.isEmpty() || roomName.toLowerCase().contains(query.toLowerCase()))) {
                        Boolean isPrivate = snapshot.child(FirebasePaths.ROOM_META + "/is_private").getValue(Boolean.class);
                        if (isPrivate != null && isPrivate) continue;
                        
                        String songTitle = snapshot.child(FirebasePaths.PLAYBACK_STATE + "/current_track_title").getValue(String.class);
                        int memberCount = (int) snapshot.child(FirebasePaths.PRESENCE).getChildrenCount();
                        roomResults.add(new PublicRoomItem(snapshot.getKey(), roomName, songTitle, memberCount));
                    }
                }
                
                if (roomResults.isEmpty()) {
                    mRecyclerView.setVisibility(View.GONE);
                    mEmptyText.setVisibility(View.VISIBLE);
                    mEmptyText.setText(query.isEmpty() ? "No rooms available" : "No results for \"" + query + "\"");
                } else {
                    mRecyclerView.setVisibility(View.VISIBLE);
                    mEmptyText.setVisibility(View.GONE);
                    mAdapter.setRooms(roomResults);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                mProgressBar.setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.start();
    }

    @Override
    protected void onStop() {
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.stop();
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}