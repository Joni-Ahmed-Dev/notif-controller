package com.joni.notifcontroller;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Blocked package list, local counters, and the Worker fetch/post calls. */
public class BlockedStore {

    private static final String PREFS = "notif_controller";
    private static final String KEY_BLOCKED = "blocked_set";
    private static final String KEY_LAST_PKG = "last_blocked_pkg";
    private static final String KEY_LAST_AT = "last_blocked_at";
    private static final String KEY_COUNT = "blocked_count";
    private static final String KEY_LOCAL_UPDATED = "local_updated_at";
    private static final String KEY_SYNCED_AT = "synced_at";
    private static final String KEY_CONTROLLED = "controlled_set";
    private static final String KEY_CHECKED = "checked_set";
    private static final String KEY_V12_MIGRATED = "v12_migrated";
    private static final String KEY_V13_MIGRATED = "v13_migrated";

    public static class FetchResult {
        public final Set<String> blocked;
        public final Instant updatedAt;
        public final String raw;

        public FetchResult(Set<String> blocked, Instant updatedAt, String raw) {
            this.blocked = blocked;
            this.updatedAt = updatedAt;
            this.raw = raw;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Set<String> getBlocked(Context context) {
        Set<String> stored = prefs(context).getStringSet(KEY_BLOCKED, null);
        if (stored == null) {
            return new HashSet<>();
        }
        return new HashSet<>(stored);
    }

    public static boolean isBlocked(Context context, String pkg) {
        if (pkg == null) {
            return false;
        }
        return getBlocked(context).contains(pkg);
    }

    /** Local toggle from the app or a widget. Bumps local_updated_at so the
     * next sync keeps the local choice over an older remote list. */
    public static void setBlocked(Context context, String pkg, boolean blocked) {
        Set<String> current = getBlocked(context);
        if (blocked) {
            current.add(pkg);
        } else {
            current.remove(pkg);
        }
        prefs(context).edit()
                .putStringSet(KEY_BLOCKED, current)
                .putLong(KEY_LOCAL_UPDATED, System.currentTimeMillis())
                .apply();
    }

    /** Apply a remote list. local_updated_at becomes the remote timestamp so
     * the same remote list is not reapplied on every sync. */
    public static void setBlockedSet(Context context, Set<String> blocked, long updatedAtMillis) {
        prefs(context).edit()
                .putStringSet(KEY_BLOCKED, new HashSet<>(blocked))
                .putLong(KEY_LOCAL_UPDATED, updatedAtMillis)
                .putLong(KEY_SYNCED_AT, System.currentTimeMillis())
                .apply();
    }

    /** Controlled apps: the list the user deliberately added, in insertion
     * order. Stored as one JSON array string because getStringSet loses
     * order. The blocked set is a subset of this list. */
    public static java.util.LinkedHashSet<String> getControlled(Context context) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        String raw = prefs(context).getString(KEY_CONTROLLED, null);
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String pkg = arr.optString(i, null);
                if (pkg != null && !pkg.isEmpty()) {
                    out.add(pkg);
                }
            }
        } catch (Exception ignored) {
            // Corrupt value: treat as empty.
        }
        return out;
    }

    private static void saveControlled(Context context,
            java.util.LinkedHashSet<String> controlled) {
        JSONArray arr = new JSONArray();
        for (String pkg : controlled) {
            arr.put(pkg);
        }
        prefs(context).edit().putString(KEY_CONTROLLED, arr.toString()).apply();
    }

    public static boolean isControlled(Context context, String pkg) {
        if (pkg == null) {
            return false;
        }
        return getControlled(context).contains(pkg);
    }

    public static void addControlled(Context context, String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<String> current = getControlled(context);
        if (current.add(pkg)) {
            saveControlled(context, current);
        }
    }

    /** Removing also unblocks the app and unticks it. */
    public static void removeControlled(Context context, String pkg) {
        if (pkg == null) {
            return;
        }
        java.util.LinkedHashSet<String> current = getControlled(context);
        if (current.remove(pkg)) {
            saveControlled(context, current);
        }
        java.util.LinkedHashSet<String> checked = getChecked(context);
        if (checked.remove(pkg)) {
            saveChecked(context, checked);
        }
        if (isBlocked(context, pkg)) {
            setBlocked(context, pkg, false);
        }
    }

    /** Ticked apps: the only ones the master button acts on. Stored as one
     * JSON array string, same pattern as the controlled set, so the ticks
     * survive restarts. Ticking only selects; it never blocks or unblocks
     * anything by itself. */
    public static java.util.LinkedHashSet<String> getChecked(Context context) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        String raw = prefs(context).getString(KEY_CHECKED, null);
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String pkg = arr.optString(i, null);
                if (pkg != null && !pkg.isEmpty()) {
                    out.add(pkg);
                }
            }
        } catch (Exception ignored) {
            // Corrupt value: treat as empty.
        }
        return out;
    }

    private static void saveChecked(Context context,
            java.util.LinkedHashSet<String> checked) {
        JSONArray arr = new JSONArray();
        for (String pkg : checked) {
            arr.put(pkg);
        }
        prefs(context).edit().putString(KEY_CHECKED, arr.toString()).apply();
    }

    public static boolean isChecked(Context context, String pkg) {
        if (pkg == null) {
            return false;
        }
        return getChecked(context).contains(pkg);
    }

    public static void setChecked(Context context, String pkg, boolean checked) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<String> current = getChecked(context);
        if (checked) {
            current.add(pkg);
        } else {
            current.remove(pkg);
        }
        saveChecked(context, current);
    }

    /** v1.3 migration, once only: apps already in the controlled list from
     * v1.2 start ticked, so the master button keeps working on them without
     * the user having to re-tick everything. */
    public static void migrateV13IfNeeded(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getBoolean(KEY_V13_MIGRATED, false)) {
            return;
        }
        java.util.LinkedHashSet<String> controlled = getControlled(context);
        if (!controlled.isEmpty() && getChecked(context).isEmpty()) {
            saveChecked(context, controlled);
        }
        p.edit().putBoolean(KEY_V13_MIGRATED, true).apply();
    }

    /** One write for the master button: block or unblock every given app. */
    public static void setBlockedAll(Context context, Set<String> pkgs, boolean blocked) {
        Set<String> current = getBlocked(context);
        if (blocked) {
            current.addAll(pkgs);
        } else {
            current.removeAll(pkgs);
        }
        prefs(context).edit()
                .putStringSet(KEY_BLOCKED, current)
                .putLong(KEY_LOCAL_UPDATED, System.currentTimeMillis())
                .apply();
    }

    /** v1.2 migration, once only: the controlled list starts EMPTY and any
     * accidental blocks from the v1.1 full installed-app list are wiped. */
    public static void migrateV12IfNeeded(Context context) {
        SharedPreferences p = prefs(context);
        if (p.getBoolean(KEY_V12_MIGRATED, false)) {
            return;
        }
        p.edit()
                .putString(KEY_CONTROLLED, "[]")
                .putStringSet(KEY_BLOCKED, new HashSet<String>())
                .putBoolean(KEY_V12_MIGRATED, true)
                .apply();
    }

    public static long getLocalUpdatedAt(Context context) {
        return prefs(context).getLong(KEY_LOCAL_UPDATED, 0L);
    }

    public static long lastSyncedAt(Context context) {
        return prefs(context).getLong(KEY_SYNCED_AT, 0L);
    }

    public static int getBlockedCount(Context context) {
        return prefs(context).getInt(KEY_COUNT, 0);
    }

    public static String getLastBlockedPkg(Context context) {
        return prefs(context).getString(KEY_LAST_PKG, null);
    }

    public static long getLastBlockedAt(Context context) {
        return prefs(context).getLong(KEY_LAST_AT, 0L);
    }

    public static void recordBlocked(Context context, String pkg) {
        SharedPreferences p = prefs(context);
        p.edit()
                .putString(KEY_LAST_PKG, pkg)
                .putLong(KEY_LAST_AT, System.currentTimeMillis())
                .putInt(KEY_COUNT, p.getInt(KEY_COUNT, 0) + 1)
                .apply();
    }

    public static List<String> sortedBlocked(Context context) {
        List<String> list = new ArrayList<>(getBlocked(context));
        java.util.Collections.sort(list);
        return list;
    }

    /** Downloads the blocked list from the Worker. Throws on failure. */
    public static FetchResult fetchRemote() throws Exception {
        URL url = new URL(BuildConfig.NOTIF_WORKER_URL + "/blocked?token=" + BuildConfig.NOTIF_READ_TOKEN);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("GET");
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new Exception("blocked fetch: HTTP " + code);
            }
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            String body = sb.toString();
            JSONObject o = new JSONObject(body);
            JSONArray arr = o.optJSONArray("blocked");
            Set<String> blocked = new HashSet<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    blocked.add(arr.getString(i));
                }
            }
            Instant updatedAt = null;
            String updatedRaw = o.optString("updated_at", null);
            if (updatedRaw != null && !updatedRaw.isEmpty()) {
                try {
                    updatedAt = Instant.parse(updatedRaw);
                } catch (Exception ignored) {
                    updatedAt = null;
                }
            }
            return new FetchResult(blocked, updatedAt, body);
        } finally {
            conn.disconnect();
        }
    }

    /** Reports the applied state so Muse can see what the app did. Best effort. */
    public static void postStatus(Context context, String trigger) throws Exception {
        URL url = new URL(BuildConfig.NOTIF_WORKER_URL + "/status?token=" + BuildConfig.NOTIF_READ_TOKEN);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        JSONArray arr = new JSONArray();
        for (String pkg : sortedBlocked(context)) {
            arr.put(pkg);
        }
        body.put("blocked", arr);
        body.put("listener_on", NotifListenerService.isEnabled(context));
        // blocked_count is the size of the blocked set. The listener cancel
        // counter lives under cancelled_count (getBlockedCount is that counter).
        body.put("blocked_count", getBlocked(context).size());
        body.put("cancelled_count", getBlockedCount(context));
        JSONArray controlled = new JSONArray();
        for (String pkg : getControlled(context)) {
            controlled.put(pkg);
        }
        body.put("controlled", controlled);
        String lastPkg = getLastBlockedPkg(context);
        if (lastPkg != null) {
            body.put("last_blocked_pkg", lastPkg);
        }
        body.put("trigger", trigger);
        body.put("app_version", BuildConfig.VERSION_NAME);
        try {
            byte[] bytes = body.toString().getBytes("UTF-8");
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.close();
            int code = conn.getResponseCode();
            if (code < 200 || code > 299) {
                throw new Exception("status post: HTTP " + code);
            }
        } finally {
            conn.disconnect();
        }
    }
}
