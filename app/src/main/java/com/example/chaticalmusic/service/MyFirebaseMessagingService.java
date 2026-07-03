package com.example.chaticalmusic.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import com.example.chaticalmusic.ChaticalApplication;
import com.example.chaticalmusic.DirectChatActivity;
import com.example.chaticalmusic.FirebasePaths;
import com.example.chaticalmusic.R;
import com.example.chaticalmusic.model.ChatMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        if (remoteMessage.getData().size() > 0) {
            String title = remoteMessage.getData().get("title");
            String body = remoteMessage.getData().get("body");
            String senderUid = remoteMessage.getData().get("senderUid");
            String senderName = remoteMessage.getData().get("senderName");
            String chatId = remoteMessage.getData().get("chatId");
            String messageId = remoteMessage.getData().get("messageId");
            
            if (chatId != null && messageId != null) {
                FirebaseDatabase.getInstance().getReference(FirebasePaths.DIRECT_MESSAGES)
                        .child(chatId).child(FirebasePaths.MESSAGES).child(messageId)
                        .child("status").setValue(ChatMessage.STATUS_DELIVERED);
            }

            // Don't show notification if already in this chat
            if (chatId != null && chatId.equals(ChaticalApplication.CURRENT_CHAT_ID)) {
                return;
            }

            showNotification(title, body, senderUid, senderName);
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(uid).child("fcm_token").setValue(token);
        }
    }

    private void showNotification(String title, String body, String senderUid, String senderName) {
        // Check if chat is muted
        if (senderUid != null) {
            android.content.SharedPreferences prefs = getSharedPreferences("MutedChats", Context.MODE_PRIVATE);
            long mutedUntil = prefs.getLong(senderUid, 0);
            if (mutedUntil == -1 || (mutedUntil > 0 && mutedUntil > System.currentTimeMillis())) {
                return; // Muted
            }
        }

        String channelId = "chat_messages";
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Chat Messages", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, DirectChatActivity.class);
        intent.putExtra("TARGET_UID", senderUid);
        intent.putExtra("TARGET_NAME", senderName);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setVibrate(new long[]{1000, 1000, 1000, 1000, 1000})
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        notificationManager.notify(senderUid != null ? senderUid.hashCode() : 0, builder.build());
    }
}
