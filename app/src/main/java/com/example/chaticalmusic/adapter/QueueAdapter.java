package com.example.chaticalmusic.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chaticalmusic.R;
import com.example.chaticalmusic.model.QueueTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.QueueViewHolder> {

    public interface OnVoteClickListener {
        void onVoteClick(QueueTrack track, boolean isUpvote);
    }

    private final List<QueueTrack> mTracks = new ArrayList<>();
    private final String mCurrentUid;
    private final OnVoteClickListener mVoteClickListener;

    public QueueAdapter(String currentUid, OnVoteClickListener voteClickListener) {
        this.mCurrentUid = currentUid;
        this.mVoteClickListener = voteClickListener;
    }

    public void setTracks(List<QueueTrack> tracks) {
        mTracks.clear();
        mTracks.addAll(tracks);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        if (holder.mUpNextHeader != null) {
            holder.mUpNextHeader.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
        }

        QueueTrack track = mTracks.get(position);
        holder.mTitle.setText(track.getTrack_title());
        holder.mArtist.setText(track.getTrack_artist());

        String addedByText;
        if (track.getAdded_by() != null && track.getAdded_by().equals(mCurrentUid)) {
            addedByText = "added by You";
        } else if (track.getAdded_by() != null) {
            String uid = track.getAdded_by();
            addedByText = "added by " + (uid.length() >= 4 ? uid.substring(0, 4) : uid);
        } else {
            addedByText = "added by Unknown";
        }
        holder.mAddedBy.setText(addedByText);

        Glide.with(holder.itemView.getContext())
                 .load(track.getAlbum_art_url())
                 .placeholder(R.drawable.ic_music_placeholder)
                 .error(R.drawable.ic_music_placeholder)
                 .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                 .into(holder.mArt);

        // Bind vote counts
        holder.mUpvoteCount.setText(String.valueOf(track.getUpvotes()));
        holder.mDownvoteCount.setText(String.valueOf(track.getDownvotes()));

        // Highlight user's vote
        Map<String, String> votedUsers = track.getVoted_users();
        if (votedUsers != null && votedUsers.containsKey(mCurrentUid)) {
            String voteType = votedUsers.get(mCurrentUid);
            if ("up".equals(voteType)) {
                holder.mUpvoteBtn.setTextColor(0xFF1DB954); // Green
                holder.mDownvoteBtn.setTextColor(0xFF8E8E93); // Neutral Gray
            } else if ("down".equals(voteType)) {
                holder.mUpvoteBtn.setTextColor(0xFF8E8E93); // Neutral Gray
                holder.mDownvoteBtn.setTextColor(0xFFE02424); // Red
            } else {
                holder.mUpvoteBtn.setTextColor(0xFF8E8E93);
                holder.mDownvoteBtn.setTextColor(0xFF8E8E93);
            }
        } else {
            holder.mUpvoteBtn.setTextColor(0xFF8E8E93);
            holder.mDownvoteBtn.setTextColor(0xFF8E8E93);
        }

        // Set click listeners for voting
        holder.mUpvoteBtn.setOnClickListener(v -> {
            if (mVoteClickListener != null) {
                mVoteClickListener.onVoteClick(track, true);
            }
        });

        holder.mDownvoteBtn.setOnClickListener(v -> {
            if (mVoteClickListener != null) {
                mVoteClickListener.onVoteClick(track, false);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mTracks.size();
    }

    static class QueueViewHolder extends RecyclerView.ViewHolder {
        ImageView mArt;
        TextView mTitle;
        TextView mArtist;
        TextView mAddedBy;
        TextView mUpvoteBtn;
        TextView mUpvoteCount;
        TextView mDownvoteBtn;
        TextView mDownvoteCount;
        TextView mUpNextHeader;

        QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            mArt = itemView.findViewById(R.id.queue_track_art);
            mTitle = itemView.findViewById(R.id.queue_track_title);
            mArtist = itemView.findViewById(R.id.queue_track_artist);
            mAddedBy = itemView.findViewById(R.id.queue_added_by);
            mUpvoteBtn = itemView.findViewById(R.id.queue_upvote_btn);
            mUpvoteCount = itemView.findViewById(R.id.queue_upvote_count);
            mDownvoteBtn = itemView.findViewById(R.id.queue_downvote_btn);
            mDownvoteCount = itemView.findViewById(R.id.queue_downvote_count);
            mUpNextHeader = itemView.findViewById(R.id.up_next_header);
        }
    }
}
