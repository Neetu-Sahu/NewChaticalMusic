package com.example.chaticalmusic;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chaticalmusic.adapter.ContactAdapter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class FollowListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_follow_list);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.follow_toolbar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
            lp.topMargin = systemBars.top;
            v.setLayoutParams(lp);
            return insets;
        });

        String uid = getIntent().getStringExtra("UID");
        String type = getIntent().getStringExtra("TYPE"); // "followers" or "following"
        
        if (uid == null || type == null) {
            finish();
            return;
        }

        Toolbar toolbar = findViewById(R.id.follow_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(type.substring(0, 1).toUpperCase() + type.substring(1));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.follow_list_recycler);
        TextView emptyText = findViewById(R.id.empty_follow_text);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        ContactAdapter adapter = new ContactAdapter((targetUid, name) -> {
            Intent intent = new Intent(FollowListActivity.this, ProfileActivity.class);
            intent.putExtra("TARGET_UID", targetUid);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(uid).child(type);
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> uids = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    uids.add(child.getKey());
                }
                adapter.setUids(uids);
                emptyText.setVisibility(uids.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}