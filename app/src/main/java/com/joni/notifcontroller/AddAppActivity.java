package com.joni.notifcontroller;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Add apps to the controlled list one by one. Only apps not already
 * controlled are shown. Tapping one row adds it (notifications ON by
 * default, i.e. not blocked) and finishes back to MainActivity. */
public class AddAppActivity extends Activity {

    private List<AppListHelper.AppInfo> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_app);

        ListView listView = findViewById(R.id.lvAddApps);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= apps.size()) {
                return;
            }
            AppListHelper.AppInfo chosen = apps.get(position);
            BlockedStore.addControlled(this, chosen.pkg);
            // Newly added apps start ticked, so the master button works on
            // them straight away.
            BlockedStore.setChecked(this, chosen.pkg, true);
            WidgetUpdater.updateAllWidgets(this);
            Toast.makeText(this, chosen.label + " যোগ করা হয়েছে",
                    Toast.LENGTH_SHORT).show();
            finish();
        });

        new Thread(() -> {
            List<AppListHelper.AppInfo> loaded =
                    AppListHelper.listLaunchableApps(getApplicationContext());
            List<AppListHelper.AppInfo> filtered = new ArrayList<>();
            for (AppListHelper.AppInfo app : loaded) {
                if (!BlockedStore.isControlled(getApplicationContext(), app.pkg)) {
                    filtered.add(app);
                }
            }
            runOnUiThread(() -> {
                apps = filtered;
                listView.setAdapter(new SimpleAppAdapter(this, apps));
            });
        }).start();
    }
}
