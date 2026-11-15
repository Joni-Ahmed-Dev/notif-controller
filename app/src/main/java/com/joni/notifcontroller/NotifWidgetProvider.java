package com.joni.notifcontroller;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

/** Home screen widget: one widget controls one app. Tapping its button
 * flips that app between notifications on and off. No periodic polling:
 * the widget is redrawn whenever the blocked state changes. */
public class NotifWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_TOGGLE = "com.joni.notifcontroller.TOGGLE_WIDGET";
    public static final String EXTRA_PACKAGE = "package";

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            WidgetUpdater.updateOne(context, mgr, id);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            int widgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            String pkg = intent.getStringExtra(EXTRA_PACKAGE);
            if (pkg == null && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                pkg = WidgetPrefs.getPackage(context, widgetId);
            }
            if (pkg != null && !pkg.isEmpty()) {
                if (!BlockedStore.isControlled(context, pkg)) {
                    BlockedStore.addControlled(context, pkg);
                }
                BlockedStore.setChecked(context, pkg, true);
                boolean nowBlocked = !BlockedStore.isBlocked(context, pkg);
                BlockedStore.setBlocked(context, pkg, nowBlocked);
                WidgetUpdater.updateAllWidgets(context);
                SyncHelper.reportLocalChange(context, "widget");
            }
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            WidgetPrefs.remove(context, id);
        }
        super.onDeleted(context, appWidgetIds);
    }
}
