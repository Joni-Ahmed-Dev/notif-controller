package com.joni.notifcontroller;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;

/** Icon + label + package rows for the pick-an-app screens
 * (AddAppActivity and WidgetConfigActivity). Selection logic stays in
 * the owning activity; this adapter only renders rows. */
public class SimpleAppAdapter extends BaseAdapter {

    private final Context ctx;
    private final LayoutInflater inflater;
    private final List<AppListHelper.AppInfo> apps;
    private final HashMap<String, Drawable> iconCache = new HashMap<>();

    public SimpleAppAdapter(Context ctx, List<AppListHelper.AppInfo> apps) {
        this.ctx = ctx;
        this.inflater = LayoutInflater.from(ctx);
        this.apps = apps;
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
            row = inflater.inflate(R.layout.pick_app_row, parent, false);
        }
        AppListHelper.AppInfo app = apps.get(position);
        ((TextView) row.findViewById(R.id.tvPickLabel)).setText(app.label);
        ((TextView) row.findViewById(R.id.tvPickPkg)).setText(app.pkg);
        ImageView icon = row.findViewById(R.id.ivPickIcon);
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
        return row;
    }
}
