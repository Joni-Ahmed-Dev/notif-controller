package com.joni.notifcontroller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** v1.3 behaviour, v1.4 look (BikeGuard Master Design Reference):
 * each row has ONLY a select checkbox, no per-app ON/OFF button.
 * The master button acts ONLY on ticked apps. Ticking only selects; it
 * never turns notifications on or off by itself. Long-press a row to
 * remove that app from the controlled list. */
public class MainActivity extends Activity {

    private static final String PREFS = "notif_controller";
    private static final String KEY_LAST_ACTION_TEXT = "last_action_text";
    private static final String KEY_LAST_ACTION_AT = "last_action_at";

    private final ZoneId zone = ZoneId.of("Asia/Dhaka");
    private final DateTimeFormatter timeFmt =
            DateTimeFormatter.ofPattern("d MMM, h:mm a", new Locale("bn"));

    private List<AppListHelper.AppInfo> apps = new ArrayList<>();
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        BlockedStore.migrateV12IfNeeded(this);
        BlockedStore.migrateV13IfNeeded(this);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnSync).setOnClickListener(v -> {
            Toast.makeText(this, "সিঙ্ক হচ্ছে", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                final boolean ok = SyncHelper.doSync(getApplicationContext());
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            ok ? "সিঙ্ক হয়ে গেছে" : "সিঙ্ক ব্যর্থ হয়েছে",
                            Toast.LENGTH_SHORT).show();
                    refresh();
                });
            }).start();
        });
        findViewById(R.id.btnGrant).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        findViewById(R.id.btnMasterToggle).setOnClickListener(v -> masterToggle());
        findViewById(R.id.btnAddApp).setOnClickListener(v ->
                startActivity(new Intent(this, AddAppActivity.class)));

        ListView listView = findViewById(R.id.lvApps);
        adapter = new AppAdapter(this);
        listView.setAdapter(adapter);
        // Row tap toggles the checkbox (the checkbox itself is not
        // clickable, so the tap lands on the row).
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= apps.size()) {
                return;
            }
            AppListHelper.AppInfo app = apps.get(position);
            BlockedStore.setChecked(this, app.pkg,
                    !BlockedStore.isChecked(this, app.pkg));
            adapter.notifyDataSetChanged();
            updateMasterState();
        });
        // Long-press removes the app from the controlled list (and unblocks
        // it). There is no remove button in the row any more.
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= apps.size()) {
                return false;
            }
            AppListHelper.AppInfo app = apps.get(position);
            BlockedStore.removeControlled(this, app.pkg);
            WidgetUpdater.updateAllWidgets(this);
            SyncHelper.reportLocalChange(this, "app");
            Toast.makeText(this, app.label + " সরিয়ে দেওয়া হয়েছে",
                    Toast.LENGTH_SHORT).show();
            refresh();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** Master button: acts ONLY on ticked apps. If every ticked app is
     * already blocked, unblocks them all; otherwise blocks them all.
     * Unticked controlled apps are left completely untouched. One write,
     * one widget update, one status report. */
    private void masterToggle() {
        LinkedHashSet<String> checked = BlockedStore.getChecked(this);
        if (checked.isEmpty()) {
            return;
        }
        boolean allBlocked = true;
        for (String pkg : checked) {
            if (!BlockedStore.isBlocked(this, pkg)) {
                allBlocked = false;
                break;
            }
        }
        BlockedStore.setBlockedAll(this, checked, !allBlocked);
        WidgetUpdater.updateAllWidgets(this);
        SyncHelper.reportLocalChange(this, "master");
        String actionText = (allBlocked ? "চালু করা হয়েছে: " : "বন্ধ করা হয়েছে: ")
                + checked.size() + "টি অ্যাপ";
        Toast.makeText(this, actionText, Toast.LENGTH_SHORT).show();
        saveLastAction(actionText);
        refresh();
    }

    private void saveLastAction(String text) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_LAST_ACTION_TEXT, text)
                .putLong(KEY_LAST_ACTION_AT, System.currentTimeMillis())
                .apply();
    }

    private void showLastAction() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String text = p.getString(KEY_LAST_ACTION_TEXT, null);
        TextView tv = findViewById(R.id.tvLastAction);
        if (text == null || text.isEmpty()) {
            tv.setVisibility(View.GONE);
            return;
        }
        long at = p.getLong(KEY_LAST_ACTION_AT, 0L);
        tv.setVisibility(View.VISIBLE);
        tv.setText("শেষ কাজ: " + text + (at > 0
                ? " (" + timeFmt.format(Instant.ofEpochMilli(at).atZone(zone)) + ")"
                : ""));
    }

    private void refresh() {
        boolean granted = NotifListenerService.isEnabled(this);
        setText(R.id.tvListenerState,
                granted ? "Notification Access: দেওয়া আছে"
                        : "Notification Access: দেওয়া হয়নি, উপরের বোতাম চাপো");
        findViewById(R.id.ivListenerDot).setBackgroundResource(
                granted ? R.drawable.dot_green : R.drawable.dot_peach);
        LinkedHashSet<String> controlled = BlockedStore.getControlled(this);
        int blockedCount = 0;
        for (String pkg : controlled) {
            if (BlockedStore.isBlocked(this, pkg)) {
                blockedCount++;
            }
        }
        int checkedCount = BlockedStore.getChecked(this).size();
        setText(R.id.tvStatChecked, "টিক দেওয়া " + checkedCount + "টি");
        setText(R.id.tvBlockedCount, "বন্ধ " + blockedCount + "টি");
        setText(R.id.tvStatOn, "চালু " + (controlled.size() - blockedCount) + "টি");
        String lastPkg = BlockedStore.getLastBlockedPkg(this);
        long lastAt = BlockedStore.getLastBlockedAt(this);
        if (lastPkg == null) {
            setText(R.id.tvLastBlocked, "শেষ বন্ধ করা নোটিফিকেশন: এখনো নেই");
        } else {
            setText(R.id.tvLastBlocked, "শেষ বন্ধ করা নোটিফিকেশন: "
                    + AppListHelper.labelForPackage(this, lastPkg)
                    + " (" + timeFmt.format(Instant.ofEpochMilli(lastAt).atZone(zone)) + ")");
        }
        long synced = BlockedStore.lastSyncedAt(this);
        setText(R.id.tvSync, "শেষ সিঙ্ক: " + (synced > 0
                ? timeFmt.format(Instant.ofEpochMilli(synced).atZone(zone))
                : "এখনো হয়নি"));
        showLastAction();
        boolean empty = controlled.isEmpty();
        findViewById(R.id.layoutEmpty).setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.lvApps).setVisibility(empty ? View.GONE : View.VISIBLE);
        updateMasterState();
        rebuildControlledList(controlled);
    }

    /** Master button text/state from the ticked set only. Does not rebuild
     * the list, so ticking a checkbox does not reset the scroll position.
     * The button colour says what will happen: green to turn ON, dark to
     * turn OFF, grey when disabled. */
    private void updateMasterState() {
        LinkedHashSet<String> checked = BlockedStore.getChecked(this);
        Button masterBtn = findViewById(R.id.btnMasterToggle);
        if (checked.isEmpty()) {
            setText(R.id.tvMasterState, "টিক দেওয়া: ০টি অ্যাপ");
            masterBtn.setText("আগে নিচে থেকে অ্যাপে টিক দাও");
            masterBtn.setBackgroundResource(R.drawable.btn_master_disabled);
            masterBtn.setTextColor(getColor(R.color.muted));
            masterBtn.setEnabled(false);
            return;
        }
        int blockedAmongChecked = 0;
        for (String pkg : checked) {
            if (BlockedStore.isBlocked(this, pkg)) {
                blockedAmongChecked++;
            }
        }
        boolean allBlocked = blockedAmongChecked == checked.size();
        setText(R.id.tvMasterState, "টিক দেওয়া: " + checked.size()
                + "টি অ্যাপ, বন্ধ: " + blockedAmongChecked + "টি");
        if (allBlocked) {
            masterBtn.setText("সব চালু করো");
            masterBtn.setBackgroundResource(R.drawable.btn_master_on);
            masterBtn.setTextColor(getColor(R.color.ink));
        } else {
            masterBtn.setText("সব বন্ধ করো");
            masterBtn.setBackgroundResource(R.drawable.btn_master_off);
            masterBtn.setTextColor(getColor(R.color.white));
        }
        masterBtn.setEnabled(true);
    }

    private void rebuildControlledList(LinkedHashSet<String> controlled) {
        List<AppListHelper.AppInfo> next = new ArrayList<>();
        for (String pkg : controlled) {
            next.add(new AppListHelper.AppInfo(
                    AppListHelper.labelForPackage(this, pkg), pkg));
        }
        apps = next;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void setText(int id, String text) {
        ((TextView) findViewById(id)).setText(text);
    }

    private class AppAdapter extends BaseAdapter {
        private final Context ctx;
        private final LayoutInflater inflater;
        private final HashMap<String, Drawable> iconCache = new HashMap<>();

        AppAdapter(Context ctx) {
            this.ctx = ctx;
            this.inflater = LayoutInflater.from(ctx);
        }

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = inflater.inflate(R.layout.app_row, parent, false);
            }
            AppListHelper.AppInfo app = apps.get(position);
            ((TextView) row.findViewById(R.id.tvAppLabel)).setText(app.label);
            ((TextView) row.findViewById(R.id.tvAppPkg)).setText(app.pkg);
            ImageView icon = row.findViewById(R.id.ivAppIcon);
            Drawable d = iconCache.get(app.pkg);
            if (d == null) {
                try {
                    d = ctx.getPackageManager().getApplicationIcon(app.pkg);
                    iconCache.put(app.pkg, d);
                } catch (Exception ignored) {
                    // No icon: leave the ImageView empty.
                }
            }
            if (d != null) {
                icon.setImageDrawable(d);
            }
            TextView state = row.findViewById(R.id.tvRowState);
            boolean blocked = BlockedStore.isBlocked(ctx, app.pkg);
            state.setText(blocked ? "বন্ধ" : "চালু");
            state.setBackgroundResource(
                    blocked ? R.drawable.pill_off : R.drawable.pill_green);
            CheckBox cb = row.findViewById(R.id.cbSelect);
            // Clear the listener first so setChecked during bind does not
            // fire it on a recycled row.
            cb.setOnCheckedChangeListener(null);
            cb.setChecked(BlockedStore.isChecked(ctx, app.pkg));
            cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                BlockedStore.setChecked(ctx, app.pkg, isChecked);
                // Ticking only selects; it never blocks or unblocks.
                updateMasterState();
            });
            return row;
        }
    }
}
