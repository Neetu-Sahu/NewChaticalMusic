package com.example.chaticalmusic;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chaticalmusic.adapter.ConversationAdapter;
import com.example.chaticalmusic.model.Conversation;
import com.example.chaticalmusic.model.NotificationModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.widget.Toast;
import android.content.SharedPreferences;

public class DmListActivity extends AppCompatActivity {

    private RecyclerView mRecyclerView;
    private ConversationAdapter mAdapter;
    private TextView mNoDmsText;
    private TextView mRequestsBadge;
    private String mMyUid;
    private DatabaseReference mConvRef;
    private final List<Conversation> mConversations = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dm_list);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dm_toolbar), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
            lp.topMargin = systemBars.top;
            v.setLayoutParams(lp);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottom_nav_container), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });

        mMyUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        mConvRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.CONVERSATIONS).child(mMyUid);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.dm_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        mRecyclerView = findViewById(R.id.dm_list_recycler);
        mNoDmsText = findViewById(R.id.no_dms_text);
        mRequestsBadge = findViewById(R.id.requests_badge);

        findViewById(R.id.btn_requests).setOnClickListener(v -> {
            startActivity(new Intent(this, FollowRequestsActivity.class));
        });

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new ConversationAdapter(conversation -> {
            Intent intent = new Intent(DmListActivity.this, DirectChatActivity.class);
            intent.putExtra("TARGET_UID", conversation.getTarget_uid());
            intent.putExtra("TARGET_NAME", conversation.getTarget_name());
            startActivity(intent);
        }, this::showConversationOptions);
        mRecyclerView.setAdapter(mAdapter);

        loadConversations();
        loadRequestBadge();
        setupNotificationListener();

        findViewById(R.id.nav_lobby).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        findViewById(R.id.nav_search).setOnClickListener(v -> {
            startActivity(new Intent(this, UserSearchActivity.class));
            finish();
        });
        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
            finish();
        });
    }

    private void loadConversations() {
        mConvRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mConversations.clear();
                for (DataSnapshot convSnap : snapshot.getChildren()) {
                    Conversation conv = convSnap.getValue(Conversation.class);
                    if (conv != null) {
                        mConversations.add(conv);
                    }
                }
                
                // Sort by timestamp descending
                Collections.sort(mConversations, (o1, o2) -> Long.compare(o2.getLast_timestamp(), o1.getLast_timestamp()));
                
                mAdapter.setConversations(mConversations);
                mNoDmsText.setVisibility(mConversations.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadRequestBadge() {
        FirebaseDatabase.getInstance().getReference(FirebasePaths.FOLLOW_REQUESTS)
                .child(mMyUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        long count = snapshot.getChildrenCount();
                        if (count > 0) {
                            mRequestsBadge.setVisibility(View.VISIBLE);
                            mRequestsBadge.setText(String.valueOf(count));
                        } else {
                            mRequestsBadge.setVisibility(View.GONE);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void setupNotificationListener() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        DatabaseReference notificationsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.NOTIFICATIONS).child(uid);
        notificationsRef.addChildEventListener(new com.google.firebase.database.ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @androidx.annotation.Nullable String previousChildName) {
                NotificationModel notification = snapshot.getValue(NotificationModel.class);
                if (notification != null && !notification.isRead()) {
                    if ("follow_request".equals(notification.getType())) {
                        showFriendRequestPopup(notification, snapshot.getRef());
                    } else if ("request_accepted".equals(notification.getType())) {
                        Toast.makeText(DmListActivity.this, "Request accepted by " + notification.getSender_name(), Toast.LENGTH_SHORT).show();
                        snapshot.getRef().child("read").setValue(true);
                    }
                }
            }
            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @androidx.annotation.Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @androidx.annotation.Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showFriendRequestPopup(NotificationModel notification, DatabaseReference ref) {
        new AlertDialog.Builder(this)
                .setTitle("New Friend Request")
                .setMessage(notification.getSender_name() + " sent you a friend request!")
                .setPositiveButton("View", (d, which) -> {
                    ref.child("read").setValue(true);
                    startActivity(new Intent(this, FollowRequestsActivity.class));
                })
                .setNegativeButton("Ignore", (d, which) -> ref.child("read").setValue(true))
                .show();
    }

    private void showConversationOptions(Conversation conversation) {
        long mutedUntil = conversation.getMuted_until();
        boolean isMuted = (mutedUntil == -1 || (mutedUntil > 0 && mutedUntil > System.currentTimeMillis()));
        String muteText = isMuted ? "Unmute Notifications" : "Mute Notifications";

        String[] options = {muteText, "Delete Chat"};
        new AlertDialog.Builder(this)
                .setTitle(conversation.getTarget_name())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (isMuted) {
                            unmuteConversation(conversation);
                        } else {
                            showMuteDialog(conversation);
                        }
                    } else if (which == 1) {
                        showDeleteChatDialog(conversation);
                    }
                })
                .show();
    }

    private void unmuteConversation(Conversation conversation) {
        mConvRef.child(conversation.getTarget_uid()).child(FirebasePaths.MUTED_UNTIL).setValue(0);
        SharedPreferences.Editor editor = getSharedPreferences("MutedChats", MODE_PRIVATE).edit();
        editor.remove(conversation.getTarget_uid());
        editor.apply();
        Toast.makeText(this, "Unmuted", Toast.LENGTH_SHORT).show();
    }

    private void showMuteDialog(Conversation conversation) {
        String[] options = {"8 Hours", "24 Hours", "1 Week", "Until Turned Off", "Unmute"};
        new AlertDialog.Builder(this)
                .setTitle("Mute Notifications")
                .setItems(options, (dialog, which) -> {
                    long until = 0;
                    long now = System.currentTimeMillis();
                    switch (which) {
                        case 0: until = now + (8 * 3600 * 1000); break;
                        case 1: until = now + (24 * 3600 * 1000); break;
                        case 2: until = now + (7 * 24 * 3600 * 1000); break;
                        case 3: until = -1; break;
                        case 4: until = 0; break;
                    }
                    
                    mConvRef.child(conversation.getTarget_uid()).child(FirebasePaths.MUTED_UNTIL).setValue(until);
                    
                    // Cache locally for FCM service
                    SharedPreferences.Editor editor = getSharedPreferences("MutedChats", MODE_PRIVATE).edit();
                    if (until == 0) editor.remove(conversation.getTarget_uid());
                    else editor.putLong(conversation.getTarget_uid(), until);
                    editor.apply();

                    Toast.makeText(this, until == 0 ? "Unmuted" : "Muted", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showDeleteChatDialog(Conversation conversation) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Chat?")
                .setMessage("This will remove the conversation from your list.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    mConvRef.child(conversation.getTarget_uid()).removeValue();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
