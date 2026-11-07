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

    /** Set by the window helper at boot: open the map app beside this panel as soon as it is up. */
    static final String EXTRA_BESIDE = "beside";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private PanelView panel;
    private android.widget.FrameLayout root;
    private PlayerPicker picker;
    private PrayerScreen prayerScreen;
    private DriveScreen driveScreen;
    private WeatherScreen weatherScreen;
    private Forecast forecast;
    private SpeedFollower speed;
    private int tickCount;
    private boolean freshStart;   // true until the first start of this new panel has been handled
    private long lastSpeedAt;   // when the last GPS speed arrived (0 = none yet)
    private final SpeedAlertPolicy alertPolicy = new SpeedAlertPolicy();
    private SpeedAlert speedAlert;
    private static boolean autoPlayScheduled;   // once per process (per panel start)
    private MediaFollower follower;
    private PanelController controller;
    private boolean demo;
    private int demoLine;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            panel.tick();
            // a speed that stops being refreshed (GPS lost its fix) turns to dashes rather than staying frozen
            if (!demo && SpeedFormat.isStale(lastSpeedAt, System.currentTimeMillis())) panel.showSpeed(0f, false);
            if (prayerScreen != null) prayerScreen.tick();
            if (++tickCount % 5 == 0) {
                EcoScore trip = DriveTrip.trip();
                panel.showScore(trip.isTripActive() ? trip.calculate(System.currentTimeMillis()) : null);
                if (driveScreen != null) driveScreen.refresh();
            }
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
        freshStart = b == null;
        panel = new PanelView(this);
        applyStyle(getResources().getConfiguration());
        panel.setOnSettings(() -> startActivity(new android.content.Intent(this, SettingsActivity.class)));
        panel.setOnPlayer(this::showPicker);
        panel.setOnPrayer(this::showPrayer);
        panel.setOnDrive(this::showDrive);
        panel.setOnWeather(this::showWeather);
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
        panel.setStyle(PanelMode.forWidthDp(c.screenWidthDp), s.textSize(), s.lyricsSize(), dark);
        getWindow().getDecorView().setBackgroundColor(PanelStyle.bg(dark));
    }

    private void showPicker() {
        if (picker != null) return;
        Settings s = Settings.of(this);
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        picker = new PlayerPicker(this, follower, PanelStyle.isDark(s.theme(), night), this::hidePicker);
        root.addView(picker, new android.widget.FrameLayout.LayoutParams(-1, -1));
    }

    /** Once per start: if the setting is on and nothing is playing after a few seconds, resume the last player. */
    private void scheduleAutoPlay() {
        if (autoPlayScheduled) return;
        autoPlayScheduled = true;
        final PlayerSwitcher switcher = new PlayerSwitcher(this, follower);
        handler.postDelayed(() -> {
            if (follower == null) return;
            Settings s = Settings.of(this);
            String pkg = s.lastPlayer();
            boolean installed = false;
            try { installed = !pkg.isEmpty() && getPackageManager().getApplicationInfo(pkg, 0).enabled; } catch (Exception ignored) { }
            if (AutoPlayPolicy.shouldStart(s.autoPlayLast(), pkg, installed, follower.anyPlaying(), false)) {
                Spike.log("auto-play: resuming " + pkg);
                switcher.resume(pkg);
            } else {
                Spike.log("auto-play: not starting (setting " + (s.autoPlayLast() ? "on" : "off") + ", last player '" + pkg + "', playing " + follower.anyPlaying() + ")");
            }
        }, 5000);
    }

    private void showWeather() {
        if (weatherScreen != null) return;
        Weather.Data w = panel.weather();
        weatherScreen = new WeatherScreen(this, panel.isDark(), panel.textSize(), panel.mode(), w == null ? "" : w.city, forecast, this::hideWeather);
        root.addView(weatherScreen, new android.widget.FrameLayout.LayoutParams(-1, -1));
    }

    private void hideWeather() {
        if (weatherScreen == null) return;
        root.removeView(weatherScreen);
        weatherScreen = null;
    }

    private void showDrive() {
        if (driveScreen != null) return;
        driveScreen = new DriveScreen(this, panel.isDark(), panel.textSize(), panel.mode(), this::hideDrive);
        root.addView(driveScreen, new android.widget.FrameLayout.LayoutParams(-1, -1));
    }

    private void hideDrive() {
        if (driveScreen == null) return;
        root.removeView(driveScreen);
        driveScreen = null;
    }

    /** For the demo only: a made-up ten-minute trip so the score screen has something to show. */
    private static void seedDemoTrip() {
        EcoScore e = DriveTrip.trip();
        if (e.isTripActive()) return;
        long t = System.currentTimeMillis() - 600_000L;
        for (int i = 0; i < 600; i++) e.onSpeed(i < 40 ? 0f + i * 2 : (i > 560 ? 20f : 62f + (float) Math.sin(i / 20.0) * 14f), t + i * 1000L);
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
        if (weatherScreen != null) { hideWeather(); return; }
        if (driveScreen != null) { hideDrive(); return; }
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
        panel.setScoreEnabled(Settings.of(this).driveScore());
        lastSpeedAt = 0;
        panel.showSpeed(0f, false);
        if (demo) {
            forecast = DemoData.forecast();
            panel.showSpeed(64f, true);
            seedDemoTrip();
            panel.showScore(DriveTrip.trip().calculate(System.currentTimeMillis()));
            panel.showData(demoPrayer(), demoWeather(), false, false);
            // --ez idle true shows the nothing-playing screen (the prayer table) instead of a song
            if (getIntent().getBooleanExtra("idle", false)) panel.showNowPlaying(NowPlaying.idle());
            else if (getIntent().getBooleanExtra("radio", false)) panel.showNowPlaying(demoRadio());   // --ez radio true: a station instead of a song
            else handler.post(demoStep);
            return;
        }
        follower = new MediaFollower(this, n -> panel.showNowPlaying(n));
        controller = new PanelController(this, (p, w, off, loc) -> panel.showData(p, w, off, loc));
        controller.setForecastListener(f -> { forecast = f; if (weatherScreen != null) weatherScreen.setForecast(f); });
        panel.setControls(new PanelView.Controls() {
            @Override public void play() { follower.play(); }
            @Override public void pause() { follower.pause(); }
            @Override public void next() { follower.next(); }
            @Override public void previous() { follower.previous(); }
            @Override public void toggleShuffle() { follower.toggleShuffle(); }
            @Override public void toggleRepeat() { follower.toggleRepeat(); }
        });
        follower.start();
        controller.start();
        scheduleAutoPlay();
        if (Settings.of(this).driveScore()) {
            speed = new SpeedFollower(this, kmh -> {
                long now = System.currentTimeMillis();
                DriveTrip.trip().onSpeed(kmh, now);
                lastSpeedAt = now;
                panel.showSpeed(kmh, true);
                Settings st = Settings.of(PanelActivity.this);
                // beep once when the speed goes over the limit (see SpeedAlertPolicy for when it can sound again)
                if (st.speedAlert() && alertPolicy.onSpeed(kmh, st.speedLimit())) {
                    if (speedAlert == null) speedAlert = new SpeedAlert(PanelActivity.this);
                    speedAlert.play();
                }
            });
            speed.start();
        }
        // open the map app beside the panel: when the window helper asked for it at boot, or on every fresh start of the panel
        boolean helperAsked = getIntent().getBooleanExtra(EXTRA_BESIDE, false);
        getIntent().removeExtra(EXTRA_BESIDE);
        boolean auto = MapsAutoOpen.shouldOpen(Settings.of(this).autoOpenMaps(), panel.mapsInstalled(), freshStart, demo);
        freshStart = false;
        if (helperAsked || auto) {
            Spike.log("opening the map app beside the panel" + (helperAsked ? " (window helper)" : " (panel start)"));
            handler.postDelayed(() -> panel.openMaps(false), 800);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(tick);
        handler.removeCallbacks(demoStep);
        hidePicker();
        hidePrayer();
        hideDrive();
        hideWeather();
        if (speed != null) { speed.stop(); speed = null; }
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

    private static NowPlaying demoRadio() {
        return new NowPlaying(Proxy.VRADIO, "VRadio", "Hot FM", "Now playing: Sesaat Cuma Kamu", true, 0, 0, null, false, -1,
                false, false, false, 0, null, "");
    }

    private static NowPlaying demoNow(int line) {
        Lyrics l = new Lyrics();
        String[] lines = {"Senja turun perlahan di kota", "Kita duduk, tak mahu pulang dulu", "", "Biar lampu jalan jadi saksi",
                "Stay a while, we're parked here tonight", "Dengar radio, dengar hujan", "", "Di antara dua lorong yang sunyi",
                "Kau senyum, aku lupa jalan pulang", "Biar malam yang tentukan", "Satu lagu lagi, satu lagi saja", "Dan kita pulang bila pagi"};
        l.lines.addAll(Arrays.asList(lines));
        for (int i = 0; i < lines.length; i++) l.times.add(i * 3000L);
        return new NowPlaying("demo.player", "Demo", "Stay a while, we're parked", "Senja Sessions", true,
                line * 3000L, 36000, l, false, line, true, true, true, android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ALL, null, "");
    }
}
