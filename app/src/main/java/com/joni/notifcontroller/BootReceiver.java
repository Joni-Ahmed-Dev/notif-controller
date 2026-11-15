package com.joni.notifcontroller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms the periodic sync after a reboot (alarms do not survive reboot). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        SyncScheduler.scheduleRepeating(context.getApplicationContext());
    }
}
