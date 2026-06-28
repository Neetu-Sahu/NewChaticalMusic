package com.example.chaticalmusic.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chaticalmusic.R;
import com.example.chaticalmusic.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_UNGROUPED = 0;
    private static final int VIEW_TYPE_GROUPED = 1;

    private final List<ChatMessage> mMessages = new ArrayList<>();
    private final String mCurrentUid;
    private final SimpleDateFormat mTimeFormatter = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatAdapter(String currentUid) {
        this.mCurrentUid = currentUid;
    }

    public void setMessages(List<ChatMessage> messages) {
        mMessages.clear();
        mMessages.addAll(messages);
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        mMessages.add(message);
        notifyItemInserted(mMessages.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        if (position > 0) {
            ChatMessage prev = mMessages.get(position - 1);
            ChatMessage curr = mMessages.get(position);
            if (curr.getSender_uid() != null && curr.getSender_uid().equals(prev.getSender_uid())) {
                long diff = curr.getTimestamp() - prev.getTimestamp();
                if (diff >= 0 && diff < 120000) { // 2 minutes
                    return VIEW_TYPE_GROUPED;
                }
            }
        }
        return VIEW_TYPE_UNGROUPED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_GROUPED) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_compact, parent, false);
            return new GroupedViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
            return new UngroupedViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = mMessages.get(position);
        boolean showTime = isLastInGroup(position);
        String timeStr = mTimeFormatter.format(new Date(message.getTimestamp()));

        if (holder instanceof UngroupedViewHolder) {
            UngroupedViewHolder uHolder = (UngroupedViewHolder) holder;
            uHolder.mText.setText(message.getMessage_text());

            if (message.getSender_uid() != null && message.getSender_uid().equals(mCurrentUid)) {
                uHolder.mSender.setText("You");
                uHolder.mSender.setTextColor(0xFF1DB954); // Spotify green
            } else {
                uHolder.mSender.setText(message.getSender_name());
                uHolder.mSender.setTextColor(0xFF8E8E93); // Gray
            }

            if (showTime) {
                uHolder.mTimestamp.setVisibility(View.VISIBLE);
                uHolder.mTimestamp.setText(timeStr);
            } else {
                uHolder.mTimestamp.setVisibility(View.GONE);
            }
        } else if (holder instanceof GroupedViewHolder) {
            GroupedViewHolder gHolder = (GroupedViewHolder) holder;
            gHolder.mText.setText(message.getMessage_text());

            if (showTime) {
                gHolder.mTimestamp.setVisibility(View.VISIBLE);
                gHolder.mTimestamp.setText(timeStr);
            } else {
                gHolder.mTimestamp.setVisibility(View.GONE);
            }
        }
    }

    private boolean isLastInGroup(int position) {
        if (position == getItemCount() - 1) {
            return true;
        }
        ChatMessage curr = mMessages.get(position);
        ChatMessage next = mMessages.get(position + 1);
        if (next.getSender_uid() != null && next.getSender_uid().equals(curr.getSender_uid())) {
            long diff = next.getTimestamp() - curr.getTimestamp();
            if (diff >= 0 && diff < 120000) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getItemCount() {
        return mMessages.size();
    }

    static class UngroupedViewHolder extends RecyclerView.ViewHolder {
        TextView mSender;
        TextView mText;
        TextView mTimestamp;

        UngroupedViewHolder(@NonNull View itemView) {
            super(itemView);
            mSender = itemView.findViewById(R.id.chat_sender_name);
            mText = itemView.findViewById(R.id.chat_text);
            mTimestamp = itemView.findViewById(R.id.chat_timestamp);
        }
    }

    static class GroupedViewHolder extends RecyclerView.ViewHolder {
        TextView mText;
        TextView mTimestamp;

        GroupedViewHolder(@NonNull View itemView) {
            super(itemView);
            mText = itemView.findViewById(R.id.chat_text);
            mTimestamp = itemView.findViewById(R.id.chat_timestamp);
        }
    }
}
