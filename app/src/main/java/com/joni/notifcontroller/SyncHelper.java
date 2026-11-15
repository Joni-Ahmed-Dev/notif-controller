package com.joni.notifcontroller;

import android.content.Context;

/** Shared sync logic used by the periodic alarm and the manual sync button. */
public class SyncHelper {

    /** Returns true when the sync succeeded. */
    public static boolean doSync(Context appCtx) {
        try {
            BlockedStore.migrateV12IfNeeded(appCtx);
            BlockedStore.migrateV13IfNeeded(appCtx);
            BlockedStore.FetchResult r = BlockedStore.fetchRemote();
            long remoteMillis = r.updatedAt != null ? r.updatedAt.toEpochMilli() : 0L;
            long localMillis = BlockedStore.getLocalUpdatedAt(appCtx);
            if (r.updatedAt != null && remoteMillis > localMillis) {
                // A remote block for an app the user has not added yet still
                // works: add it to the controlled list and tick it first,
                // then apply. setBlockedSet overwrites local_updated_at
                // with the remote timestamp, so the bumps above are harmless.
                for (String pkg : r.blocked) {
                    BlockedStore.addControlled(appCtx, pkg);
                    BlockedStore.setChecked(appCtx, pkg, true);
                }
                BlockedStore.setBlockedSet(appCtx, r.blocked, remoteMillis);
            } else if (r.updatedAt == null && localMillis == 0L) {
                // First ever sync with no remote timestamp: take the remote list.
                for (String pkg : r.blocked) {
                    BlockedStore.addControlled(appCtx, pkg);
                    BlockedStore.setChecked(appCtx, pkg, true);
                }
                BlockedStore.setBlockedSet(appCtx, r.blocked, System.currentTimeMillis());
            }
            WidgetUpdater.updateAllWidgets(appCtx);
            try {
                BlockedStore.postStatus(appCtx, "sync");
            } catch (Exception ignored) {
                // status reporting is best effort
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** After a local change, report it so Muse can see the new state. */
    public static void reportLocalChange(Context appCtx, String trigger) {
        final Context ctx = appCtx.getApplicationContext();
        new Thread(() -> {
            try {
                BlockedStore.postStatus(ctx, trigger);
            } catch (Exception ignored) {
                // best effort
            }
        }).start();
    }
}
