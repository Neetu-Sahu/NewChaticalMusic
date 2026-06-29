package com.example.chaticalmusic.adapter;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chaticalmusic.R;
import com.example.chaticalmusic.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnMessageLongClickListener {
        void onMessageLongClick(ChatMessage message);
    }

    public interface OnRequestActionListener {
        void onApproveRequest(ChatMessage message);
    }

    private static final int TYPE_DATE = 0;
    private static final int TYPE_MESSAGE = 1;

    private final List<Object> mItems = new ArrayList<>();
    private final List<ChatMessage> mMessages = new ArrayList<>();
    private final String mCurrentUid;
    private final OnMessageLongClickListener mLongClickListener;
    private final OnRequestActionListener mRequestListener;
    private final SimpleDateFormat mTimeFormatter = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat mDateFormatter = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
    private boolean mIsHost = false;

    public ChatAdapter(String currentUid, OnMessageLongClickListener longClickListener, OnRequestActionListener requestListener) {
        this.mCurrentUid = currentUid;
        this.mLongClickListener = longClickListener;
        this.mRequestListener = requestListener;
    }

    private void openProfile(android.content.Context context, String uid) {
        if (uid == null) return;
        android.content.Intent intent = new android.content.Intent(context, com.example.chaticalmusic.ProfileActivity.class);
        intent.putExtra("TARGET_UID", uid);
        context.startActivity(intent);
    }

    public void setHost(boolean isHost) {
        this.mIsHost = isHost;
        notifyDataSetChanged();
    }

    public void setMessages(List<ChatMessage> messages) {
        mMessages.clear();
        mMessages.addAll(messages);
        
        mItems.clear();
        if (!messages.isEmpty()) {
            String lastDate = "";
            for (ChatMessage msg : messages) {
                String msgDate = mDateFormatter.format(new Date(msg.getTimestamp()));
                if (!msgDate.equals(lastDate)) {
                    mItems.add(msgDate);
                    lastDate = msgDate;
                }
                mItems.add(msg);
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return mItems.get(position) instanceof String ? TYPE_DATE : TYPE_MESSAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_DATE) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_date, parent, false);
            return new DateViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_DATE) {
            String dateStr = (String) mItems.get(position);
            String today = mDateFormatter.format(new Date());
            ((DateViewHolder) holder).dateText.setText(dateStr.equals(today) ? "Today" : dateStr);
            return;
        }

        ChatViewHolder chatHolder = (ChatViewHolder) holder;
        ChatMessage message = (ChatMessage) mItems.get(position);
        boolean isMe = message.getSender_uid() != null && message.getSender_uid().equals(mCurrentUid);

        chatHolder.senderName.setText(isMe ? "You" : message.getSender_name());
        
        String text = message.getMessage_text();
        if (text != null && text.contains("@")) {
            SpannableString spannable = new SpannableString(text);
            int start = text.indexOf("@");
            while (start != -1) {
                int end = text.indexOf(" ", start);
                if (end == -1) end = text.length();
                spannable.setSpan(new ForegroundColorSpan(0xFFFF748D), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = text.indexOf("@", end);
            }
            chatHolder.messageText.setText(spannable);
        } else {
            chatHolder.messageText.setText(text);
        }

        chatHolder.timestamp.setText(mTimeFormatter.format(new Date(message.getTimestamp())));

        // Alignment logic
        RelativeLayout.LayoutParams bubbleParams = (RelativeLayout.LayoutParams) chatHolder.bubble.getLayoutParams();
        RelativeLayout.LayoutParams avatarParams = (RelativeLayout.LayoutParams) chatHolder.avatar.getLayoutParams();
        RelativeLayout.LayoutParams statusParams = (RelativeLayout.LayoutParams) chatHolder.statusContainer.getLayoutParams();

        if (isMe) {
            bubbleParams.addRule(RelativeLayout.ALIGN_PARENT_END);
            bubbleParams.removeRule(RelativeLayout.ALIGN_PARENT_START);
            bubbleParams.removeRule(RelativeLayout.RIGHT_OF);
            bubbleParams.setMargins(100, 0, 4, 0);
            
            avatarParams.addRule(RelativeLayout.ALIGN_PARENT_END);
            avatarParams.removeRule(RelativeLayout.ALIGN_PARENT_START);
            
            chatHolder.bubble.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF829B)); // Melodify Pink
            chatHolder.messageText.setTextColor(0xFF251422); // Dark Maroon text for pink bubble
            chatHolder.avatar.setVisibility(View.GONE);
            chatHolder.senderName.setVisibility(View.GONE);
            
            statusParams.addRule(RelativeLayout.ALIGN_END, R.id.chat_bubble);
            statusParams.removeRule(RelativeLayout.ALIGN_START);
            chatHolder.statusContainer.setLayoutParams(statusParams);
            chatHolder.readReceipt.setVisibility(View.VISIBLE);
        } else {
            bubbleParams.addRule(RelativeLayout.RIGHT_OF, R.id.chat_sender_avatar);
            bubbleParams.removeRule(RelativeLayout.ALIGN_PARENT_END);
            bubbleParams.setMargins(8, 0, 100, 0);
            
            avatarParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            avatarParams.removeRule(RelativeLayout.ALIGN_PARENT_END);
            
            chatHolder.bubble.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xCC2C2C2E)); // Glassy Dark
            chatHolder.messageText.setTextColor(0xFFFFFFFF); // White text
            chatHolder.avatar.setVisibility(View.VISIBLE);
            chatHolder.senderName.setVisibility(View.VISIBLE);
            
            statusParams.addRule(RelativeLayout.ALIGN_START, R.id.chat_bubble);
            statusParams.removeRule(RelativeLayout.ALIGN_END);
            chatHolder.statusContainer.setLayoutParams(statusParams);
            chatHolder.readReceipt.setVisibility(View.GONE);

            Glide.with(chatHolder.itemView.getContext())
                    .load(message.getSender_photo_url())
                    .placeholder(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(chatHolder.avatar);

            chatHolder.avatar.setOnClickListener(v -> openProfile(chatHolder.itemView.getContext(), message.getSender_uid()));
            chatHolder.senderName.setOnClickListener(v -> openProfile(chatHolder.itemView.getContext(), message.getSender_uid()));
        }
        chatHolder.bubble.setLayoutParams(bubbleParams);
        chatHolder.avatar.setLayoutParams(avatarParams);

        // Reply logic
        if (message.isIs_reply()) {
            chatHolder.replyContainer.setVisibility(View.VISIBLE);
            chatHolder.replyName.setText(message.getReplied_to_name());
            chatHolder.replyText.setText(message.getReplied_to_text());
        } else {
            chatHolder.replyContainer.setVisibility(View.GONE);
        }

        // Host Approval UI
        if (mIsHost && message.getRequest_type() != null && !message.getSender_uid().equals(mCurrentUid)) {
            chatHolder.approveBtn.setVisibility(View.VISIBLE);
            chatHolder.approveBtn.setText("Approve " + message.getRequest_type().toUpperCase());
            chatHolder.approveBtn.setOnClickListener(v -> {
                if (mRequestListener != null) mRequestListener.onApproveRequest(message);
                chatHolder.approveBtn.setVisibility(View.GONE);
            });
        } else {
            chatHolder.approveBtn.setVisibility(View.GONE);
        }

        chatHolder.itemView.setOnLongClickListener(v -> {
            if (mLongClickListener != null) mLongClickListener.onMessageLongClick(message);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class DateViewHolder extends RecyclerView.ViewHolder {
        TextView dateText;
        DateViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = (TextView) itemView;
        }
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        View bubble;
        TextView senderName;
        TextView messageText;
        TextView timestamp;
        View replyContainer;
        TextView replyName;
        TextView replyText;
        Button approveBtn;
        View statusContainer;
        TextView readReceipt;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.chat_sender_avatar);
            bubble = itemView.findViewById(R.id.chat_bubble);
            senderName = itemView.findViewById(R.id.chat_sender_name);
            messageText = itemView.findViewById(R.id.chat_text);
            timestamp = itemView.findViewById(R.id.chat_timestamp);
            replyContainer = itemView.findViewById(R.id.reply_preview_container);
            replyName = itemView.findViewById(R.id.reply_preview_name);
            replyText = itemView.findViewById(R.id.reply_preview_text);
            approveBtn = itemView.findViewById(R.id.btn_approve_request);
            statusContainer = itemView.findViewById(R.id.chat_status_container);
            readReceipt = itemView.findViewById(R.id.chat_read_receipt);
        }
    }
}
