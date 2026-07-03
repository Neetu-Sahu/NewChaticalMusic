package com.example.chaticalmusic;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.chaticalmusic.model.NotificationModel;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class NotificationHelper {

    public static void sendNotification(Context context, String targetUid, String type) {
        SharedPreferences prefs = context.getSharedPreferences("MusicalChatPrefs", Context.MODE_PRIVATE);
        String myUid = prefs.getString("user_uid", "");
        String myName = prefs.getString("display_name", "Someone");
        String myPhoto = prefs.getString("photo_url", "");

        DatabaseReference notificationsRef = FirebaseDatabase.getInstance().getReference(FirebasePaths.NOTIFICATIONS).child(targetUid);
        String id = notificationsRef.push().getKey();
        
        NotificationModel notification = new NotificationModel(type, myUid, myName, myPhoto);
        notification.setId(id);
        
        if (id != null) {
            notificationsRef.child(id).setValue(notification);
        }
    }
}