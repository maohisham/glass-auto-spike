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
    private LinearLayout checklistBox, mapChoiceBox;

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
            @Override public void onStopTrackingTouch(SeekBar sb) { applyRatioNow(); }
        });
        root.addView(seek);

        root.addView(heading("Map app"));
        root.addView(toggle("Open the map app beside the panel when it starts (off: only the panel opens)", s.autoOpenMaps(), s::setAutoOpenMaps));
        mapChoiceBox = new LinearLayout(this);
        mapChoiceBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(mapChoiceBox);

        root.addView(heading("Text size"));
        root.addView(choice(new String[]{"Small", "Medium", "Large"}, s.textSize(), s::setTextSize));
        root.addView(heading("Lyrics text size"));
        root.addView(choice(new String[]{"Same as the panel", "Small", "Medium", "Large", "Extra large"}, s.lyricsSize() + 1, i -> s.setLyricsSize(i - 1), true));
        root.addView(heading("Theme"));
        root.addView(choice(new String[]{"Follow the unit", "Light", "Dark"}, s.theme(), s::setTheme));

        root.addView(toggle("Panel on the left, map on the right", s.swapSides(), on -> { s.setSwapSides(on); applyRatioNow(); }));

        root.addView(heading("Music"));
        root.addView(toggle("Auto-play the last player when the panel starts", s.autoPlayLast(), s::setAutoPlayLast));

        root.addView(heading("Driving"));
        root.addView(toggle("Driving score and speed (uses the GPS while the panel is open)", s.driveScore(), s::setDriveScore));

        root.addView(toggle("Speed alert: beep once when over the limit", s.speedAlert(), s::setSpeedAlert));
        final TextView limitText = new TextView(this);
        limitText.setText("Alert above " + s.speedLimit() + " km/h");
        root.addView(limitText);
        SeekBar limitSeek = new SeekBar(this);
        limitSeek.setMax(28);                                    // 40 to 180 km/h in steps of 5
        limitSeek.setProgress((s.speedLimit() - 40) / 5);
        limitSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                int kmh = 40 + p * 5;
                limitText.setText("Alert above " + kmh + " km/h");
                if (user) s.setSpeedLimit(kmh);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        root.addView(limitSeek);
        root.addView(button("Test the alert sound", () -> new SpeedAlert(this).play()));

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
        buildMapChoice();
        zoneRow.setText(s.manualZone().isEmpty() ? "Automatic: follows the GPS" : "Manual: " + s.manualZone());
        buildChecklist();
        BubbleService.apply(this); // picks up a permission granted while Settings was away
    }

    /** When the screen is already split, moves the divider to the new ratio at once (needs the window helper). */
    private void applyRatioNow() {
        SplitHelperService h = SplitHelperService.get();
        if (h != null) h.applyRatioIfSplit();
    }

    /** The map app choice: Automatic, the navigation apps found on the unit, and a button for any other app. */
    private void buildMapChoice() {
        mapChoiceBox.removeAllViews();
        final java.util.List<String[]> apps = MapAppRepo.installed(this);
        final MapApps.Split split = MapApps.split(apps);
        String chosen = s.mapApp();
        String using = MapApps.choose(chosen, apps);
        TextView now = new TextView(this);
        now.setText(using == null ? "No map app found on this unit." : "Opens: " + MapAppRepo.label(this, using));
        mapChoiceBox.addView(now);
        final java.util.List<String> pkgs = new java.util.ArrayList<>();
        final java.util.List<String> names = new java.util.ArrayList<>();
        pkgs.add(""); names.add("Automatic (Google Maps if installed)");
        for (String[] a : split.navigation) { pkgs.add(a[0]); names.add(a[1]); }
        int selected = pkgs.indexOf(chosen);
        RadioGroup g = choice(names.toArray(new String[0]), selected, i -> { s.setMapApp(pkgs.get(i)); buildMapChoice(); }, true);
        if (selected < 0) g.clearCheck();  // an app picked from the full list is not one of these
        mapChoiceBox.addView(g);
        if (!split.other.isEmpty()) {
            mapChoiceBox.addView(button("Another app…", () -> {
                final String[] labels = new String[split.other.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = split.other.get(i)[1];
                new android.app.AlertDialog.Builder(this).setTitle("Choose the map app")
                        .setItems(labels, (d, i) -> { s.setMapApp(split.other.get(i)[0]); buildMapChoice(); }).show();
            }));
        }
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
        return choice(labels, selected, set, false);
    }

    private RadioGroup choice(String[] labels, int selected, java.util.function.Consumer<Integer> set, boolean vertical) {
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(vertical ? RadioGroup.VERTICAL : RadioGroup.HORIZONTAL);
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
