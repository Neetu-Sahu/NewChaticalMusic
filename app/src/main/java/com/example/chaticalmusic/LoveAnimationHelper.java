package com.example.chaticalmusic;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.Random;

public class LoveAnimationHelper {
    private final FrameLayout container;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private boolean isRunning = false;

    public LoveAnimationHelper(FrameLayout container) {
        this.container = container;
    }

    public void start() {
        if (isRunning) return;
        isRunning = true;
        spawnHeartLoop();
    }

    public void stop() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
    }

    private void spawnHeartLoop() {
        if (!isRunning) return;
        spawnHeart();
        handler.postDelayed(this::spawnHeartLoop, 2000 + random.nextInt(3000));
    }

    private void spawnHeart() {
        final Context context = container.getContext();
        final TextView heart = new TextView(context);
        heart.setText("❤");
        int pinkColor = androidx.core.content.ContextCompat.getColor(context, R.color.melodify_pink);
        heart.setTextColor((pinkColor & 0x00FFFFFF) | 0x44000000);
        heart.setTextSize(12 + random.nextInt(12));

        int width = container.getWidth();
        int height = container.getHeight();
        if (width <= 0) width = context.getResources().getDisplayMetrics().widthPixels;
        if (height <= 0) height = context.getResources().getDisplayMetrics().heightPixels;

        heart.setX(random.nextInt(width));
        heart.setY(height + 50);

        container.addView(heart);

        heart.animate()
                .translationY(-100)
                .alpha(0)
                .setDuration(8000 + random.nextInt(4000))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        container.removeView(heart);
                    }
                })
                .start();
    }
}
