package com.example.chaticalmusic;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chaticalmusic.adapter.QueueAdapter;
import com.example.chaticalmusic.model.QueueTrack;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class RoomQueueActivity extends AppCompatActivity {

    private String mRoomId, mUid;
    private DatabaseReference mQueueRef;
    private QueueAdapter mAdapter;
    private TextView mEmptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_room_queue);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.queue_toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.queue_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mRoomId = getIntent().getStringExtra("ROOM_ID");
        mUid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (mRoomId == null) {
            finish();
            return;
        }

        mQueueRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS).child(mRoomId).child(FirebasePaths.QUEUE);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        mEmptyText = findViewById(R.id.no_queue_text);
        RecyclerView recycler = findViewById(R.id.queue_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new QueueAdapter(mUid, (track, isUpvote) -> {
            // Re-implement vote logic or call RoomActivity static method
        });
        recycler.setAdapter(mAdapter);

        findViewById(R.id.btn_add_song).setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, SearchActivity.class);
            intent.putExtra("ROOM_ID", mRoomId);
            startActivity(intent);
        });

        loadQueue();
    }

    private void loadQueue() {
        mQueueRef.addValueEventListener(new ValueEventListener() {
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
                mAdapter.setTracks(tracks);
                mEmptyText.setVisibility(tracks.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}