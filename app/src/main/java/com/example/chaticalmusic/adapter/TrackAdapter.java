package com.example.chaticalmusic.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.chaticalmusic.R;
import com.example.chaticalmusic.model.JamendoTrack;
import java.util.ArrayList;
import java.util.List;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    public interface OnTrackPlayClickListener {
        void onTrackPlayClicked(JamendoTrack track);
    }

    private final List<JamendoTrack> mTracks = new ArrayList<>();
    private final OnTrackPlayClickListener mPlayClickListener;

    public TrackAdapter(OnTrackPlayClickListener playClickListener) {
        this.mPlayClickListener = playClickListener;
    }

    public void setTracks(List<JamendoTrack> tracks) {
        mTracks.clear();
        if (tracks != null) {
            mTracks.addAll(tracks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_track, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        JamendoTrack track = mTracks.get(position);
        holder.bind(track, mPlayClickListener);
    }

    @Override
    public int getItemCount() {
        return mTracks.size();
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mTrackArt;
        private final TextView mTrackTitle;
        private final TextView mTrackArtist;
        private final ImageButton mPlayBtn;

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            mTrackArt = itemView.findViewById(R.id.track_art);
            mTrackTitle = itemView.findViewById(R.id.track_title);
            mTrackArtist = itemView.findViewById(R.id.track_artist);
            mPlayBtn = itemView.findViewById(R.id.play_btn);
        }

        public void bind(JamendoTrack track, OnTrackPlayClickListener listener) {
            mTrackTitle.setText(track.getTrackTitle());
            mTrackArtist.setText(track.getTrackArtist());

            Glide.with(itemView.getContext())
                    .load(track.getAlbumArtUrl())
                    .placeholder(R.drawable.ic_music_placeholder)
                    .error(R.drawable.ic_music_placeholder)
                    .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
                    .into(mTrackArt);

            mPlayBtn.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTrackPlayClicked(track);
                }
            });
        }
    }
}
