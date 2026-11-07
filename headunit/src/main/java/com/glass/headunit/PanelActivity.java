package com.glass.headunit;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;

/**
 * The panel the user opens beside Google Maps. It follows the media and refreshes prayer and weather only while it
 * is visible (between onStart and onStop). `--ez demo true` shows made-up data so the layout can be checked
 * without a car, a player or a network.
 */
public class PanelActivity extends Activity {

    /** Set by the window helper at boot: open Google Maps beside this panel as soon as it is up. */
    static final String EXTRA_BESIDE = "beside";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PanelView panel;
    private android.widget.FrameLayout root;
    private PlayerPicker picker;
    private PrayerScreen prayerScreen;
    private MediaFollower follower;
    private PanelController controller;
    private boolean demo;
    private int demoLine;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            panel.tick();
            if (prayerScreen != null) prayerScreen.tick();
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable demoStep = new Runnable() {
        @Override public void run() {
            panel.showNowPlaying(demoNow(demoLine));
            demoLine = (demoLine + 1) % 12;
            handler.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        demo = getIntent().getBooleanExtra("demo", false);
        panel = new PanelView(this);
        applyStyle(getResources().getConfiguration());
        panel.setOnSettings(() -> startActivity(new android.content.Intent(this, SettingsActivity.class)));
        panel.setOnPlayer(this::showPicker);
        panel.setOnPrayer(this::showPrayer);
        root = new android.widget.FrameLayout(this);
        root.addView(panel, new android.widget.FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        if (!demo && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }
    }

    private void applyStyle(Configuration c) {
        Settings s = Settings.of(this);
        boolean night = (c.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean dark = PanelStyle.isDark(s.theme(), night);
        panel.setStyle(PanelMode.forWidthDp(c.screenWidthDp), s.textSize(), dark);
        getWindow().getDecorView().setBackgroundColor(PanelStyle.bg(dark));
    }

    private void showPicker() {
        if (picker != null) return;
        Settings s = Settings.of(this);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        picker = new PlayerPicker(this, follower, PanelStyle.isDark(s.theme(), night), this::hidePicker);
        root.addView(picker, new android.widget.FrameLayout.LayoutParams(-1, -1));
    }

    private void showPrayer() {
        if (prayerScreen != null || panel.prayer() == null) return;
        prayerScreen = new PrayerScreen(this, panel.isDark(), panel.textSize(), panel.mode(), panel.prayer(), panel.weather(), this::hidePrayer);
        root.addView(prayerScreen, new android.widget.FrameLayout.LayoutParams(-1, -1));
    }

    private void hidePrayer() {
        if (prayerScreen == null) return;
        root.removeView(prayerScreen);
        prayerScreen = null;
    }

    private void hidePicker() {
        if (picker == null) return;
        root.removeView(picker);
        picker = null;
    }

    @Override
    public void onBackPressed() {
        if (prayerScreen != null) { hidePrayer(); return; }
        if (picker != null) { if (!picker.handleBack()) hidePicker(); return; }
        super.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyStyle(c);
    }

    @Override
    protected void onStart() {
        super.onStart();
        applyStyle(getResources().getConfiguration());
        handler.post(tick);
        if (!demo) BubbleService.apply(this);
        if (demo) {
            panel.showData(demoPrayer(), demoWeather(), false, false);
            // --ez idle true shows the nothing-playing screen (the prayer table) instead of a song
            if (getIntent().getBooleanExtra("idle", false)) panel.showNowPlaying(NowPlaying.idle()); else handler.post(demoStep);
            return;
        }
        follower = new MediaFollower(this, n -> panel.showNowPlaying(n));
        controller = new PanelController(this, (p, w, off, loc) -> panel.showData(p, w, off, loc));
        panel.setControls(new PanelView.Controls() {
            @Override public void play() { follower.play(); }
            @Override public void pause() { follower.pause(); }
            @Override public void next() { follower.next(); }
            @Override public void previous() { follower.previous(); }
            @Override public void toggleShuffle() { follower.toggleShuffle(); }
        });
        follower.start();
        controller.start();
        if (getIntent().getBooleanExtra(EXTRA_BESIDE, false)) {
            getIntent().removeExtra(EXTRA_BESIDE);
            handler.postDelayed(() -> panel.openMaps(), 800);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(demoStep);
        hidePicker();
        hidePrayer();
        if (follower != null) { follower.stop(); follower = null; }
        if (controller != null) { controller.stop(); controller = null; }
    }

    // ---- demo data ----

    private static Prayer.Data demoPrayer() {
        Prayer.Data d = new Prayer.Data();
        d.times.put("Subuh", "05:48");
        d.times.put("Zohor", "13:10");
        d.times.put("Asar", "16:25");
        d.times.put("Maghrib", "19:15");
        d.times.put("Isyak", "20:27");
        d.syuruk = "07:00";
        d.hijri = "demo";
        return Prayer.recompute(d);
    }

    private static Weather.Data demoWeather() {
        Weather.Data w = new Weather.Data();
        w.tempC = 31f;
        w.emoji = "🌦";
        w.desc = "Drizzle";
        w.city = "Sepang";
        w.code = 53;
        return w;
    }

    private static NowPlaying demoNow(int line) {
        Lyrics l = new Lyrics();
        String[] lines = {"Senja turun perlahan di kota", "Kita duduk, tak mahu pulang dulu", "", "Biar lampu jalan jadi saksi",
                "Stay a while, we're parked here tonight", "Dengar radio, dengar hujan", "", "Di antara dua lorong yang sunyi",
                "Kau senyum, aku lupa jalan pulang", "Biar malam yang tentukan", "Satu lagu lagi, satu lagi saja", "Dan kita pulang bila pagi"};
        l.lines.addAll(Arrays.asList(lines));
        for (int i = 0; i < lines.length; i++) l.times.add(i * 3000L);
        return new NowPlaying("demo.player", "Demo", "Stay a while, we're parked", "Senja Sessions", true,
                line * 3000L, 36000, l, false, line, true, true);
    }
}
