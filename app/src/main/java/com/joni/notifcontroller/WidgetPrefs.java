package com.joni.notifcontroller;

import android.content.Context;
import android.content.SharedPreferences;

/** Which app each home screen widget instance controls. */
public class WidgetPrefs {

    private static final String PREFS = "notif_widget_prefs";

    private static String key(int appWidgetId) {
        return "widget_" + appWidgetId;
    }

    public static String getPackage(Context context, int appWidgetId) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(key(appWidgetId), null);
    }

    public static void setPackage(Context context, int appWidgetId, String pkg) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key(appWidgetId), pkg)
                .apply();
    }

    public static void remove(Context context, int appWidgetId) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(key(appWidgetId))
                .apply();
    }
}
