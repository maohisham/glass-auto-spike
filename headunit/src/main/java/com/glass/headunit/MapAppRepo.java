package com.glass.headunit;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.List;

/** The Android side of MapApps: reads the apps installed on the unit and says which one the panel opens. */
final class MapAppRepo {

    private MapAppRepo() { }

    /** Every launchable app except this one, as {package, label}. */
    static List<String[]> installed(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<String[]> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || !seen.add(pkg)) continue;
            if (!ri.activityInfo.applicationInfo.enabled) continue;
            out.add(new String[]{pkg, String.valueOf(ri.loadLabel(pm))});
        }
        return out;
    }

    /** The package the panel opens, or null when no map app is installed and none was chosen. */
    static String selected(Context ctx) {
        return MapApps.choose(Settings.of(ctx).mapApp(), installed(ctx));
    }

    /** The name shown to the user for a package, or the package itself when it is unknown. */
    static String label(Context ctx, String pkg) {
        for (String[] a : installed(ctx)) if (a[0].equals(pkg)) return a[1];
        return pkg == null ? "map app" : pkg;
    }
}
