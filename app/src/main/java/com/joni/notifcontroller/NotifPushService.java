package com.joni.notifcontroller;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONArray;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Receives instant push commands from Muse via Firebase Cloud Messaging.
 * The Worker sends data-only messages (no "notification" payload), so this
 * runs even when the app is in the background: the blocked list is applied
 * immediately instead of waiting for the 15-minute poll, which stays as
 * the backup. The payload is {"blocked": ["pkg", ...], "updated_at": iso}.
 */
public class NotifPushService extends FirebaseMessagingService {

    public static final String TOPIC = "notif-controller";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        subscribeToTopic();
    }

    public static void subscribeToTopic() {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC);
        } catch (Exception ignored) {
            // Firebase not initialised (e.g. a dev build without
            // google-services.json). Polling sync still works.
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();
        String blockedJson = data.get("blocked");
        String updatedAt = data.get("updated_at");
        if (blockedJson == null) {
            return;
        }
        try {
            Set<String> blocked = new HashSet<>();
            JSONArray arr = new JSONArray(blockedJson);
            for (int i = 0; i < arr.length(); i++) {
                blocked.add(arr.getString(i));
            }
            long remoteMillis;
            try {
                remoteMillis = (updatedAt != null && !updatedAt.isEmpty())
                        ? Instant.parse(updatedAt).toEpochMilli()
                        : System.currentTimeMillis();
            } catch (Exception e) {
                remoteMillis = System.currentTimeMillis();
            }
            BlockedStore.migrateV12IfNeeded(this);
            BlockedStore.migrateV13IfNeeded(this);
            long localMillis = BlockedStore.getLocalUpdatedAt(this);
            if (localMillis != 0L && remoteMillis <= localMillis) {
                // A stale push (an older command arriving after a newer
                // local change): do not let it undo the local choice.
                SyncHelper.reportLocalChange(this, "push-stale");
                return;
            }
            // A remote block for an app the user has not added yet still
            // works: add it to the controlled list and tick it first.
            for (String pkg : blocked) {
                BlockedStore.addControlled(this, pkg);
                BlockedStore.setChecked(this, pkg, true);
            }
            BlockedStore.setBlockedSet(this, blocked, remoteMillis);
            WidgetUpdater.updateAllWidgets(this);
            try {
                BlockedStore.postStatus(this, "push");
            } catch (Exception ignored) {
                // Status reporting is best effort.
            }
        } catch (Exception ignored) {
            // A malformed push must never crash the app.
        }
    }
}
