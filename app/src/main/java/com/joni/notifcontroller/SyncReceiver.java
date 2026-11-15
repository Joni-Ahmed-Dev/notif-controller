package com.joni.notifcontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Periodic (and manual) blocked list sync: pulls the list from the Worker
 * and reports the applied state back. */
public class SyncReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                SyncHelper.doSync(context.getApplicationContext());
            } finally {
                pending.finish();
            }
        }).start();
    }
}
