package com.example.chaticalmusic;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chaticalmusic.model.PlaybackState;
import com.example.chaticalmusic.model.Room;
import com.example.chaticalmusic.model.PublicRoomItem;
import com.example.chaticalmusic.adapter.PublicRoomsAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.ChildEventListener;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private EditText mRoomNameInput;
    private Button mCreateRoomBtn;
    private Button mJoinRoomBtn;
    private Button mSearchMusicBtn;
    private android.widget.ProgressBar mProgressBar;
    private androidx.appcompat.widget.SwitchCompat mPrivateRoomSwitch;

    // Public Rooms List Fields
    private RecyclerView mPublicRoomsRecycler;
    private android.widget.TextView mNoPublicRoomsText;
    private PublicRoomsAdapter mPublicRoomsAdapter;
    private final List<PublicRoomItem> mPublicRoomsList = new ArrayList<>();
    private DatabaseReference mRoomsRef;
    private ChildEventListener mRoomsChildListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mRoomNameInput = findViewById(R.id.room_name_input);
        mCreateRoomBtn = findViewById(R.id.create_room_btn);
        mJoinRoomBtn = findViewById(R.id.join_room_btn);
        mSearchMusicBtn = findViewById(R.id.search_music_btn);
        mProgressBar = findViewById(R.id.main_progress_bar);
        mPrivateRoomSwitch = findViewById(R.id.private_room_switch);

        // Create Room Flow
        mCreateRoomBtn.setOnClickListener(v -> {
            String roomName = mRoomNameInput.getText().toString().trim();
            if (TextUtils.isEmpty(roomName)) {
                Toast.makeText(MainActivity.this, "Please enter a room name", Toast.LENGTH_SHORT).show();
            } else {
                createRoom(roomName, mPrivateRoomSwitch.isChecked());
            }
        });

        // Join Room Flow
        mJoinRoomBtn.setOnClickListener(v -> {
            String roomName = mRoomNameInput.getText().toString().trim();
            if (TextUtils.isEmpty(roomName)) {
                Toast.makeText(MainActivity.this, "Please enter a room name to join", Toast.LENGTH_SHORT).show();
            } else {
                joinRoom(roomName);
            }
        });

        // Search Music action
        mSearchMusicBtn.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SearchActivity.class));
        });

        // Public Rooms RecyclerView Setup
        mPublicRoomsRecycler = findViewById(R.id.public_rooms_recycler);
        mNoPublicRoomsText = findViewById(R.id.no_public_rooms_text);

        mPublicRoomsAdapter = new PublicRoomsAdapter(item -> {
            Intent intent = new Intent(MainActivity.this, RoomActivity.class);
            intent.putExtra("ROOM_ID", item.getRoomId());
            intent.putExtra("ROOM_NAME", item.getRoomName());
            startActivity(intent);
        });

        mPublicRoomsRecycler.setLayoutManager(new LinearLayoutManager(this));
        mPublicRoomsRecycler.setAdapter(mPublicRoomsAdapter);

        updateEmptyText();
        setupPublicRoomsListener();
    }

    private void setLoadingState(boolean isLoading) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(isLoading ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        mCreateRoomBtn.setEnabled(!isLoading);
        mJoinRoomBtn.setEnabled(!isLoading);
    }

    private void createRoom(String roomName, boolean isPrivate) {
        if (!isNetworkAvailable()) {
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("Connection Error")
                .setMessage("Could not connect — check your internet.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Authentication error. Please restart the app.", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS);
        String roomId = roomsRef.push().getKey();

        if (roomId == null) {
            Toast.makeText(this, "Error generating Room ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show centered circular ProgressBar and disable buttons
        setLoadingState(true);

        // Generate 6-character alphanumeric code
        String roomCode = generateRoomCode();

        // Create models
        Room roomMeta = new Room(roomName, uid, isPrivate, System.currentTimeMillis(), false, roomCode);
        PlaybackState playbackState = new PlaybackState(false, 0L, System.currentTimeMillis(), "", "", "", "");

        // Set values together
        Map<String, Object> roomData = new HashMap<>();
        roomData.put(FirebasePaths.ROOM_META, roomMeta);
        roomData.put(FirebasePaths.PLAYBACK_STATE, playbackState);

        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final boolean[] completed = {false};

        Runnable timeoutRunnable = () -> {
            completed[0] = true;
            setLoadingState(false);
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("Connection Error")
                .setMessage("Could not connect — check your internet.")
                .setPositiveButton("OK", null)
                .show();
        };
        handler.postDelayed(timeoutRunnable, 5000);

        roomsRef.child(roomId).setValue(roomData).addOnCompleteListener(task -> {
            if (completed[0]) return;
            completed[0] = true;
            handler.removeCallbacks(timeoutRunnable);
            setLoadingState(false);

            if (task.isSuccessful()) {
                Intent intent = new Intent(MainActivity.this, RoomActivity.class);
                intent.putExtra("ROOM_ID", roomId);
                intent.putExtra("ROOM_NAME", roomName);
                startActivity(intent);
            } else {
                Toast.makeText(MainActivity.this, "Failed to create room", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager = 
                (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.net.Network network = connectivityManager.getActiveNetwork();
                if (network != null) {
                    android.net.NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                    return capabilities != null && (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || 
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
                }
            } else {
                android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        }
        return false;
    }

    private void joinRoom(String roomInput) {
        if (!isNetworkAvailable()) {
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("Connection Error")
                .setMessage("Could not connect — check your internet.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        // Show centered circular ProgressBar and disable buttons
        setLoadingState(true);

        DatabaseReference roomsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS);
        
        // 1. Try querying by room name first
        Query nameQuery = roomsRef.orderByChild(FirebasePaths.ROOM_META + "/room_name").equalTo(roomInput);

        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final boolean[] completed = {false};

        Runnable timeoutRunnable = () -> {
            if (completed[0]) return;
            completed[0] = true;
            setLoadingState(false);
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("Connection Error")
                .setMessage("Could not connect — check your internet.")
                .setPositiveButton("OK", null)
                .show();
        };
        handler.postDelayed(timeoutRunnable, 5000);

        nameQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (completed[0]) return;

                if (dataSnapshot.exists() && dataSnapshot.hasChildren()) {
                    completed[0] = true;
                    handler.removeCallbacks(timeoutRunnable);
                    setLoadingState(false);
                    for (DataSnapshot roomSnapshot : dataSnapshot.getChildren()) {
                        String roomId = roomSnapshot.getKey();
                        String actualRoomName = roomSnapshot.child(FirebasePaths.ROOM_META + "/room_name").getValue(String.class);
                        Intent intent = new Intent(MainActivity.this, RoomActivity.class);
                        intent.putExtra("ROOM_ID", roomId);
                        intent.putExtra("ROOM_NAME", actualRoomName != null ? actualRoomName : roomInput);
                        startActivity(intent);
                        return;
                    }
                } else {
                    // 2. Try querying by room code next
                    Query codeQuery = roomsRef.orderByChild(FirebasePaths.ROOM_META + "/" + FirebasePaths.ROOM_CODE).equalTo(roomInput.toUpperCase().trim());
                    codeQuery.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot codeSnapshot) {
                            if (completed[0]) return;
                            completed[0] = true;
                            handler.removeCallbacks(timeoutRunnable);
                            setLoadingState(false);

                            if (codeSnapshot.exists() && codeSnapshot.hasChildren()) {
                                for (DataSnapshot roomSnapshot : codeSnapshot.getChildren()) {
                                    String roomId = roomSnapshot.getKey();
                                    String actualRoomName = roomSnapshot.child(FirebasePaths.ROOM_META + "/room_name").getValue(String.class);
                                    Intent intent = new Intent(MainActivity.this, RoomActivity.class);
                                    intent.putExtra("ROOM_ID", roomId);
                                    intent.putExtra("ROOM_NAME", actualRoomName != null ? actualRoomName : roomInput);
                                    startActivity(intent);
                                    return;
                                }
                            } else {
                                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Room Not Found")
                                    .setMessage("No room found with that name or code. Check and try again.")
                                    .setPositiveButton("OK", null)
                                    .show();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError databaseError) {
                            if (completed[0]) return;
                            completed[0] = true;
                            handler.removeCallbacks(timeoutRunnable);
                            setLoadingState(false);
                            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                                .setTitle("Connection Error")
                                .setMessage("Could not connect — check your internet.")
                                .setPositiveButton("OK", null)
                                .show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                if (completed[0]) return;
                completed[0] = true;
                handler.removeCallbacks(timeoutRunnable);
                setLoadingState(false);
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("Connection Error")
                    .setMessage("Could not connect — check your internet.")
                    .setPositiveButton("OK", null)
                    .show();
            }
        });
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        java.util.Random rnd = new java.util.Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void setupPublicRoomsListener() {
        mRoomsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS);
        mRoomsChildListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                processRoomSnapshot(snapshot);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                processRoomSnapshot(snapshot);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String roomId = snapshot.getKey();
                if (roomId != null) {
                    removeRoomById(roomId);
                }
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mRoomsRef.addChildEventListener(mRoomsChildListener);
    }

    private void processRoomSnapshot(DataSnapshot snapshot) {
        String roomId = snapshot.getKey();
        if (roomId == null) return;

        Boolean isPrivate = snapshot.child(FirebasePaths.ROOM_META + "/is_private").getValue(Boolean.class);
        if (isPrivate != null && isPrivate) {
            // Room is private, so remove it if it was previously public
            removeRoomById(roomId);
            return;
        }

        String roomName = snapshot.child(FirebasePaths.ROOM_META + "/room_name").getValue(String.class);
        if (roomName == null || roomName.isEmpty()) {
            removeRoomById(roomId);
            return;
        }

        String songTitle = snapshot.child(FirebasePaths.PLAYBACK_STATE + "/current_track_title").getValue(String.class);
        int memberCount = (int) snapshot.child(FirebasePaths.PRESENCE).getChildrenCount();

        // Update or add in list
        boolean found = false;
        for (int i = 0; i < mPublicRoomsList.size(); i++) {
            PublicRoomItem item = mPublicRoomsList.get(i);
            if (item.getRoomId().equals(roomId)) {
                item.setRoomName(roomName);
                item.setCurrentTrackTitle(songTitle);
                item.setMemberCount(memberCount);
                found = true;
                break;
            }
        }

        if (!found) {
            mPublicRoomsList.add(new PublicRoomItem(roomId, roomName, songTitle, memberCount));
        }

        mPublicRoomsAdapter.setRooms(mPublicRoomsList);
        updateEmptyText();
    }

    private void removeRoomById(String roomId) {
        for (int i = 0; i < mPublicRoomsList.size(); i++) {
            if (mPublicRoomsList.get(i).getRoomId().equals(roomId)) {
                mPublicRoomsList.remove(i);
                mPublicRoomsAdapter.setRooms(mPublicRoomsList);
                updateEmptyText();
                return;
            }
        }
    }

    private void updateEmptyText() {
        if (mNoPublicRoomsText != null) {
            mNoPublicRoomsText.setVisibility(mPublicRoomsList.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        if (mRoomsRef != null && mRoomsChildListener != null) {
            mRoomsRef.removeEventListener(mRoomsChildListener);
        }
        super.onDestroy();
    }
}