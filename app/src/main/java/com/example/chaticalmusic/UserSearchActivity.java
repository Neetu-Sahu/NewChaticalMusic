package com.example.chaticalmusic;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chaticalmusic.adapter.UserSearchAdapter;
import com.example.chaticalmusic.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class UserSearchActivity extends AppCompatActivity {

    private EditText mSearchInput;
    private RecyclerView mUserRecyclerView;
    private UserSearchAdapter mUserAdapter;
    private ProgressBar mProgressBar;
    private TextView mEmptyText;
    private LoveAnimationHelper mLoveAnimationHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_user_search);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.search_toolbar), (v, insets) -> {
            androidx.core.graphics.Insets statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars());
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) v.getLayoutParams();
            lp.topMargin = statusBarInsets.top;
            v.setLayoutParams(lp);
            return insets;
        });

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.search_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mSearchInput = findViewById(R.id.search_input);
        mUserRecyclerView = findViewById(R.id.user_search_results_recycler);
        mProgressBar = findViewById(R.id.search_progress_bar);
        mEmptyText = findViewById(R.id.search_empty_text);

        mLoveAnimationHelper = new LoveAnimationHelper(findViewById(R.id.love_animation_container));

        mUserRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mUserAdapter = new UserSearchAdapter(new UserSearchAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(User user) {
                android.content.Intent intent = new android.content.Intent(UserSearchActivity.this, ProfileActivity.class);
                intent.putExtra("TARGET_UID", user.getUid());
                startActivity(intent);
            }

            @Override
            public void onFollowClick(User user) {
                toggleFollowRequest(user);
            }
        });
        mUserRecyclerView.setAdapter(mUserAdapter);

        mSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                performUserSearch(mSearchInput.getText().toString().trim());
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
                return true;
            }
            return false;
        });
        
        mSearchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (s.toString().trim().isEmpty()) {
                    mUserAdapter.setUsers(new ArrayList<>());
                    mEmptyText.setVisibility(View.GONE);
                } else {
                    performUserSearch(s.toString().trim());
                }
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

    private void performUserSearch(String query) {
        if (query.isEmpty()) return;
        mProgressBar.setVisibility(View.VISIBLE);
        mEmptyText.setVisibility(View.GONE);
        
        FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mProgressBar.setVisibility(View.GONE);
                List<User> userResults = new ArrayList<>();
                String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    if (snapshot.getKey().equals(myUid)) continue;
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        user.setUid(snapshot.getKey());
                        if ((user.getUsername() != null && user.getUsername().toLowerCase().contains(query.toLowerCase())) ||
                            (user.getDisplay_name() != null && user.getDisplay_name().toLowerCase().contains(query.toLowerCase()))) {
                            userResults.add(user);
                        }
                    }
                }
                mUserAdapter.setUsers(userResults);
                mEmptyText.setVisibility(userResults.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                mProgressBar.setVisibility(View.GONE);
            }
        });
    }

    private void toggleFollowRequest(User targetUser) {
        String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference requestRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.FOLLOW_REQUESTS)
                .child(targetUser.getUid()).child(myUid);
        
        requestRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Unsend request
                    requestRef.removeValue();
                    Toast.makeText(UserSearchActivity.this, "Request cancelled", Toast.LENGTH_SHORT).show();
                } else {
                    // Check if already following
                    FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS)
                            .child(myUid).child(FirebasePaths.FOLLOWING).child(targetUser.getUid())
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot followingSnap) {
                                    if (followingSnap.exists()) {
                                        Toast.makeText(UserSearchActivity.this, "Already following", Toast.LENGTH_SHORT).show();
                                    } else {
                                        if (targetUser.isIs_private()) {
                                            // Send request
                                            requestRef.setValue(com.google.firebase.database.ServerValue.TIMESTAMP);
                                            NotificationHelper.sendNotification(UserSearchActivity.this, targetUser.getUid(), "follow_request");
                                            Toast.makeText(UserSearchActivity.this, "Follow request sent!", Toast.LENGTH_SHORT).show();
                                        } else {
                                            // Immediate follow
                                            DatabaseReference myFollowingRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(myUid).child(FirebasePaths.FOLLOWING).child(targetUser.getUid());
                                            DatabaseReference targetFollowersRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(targetUser.getUid()).child(FirebasePaths.FOLLOWERS).child(myUid);
                                            myFollowingRef.setValue(com.google.firebase.database.ServerValue.TIMESTAMP);
                                            targetFollowersRef.setValue(com.google.firebase.database.ServerValue.TIMESTAMP);
                                            NotificationHelper.sendNotification(UserSearchActivity.this, targetUser.getUid(), "new_follower");
                                            Toast.makeText(UserSearchActivity.this, "Following!", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }
                                @Override public void onCancelled(@NonNull DatabaseError error) {}
                            });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) { 
        if (item.getItemId() == android.R.id.home) { 
            finish(); 
            return true; 
        } 
        return super.onOptionsItemSelected(item); 
    }
}