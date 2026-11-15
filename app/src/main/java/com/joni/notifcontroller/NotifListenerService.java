package com.joni.notifcontroller;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Cancels notifications from blocked apps the moment they arrive.
 * Apps that are not blocked are left completely alone. */
public class NotifListenerService extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }
        String pkg = sbn.getPackageName();
        if (pkg == null) {
            return;
        }
        if (BlockedStore.isBlocked(this, pkg)) {
            try {
                cancelNotification(sbn.getKey());
            } catch (Exception ignored) {
                // The notification may already be gone; nothing to do.
            }
            BlockedStore.recordBlocked(this, pkg);
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
    }

    /** True when the user granted Notification Access to this service. */
    public static boolean isEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        String mine = new ComponentName(context, NotifListenerService.class).flattenToString();
        return enabled.contains(mine);
    }
}
