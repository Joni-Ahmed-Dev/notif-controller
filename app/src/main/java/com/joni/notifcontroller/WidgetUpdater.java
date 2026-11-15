package com.joni.notifcontroller;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.RemoteViews;

/**
 * Renders every widget instance from the current blocked state.
 * The widget is a small 1x1 logo icon: the target app's own launcher
 * icon (48dp) plus a tiny state dot (green = notifications ON/allowed,
 * red = OFF/blocked). No text on the widget itself.
 */
public class WidgetUpdater {

    public static void updateAllWidgets(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(
                new ComponentName(context, NotifWidgetProvider.class));
        for (int id : ids) {
            updateOne(context, mgr, id);
        }
    }

    public static void updateOne(Context context, AppWidgetManager mgr, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_notif);
        String pkg = WidgetPrefs.getPackage(context, appWidgetId);
        if (pkg == null || pkg.isEmpty()) {
            views.setImageViewBitmap(R.id.btnWidgetToggle, iconBitmap(context, null));
            views.setImageViewResource(R.id.ivWidgetDot, R.drawable.dot_red);
            Intent config = new Intent(context, WidgetConfigActivity.class);
            config.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            PendingIntent pi = PendingIntent.getActivity(context, appWidgetId, config,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.btnWidgetToggle, pi);
        } else {
            boolean blocked = BlockedStore.isBlocked(context, pkg);
            views.setImageViewBitmap(R.id.btnWidgetToggle, iconBitmap(context, pkg));
            views.setImageViewResource(R.id.ivWidgetDot,
                    blocked ? R.drawable.dot_red : R.drawable.dot_green);
            Intent toggle = new Intent(context, NotifWidgetProvider.class);
            toggle.setAction(NotifWidgetProvider.ACTION_TOGGLE);
            toggle.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            toggle.putExtra(NotifWidgetProvider.EXTRA_PACKAGE, pkg);
            PendingIntent pi = PendingIntent.getBroadcast(context, appWidgetId, toggle,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.btnWidgetToggle, pi);
        }
        mgr.updateAppWidget(appWidgetId, views);
    }

    /** Target app's launcher icon as a bitmap; falls back to our own icon. */
    private static Bitmap iconBitmap(Context context, String pkg) {
        int sizePx = Math.round(48f * context.getResources().getDisplayMetrics().density);
        if (sizePx <= 0) {
            sizePx = 96;
        }
        Drawable icon = null;
        if (pkg != null && !pkg.isEmpty()) {
            try {
                icon = context.getPackageManager().getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
                icon = null;
            } catch (Exception ignored) {
                icon = null;
            }
        }
        if (icon == null) {
            try {
                icon = context.getDrawable(R.drawable.ic_launcher);
            } catch (Exception ignored) {
                icon = null;
            }
        }
        if (icon == null) {
            return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        }
        if (icon instanceof BitmapDrawable) {
            Bitmap src = ((BitmapDrawable) icon).getBitmap();
            if (src != null) {
                return Bitmap.createScaledBitmap(src, sizePx, sizePx, true);
            }
        }
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        icon.setBounds(0, 0, sizePx, sizePx);
        icon.draw(canvas);
        return bmp;
    }
}
