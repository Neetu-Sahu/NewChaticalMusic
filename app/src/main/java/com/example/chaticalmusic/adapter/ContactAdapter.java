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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {

    public interface OnContactClickListener {
        void onContactClick(String uid, String name);
    }

    private final List<String> mUids = new ArrayList<>();
    private final OnContactClickListener mListener;

    public ContactAdapter(OnContactClickListener listener) {
        this.mListener = listener;
    }

    public void setUids(List<String> uids) {
        mUids.clear();
        mUids.addAll(uids);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        String uid = mUids.get(position);
        
        // Fetch user details
        FirebaseDatabase.getInstance().getReference("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.child("display_name").getValue(String.class);
                        String photo = snapshot.child("photo_url").getValue(String.class);
                        
                        holder.nameText.setText(name != null ? name : "User");
                        Glide.with(holder.itemView.getContext())
                                .load(photo)
                                .placeholder(R.drawable.ic_user_placeholder)
                                .circleCrop()
                                .into(holder.avatarImage);
                                
                        holder.itemView.setOnClickListener(v -> mListener.onContactClick(uid, name));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    @Override
    public int getItemCount() {
        return mUids.size();
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImage;
        TextView nameText;
        TextView statusText;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.member_avatar);
            nameText = itemView.findViewById(R.id.member_name);
            statusText = itemView.findViewById(R.id.member_status);
        }
    }
}