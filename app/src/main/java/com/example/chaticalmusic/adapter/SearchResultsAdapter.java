package com.example.chaticalmusic.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.chaticalmusic.R;
import com.example.chaticalmusic.model.JamendoTrack;
import java.util.ArrayList;
import java.util.List;

public class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.ResultViewHolder> {

    public interface Listener {
        void onTrackPlay(JamendoTrack track);
        void onAddToQueue(JamendoTrack track);
    }

    private final List<JamendoTrack> mTracks = new ArrayList<>();
    private final Listener mListener;

    public SearchResultsAdapter(Listener listener) {
        this.mListener = listener;
    }

    public void setTracks(List<JamendoTrack> tracks) {
        mTracks.clear();
        if (tracks != null) {
            mTracks.addAll(tracks);
        }
        notifyDataSetChanged();
    }

    public List<JamendoTrack> getTracks() {
        return mTracks;
    }

    @NonNull
    @Override
    public ResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new ResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ResultViewHolder holder, int position) {
        JamendoTrack track = mTracks.get(position);
        holder.bind(track, mListener);
    }

    @Override
    public int getItemCount() {
        return mTracks.size();
    }

    static class ResultViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mArt;
        private final TextView mTitle;
        private final TextView mArtist;
        private final ImageButton mMoreBtn;

        public ResultViewHolder(@NonNull View itemView) {
            super(itemView);
            mArt = itemView.findViewById(R.id.result_art);
            mTitle = itemView.findViewById(R.id.result_title);
            mArtist = itemView.findViewById(R.id.result_artist);
            mMoreBtn = itemView.findViewById(R.id.result_more_btn);
        }

        public void bind(JamendoTrack track, Listener listener) {
            mTitle.setText(track.getTrackTitle());
            mArtist.setText(track.getTrackArtist());

            Glide.with(itemView.getContext())
                    .load(track.getAlbumArtUrl())
                    .placeholder(R.drawable.ic_music_placeholder)
                    .error(R.drawable.ic_music_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(mArt);

            // Whole item tap → play immediately
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTrackPlay(track);
                }
            });

            // 3-dot overflow → popup menu
            mMoreBtn.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(v.getContext(), v);
                popup.getMenuInflater().inflate(R.menu.menu_search_result, popup.getMenu());
                popup.setOnMenuItemClickListener(item -> {
                    if (listener == null) return false;
                    int id = item.getItemId();
                    if (id == R.id.action_play_now) {
                        listener.onTrackPlay(track);
                        return true;
                    } else if (id == R.id.action_add_to_queue) {
                        listener.onAddToQueue(track);
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }
    }
}
