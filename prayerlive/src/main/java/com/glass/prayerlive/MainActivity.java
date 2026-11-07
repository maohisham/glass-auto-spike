package com.glass.prayerlive;

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
        t.setText("Prayer Live\n\nOpen Android Auto and look for 'Prayer Live' in the app launcher. "
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
        if (getIntent().getBooleanExtra("preview", false)) renderPreviews();
    }

    /** Draws the car screen offscreen for a few weather types and saves PNGs, so the look can be checked without the car. */
    private void renderPreviews() {
        new Thread(() -> {
            double[] at = Where.get(this);
            if (at == null) { Spike.log("preview: no location fix"); return; }
            Prayer.Data p = Prayer.fetch(at[0], at[1]);
            Weather.Data real = Weather.fetch(getApplicationContext(), at[0], at[1]);
            if (p == null || real == null) { Spike.log("preview: no data"); return; }
            int[] codes = {53, 0, 95, 3};
            String[] names = {"drizzle", "clear", "storm", "cloud"};
            for (int i = 0; i < codes.length; i++) {
                Weather.Data w = new Weather.Data();
                w.tempC = real.tempC; w.city = real.city; w.code = codes[i];
                w.emoji = real.emoji; w.desc = names[i].substring(0, 1).toUpperCase() + names[i].substring(1);
                LiveRenderer r = new LiveRenderer();
                r.setData(p, w, "");
                android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(1220, 630, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas c = new android.graphics.Canvas(bmp);
                long t = System.currentTimeMillis();
                for (int f = 0; f < 90; f++) { r.draw(c, 1220, 630, new android.graphics.Rect(), t + f * 33L); }
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(new java.io.File(getFilesDir(), "preview_" + names[i] + ".png"))) {
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
                } catch (Exception e) { Spike.log("preview: save failed " + e.getMessage()); }
            }
            Spike.log("preview: saved " + codes.length + " images");
        }, "preview").start();
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
