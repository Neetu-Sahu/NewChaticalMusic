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
import com.example.chaticalmusic.model.Member;
import java.util.ArrayList;
import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.ViewHolder> {

    public interface OnMemberClickListener {
        void onMemberClick(Member member);
    }

    private final List<Member> mMembers = new ArrayList<>();
    private final OnMemberClickListener mListener;

    public MemberAdapter(OnMemberClickListener listener) {
        this.mListener = listener;
    }

    public void setMembers(List<Member> members) {
        mMembers.clear();
        mMembers.addAll(members);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Member member = mMembers.get(position);
        holder.bind(member, mListener);
    }

    @Override
    public int getItemCount() {
        return mMembers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView name;
        TextView status;
        TextView hand;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.member_avatar);
            name = itemView.findViewById(R.id.member_name);
            status = itemView.findViewById(R.id.member_status);
            hand = itemView.findViewById(R.id.hand_raised_icon);
        }

        void bind(Member member, OnMemberClickListener listener) {
            name.setText(member.getDisplayName());
            
            if (member.isHost()) {
                status.setVisibility(View.VISIBLE);
                status.setText("DJ / Host");
            } else if (member.isCoDj()) {
                status.setVisibility(View.VISIBLE);
                status.setText("Co-DJ");
            } else {
                status.setVisibility(View.GONE);
            }

            hand.setVisibility(member.hasRequestedAux() ? View.VISIBLE : View.GONE);

            Glide.with(itemView.getContext())
                    .load(member.getPhotoUrl())
                    .placeholder(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(avatar);

            itemView.setOnClickListener(v -> listener.onMemberClick(member));
        }
    }
}
