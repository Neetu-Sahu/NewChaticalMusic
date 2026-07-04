package com.example.chaticalmusic;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

public class FullPlayerBottomSheet extends BottomSheetDialogFragment {

    private MediaController mediaController;

    private ImageView fullArt;
    private TextView fullTitle;
    private TextView fullArtist;
    private SeekBar fullSeekBar;
    private TextView fullCurrentTime;
    private TextView fullDuration;
    private ImageButton fullPlayPause;
    private ImageButton fullSkipPrev;
    private ImageButton fullSkipNext;

    private final Handler seekHandler = new Handler(Looper.getMainLooper());
    private Runnable seekRunnable;
    private boolean isUserSeeking = false;
    private Player.Listener controllerListener;
    private LoveAnimationHelper loveAnimationHelper;

    public static FullPlayerBottomSheet newInstance() {
        return new FullPlayerBottomSheet();
    }

    public void setMediaController(MediaController controller) {
        this.mediaController = controller;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_full_player, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        fullArt = view.findViewById(R.id.fullArt);
        fullTitle = view.findViewById(R.id.fullTitle);
        fullArtist = view.findViewById(R.id.fullArtist);
        fullSeekBar = view.findViewById(R.id.fullSeekBar);
        fullCurrentTime = view.findViewById(R.id.fullCurrentTime);
        fullDuration = view.findViewById(R.id.fullDuration);
        fullPlayPause = view.findViewById(R.id.fullPlayPause);
        fullSkipPrev = view.findViewById(R.id.fullSkipPrev);
        fullSkipNext = view.findViewById(R.id.fullSkipNext);

        loveAnimationHelper = new LoveAnimationHelper(view.findViewById(R.id.love_animation_container));
        loveAnimationHelper.start();

        // Seekbar live update (Handler loop)
        seekRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaController != null && mediaController.isPlaying() && !isUserSeeking) {
                    long pos = mediaController.getCurrentPosition();
                    fullSeekBar.setProgress((int) pos);
                    fullCurrentTime.setText(formatTime(pos));
                }
                seekHandler.postDelayed(this, 1000);
            }
        };
        seekHandler.post(seekRunnable);

        // SeekBar drag (always enabled in solo listening mode)
        fullSeekBar.setEnabled(true);
        fullSeekBar.setAlpha(1.0f);

        fullSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    fullCurrentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaController != null) {
                    mediaController.seekTo(seekBar.getProgress());
                }
                isUserSeeking = false;
            }
        });

        // Control buttons click listeners
        fullPlayPause.setOnClickListener(v -> {
            if (mediaController != null) {
                if (mediaController.isPlaying()) {
                    mediaController.pause();
                    fullPlayPause.setImageResource(R.drawable.ic_play_arrow);
                } else {
                    mediaController.play();
                    fullPlayPause.setImageResource(R.drawable.ic_pause);
                }
            }
        });

        fullSkipNext.setOnClickListener(v -> {
            if (mediaController != null) {
                mediaController.seekToNextMediaItem();
                seekHandler.postDelayed(this::updateMetadata, 300);
            }
        });

        fullSkipPrev.setOnClickListener(v -> {
            if (mediaController != null) {
                if (mediaController.getCurrentPosition() > 3000) {
                    mediaController.seekTo(0);
                } else {
                    mediaController.seekToPreviousMediaItem();
                }
                seekHandler.postDelayed(this::updateMetadata, 300);
            }
        });

        // Populate metadata on open
        updateMetadata();
    }

    private void updateMetadata() {
        if (mediaController == null) return;

        MediaMetadata meta = mediaController.getMediaMetadata();
        if (meta != null) {
            if (meta.title != null) {
                fullTitle.setText(meta.title);
            } else {
                fullTitle.setText("Track Title");
            }
            if (meta.artist != null) {
                fullArtist.setText(meta.artist);
            } else {
                fullArtist.setText("Artist Name");
            }
            if (meta.artworkUri != null) {
                Glide.with(this)
                        .asBitmap()
                        .load(meta.artworkUri)
                        .placeholder(R.drawable.ic_music_placeholder)
                        .error(R.drawable.ic_music_placeholder)
                        .into(new CustomTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                                fullArt.setImageBitmap(bitmap);
                                Palette.from(bitmap).generate(palette -> {
                                    if (palette != null) {
                                        int vibrant = palette.getVibrantColor(0xFF22C55E);
                                        fullSeekBar.getProgressDrawable().setColorFilter(vibrant, android.graphics.PorterDuff.Mode.SRC_IN);
                                        if (fullSeekBar.getThumb() != null) {
                                            fullSeekBar.getThumb().setColorFilter(vibrant, android.graphics.PorterDuff.Mode.SRC_IN);
                                        }
                                        fullPlayPause.setColorFilter(vibrant, android.graphics.PorterDuff.Mode.SRC_IN);
                                    }
                                });
                            }

                            @Override
                            public void onLoadCleared(@Nullable Drawable placeholder) {
                                fullArt.setImageDrawable(placeholder);
                            }
                        });
            } else {
                fullArt.setImageResource(R.drawable.ic_music_placeholder);
            }
        }

        // Update duration
        long duration = mediaController.getContentDuration();
        if (duration > 0) {
            fullSeekBar.setMax((int) duration);
            fullDuration.setText(formatTime(duration));
        } else {
            fullSeekBar.setMax(0);
            fullDuration.setText("00:00");
        }

        // Update play/pause icon
        if (mediaController.isPlaying()) {
            fullPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            fullPlayPause.setImageResource(R.drawable.ic_play_arrow);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mediaController != null) {
            controllerListener = new Player.Listener() {
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    fullPlayPause.setImageResource(
                            isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
                } 

                @Override
                public void onMediaMetadataChanged(MediaMetadata metadata) {
                    updateMetadata();
                }

                @Override
                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                    updateMetadata();
                }
            };
            mediaController.addListener(controllerListener);
            updateMetadata();
        }
    }

    @Override
    public void onStop() {
        if (loveAnimationHelper != null) loveAnimationHelper.stop();
        if (mediaController != null && controllerListener != null) {
            mediaController.removeListener(controllerListener);
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        if (seekRunnable != null) {
            seekHandler.removeCallbacks(seekRunnable);
        }
        super.onDestroyView();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            FrameLayout bottomSheet = bsd.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior =
                    BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
                behavior.setPeekHeight(0);
                behavior.setDraggable(true);
                behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        if (newState == BottomSheetBehavior.STATE_HIDDEN ||
                            newState == BottomSheetBehavior.STATE_COLLAPSED) {
                            dismiss();
                        }
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
                });
            }
        });
        return dialog;
    }

    @Override
    public int getTheme() {
        return R.style.FullScreenBottomSheetTheme;
    }

    private String formatTime(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
}
