package com.example.chaticalmusic;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chaticalmusic.adapter.ChatAdapter;
import com.example.chaticalmusic.model.ChatMessage;
import com.example.chaticalmusic.model.NotificationModel;
import com.example.chaticalmusic.model.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DirectChatActivity extends AppCompatActivity {

    private String mMyUid;
    private String mTargetUid;
    private String mChatId;
    private String mMyName, mMyPhoto;

    private RecyclerView mRecyclerView;
    private ChatAdapter mAdapter;
    private EditText mInput;
    private ImageButton mSendBtn;
    private TextView mChatName, mChatStatus;
    private ImageView mChatAvatar;
    private View mTypingIndicator;

    private View mReplyDraftBar;
    private TextView mReplyDraftName, mReplyDraftText;
    private ImageButton mCancelReplyBtn;
    private ChatMessage mMessageToReply;

    private View mSearchContainer;
    private EditText mSearchInput;
    private TextView mSearchCountText, mSearchNoResults;
    private ImageButton mBtnPrevSearch, mBtnNextSearch, mBtnCloseSearch;
    private int mCurrentSearchIndex = -1;

    private boolean mIsBlocked = false;
    private boolean mAmIBlocked = false;
    private long mMuteUntil = 0;

    private DatabaseReference mChatRef, mStatusRef, mMyTypingRef, mTargetTypingRef, mMyConvRef, mTargetConvRef;
    private ChildEventListener mChatListener;
    private ValueEventListener mStatusListener, mTargetTypingListener;
    private final List<ChatMessage> mMessages = new ArrayList<>();
    private LoveAnimationHelper mLoveAnimationHelper;

    private final android.os.Handler mTypingHandler = new android.os.Handler();
    private final Runnable mTypingRunnable = () -> {
        if (mMyTypingRef != null) mMyTypingRef.setValue(false);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_direct_chat);

        mMyUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        mTargetUid = getIntent().getStringExtra("TARGET_UID");
        String targetName = getIntent().getStringExtra("TARGET_NAME");

        if (mTargetUid == null) {
            finish();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("MusicalChatPrefs", MODE_PRIVATE);
        mMyName = prefs.getString("display_name", "Me");
        mMyPhoto = prefs.getString("photo_url", "");

        // Fix padding for edge-to-edge
        View root = findViewById(R.id.chat_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Generate Chat ID
        if (mMyUid.compareTo(mTargetUid) < 0) {
            mChatId = mMyUid + "_" + mTargetUid;
        } else {
            mChatId = mTargetUid + "_" + mMyUid;
        }

        mChatRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.DIRECT_MESSAGES).child(mChatId).child(FirebasePaths.MESSAGES);
        mMyConvRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.CONVERSATIONS).child(mMyUid).child(mTargetUid);
        mTargetConvRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.CONVERSATIONS).child(mTargetUid).child(mMyUid);

        mChatName = findViewById(R.id.chat_name);
        mChatStatus = findViewById(R.id.chat_status);
        mChatAvatar = findViewById(R.id.chat_avatar);
        mRecyclerView = findViewById(R.id.direct_chat_recycler);
        mInput = findViewById(R.id.chat_input);
        mSendBtn = findViewById(R.id.chat_send_btn);
        mTypingIndicator = findViewById(R.id.typing_indicator_container);
        mReplyDraftBar = findViewById(R.id.reply_draft_bar);
        mReplyDraftName = findViewById(R.id.reply_draft_name);
        mReplyDraftText = findViewById(R.id.reply_draft_text);
        mCancelReplyBtn = findViewById(R.id.cancel_reply_btn);

        mSearchContainer = findViewById(R.id.search_container);
        mSearchInput = findViewById(R.id.search_input);
        mSearchCountText = findViewById(R.id.search_count_text);
        mSearchNoResults = findViewById(R.id.search_no_results);
        mBtnPrevSearch = findViewById(R.id.btn_prev_search);
        mBtnNextSearch = findViewById(R.id.btn_next_search);
        mBtnCloseSearch = findViewById(R.id.btn_close_search);

        setupSearchListeners();
        setupHeaderClick();
        loadWallpaper();
        checkBlockStatus();
        checkMuteStatus();

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.chat_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        mLoveAnimationHelper = new LoveAnimationHelper(findViewById(R.id.love_animation_container));
        mLoveAnimationHelper.start();

        mChatName.setText(targetName != null ? targetName : "User");
        setupTargetUserInfo();

        mAdapter = new ChatAdapter(mMyUid, this::onMessageLongClick, null, this::onReplyClick);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.setAdapter(mAdapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new SwipeToReplyCallback(this, position -> {
            Object item = mAdapter.getItem(position);
            if (item instanceof ChatMessage) {
                startReply((ChatMessage) item);
            }
        }));
        itemTouchHelper.attachToRecyclerView(mRecyclerView);

        mSendBtn.setOnClickListener(v -> sendMessage());
        mCancelReplyBtn.setOnClickListener(v -> cancelReply());

        View inputContainer = findViewById(R.id.bottom_input_container);
        ViewCompat.setOnApplyWindowInsetsListener(inputContainer, (v, insets) -> {
            androidx.core.graphics.Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), Math.max(imeInsets.bottom, systemBars.bottom));
            return insets;
        });

        setupChatListener();
        setupStatusListener();
        setupTypingListeners();
        markAsRead();
        setupNotificationListener();

        findViewById(R.id.chat_header_info).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.putExtra("TARGET_UID", mTargetUid);
            startActivity(intent);
        });
    }

    private void markAsRead() {
        mMyConvRef.child(FirebasePaths.UNREAD_COUNT).setValue(0);
        mChatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot msgSnap : snapshot.getChildren()) {
                    ChatMessage msg = msgSnap.getValue(ChatMessage.class);
                    if (msg != null && !msg.getSender_uid().equals(mMyUid) && msg.getStatus() != ChatMessage.STATUS_READ) {
                        msgSnap.getRef().child("status").setValue(ChatMessage.STATUS_READ);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupTypingListeners() {
        mInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mIsBlocked || mAmIBlocked) return;
                if (mMyTypingRef != null) {
                    mMyTypingRef.setValue(true);
                    mTypingHandler.removeCallbacks(mTypingRunnable);
                    mTypingHandler.postDelayed(mTypingRunnable, 2000);
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        mMyTypingRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.DIRECT_MESSAGES).child(mChatId).child(FirebasePaths.TYPING).child(mMyUid);
        mTargetTypingRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.DIRECT_MESSAGES).child(mChatId).child(FirebasePaths.TYPING).child(mTargetUid);
        mMyTypingRef.onDisconnect().removeValue();

        mTargetTypingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (mIsBlocked || mAmIBlocked) {
                    mTypingIndicator.setVisibility(View.GONE);
                    return;
                }
                Boolean isTyping = snapshot.getValue(Boolean.class);
                mTypingIndicator.setVisibility(isTyping != null && isTyping ? View.VISIBLE : View.GONE);
                if (isTyping != null && isTyping) {
                    mRecyclerView.scrollToPosition(mAdapter.getItemCount() - 1);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        mTargetTypingRef.addValueEventListener(mTargetTypingListener);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_clear_chat) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Clear Chat")
                    .setMessage("Are you sure you want to clear all messages? This cannot be undone.")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        mChatRef.removeValue();
                        mMyConvRef.child(FirebasePaths.LAST_MESSAGE).setValue("Chat cleared");
                        mMessages.clear();
                        mAdapter.setMessages(mMessages);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        } else if (id == R.id.action_search) {
            mSearchContainer.setVisibility(View.VISIBLE);
            showKeyboard();
            return true;
        } else if (id == R.id.action_mute) {
            showMuteDialog();
            return true;
        } else if (id == R.id.action_block) {
            showBlockDialog();
            return true;
        } else if (id == R.id.action_share_profile) {
            shareProfile();
            return true;
        } else if (id == R.id.action_wallpaper) {
            showWallpaperDialog();
            return true;
        } else if (id == R.id.action_delete_chat) {
            showDeleteChatDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupTargetUserInfo() {
        FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(mTargetUid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.child("display_name").getValue(String.class);
                        String photo = snapshot.child("photo_url").getValue(String.class);
                        if (name != null) mChatName.setText(name);
                        Glide.with(DirectChatActivity.this).load(photo).placeholder(R.drawable.ic_user_placeholder).circleCrop().into(mChatAvatar);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void setupStatusListener() {
        mStatusRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(mTargetUid);
        mStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Boolean online = snapshot.child(FirebasePaths.ONLINE).getValue(Boolean.class);
                Long lastActive = snapshot.child(FirebasePaths.LAST_ACTIVE).getValue(Long.class);

                if (online != null && online) {
                    mChatStatus.setText("Online");
                    mChatStatus.setTextColor(getResources().getColor(R.color.melodify_pink));
                } else if (lastActive != null) {
                    mChatStatus.setText(formatLastActive(lastActive));
                    mChatStatus.setTextColor(getResources().getColor(R.color.melodify_text_secondary));
                } else {
                    mChatStatus.setText("Offline");
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        mStatusRef.addValueEventListener(mStatusListener);
    }

    private String formatLastActive(long timestamp) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar active = java.util.Calendar.getInstance();
        active.setTimeInMillis(timestamp);

        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        String time = timeFormat.format(new Date(timestamp));

        if (now.get(java.util.Calendar.DATE) == active.get(java.util.Calendar.DATE)) {
            return "Last seen today at " + time;
        } else if (now.get(java.util.Calendar.DATE) - active.get(java.util.Calendar.DATE) == 1) {
            return "Last seen yesterday at " + time;
        } else {
            return "Last seen on " + new SimpleDateFormat("MMM d", Locale.getDefault()).format(new Date(timestamp));
        }
    }

    private void onMessageLongClick(ChatMessage message) {
        if (message.isIs_deleted()) return;

        List<String> options = new ArrayList<>();
        options.add("Reply");
        options.add("React ❤️");
        options.add("React 😂");
        options.add("React 👍");
        options.add("Copy Text");
        options.add("Delete for Me");
        if (message.getSender_uid().equals(mMyUid)) {
            options.add("Delete for Everyone");
        }

        new AlertDialog.Builder(this)
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String choice = options.get(which);
                    if (choice.equals("Reply")) {
                        startReply(message);
                    } else if (choice.startsWith("React")) {
                        addReaction(message, choice.split(" ")[1]);
                    } else if (choice.equals("Copy Text")) {
                        copyToClipboard(message.getMessage_text());
                    } else if (choice.equals("Delete for Me")) {
                        showDeleteForMeDialog(message);
                    } else if (choice.equals("Delete for Everyone")) {
                        showDeleteForEveryoneDialog(message);
                    }
                }).show();
    }

    private void copyToClipboard(String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Melodify Message", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show();
    }

    private void showDeleteForMeDialog(ChatMessage message) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Message?")
                .setMessage("This message will only be removed from your chat.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    mChatRef.child(message.getMessage_id()).child("deleted_for").child(mMyUid).setValue(true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteForEveryoneDialog(ChatMessage message) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Message for Everyone?")
                .setMessage("This message will be removed for all participants.")
                .setPositiveButton("Delete for Everyone", (dialog, which) -> {
                    mChatRef.child(message.getMessage_id()).child("is_deleted").setValue(true);
                    mChatRef.child(message.getMessage_id()).child("message_text").setValue("");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addReaction(ChatMessage message, String emoji) {
        mChatRef.child(message.getMessage_id()).child("reactions").child(mMyUid).setValue(emoji);
    }

    private void onReplyClick(ChatMessage message) {
        for (int i = 0; i < mMessages.size(); i++) {
            if (mMessages.get(i).getMessage_text().equals(message.getReplied_to_text())) {
                mRecyclerView.smoothScrollToPosition(i);
                
                final int finalI = i;
                mRecyclerView.postDelayed(() -> {
                    RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(finalI);
                    if (holder instanceof ChatAdapter.ChatViewHolder) {
                        View bubble = ((ChatAdapter.ChatViewHolder) holder).bubble;
                        bubble.animate().alpha(0.5f).setDuration(200).withEndAction(() -> 
                            bubble.animate().alpha(1.0f).setDuration(200).start()
                        ).start();
                    }
                }, 300);
                break;
            }
        }
    }

    private void setupChatListener() {
        mChatListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                ChatMessage msg = snapshot.getValue(ChatMessage.class);
                if (msg != null) {
                    msg.setMessage_id(snapshot.getKey());
                    mMessages.add(msg);
                    mAdapter.setMessages(mMessages);
                    mRecyclerView.smoothScrollToPosition(mMessages.size() - 1);
                    
                    if (!msg.getSender_uid().equals(mMyUid) && msg.getStatus() != ChatMessage.STATUS_READ) {
                        snapshot.getRef().child("status").setValue(ChatMessage.STATUS_READ);
                    }
                }
            }
            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                ChatMessage updatedMsg = snapshot.getValue(ChatMessage.class);
                if (updatedMsg != null) {
                    updatedMsg.setMessage_id(snapshot.getKey());
                    for (int i = 0; i < mMessages.size(); i++) {
                        if (mMessages.get(i).getMessage_id().equals(updatedMsg.getMessage_id())) {
                            mMessages.set(i, updatedMsg);
                            mAdapter.setMessages(mMessages);
                            break;
                        }
                    }
                }
            }
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        mChatRef.addChildEventListener(mChatListener);

        // Add Swipe to Reply
        new androidx.recyclerview.widget.ItemTouchHelper(new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
            @Override public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) { return false; }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                Object item = mAdapter.getItem(position);
                if (item instanceof ChatMessage) {
                    startReply((ChatMessage) item);
                }
                mAdapter.notifyItemChanged(position);
            }
        }).attachToRecyclerView(mRecyclerView);
    }

    private void startReply(ChatMessage message) {
        mMessageToReply = message;
        mReplyDraftBar.setVisibility(View.VISIBLE);
        mReplyDraftName.setText(message.getSender_uid().equals(mMyUid) ? "You" : message.getSender_name());
        mReplyDraftText.setText(message.getMessage_text());
        mInput.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(mInput, 0);
    }

    private void cancelReply() {
        mMessageToReply = null;
        mReplyDraftBar.setVisibility(View.GONE);
    }

    private void sendMessage() {
        if (mIsBlocked || mAmIBlocked) {
            Toast.makeText(this, "You cannot send messages to this user", Toast.LENGTH_SHORT).show();
            return;
        }
        String text = mInput.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        long now = System.currentTimeMillis();
        ChatMessage msg;
        if (mMessageToReply != null) {
            msg = new ChatMessage(mMyUid, mMyName, mMyPhoto, text, now, true, mMessageToReply.getMessage_text(), mMessageToReply.getSender_name());
        } else {
            msg = new ChatMessage(mMyUid, mMyName, mMyPhoto, text, now);
        }
        
        String msgKey = mChatRef.push().getKey();
        if (msgKey != null) {
            mChatRef.child(msgKey).setValue(msg);
        }
        
        updateConversation(text, now);
        cancelReply();
        mInput.setText("");
    }

    private void updateConversation(String lastMsg, long timestamp) {
        Map<String, Object> myConv = new HashMap<>();
        myConv.put("last_message", lastMsg);
        myConv.put("last_timestamp", timestamp);
        myConv.put("target_uid", mTargetUid);
        myConv.put("target_name", mChatName.getText().toString());
        mMyConvRef.updateChildren(myConv);

        Map<String, Object> targetConv = new HashMap<>();
        targetConv.put("last_message", lastMsg);
        targetConv.put("last_timestamp", timestamp);
        targetConv.put("target_uid", mMyUid);
        targetConv.put("target_name", mMyName);
        targetConv.put("target_photo", mMyPhoto);
        
        mTargetConvRef.updateChildren(targetConv);
        mTargetConvRef.child("unread_count").runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @NonNull
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(@NonNull com.google.firebase.database.MutableData currentData) {
                Integer count = currentData.getValue(Integer.class);
                if (count == null) {
                    currentData.setValue(1);
                } else {
                    currentData.setValue(count + 1);
                }
                return com.google.firebase.database.Transaction.success(currentData);
            }
            @Override public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {}
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        ChaticalApplication.CURRENT_CHAT_ID = mChatId;
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.start();
        markAsRead();
    }

    @Override
    protected void onStop() {
        ChaticalApplication.CURRENT_CHAT_ID = null;
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.stop();
        super.onStop();
    }

    private void setupSearchListeners() {
        mSearchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        mBtnPrevSearch.setOnClickListener(v -> {
            List<Integer> matches = mAdapter.getMatchPositions();
            if (matches.isEmpty()) return;
            mCurrentSearchIndex = (mCurrentSearchIndex - 1 + matches.size()) % matches.size();
            updateSearchNavigation();
        });

        mBtnNextSearch.setOnClickListener(v -> {
            List<Integer> matches = mAdapter.getMatchPositions();
            if (matches.isEmpty()) return;
            mCurrentSearchIndex = (mCurrentSearchIndex + 1) % matches.size();
            updateSearchNavigation();
        });

        mBtnCloseSearch.setOnClickListener(v -> {
            mSearchContainer.setVisibility(View.GONE);
            mSearchInput.setText("");
            mAdapter.setSearchQuery("", -1);
            mCurrentSearchIndex = -1;
            hideKeyboard();
        });
    }

    private void performSearch(String query) {
        if (query.isEmpty()) {
            mAdapter.setSearchQuery("", -1);
            mSearchCountText.setVisibility(View.GONE);
            mSearchNoResults.setVisibility(View.GONE);
            mCurrentSearchIndex = -1;
            return;
        }

        mAdapter.setSearchQuery(query, -1);
        List<Integer> matches = mAdapter.getMatchPositions();
        if (matches.isEmpty()) {
            mSearchCountText.setVisibility(View.GONE);
            mSearchNoResults.setVisibility(View.VISIBLE);
            mCurrentSearchIndex = -1;
        } else {
            mSearchNoResults.setVisibility(View.GONE);
            mSearchCountText.setVisibility(View.VISIBLE);
            mCurrentSearchIndex = matches.size() - 1; // Start from latest
            updateSearchNavigation();
        }
    }

    private void updateSearchNavigation() {
        List<Integer> matches = mAdapter.getMatchPositions();
        if (matches.isEmpty()) return;
        
        mSearchCountText.setText((mCurrentSearchIndex + 1) + "/" + matches.size());
        mAdapter.setSearchQuery(mSearchInput.getText().toString(), mCurrentSearchIndex);
        mRecyclerView.scrollToPosition(matches.get(mCurrentSearchIndex));
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showKeyboard() {
        mSearchInput.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(mSearchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    private void setupHeaderClick() {
        findViewById(R.id.chat_header_info).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.putExtra("USER_ID", mTargetUid);
            startActivity(intent);
        });
    }

    private void checkBlockStatus() {
        FirebaseDatabase.getInstance().getReference(FirebasePaths.BLOCKED).child(mMyUid).child(mTargetUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        mIsBlocked = snapshot.exists();
                        invalidateOptionsMenu();
                        updateChatReadOnlyStatus();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });

        FirebaseDatabase.getInstance().getReference(FirebasePaths.BLOCKED).child(mTargetUid).child(mMyUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        mAmIBlocked = snapshot.exists();
                        updateChatReadOnlyStatus();
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void updateChatReadOnlyStatus() {
        boolean readOnly = mIsBlocked || mAmIBlocked;
        mInput.setEnabled(!readOnly);
        mSendBtn.setEnabled(!readOnly);
        mInput.setHint(readOnly ? "You cannot message this user" : "Message...");
        mTypingIndicator.setVisibility(View.GONE);
    }

    private void checkMuteStatus() {
        mMyConvRef.child(FirebasePaths.MUTED_UNTIL).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    mMuteUntil = snapshot.getValue(Long.class);
                } else {
                    mMuteUntil = 0;
                }
                invalidateOptionsMenu();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showMuteDialog() {
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
                        case 3: until = -1; break; // Permanent until manual unmute
                        case 4: until = 0; break;
                    }
                    mMyConvRef.child(FirebasePaths.MUTED_UNTIL).setValue(until);
                    
                    // Cache locally for FCM service
                    SharedPreferences.Editor editor = getSharedPreferences("MutedChats", MODE_PRIVATE).edit();
                    if (until == 0) editor.remove(mTargetUid);
                    else editor.putLong(mTargetUid, until);
                    editor.apply();

                    Toast.makeText(this, until == 0 ? "Unmuted" : "Muted", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showBlockDialog() {
        if (mIsBlocked) {
            new AlertDialog.Builder(this)
                    .setTitle("Unblock User?")
                    .setMessage("Do you want to unblock " + mChatName.getText() + "?")
                    .setPositiveButton("Unblock", (dialog, which) -> {
                        FirebaseDatabase.getInstance().getReference(FirebasePaths.BLOCKED).child(mMyUid).child(mTargetUid).removeValue();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Block User?")
                    .setMessage("You will no longer receive messages from this user. They will not be able to contact you.")
                    .setPositiveButton("Block", (dialog, which) -> {
                        FirebaseDatabase.getInstance().getReference(FirebasePaths.BLOCKED).child(mMyUid).child(mTargetUid).setValue(true);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void shareProfile() {
        String shareText = "Check out " + mChatName.getText() + " on Melodify!\n" +
                "Profile: https://melodify.app/user/" + mTargetUid + "\n" +
                "Join me on the ultimate music chat app!";
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, "Share Profile"));
    }

    private void showWallpaperDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_wallpaper_picker, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.wall_gradient).setOnClickListener(v -> {
            updateWallpaper("gradient");
            dialog.dismiss();
        });
        dialogView.findViewById(R.id.wall_dark).setOnClickListener(v -> {
            updateWallpaper("dark");
            dialog.dismiss();
        });
        dialogView.findViewById(R.id.wall_light).setOnClickListener(v -> {
            updateWallpaper("light");
            dialog.dismiss();
        });
        dialogView.findViewById(R.id.wall_blue).setOnClickListener(v -> {
            updateWallpaper("blue");
            dialog.dismiss();
        });
        dialogView.findViewById(R.id.btn_gallery_wall).setOnClickListener(v -> {
            Toast.makeText(this, "Gallery picker coming soon!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateWallpaper(String wallpaper) {
        mMyConvRef.child(FirebasePaths.WALLPAPER).setValue(wallpaper);
        applyWallpaper(wallpaper);
    }

    private void loadWallpaper() {
        mMyConvRef.child(FirebasePaths.WALLPAPER).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    applyWallpaper(snapshot.getValue(String.class));
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void applyWallpaper(String wallpaper) {
        View root = findViewById(R.id.chat_root);
        if ("dark".equals(wallpaper)) {
            root.setBackgroundColor(0xFF121212);
        } else if ("light".equals(wallpaper)) {
            root.setBackgroundColor(0xFFF5F5F5);
        } else if ("blue".equals(wallpaper)) {
            root.setBackgroundColor(0xFF1A237E);
        } else if ("gradient".equals(wallpaper)) {
            root.setBackgroundResource(R.drawable.heartbeat_animated_bg);
        } else {
            root.setBackgroundResource(R.drawable.heartbeat_animated_bg);
        }
    }

    private void showDeleteChatDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Chat?")
                .setMessage("This will remove the conversation from your list. Messages may still be visible to the other person.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    mMyConvRef.removeValue().addOnSuccessListener(aVoid -> finish());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem blockItem = menu.findItem(R.id.action_block);
        if (blockItem != null) {
            blockItem.setTitle(mIsBlocked ? "Unblock User" : "Block User");
        }

        MenuItem muteItem = menu.findItem(R.id.action_mute);
        if (muteItem != null) {
            boolean isMuted = (mMuteUntil == -1 || (mMuteUntil > 0 && mMuteUntil > System.currentTimeMillis()));
            muteItem.setTitle(isMuted ? "Unmute Notifications" : "Mute Notifications");
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onDestroy() {
        if (mLoveAnimationHelper != null) mLoveAnimationHelper.stop();
        if (mChatRef != null && mChatListener != null) mChatRef.removeEventListener(mChatListener);
        if (mStatusRef != null && mStatusListener != null) mStatusRef.removeEventListener(mStatusListener);
        if (mTargetTypingRef != null && mTargetTypingListener != null) mTargetTypingRef.removeEventListener(mTargetTypingListener);
        super.onDestroy();
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
                        Toast.makeText(DirectChatActivity.this, "Request accepted by " + notification.getSender_name(), Toast.LENGTH_SHORT).show();
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
}
