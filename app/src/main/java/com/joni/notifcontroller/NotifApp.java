package com.joni.notifcontroller;

import android.app.Application;

public class NotifApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SyncScheduler.scheduleRepeating(this);
        // Subscribe to the push topic so a Muse command reaches the phone
        // in seconds. Safe to call even before the first token refresh.
        NotifPushService.subscribeToTopic();
    }
}
