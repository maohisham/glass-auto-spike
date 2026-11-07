package com.glass.prayer;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Phone-side screen. The car template cannot ask for location, so this screen asks, and it also saves the
 * phone's position so the car screen still has one when Android Auto starts it in the background.
 */
public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 96, 48, 48);

        TextView t = new TextView(this);
        t.setTextSize(16f);
        t.setText("Prayer Times\n\nOpen Android Auto and look for 'Prayer Times' in the app launcher. "
                + "Allow location here so it can pick your prayer zone.");
        root.addView(t);

        status = new TextView(this);
        status.setTextSize(14f);
        status.setPadding(0, 32, 0, 32);
        root.addView(status);

        Button bg = new Button(this);
        bg.setAllCaps(false);
        bg.setText("Location: allow all the time");
        bg.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        root.addView(bg);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        refreshStatus();
    }

    private void refreshStatus() {
        boolean fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean all = Build.VERSION.SDK_INT < 29
                || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        double[] at = fine ? Where.get(this) : null; // also saves the fix for the car screen
        final String base = "Location permission: " + (fine ? "allowed" : "not allowed yet")
                + "\nAll the time: " + (all ? "allowed" : "not allowed (the car screen may not see your position)")
                + "\nLast fix: " + (at == null ? "none yet" : String.format(java.util.Locale.US, "%.3f, %.3f", at[0], at[1]));
        status.setText(base);
        if (at == null) return;
        // Same lookup the car screen does, shown here so it can be checked without the car.
        final double[] fix = at;
        new Thread(() -> {
            Prayer.Data p = Prayer.fetch(fix[0], fix[1]);
            Weather.Data w = Weather.fetch(getApplicationContext(), fix[0], fix[1]);
            final String line = p == null ? "Prayer lookup failed (check internet)"
                    : "Zone: " + p.zoneCode + "\nNext: " + p.nextName + " " + Prayer.to12(p.nextTime) + ", in " + Prayer.untilNext(p)
                    + (w == null ? "" : "\nPlace: " + (w.city.isEmpty() ? "(city unknown)" : w.city) + ", " + Math.round(w.tempC) + "°C");
            runOnUiThread(() -> status.setText(base + "\n\n" + line));
        }, "prayer-preview").start();
    }
}
