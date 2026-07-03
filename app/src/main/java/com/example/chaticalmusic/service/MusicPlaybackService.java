package com.example.chaticalmusic.service;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;

import com.example.chaticalmusic.FirebasePaths;
import com.example.chaticalmusic.model.PlaybackState;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

public class MusicPlaybackService extends MediaSessionService {

    private static SimpleCache sCache;
    private ExoPlayer mPlayer;
    private MediaSession mMediaSession;

    // Phase 2 Room parameters
    private String mRoomId;
    private String mUid;
    private String mHostId;
    private boolean mIsHost;
    private boolean mIsCoDj;
    private final List<String> mCoDjUids = new ArrayList<>();

    private DatabaseReference mRoomsRef;
    private DatabaseReference mRoomMetaRef;
    private DatabaseReference mPlaybackStateRef;
    private DatabaseReference mPresenceRef;
    private DatabaseReference mQueueRef;
    private DatabaseReference mCoDjsRef;

    private ValueEventListener mRoomMetaListener;
    private ValueEventListener mPlaybackStateListener;
    private ValueEventListener mNeedsNewHostListener;
    private ValueEventListener mQueueListener;
    private ValueEventListener mCoDjsListener;

    private DatabaseReference mConnectedRef;
    private ValueEventListener mConnectionListener;

    private String mCurrentTrackKey;

