package com.joni.notifcontroller;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Keeps the blocked list fresh with a repeating inexact alarm (replaces
 * WorkManager, which would need an external dependency). */
public class SyncScheduler {

    private static final int REQ_CODE = 3001;
    // 15 minutes: picks up blocked list changes Muse makes without a manual sync.
    // One tiny HTTPS GET per interval; negligible battery use.
    private static final long INTERVAL_MS = 15L * 60L * 1000L;

    public static void scheduleRepeating(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, SyncReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, REQ_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + INTERVAL_MS, INTERVAL_MS, pi);
    }
}
