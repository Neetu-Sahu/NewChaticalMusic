package com.example.chaticalmusic.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chaticalmusic.R;
import com.example.chaticalmusic.model.User;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

public class FollowRequestAdapter extends RecyclerView.Adapter<FollowRequestAdapter.ViewHolder> {

    private List<User> mRequests = new ArrayList<>();
    private final java.util.Set<String> mAcceptedUids = new java.util.HashSet<>();
    private final OnRequestListener mListener;

    public interface OnRequestListener {
        void onAccept(User user);
        void onDecline(User user);
        void onFollowBack(User user);
    }

    public FollowRequestAdapter(OnRequestListener listener) {
        this.mListener = listener;
    }

    public void setRequests(List<User> requests) {
        this.mRequests = requests;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_follow_request, parent, false);
        return new ViewHolder(view);
    }

    public void markAsAccepted(String uid) {
        mAcceptedUids.add(uid);
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = mRequests.get(position);
        holder.name.setText(user.getDisplay_name());
        holder.username.setText("@" + user.getUsername());
        
        Glide.with(holder.itemView.getContext())
                .load(user.getPhoto_url())
                .placeholder(R.drawable.ic_user_placeholder)
                .circleCrop()
                .into(holder.image);

        if (mAcceptedUids.contains(user.getUid())) {
            holder.btnAccept.setVisibility(View.GONE);
            holder.btnDecline.setVisibility(View.GONE);
            holder.btnFollowBack.setVisibility(View.VISIBLE);
        } else {
            holder.btnAccept.setVisibility(View.VISIBLE);
            holder.btnDecline.setVisibility(View.VISIBLE);
            holder.btnFollowBack.setVisibility(View.GONE);
        }

        holder.btnAccept.setOnClickListener(v -> mListener.onAccept(user));
        holder.btnDecline.setOnClickListener(v -> mListener.onDecline(user));
        holder.btnFollowBack.setOnClickListener(v -> mListener.onFollowBack(user));
    }

    @Override
    public int getItemCount() {
        return mRequests.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView image;
        TextView name, username;
        Button btnAccept, btnDecline, btnFollowBack;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.requester_image);
            name = itemView.findViewById(R.id.requester_name);
            username = itemView.findViewById(R.id.requester_username);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnDecline = itemView.findViewById(R.id.btn_decline);
            btnFollowBack = itemView.findViewById(R.id.btn_follow_back);
        }
    }
}