package com.example.chaticalmusic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class SwipeToReplyCallback extends ItemTouchHelper.SimpleCallback {

    public interface SwipeReplyListener {
        void onSwipeToReply(int position);
    }

    private final Drawable replyIcon;
    private final SwipeReplyListener listener;
    private boolean isTriggered = false;

    public SwipeToReplyCallback(Context context, SwipeReplyListener listener) {
        super(0, ItemTouchHelper.RIGHT);
        this.listener = listener;
        this.replyIcon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_revert);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return Float.MAX_VALUE; 
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return Float.MAX_VALUE;
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        float maxDx = 80 * recyclerView.getContext().getResources().getDisplayMetrics().density;
        float translationX = Math.min(dX, maxDx);
        
        super.onChildDraw(c, recyclerView, viewHolder, translationX, dY, actionState, isCurrentlyActive);

        if (isCurrentlyActive && dX > maxDx * 0.7f && !isTriggered) {
            isTriggered = true;
            viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            listener.onSwipeToReply(viewHolder.getBindingAdapterPosition());
        }

        if (!isCurrentlyActive) {
            isTriggered = false;
        }

        // Draw icon
        if (dX > 0 && replyIcon != null) {
            int iconSize = (int) (24 * recyclerView.getContext().getResources().getDisplayMetrics().density);
            int margin = (int) (16 * recyclerView.getContext().getResources().getDisplayMetrics().density);
            int top = viewHolder.itemView.getTop() + (viewHolder.itemView.getHeight() - iconSize) / 2;
            int left = (int) (translationX - iconSize - margin);
            
            if (left > margin) {
                replyIcon.setBounds(margin, top, margin + iconSize, top + iconSize);
                replyIcon.setAlpha(Math.min(255, (int)(dX * 2)));
                replyIcon.draw(c);
            }
        }
    }
}
