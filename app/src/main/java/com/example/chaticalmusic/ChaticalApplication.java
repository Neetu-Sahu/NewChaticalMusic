package com.example.chaticalmusic;

import android.app.Application;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

public class ChaticalApplication extends Application {

    public static String CURRENT_CHAT_ID = null;

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            private int running = 0;

            @Override
            public void onActivityStarted(android.app.Activity activity) {
                if (++running == 1) {
                    updateStatus(true);
                }
            }

            @Override
            public void onActivityStopped(android.app.Activity activity) {
                if (--running == 0) {
                    updateStatus(false);
                }
            }

            @Override public void onActivityCreated(android.app.Activity activity, android.os.Bundle savedInstanceState) {}
            @Override public void onActivityResumed(android.app.Activity activity) {}
            @Override public void onActivityPaused(android.app.Activity activity) {}
            @Override public void onActivitySaveInstanceState(android.app.Activity activity, android.os.Bundle outState) {}
            @Override public void onActivityDestroyed(android.app.Activity activity) {}
        });
    }

    private void updateStatus(boolean online) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid != null) {
            FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(uid).child(FirebasePaths.ONLINE).setValue(online);
            if (!online) {
                FirebaseDatabase.getInstance().getReference(FirebasePaths.USERS).child(uid).child(FirebasePaths.LAST_ACTIVE).setValue(ServerValue.TIMESTAMP);
            }
        }
    }
}
