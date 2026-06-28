package com.example.chaticalmusic.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.chaticalmusic.R;
import java.util.List;

public class AvatarAdapter extends RecyclerView.Adapter<AvatarAdapter.ViewHolder> {

    public interface OnAvatarClickListener {
        void onAvatarClick(String url);
    }

    private final List<String> mUrls;
    private final OnAvatarClickListener mListener;

    public AvatarAdapter(List<String> urls, OnAvatarClickListener listener) {
        this.mUrls = urls;
        this.mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_avatar_selection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String url = mUrls.get(position);
        Glide.with(holder.itemView.getContext()).load(url).circleCrop().into(holder.image);
        holder.itemView.setOnClickListener(v -> mListener.onAvatarClick(url));
    }

    @Override
    public int getItemCount() {
        return mUrls.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image;
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.avatar_image);
        }
    }
}
