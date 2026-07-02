package com.example.chaticalmusic.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chaticalmusic.FirebasePaths;
import com.example.chaticalmusic.R;
import com.example.chaticalmusic.model.User;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.ViewHolder> {

    private List<User> mUsers = new ArrayList<>();
    private final OnUserClickListener mListener;

    public interface OnUserClickListener {
        void onUserClick(User user);
        void onFollowClick(User user);
    }

    public UserSearchAdapter(OnUserClickListener listener) {
        this.mListener = listener;
    }

    public void setUsers(List<User> users) {
        this.mUsers = users;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_search, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = mUsers.get(position);
        holder.displayName.setText(user.getDisplay_name());
        holder.username.setText("@" + user.getUsername());
        
        Glide.with(holder.itemView.getContext())
                .load(user.getPhoto_url())
                .placeholder(R.drawable.ic_user_placeholder)
                .circleCrop()
                .into(holder.profileImage);

        holder.itemView.setOnClickListener(v -> mListener.onUserClick(user));
        holder.followBtn.setOnClickListener(v -> mListener.onFollowClick(user));
        
        updateFollowStatus(holder, user);
    }

    private void updateFollowStatus(ViewHolder holder, User user) {
        String myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        Context context = holder.itemView.getContext();

        // 1. Check if following
        FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS)
                .child(myUid).child(FirebasePaths.FOLLOWING).child(user.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            holder.followBtn.setText("Following");
                            holder.followBtn.setBackgroundTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.melodify_card)));
                            holder.followBtn.setTextColor(context.getResources().getColor(R.color.white));
                        } else {
                            // 2. Check if requested
                            FirebaseDatabase.getInstance().getReference(FirebasePaths.FOLLOW_REQUESTS)
                                    .child(user.getUid()).child(myUid)
                                    .addValueEventListener(new ValueEventListener() {
                                        @Override
                                        public void onDataChange(@NonNull DataSnapshot reqSnap) {
                                            if (reqSnap.exists()) {
                                                holder.followBtn.setText("Requested");
                                                holder.followBtn.setBackgroundTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.white)));
                                                holder.followBtn.setTextColor(context.getResources().getColor(R.color.melodify_pink));
                                            } else {
                                                holder.followBtn.setText("Follow");
                                                holder.followBtn.setBackgroundTintList(ColorStateList.valueOf(context.getResources().getColor(R.color.melodify_pink)));
                                                holder.followBtn.setTextColor(context.getResources().getColor(R.color.melodify_background));
                                            }
                                        }
                                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                                    });
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    @Override
    public int getItemCount() {
        return mUsers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView profileImage;
        TextView displayName, username;
        Button followBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.user_profile_image);
            displayName = itemView.findViewById(R.id.user_display_name);
            username = itemView.findViewById(R.id.user_username);
            followBtn = itemView.findViewById(R.id.follow_status_btn);
        }
    }
}