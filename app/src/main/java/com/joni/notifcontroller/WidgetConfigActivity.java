package com.joni.notifcontroller;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/** Shown when a widget is added to the home screen: pick the one app
 * this widget instance will control. */
public class WidgetConfigActivity extends Activity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private List<AppListHelper.AppInfo> apps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_widget_config);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        // Default result is CANCELED so a back press drops the widget.
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_CANCELED, result);

        ListView listView = findViewById(R.id.lvConfigApps);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                finish();
                return;
            }
            AppListHelper.AppInfo chosen = apps.get(position);
            WidgetPrefs.setPackage(this, appWidgetId, chosen.pkg);
            WidgetUpdater.updateAllWidgets(this);
            Intent ok = new Intent();
            ok.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, ok);
            finish();
        });

        new Thread(() -> {
            List<AppListHelper.AppInfo> loaded =
                    AppListHelper.listLaunchableApps(getApplicationContext());
            runOnUiThread(() -> {
                apps = loaded;
                listView.setAdapter(new SimpleAppAdapter(this, apps));
            });
        }).start();
    }
}
