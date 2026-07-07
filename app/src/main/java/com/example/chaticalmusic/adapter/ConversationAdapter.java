package com.example.chaticalmusic.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chaticalmusic.FirebasePaths;
import com.example.chaticalmusic.R;
import com.example.chaticalmusic.model.Conversation;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
    }

    public interface OnConversationLongClickListener {
        void onConversationLongClick(Conversation conversation);
    }

    private final List<Conversation> mConversations = new ArrayList<>();
    private final Map<String, ValueEventListener> mStatusListeners = new HashMap<>();
    private final OnConversationClickListener mListener;
    private final OnConversationLongClickListener mLongListener;
    private final SimpleDateFormat mTimeFormatter = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat mDateFormatter = new SimpleDateFormat("dd/MM/yy", Locale.getDefault());

    public ConversationAdapter(OnConversationClickListener listener, OnConversationLongClickListener longListener) {
        this.mListener = listener;
        this.mLongListener = longListener;
    }

    public void setConversations(List<Conversation> conversations) {
        mConversations.clear();
        mConversations.addAll(conversations);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conversation = mConversations.get(position);
        holder.bind(conversation, mListener, mLongListener);
    }

    private String formatLastSeen(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        if (diff < 60000) return "Just now";
        if (diff < 3600000) return (diff / 60000) + "m ago";
        if (diff < 86400000) return (diff / 3600000) + "h ago";
        return mDateFormatter.format(new Date(timestamp));
    }

    @Override
    public int getItemCount() {
        return mConversations.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView name;
        TextView status;
        TextView lastMessage;
        TextView timestamp;
        TextView unreadCount;
        ImageView muteIndicator;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.conv_avatar);
            name = itemView.findViewById(R.id.conv_name);
            status = itemView.findViewById(R.id.conv_status);
            lastMessage = itemView.findViewById(R.id.conv_last_message);
            timestamp = itemView.findViewById(R.id.conv_timestamp);
            unreadCount = itemView.findViewById(R.id.conv_unread_count);
            muteIndicator = itemView.findViewById(R.id.conv_mute_indicator);
        }

        void bind(Conversation conversation, OnConversationClickListener listener, OnConversationLongClickListener longListener) {
            name.setText(conversation.getTarget_name() != null ? conversation.getTarget_name() : "User");
            lastMessage.setText(conversation.getLast_message());
            
            // Fetch status
            if (mStatusListeners.containsKey(conversation.getTarget_uid())) {
                FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(conversation.getTarget_uid())
                        .removeEventListener(mStatusListeners.get(conversation.getTarget_uid()));
            }

            ValueEventListener statusListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Boolean isOnline = snapshot.child(FirebasePaths.ONLINE).getValue(Boolean.class);
                    Long lastActive = snapshot.child(FirebasePaths.LAST_ACTIVE).getValue(Long.class);

                    if (isOnline != null && isOnline) {
                        status.setText("Online");
                        status.setTextColor(itemView.getContext().getResources().getColor(R.color.melodify_pink));
                        status.setVisibility(View.VISIBLE);
                    } else if (lastActive != null) {
                        status.setText("Last seen: " + formatLastSeen(lastActive));
                        status.setTextColor(itemView.getContext().getResources().getColor(R.color.melodify_text_secondary));
                        status.setVisibility(View.VISIBLE);
                    } else {
                        status.setVisibility(View.GONE);
                    }
                }

                @Override public void onCancelled(@NonNull DatabaseError error) {}
            };
            FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(conversation.getTarget_uid()).addValueEventListener(statusListener);
            mStatusListeners.put(conversation.getTarget_uid(), statusListener);

            if (conversation.getLast_timestamp() > 0) {
                Date date = new Date(conversation.getLast_timestamp());
                String formattedTime;
                long now = System.currentTimeMillis();
                if (now - conversation.getLast_timestamp() < 86400000) {
                    formattedTime = mTimeFormatter.format(date);
                } else {
                    formattedTime = mDateFormatter.format(date);
                }
                timestamp.setText(formattedTime);
            } else {
                timestamp.setText("");
            }

            if (conversation.getUnread_count() > 0) {
                unreadCount.setText(String.valueOf(conversation.getUnread_count()));
                unreadCount.setVisibility(View.VISIBLE);
                lastMessage.setTextColor(itemView.getContext().getResources().getColor(R.color.white));
            } else {
                unreadCount.setVisibility(View.GONE);
                lastMessage.setTextColor(itemView.getContext().getResources().getColor(R.color.melodify_text_secondary));
            }

            long mutedUntil = conversation.getMuted_until();
            boolean isMuted = (mutedUntil == -1 || (mutedUntil > 0 && mutedUntil > System.currentTimeMillis()));
            muteIndicator.setVisibility(isMuted ? View.VISIBLE : View.GONE);

            Glide.with(itemView.getContext())
                    .load(conversation.getTarget_photo())
                    .placeholder(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(avatar);

            itemView.setOnClickListener(v -> listener.onConversationClick(conversation));
            itemView.setOnLongClickListener(v -> {
                if (longListener != null) {
                    longListener.onConversationLongClick(conversation);
                    return true;
                }
                return false;
            });
        }
    }
}
