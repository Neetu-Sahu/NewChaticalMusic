package com.example.chaticalmusic.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.chaticalmusic.R;
import com.example.chaticalmusic.model.PublicRoomItem;
import java.util.ArrayList;
import java.util.List;

public class PublicRoomsAdapter extends RecyclerView.Adapter<PublicRoomsAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(PublicRoomItem item);
    }

    private final List<PublicRoomItem> mRooms = new ArrayList<>();
    private final OnItemClickListener mListener;

    public PublicRoomsAdapter(OnItemClickListener listener) {
        this.mListener = listener;
    }

    public void setRooms(List<PublicRoomItem> rooms) {
        mRooms.clear();
        if (rooms != null) {
            mRooms.addAll(rooms);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_public_room, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PublicRoomItem item = mRooms.get(position);
        holder.bind(item, mListener);
    }

    @Override
    public int getItemCount() {
        return mRooms.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView mRoomNameText;
        private final TextView mSongText;
        private final TextView mMembersText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            mRoomNameText = itemView.findViewById(R.id.public_room_name);
            mSongText = itemView.findViewById(R.id.public_room_song);
            mMembersText = itemView.findViewById(R.id.public_room_members);
        }

        public void bind(PublicRoomItem item, OnItemClickListener listener) {
            mRoomNameText.setText(item.getRoomName());
            
            String song = item.getCurrentTrackTitle();
            if (song == null || song.trim().isEmpty()) {
                mSongText.setText("Now Playing: None");
            } else {
                mSongText.setText("Now Playing: " + song);
            }

            mMembersText.setText("👤 " + item.getMemberCount());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }
}
