package com.example.chaticalmusic;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chaticalmusic.adapter.MemberAdapter;
import com.example.chaticalmusic.model.Member;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class MemberListActivity extends AppCompatActivity {

    private String mRoomId;
    private MemberAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_member_list);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.members_toolbar);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.members_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mRoomId = getIntent().getStringExtra("ROOM_ID");
        if (mRoomId == null) {
            finish();
            return;
        }

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.members_list_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new MemberAdapter(member -> {
            Intent intent = new Intent(MemberListActivity.this, ProfileActivity.class);
            intent.putExtra("TARGET_UID", member.getUid());
            startActivity(intent);
        });
        recycler.setAdapter(mAdapter);

        loadMembers();
    }

    private void loadMembers() {
        DatabaseReference presenceRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS).child(mRoomId).child(FirebasePaths.PRESENCE);
        presenceRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> uids = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (child.getKey() != null) uids.add(child.getKey());
                }
                
                List<Member> members = new ArrayList<>();
                final int[] count = {0};
                if (uids.isEmpty()) {
                    mAdapter.setMembers(members);
                    return;
                }

                for (String uid : uids) {
                    FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(uid)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot userSnap) {
                                    String name = userSnap.child("display_name").getValue(String.class);
                                    String photo = userSnap.child("photo_url").getValue(String.class);
                                    members.add(new Member(uid, name != null ? name : "User", photo, false, false, false));
                                    count[0]++;
                                    if (count[0] == uids.size()) mAdapter.setMembers(members);
                                }
                                @Override public void onCancelled(@NonNull DatabaseError error) {
                                    count[0]++;
                                    if (count[0] == uids.size()) mAdapter.setMembers(members);
                                }
                            });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}