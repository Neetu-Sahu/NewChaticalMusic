package com.example.chaticalmusic;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chaticalmusic.adapter.FollowRequestAdapter;
import com.example.chaticalmusic.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class FollowRequestsActivity extends AppCompatActivity {

    private FollowRequestAdapter mAdapter;
    private TextView mNoRequestsText;
    private String mMyUid;
    private DatabaseReference mRequestsRef;
    private final List<User> mRecentlyAcceptedUsers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_follow_requests);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.requests_toolbar), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
            lp.topMargin = systemBars.top;
            v.setLayoutParams(lp);
            return insets;
        });

        mMyUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        mRequestsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.FOLLOW_REQUESTS).child(mMyUid);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.requests_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.requests_recycler);
        mNoRequestsText = findViewById(R.id.no_requests_text);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new FollowRequestAdapter(new FollowRequestAdapter.OnRequestListener() {
            @Override
            public void onAccept(User user) {
                acceptRequest(user);
            }

            @Override
            public void onDecline(User user) {
                declineRequest(user);
            }

            @Override
            public void onFollowBack(User user) {
                followBack(user);
            }
        });
        recyclerView.setAdapter(mAdapter);

        loadRequests();
    }

    private void loadRequests() {
        mRequestsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> requesterUids = new ArrayList<>();
                for (DataSnapshot reqSnap : snapshot.getChildren()) {
                    requesterUids.add(reqSnap.getKey());
                }
                
                if (requesterUids.isEmpty()) {
                    mAdapter.setRequests(new ArrayList<>());
                    mNoRequestsText.setVisibility(View.VISIBLE);
                } else {
                    fetchRequesterDetails(requesterUids);
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchRequesterDetails(List<String> uids) {
        List<User> users = new ArrayList<>();
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS);
        
        for (String uid : uids) {
            usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        user.setUid(snapshot.getKey());
                        users.add(user);
                    }
                    if (users.size() == uids.size()) {
                        mergeAndDisplay(users);
                        mNoRequestsText.setVisibility(View.GONE);
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }

    private void mergeAndDisplay(List<User> firebaseUsers) {
        List<User> displayList = new ArrayList<>(firebaseUsers);
        for (User accepted : mRecentlyAcceptedUsers) {
            boolean exists = false;
            for (User u : displayList) {
                if (u.getUid().equals(accepted.getUid())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) displayList.add(accepted);
        }
        mAdapter.setRequests(displayList);
        mNoRequestsText.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void acceptRequest(User requester) {
        DatabaseReference myFollowersRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(mMyUid).child(FirebasePaths.FOLLOWERS).child(requester.getUid());
        DatabaseReference requesterFollowingRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(requester.getUid()).child(FirebasePaths.FOLLOWING).child(mMyUid);
        
        // Add to followers/following
        myFollowersRef.setValue(com.google.firebase.database.ServerValue.TIMESTAMP);
        requesterFollowingRef.setValue(com.google.firebase.database.ServerValue.TIMESTAMP);
        
        // Remove request from Firebase but keep in list temporarily for "Follow Back"
        mRequestsRef.child(requester.getUid()).removeValue().addOnSuccessListener(aVoid -> {
            mRecentlyAcceptedUsers.add(requester);
            mAdapter.markAsAccepted(requester.getUid());
            Toast.makeText(this, "Accepted " + requester.getDisplay_name(), Toast.LENGTH_SHORT).show();
            NotificationHelper.sendNotification(this, requester.getUid(), "request_accepted");
        });
    }

    private void followBack(User user) {
        // Send a follow request back to them
        DatabaseReference targetRequestsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.FOLLOW_REQUESTS).child(user.getUid()).child(mMyUid);
        targetRequestsRef.setValue(com.google.firebase.database.ServerValue.TIMESTAMP).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Following back " + user.getDisplay_name(), Toast.LENGTH_SHORT).show();
            NotificationHelper.sendNotification(this, user.getUid(), "follow_request");
            // After follow back, we can finally remove it from our local list if we want, or just disable the button
            // For now, let's just show a toast.
        });
    }

    private void declineRequest(User requester) {
        mRequestsRef.child(requester.getUid()).removeValue();
        Toast.makeText(this, "Declined " + requester.getDisplay_name(), Toast.LENGTH_SHORT).show();
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