    private final Handler mWriteHandler = new Handler(Looper.getMainLooper());
    private final Runnable mWriteRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIsHost && mRoomId != null) {
                writeHostPlaybackState();
                mWriteHandler.postDelayed(this, 5000);
            }
        }
    };

    private static synchronized SimpleCache getCache(Context context) {
        if (sCache == null) {
            File cacheDir = new File(context.getCacheDir(), "media_cache");
            LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024); // 100MB
            StandaloneDatabaseProvider databaseProvider = new StandaloneDatabaseProvider(context);
            sCache = new SimpleCache(cacheDir, evictor, databaseProvider);
        }
        return sCache;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Create cache data source factory
        CacheDataSource.Factory cacheDataSourceFactory = new CacheDataSource.Factory()
                .setCache(getCache(this))
                .setUpstreamDataSourceFactory(new DefaultHttpDataSource.Factory());

        // 2. Create MediaSource factory with caching
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(cacheDataSourceFactory);

        // 3. Create ExoPlayer instance
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();

        mPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build();

        mPlayer.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (mIsHost) {
                    writeHostPlaybackState();
                }
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (mIsHost && mRoomId != null) {
                    writeHostPlaybackState();
                }
            }

            @Override
            public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK && mIsHost) {
                    writeHostPlaybackState();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED && mIsHost && mRoomId != null) {
                    if (mCurrentTrackKey != null) {
                        mQueueRef.child(mCurrentTrackKey).removeValue();
                    }
                    if (mQueueRef != null) {
                        mQueueRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                if (!snapshot.exists() || snapshot.getChildrenCount() <= 1) {
                                    writeEmptyPlaybackState();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
                    }
                }
            }
        });

        // 4. Create MediaSession wrapping the ExoPlayer
        mMediaSession = new MediaSession.Builder(this, mPlayer)
                .setCallback(new MediaSession.Callback() {
                    @NonNull
                    @Override
                    public MediaSession.ConnectionResult onConnect(
                            @NonNull MediaSession session,
                            @NonNull MediaSession.ControllerInfo controllerInfo) {
                        MediaSession.ConnectionResult.AcceptedResultBuilder builder =
                                new MediaSession.ConnectionResult.AcceptedResultBuilder(session);
                        builder.setAvailableSessionCommands(
                                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                        .add(new SessionCommand("JOIN_ROOM", Bundle.EMPTY))
                                        .add(new SessionCommand("LEAVE_ROOM", Bundle.EMPTY))
                                        .add(new SessionCommand("SKIP_TRACK", Bundle.EMPTY))
                                        .build()
                        );
                        return builder.build();
                    }

                    @NonNull
                    @Override
                    public ListenableFuture<SessionResult> onCustomCommand(
                            @NonNull MediaSession session,
                            @NonNull MediaSession.ControllerInfo controllerInfo,
                            @NonNull SessionCommand customCommand,
                            @NonNull Bundle args) {
                        if (customCommand.customAction.equals("JOIN_ROOM")) {
                            String roomId = args.getString("ROOM_ID");
                            String uid = args.getString("UID");
                            joinRoom(roomId, uid);
                            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
                        } else if (customCommand.customAction.equals("LEAVE_ROOM")) {
                            leaveRoom();
                            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
                        } else if (customCommand.customAction.equals("SKIP_TRACK")) {
                            if ((mIsHost || mIsCoDj) && mCurrentTrackKey != null) {
                                mQueueRef.child(mCurrentTrackKey).removeValue();
                            }
                            return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
                        }
                        return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_ERROR_UNKNOWN));
                    }
                })
                .build();
    }

    private void joinRoom(String roomId, String uid) {
        // Leave previous room first
        leaveRoom();

        mRoomId = roomId;
        mUid = uid;

        mRoomsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS).child(mRoomId);
        mRoomMetaRef = mRoomsRef.child(FirebasePaths.ROOM_META);
        mPlaybackStateRef = mRoomsRef.child(FirebasePaths.PLAYBACK_STATE);
        mPresenceRef = mRoomsRef.child(FirebasePaths.PRESENCE).child(mUid);
        mCoDjsRef = mRoomMetaRef.child(FirebasePaths.CO_DJS);

        // Step 5a — Presence Setup
        mPresenceRef.setValue(ServerValue.TIMESTAMP);
        mPresenceRef.onDisconnect().removeValue();

        // Listen to room_meta for host identification
        mRoomMetaListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String hostId = snapshot.child(FirebasePaths.HOST_ID).getValue(String.class);
                    mHostId = hostId;
                    boolean wasHost = mIsHost;
                    mIsHost = mUid.equals(mHostId);

                    if (mIsHost) {
                        // Cancel fallback overrides if we were not host before
                        if (!wasHost) {
                            startHostWriteLoop();
                            // Register onDisconnect for host fallback
                            mRoomMetaRef.child(FirebasePaths.NEEDS_NEW_HOST).onDisconnect().setValue(true);
                            mRoomMetaRef.child(FirebasePaths.HOST_DISCONNECTED_AT).onDisconnect().setValue(ServerValue.TIMESTAMP);
                        }
                    } else {
                        // Stop write loop if it was running
                        if (wasHost) {
                            stopHostWriteLoop();
                            mRoomMetaRef.child(FirebasePaths.NEEDS_NEW_HOST).onDisconnect().cancel();
                            mRoomMetaRef.child(FirebasePaths.HOST_DISCONNECTED_AT).onDisconnect().cancel();
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mRoomMetaRef.addValueEventListener(mRoomMetaListener);

        // Listen to Co-DJs
        mCoDjsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mCoDjUids.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Boolean isCoDj = child.getValue(Boolean.class);
                    if (isCoDj != null && isCoDj) mCoDjUids.add(child.getKey());
                }
                mIsCoDj = mCoDjUids.contains(mUid);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mCoDjsRef.addValueEventListener(mCoDjsListener);

        // Step 5b — Guest Fallback Listener
        mNeedsNewHostListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (mIsHost) return; // Host does not need fallback
                Boolean needsNewHost = snapshot.getValue(Boolean.class);
                if (needsNewHost != null && needsNewHost) {
                    startHostFallbackFlow();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mRoomMetaRef.child(FirebasePaths.NEEDS_NEW_HOST).addValueEventListener(mNeedsNewHostListener);

        mQueueRef = mRoomsRef.child(FirebasePaths.QUEUE);
        setupQueueListener();

        // Step 4 — Guest Sync Listener (Latency Compensation Algorithm)
        mPlaybackStateListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (mIsHost || !snapshot.exists()) return; // Host does not sync to DB

                Long snapshotPositionMs = snapshot.child(FirebasePaths.CURRENT_POSITION_MS).getValue(Long.class);
                Long snapshotSystemTime = snapshot.child(FirebasePaths.LAST_UPDATED_SYSTEM_TIME).getValue(Long.class);
                Boolean snapshotIsPlaying = snapshot.child(FirebasePaths.IS_PLAYING).getValue(Boolean.class);
                String snapshotTrackUrl = snapshot.child(FirebasePaths.CURRENT_TRACK_URL).getValue(String.class);
                String snapshotTrackTitle = snapshot.child(FirebasePaths.CURRENT_TRACK_TITLE).getValue(String.class);
                String snapshotTrackArtist = snapshot.child(FirebasePaths.CURRENT_TRACK_ARTIST).getValue(String.class);
                String snapshotTrackArtUrl = snapshot.child(FirebasePaths.CURRENT_TRACK_ART_URL).getValue(String.class);

                if (snapshotPositionMs == null || snapshotSystemTime == null || snapshotIsPlaying == null || snapshotTrackUrl == null) {
                    return;
                }

                // Latency Compensation
                long networkLatency = System.currentTimeMillis() - snapshotSystemTime;
                long targetPosition = snapshotPositionMs + networkLatency;
                long drift = Math.abs(mPlayer.getCurrentPosition() - targetPosition);

                // Get current loaded URI
                MediaItem currentItem = mPlayer.getCurrentMediaItem();
                String currentUrl = "";
                if (currentItem != null && currentItem.localConfiguration != null && currentItem.localConfiguration.uri != null) {
                    currentUrl = currentItem.localConfiguration.uri.toString();
                }

                if (snapshotTrackUrl.isEmpty()) {
                    if (mPlayer.getMediaItemCount() > 0) {
                        mPlayer.clearMediaItems();
                    }
                    mPlayer.pause();
                } else if (!currentUrl.equals(snapshotTrackUrl)) {
                    // Load new track
                    MediaItem mediaItem = new MediaItem.Builder()
                            .setUri(snapshotTrackUrl)
                            .setMediaId(snapshotTrackUrl)
                            .setMediaMetadata(new MediaMetadata.Builder()
                                    .setTitle(snapshotTrackTitle)
                                    .setArtist(snapshotTrackArtist)
                                    .setArtworkUri(snapshotTrackArtUrl != null && !snapshotTrackArtUrl.isEmpty() ? Uri.parse(snapshotTrackArtUrl) : null)
                                    .build())
                            .build();

                    mPlayer.setMediaItem(mediaItem);
                    mPlayer.seekTo(targetPosition);
                    mPlayer.prepare();
                    if (snapshotIsPlaying) {
                        mPlayer.play();
                    } else {
                        mPlayer.pause();
                    }
                } else {
                    // Check drift
                    if (drift > 1200) {
                        mPlayer.seekTo(targetPosition);
                    }
                    // Sync play state
                    if (snapshotIsPlaying && !mPlayer.isPlaying()) {
                        mPlayer.play();
                    } else if (!snapshotIsPlaying && mPlayer.isPlaying()) {
                        mPlayer.pause();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mPlaybackStateRef.addValueEventListener(mPlaybackStateListener);

        mConnectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        mConnectionListener = new ValueEventListener() {
            private boolean wasConnected = true;

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class);
                if (connected) {
                    if (!wasConnected) {
                        if (!mIsHost && mPlaybackStateRef != null) {
                            mPlaybackStateRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot playbackSnapshot) {
                                    if (mIsHost) return;
                                    if (mPlaybackStateListener != null) {
                                        mPlaybackStateListener.onDataChange(playbackSnapshot);
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {}
                            });
                        }
                        Toast.makeText(getApplicationContext(), "Reconnected", Toast.LENGTH_SHORT).show();
                    }
                    wasConnected = true;
                } else {
                    wasConnected = false;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mConnectedRef.addValueEventListener(mConnectionListener);
    }

    private void startHostWriteLoop() {
        mWriteHandler.removeCallbacks(mWriteRunnable);
        mWriteHandler.post(mWriteRunnable);
    }

    private void stopHostWriteLoop() {
        mWriteHandler.removeCallbacks(mWriteRunnable);
    }

    private void writeHostPlaybackState() {
        if (!mIsHost || mRoomId == null || mPlaybackStateRef == null) return;

        MediaItem currentItem = mPlayer.getCurrentMediaItem();
        String trackUrl = "";
        String trackTitle = "";
        String trackArtist = "";
        String trackArtUrl = "";

        if (currentItem != null) {
            trackUrl = currentItem.localConfiguration != null && currentItem.localConfiguration.uri != null ?
                    currentItem.localConfiguration.uri.toString() : "";
            if (currentItem.mediaMetadata != null) {
                trackTitle = currentItem.mediaMetadata.title != null ? currentItem.mediaMetadata.title.toString() : "";
                trackArtist = currentItem.mediaMetadata.artist != null ? currentItem.mediaMetadata.artist.toString() : "";
                trackArtUrl = currentItem.mediaMetadata.artworkUri != null ? currentItem.mediaMetadata.artworkUri.toString() : "";
            }
        }

        PlaybackState state = new PlaybackState(
                mPlayer.isPlaying(),
                mPlayer.getCurrentPosition(),
                System.currentTimeMillis(),
                trackUrl,
                trackTitle,
                trackArtist,
                trackArtUrl
        );

        mPlaybackStateRef.setValue(state);
    }

    private void writeEmptyPlaybackState() {
        if (!mIsHost || mRoomId == null || mPlaybackStateRef == null) return;
        PlaybackState state = new PlaybackState(
                false,
                0L,
                System.currentTimeMillis(),
                "",
                "",
                "",
                ""
        );
        mPlaybackStateRef.setValue(state);
    }

    private void startHostFallbackFlow() {
        // Deterministic Fallback: The person who has been in the room the longest (oldest presence) becomes host
        mPresenceRef.getParent().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String oldestUid = null;
                long oldestTime = Long.MAX_VALUE;

                for (DataSnapshot child : snapshot.getChildren()) {
                    // Assuming we store entry time in presence, or just use the first key in the list
                    oldestUid = child.getKey();
                    break; // Take the first available user for now
                }

                if (mUid.equals(oldestUid)) {
                    claimHostStatus();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void claimHostStatus() {
        mRoomMetaRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                currentData.child(FirebasePaths.HOST_ID).setValue(mUid);
                currentData.child(FirebasePaths.NEEDS_NEW_HOST).setValue(false);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (committed) {
                    mIsHost = true;
                    startHostWriteLoop();
                }
            }
        });
    }

    private void setupQueueListener() {
        if (mQueueRef == null) return;
        mQueueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!mIsHost && !mIsCoDj) return;

                List<DataSnapshot> children = new ArrayList<>();
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        children.add(child);
                    }
                }

                // Sort children by added_at
                Collections.sort(children, new Comparator<DataSnapshot>() {
                    @Override
                    public int compare(DataSnapshot o1, DataSnapshot o2) {
                        Long t1 = o1.child("added_at").getValue(Long.class);
                        Long t2 = o2.child("added_at").getValue(Long.class);
                        long val1 = t1 != null ? t1 : 0L;
                        long val2 = t2 != null ? t2 : 0L;
                        return Long.compare(val1, val2);
                    }
                });

                 if (children.isEmpty()) {
                     mCurrentTrackKey = null;
                     mPlayer.clearMediaItems();
                     mPlayer.pause();
                     writeEmptyPlaybackState();
                     return;
                 }

                DataSnapshot firstChild = children.get(0);
                String firstKey = firstChild.getKey();
                String firstUrl = firstChild.child(FirebasePaths.STREAM_URL).getValue(String.class);
                String firstTitle = firstChild.child(FirebasePaths.TRACK_TITLE).getValue(String.class);
                String firstArtist = firstChild.child(FirebasePaths.TRACK_ARTIST).getValue(String.class);
                String firstArtUrl = firstChild.child(FirebasePaths.ALBUM_ART_URL).getValue(String.class);

                // 1. Transition Check: Is there a new Q[0]?
                if (firstKey != null && !firstKey.equals(mCurrentTrackKey)) {
                    mCurrentTrackKey = firstKey;

                    if (firstUrl != null && !firstUrl.isEmpty()) {
                        MediaItem mediaItem = new MediaItem.Builder()
                                .setUri(firstUrl)
                                .setMediaId(firstUrl)
                                .setMediaMetadata(new MediaMetadata.Builder()
                                        .setTitle(firstTitle)
                                        .setArtist(firstArtist)
                                        .setArtworkUri(firstArtUrl != null && !firstArtUrl.isEmpty() ? Uri.parse(firstArtUrl) : null)
                                        .build())
                                .build();

                        mPlayer.setMediaItem(mediaItem);
                        mPlayer.prepare();
                        mPlayer.play();
                        writeHostPlaybackState();
                    }
                    return;
                }

                // 2. Downvote Skip Check for currently playing track Q[0]
                if (firstChild.getKey() != null && firstChild.getKey().equals(mCurrentTrackKey)) {
                    final String trackKey = firstChild.getKey();
                    Integer downvotesObj = firstChild.child(FirebasePaths.DOWNVOTES).getValue(Integer.class);
                    int downvotes = downvotesObj != null ? downvotesObj : 0;

                    DatabaseReference presenceRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS)
                            .child(mRoomId).child(FirebasePaths.PRESENCE);

                    presenceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot presenceSnapshot) {
                            if (!mIsHost && !mIsCoDj) return;
                            long activeMembers = presenceSnapshot.getChildrenCount();
                            if (activeMembers == 0) activeMembers = 1;

                            if (downvotes > (activeMembers / 2.0)) {
                                mQueueRef.child(trackKey).removeValue();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });
                }

                // 3. Downvote Check for upcoming tracks: if any upcoming track exceeds threshold, remove it from queue
                for (int i = 1; i < children.size(); i++) {
                    DataSnapshot upcomingChild = children.get(i);
                    String upcomingKey = upcomingChild.getKey();
                    if (upcomingKey != null) {
                        Integer downvotesObj = upcomingChild.child(FirebasePaths.DOWNVOTES).getValue(Integer.class);
                        int downvotes = downvotesObj != null ? downvotesObj : 0;

                        DatabaseReference presenceRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.ROOMS)
                                .child(mRoomId).child(FirebasePaths.PRESENCE);

                        presenceRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot presenceSnapshot) {
                                if (!mIsHost && !mIsCoDj) return;
                                long activeMembers = presenceSnapshot.getChildrenCount();
                                if (activeMembers == 0) activeMembers = 1;

                                if (downvotes > (activeMembers / 2.0)) {
                                    mQueueRef.child(upcomingKey).removeValue();
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {}
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        mQueueRef.addValueEventListener(mQueueListener);
    }

    private void leaveRoom() {
        stopHostWriteLoop();

        if (mRoomMetaRef != null && mRoomMetaListener != null) {
            mRoomMetaRef.removeEventListener(mRoomMetaListener);
        }
        if (mPlaybackStateRef != null && mPlaybackStateListener != null) {
            mPlaybackStateRef.removeEventListener(mPlaybackStateListener);
        }
        if (mRoomMetaRef != null && mNeedsNewHostListener != null) {
            mRoomMetaRef.child(FirebasePaths.NEEDS_NEW_HOST).removeEventListener(mNeedsNewHostListener);
        }

        if (mPresenceRef != null) {
            mPresenceRef.removeValue();
            mPresenceRef = null;
        }

        if (mQueueRef != null && mQueueListener != null) {
            mQueueRef.removeEventListener(mQueueListener);
            mQueueListener = null;
        }

        if (mCoDjsRef != null && mCoDjsListener != null) {
            mCoDjsRef.removeEventListener(mCoDjsListener);
            mCoDjsListener = null;
        }

        if (mConnectedRef != null && mConnectionListener != null) {
            mConnectedRef.removeEventListener(mConnectionListener);
            mConnectionListener = null;
            mConnectedRef = null;
        }

        mRoomId = null;
        mIsHost = false;
        mHostId = null;
    }

    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mMediaSession;
    }

    @Override
    public void onDestroy() {
        leaveRoom();
        if (mMediaSession != null) {
            mMediaSession.release();
            mMediaSession = null;
        }
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        super.onDestroy();
    }
}
