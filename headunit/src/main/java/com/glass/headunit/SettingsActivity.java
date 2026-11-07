package com.glass.headunit;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** All the user's choices in one scrolling screen, plus a setup checklist for the permissions the panel needs. */
public class SettingsActivity extends Activity {

    private Settings s;
    private TextView ratioText, zoneRow, checklist;
    private LinearLayout checklistBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Settings");
        s = Settings.of(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        root.addView(heading("Map and panel split"));
        ratioText = new TextView(this);
        root.addView(ratioText);
        SeekBar seek = new SeekBar(this);
        seek.setMax(30); // 50% .. 80%
        seek.setProgress(Math.round(s.mapShare() * 100) - 50);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                if (user) s.setMapShare((50 + p) / 100f);
                showRatio();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        root.addView(seek);

        root.addView(heading("Text size"));
        root.addView(choice(new String[]{"Small", "Medium", "Large"}, s.textSize(), s::setTextSize));
        root.addView(heading("Theme"));
        root.addView(choice(new String[]{"Follow the unit", "Light", "Dark"}, s.theme(), s::setTheme));

        root.addView(toggle("Panel on the left, map on the right", s.swapSides(), s::setSwapSides));

        root.addView(heading("Music"));
        root.addView(toggle("Auto-play the last player when the panel starts", s.autoPlayLast(), s::setAutoPlayLast));

        root.addView(heading("Prayer zone"));
        zoneRow = new TextView(this);
        root.addView(zoneRow);
        root.addView(button("Choose a zone…", () -> startActivity(new Intent(this, ZonePickerActivity.class))));

        root.addView(heading("Floating player bubble"));
        root.addView(toggle("Show the bubble", s.bubble(), on -> {
            s.setBubble(on);
            if (on && !android.provider.Settings.canDrawOverlays(this)) {
                Intents.safeStart(this, new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + getPackageName())), "display over other apps");
            }
            BubbleService.apply(this);
        }));
        root.addView(button("Reset the bubble's position", () -> { s.resetBubble(); stopService(new Intent(this, BubbleService.class)); BubbleService.apply(this); }));

        root.addView(heading("Setup"));
        checklistBox = new LinearLayout(this);
        checklistBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(checklistBox);

        root.addView(button("Split-screen test (for setting up a unit)", () -> startActivity(new Intent(this, SplitProbeActivity.class))));
        root.addView(button("Open the log", () -> startActivity(new Intent(this, LogActivity.class))));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showRatio();
        zoneRow.setText(s.manualZone().isEmpty() ? "Automatic: follows the GPS" : "Manual: " + s.manualZone());
        buildChecklist();
        BubbleService.apply(this); // picks up a permission granted while Settings was away
    }

    /** Shows the ratio that will really be used on this screen, and says when it was widened. */
    private void showRatio() {
        android.graphics.Rect bounds = getSystemService(android.view.WindowManager.class).getMaximumWindowMetrics().getBounds();
        float density = getResources().getDisplayMetrics().density;
        SplitMath.Result r = SplitMath.effective(s.mapShare(), bounds.width(), density);
        int map = Math.round(r.mapShare * 100);
        ratioText.setText("Map " + map + "% · Panel " + (100 - map) + "%"
                + (r.widened ? "\nWidened for this screen: the panel needs about " + Math.round(SplitMath.MIN_PANEL_DP) + " dp to read well." : "")
                + "\nChosen: " + Math.round(s.mapShare() * 100) + ":" + (100 - Math.round(s.mapShare() * 100)));
    }

    private void buildChecklist() {
        checklistBox.removeAllViews();
        boolean access = MediaFollower.hasMediaAccess(this);
        checklistBox.addView(row("Media access (lyrics and now playing)", access,
                () -> Intents.safeStart(this, new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), "notification access")));
        boolean loc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        checklistBox.addView(row("Location (prayer zone and weather)", loc,
                () -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1)));
        boolean overlay = android.provider.Settings.canDrawOverlays(this);
        checklistBox.addView(row("Display over other apps (floating bubble)", overlay,
                () -> Intents.safeStart(this, new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + getPackageName())), "display over other apps")));
        checklistBox.addView(row("Window helper (auto split-screen at start)", accessibilityOn(),
                () -> Intents.safeStart(this, new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS), "accessibility settings")));
    }

    private boolean accessibilityOn() {
        String enabled = Secure.getString(getContentResolver(), Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(getPackageName());
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        buildChecklist();
    }

    // ---- small view helpers ----

    private TextView heading(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(18f);
        v.setPadding(0, 32, 0, 8);
        return v;
    }

    private Button button(String label, Runnable r) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private LinearLayout row(String label, boolean ok, Runnable fix) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = new TextView(this);
        t.setText((ok ? "✔ " : "✘ ") + label);
        l.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!ok) l.addView(button("Allow", fix));
        return l;
    }

    private Switch toggle(String label, boolean on, java.util.function.Consumer<Boolean> set) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setChecked(on);
        sw.setPadding(0, 24, 0, 24);
        sw.setOnCheckedChangeListener((CompoundButton cb, boolean v) -> set.accept(v));
        return sw;
    }

    private RadioGroup choice(String[] labels, int selected, java.util.function.Consumer<Integer> set) {
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setId(1000 + i);
            g.addView(rb);
        }
        g.check(1000 + selected);
        g.setOnCheckedChangeListener((grp, id) -> set.accept(id - 1000));
        return g;
    }
}
