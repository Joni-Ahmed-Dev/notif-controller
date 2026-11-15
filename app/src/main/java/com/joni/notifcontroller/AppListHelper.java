package com.joni.notifcontroller;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Launchable apps on the phone, sorted by label. */
public class AppListHelper {

    public static class AppInfo {
        public final String label;
        public final String pkg;

        public AppInfo(String label, String pkg) {
            this.label = label;
            this.pkg = pkg;
        }
    }

    public static List<AppInfo> listLaunchableApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(intent, 0);
        List<AppInfo> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (pkg == null || !seen.add(pkg)) {
                continue;
            }
            if (pkg.equals(context.getPackageName())) {
                continue;
            }
            String label = info.loadLabel(pm).toString();
            apps.add(new AppInfo(label, pkg));
        }
        apps.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return apps;
    }

    public static String labelForPackage(Context context, String pkg) {
        if (pkg == null) {
            return "";
        }
        try {
            PackageManager pm = context.getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            if (label != null) {
                return label.toString();
            }
        } catch (Exception ignored) {
            // Unknown package; fall through to the package name.
        }
        return pkg;
    }
}
