package com.example.chaticalmusic.adapter;

import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

    public interface OnReplyClickListener {
        void onReplyClick(ChatMessage message);
    }

    private static final int TYPE_DATE = 0;
    private static final int TYPE_MESSAGE_ME = 1;
    private static final int TYPE_MESSAGE_OTHER = 2;

    private final List<Object> mItems = new ArrayList<>();
    private final String mCurrentUid;
    private final OnMessageLongClickListener mLongClickListener;
    private final OnRequestActionListener mRequestListener;
    private final OnReplyClickListener mReplyClickListener;
    private final SimpleDateFormat mTimeFormatter = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private final SimpleDateFormat mDateFormatter = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
    private boolean mIsHost = false;
    private String mSearchQuery = "";
    private int mCurrentMatchIndex = -1;
    private final List<Integer> mMatchPositions = new ArrayList<>();
    private boolean mIsRoomChat = false;

    public ChatAdapter(String currentUid, OnMessageLongClickListener longClickListener, OnRequestActionListener requestListener, OnReplyClickListener replyClickListener) {
        this.mCurrentUid = currentUid;
        this.mLongClickListener = longClickListener;
        this.mRequestListener = requestListener;
        this.mReplyClickListener = replyClickListener;
    }

    public void setHost(boolean isHost) {
        this.mIsHost = isHost;
        notifyDataSetChanged();
    }

    public void setIsRoomChat(boolean isRoomChat) {
        this.mIsRoomChat = isRoomChat;
        notifyDataSetChanged();
    }

    public void setMessages(List<ChatMessage> messages) {
        int oldSize = mItems.size();
        mItems.clear();
        if (!messages.isEmpty()) {
            String lastDate = "";
            for (ChatMessage msg : messages) {
                // Filter out messages deleted for me
                if (msg.getDeleted_for() != null && msg.getDeleted_for().containsKey(mCurrentUid)) {
                    continue;
                }

                String msgDate = mDateFormatter.format(new Date(msg.getTimestamp()));
                if (!msgDate.equals(lastDate)) {
                    mItems.add(msgDate);
                    lastDate = msgDate;
                }
                mItems.add(msg);
            }
        }
        
        if (mItems.size() > oldSize && oldSize > 0) {
            notifyItemRangeInserted(oldSize, mItems.size() - oldSize);
        } else {
            notifyDataSetChanged();
        }
    }

    public void setSearchQuery(String query, int currentMatchIndex) {
        this.mSearchQuery = query.toLowerCase().trim();
        this.mCurrentMatchIndex = currentMatchIndex;
        this.mMatchPositions.clear();
        
        if (!mSearchQuery.isEmpty()) {
            for (int i = 0; i < mItems.size(); i++) {
                Object item = mItems.get(i);
                if (item instanceof ChatMessage) {
                    ChatMessage msg = (ChatMessage) item;
                    if (msg.getMessage_text() != null && msg.getMessage_text().toLowerCase().contains(mSearchQuery)) {
                        mMatchPositions.add(i);
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    public List<Integer> getMatchPositions() {
        return mMatchPositions;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = mItems.get(position);
        if (item instanceof String) return TYPE_DATE;
        ChatMessage msg = (ChatMessage) item;
        if (msg.getSender_uid() != null && msg.getSender_uid().equals(mCurrentUid)) {
            return TYPE_MESSAGE_ME;
        }
        return TYPE_MESSAGE_OTHER;
    }

    public Object getItem(int position) {
        return mItems.get(position);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_DATE) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_date, parent, false);
            return new DateViewHolder(view);
        } else if (viewType == TYPE_MESSAGE_ME) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_self, parent, false);
            return new ChatViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int viewType = getItemViewType(position);
        if (viewType == TYPE_DATE) {
            String dateStr = (String) mItems.get(position);
            String today = mDateFormatter.format(new Date());
            ((DateViewHolder) holder).dateText.setText(dateStr.equals(today) ? "Today" : dateStr);
            return;
        }

        ChatViewHolder chatHolder = (ChatViewHolder) holder;
        ChatMessage message = (ChatMessage) mItems.get(position);
        boolean isMe = viewType == TYPE_MESSAGE_ME;

        // Set background based on chat type (DM vs Room)
        if (mIsRoomChat) {
            chatHolder.bubble.setBackgroundResource(isMe ? R.drawable.chatroom_bubble_me : R.drawable.chatroom_bubble_other);
        } else {
            chatHolder.bubble.setBackgroundResource(isMe ? R.drawable.whatsapp_bubble_me : R.drawable.whatsapp_bubble_other);
        }

        if (chatHolder.senderName != null) {
            chatHolder.senderName.setText(isMe ? "You" : message.getSender_name());
            if (mIsRoomChat) {
                chatHolder.senderName.setTextColor(chatHolder.itemView.getContext().getResources().getColor(R.color.melodify_pink));
            } else {
                chatHolder.senderName.setTextColor(0xFF075E54); // WhatsApp green
            }
        }
        
        String text = message.getMessage_text();
        if (message.isIs_deleted()) {
            chatHolder.messageText.setText("This message was deleted");
            chatHolder.messageText.setTypeface(null, android.graphics.Typeface.ITALIC);
            chatHolder.messageText.setTextColor(0x99000000);
            chatHolder.bubble.setAlpha(0.6f);
            chatHolder.reactionsText.setVisibility(View.GONE);
            chatHolder.replyContainer.setVisibility(View.GONE);
        } else {
            chatHolder.messageText.setTypeface(null, android.graphics.Typeface.NORMAL);
            chatHolder.messageText.setTextColor(0xFF000000);
            chatHolder.bubble.setAlpha(1.0f);
            
            if (mSearchQuery != null && !mSearchQuery.isEmpty() && text != null && text.toLowerCase().contains(mSearchQuery)) {
                SpannableString spannable = new SpannableString(text);
                int start = text.toLowerCase().indexOf(mSearchQuery);
                while (start >= 0) {
                    int end = start + mSearchQuery.length();
                    int color = (mCurrentMatchIndex != -1 && mMatchPositions.size() > mCurrentMatchIndex && mMatchPositions.get(mCurrentMatchIndex) == position) 
                            ? 0xFFFF4081 : 0x88FF4081; // Pink for current, translucent pink for others
                    spannable.setSpan(new android.text.style.BackgroundColorSpan(color), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    start = text.toLowerCase().indexOf(mSearchQuery, end);
                }
                chatHolder.messageText.setText(spannable);
            } else {
                chatHolder.messageText.setText(text);
            }
        }

        chatHolder.timestamp.setText(mTimeFormatter.format(new Date(message.getTimestamp())));

        if (isMe) {
            if (chatHolder.readReceipt != null) {
                chatHolder.readReceipt.setVisibility(View.VISIBLE);
                if (message.getStatus() == ChatMessage.STATUS_READ) {
                    chatHolder.readReceipt.setText("✓✓");
                    chatHolder.readReceipt.setTextColor(chatHolder.itemView.getContext().getResources().getColor(R.color.melodify_pink));
                } else if (message.getStatus() == ChatMessage.STATUS_DELIVERED) {
                    chatHolder.readReceipt.setText("✓✓");
                    chatHolder.readReceipt.setTextColor(0xFF888888);
                } else {
                    chatHolder.readReceipt.setText("✓");
                    chatHolder.readReceipt.setTextColor(0xFF888888);
                }
            }
        } else {
            if (chatHolder.avatar != null) {
                Glide.with(chatHolder.itemView.getContext())
                        .load(message.getSender_photo_url())
                        .placeholder(R.drawable.ic_user_placeholder)
                        .circleCrop()
                        .into(chatHolder.avatar);
            }
        }

        if (message.isIs_reply()) {
            chatHolder.replyContainer.setVisibility(View.VISIBLE);
            chatHolder.replyName.setText(message.getReplied_to_name());
            chatHolder.replyText.setText(message.getReplied_to_text());
            
            if (mIsRoomChat) {
                int pink = chatHolder.itemView.getContext().getResources().getColor(R.color.melodify_pink);
                chatHolder.replyName.setTextColor(pink);
                if (chatHolder.replyBarLine != null) chatHolder.replyBarLine.setBackgroundColor(pink);
            } else {
                chatHolder.replyName.setTextColor(0xFF075E54);
                if (chatHolder.replyBarLine != null) chatHolder.replyBarLine.setBackgroundColor(0xFF075E54);
            }

            chatHolder.replyContainer.setOnClickListener(v -> {
                if (mReplyClickListener != null) mReplyClickListener.onReplyClick(message);
            });
        } else {
            chatHolder.replyContainer.setVisibility(View.GONE);
        }

        if (message.getReactions() != null && !message.getReactions().isEmpty()) {
            chatHolder.reactionsText.setVisibility(View.VISIBLE);
            StringBuilder sb = new StringBuilder();
            java.util.Map<String, Integer> counts = new java.util.HashMap<>();
            for (String emoji : message.getReactions().values()) {
                Integer current = counts.get(emoji);
                counts.put(emoji, (current == null ? 0 : current) + 1);
            }
            for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
                sb.append(entry.getKey()).append(" ").append(entry.getValue()).append("  ");
            }
            chatHolder.reactionsText.setText(sb.toString().trim());
        } else {
            chatHolder.reactionsText.setVisibility(View.GONE);
        }

        chatHolder.itemView.setOnLongClickListener(v -> {
            if (mLongClickListener != null) mLongClickListener.onMessageLongClick(message);
            return true;
        });

        // Grouping logic: Reduce top margin if previous message is from same sender
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) chatHolder.itemView.getLayoutParams();
        int defaultMargin = (int) (4 * chatHolder.itemView.getContext().getResources().getDisplayMetrics().density);
        int groupedMargin = (int) (1 * chatHolder.itemView.getContext().getResources().getDisplayMetrics().density);
        
        if (position > 0) {
            Object prevItem = mItems.get(position - 1);
            if (prevItem instanceof ChatMessage) {
                ChatMessage prevMsg = (ChatMessage) prevItem;
                if (prevMsg.getSender_uid() != null && prevMsg.getSender_uid().equals(message.getSender_uid())) {
                    lp.topMargin = groupedMargin;
                } else {
                    lp.topMargin = defaultMargin;
                }
            } else {
                lp.topMargin = defaultMargin;
            }
        } else {
            lp.topMargin = defaultMargin;
        }
        chatHolder.itemView.setLayoutParams(lp);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public static class DateViewHolder extends RecyclerView.ViewHolder {
        public TextView dateText;
        public DateViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = (TextView) itemView;
        }
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        public ImageView avatar;
        public View bubble;
        public TextView senderName;
        public TextView messageText;
        public TextView timestamp;
        public View replyContainer;
        public TextView replyName;
        public TextView replyText;
        public View replyBarLine;
        public Button approveBtn;
        public View statusContainer;
        public TextView readReceipt;
        public TextView reactionsText;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.chat_sender_avatar);
            bubble = itemView.findViewById(R.id.chat_bubble);
            senderName = itemView.findViewById(R.id.chat_sender_name);
            messageText = itemView.findViewById(R.id.chat_text);
            timestamp = itemView.findViewById(R.id.chat_timestamp);
            replyContainer = itemView.findViewById(R.id.reply_preview_container);
            replyName = itemView.findViewById(R.id.reply_preview_name);
            replyText = itemView.findViewById(R.id.reply_preview_text);
            replyBarLine = itemView.findViewById(R.id.reply_bar_line);
            approveBtn = itemView.findViewById(R.id.btn_approve_request);
            statusContainer = itemView.findViewById(R.id.chat_status_container);
            readReceipt = itemView.findViewById(R.id.chat_read_receipt);
            reactionsText = itemView.findViewById(R.id.chat_reactions);
        }
    }
}
