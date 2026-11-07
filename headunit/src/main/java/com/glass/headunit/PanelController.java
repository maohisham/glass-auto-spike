package com.glass.headunit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Keeps the panel's prayer times and weather current: shows the cache at once, then refreshes when the car has
 * moved, on a new day, every 30 minutes for weather, when the manual zone changes, and by retrying every 120 s
 * while offline.
 */
final class PanelController {

    interface Listener { void onData(Prayer.Data p, Weather.Data w, boolean offline, boolean locationMissing); }

    private static final long CHECK_MS = 60_000L;
    private static final long RETRY_MS = 120_000L;

    interface ForecastListener { void onForecast(Forecast f); }

    private final Context ctx;
    private final Listener listener;
    private ForecastListener forecastListener;
    private Forecast forecast;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LocationFollower follower;
    private Prayer.Data prayer;
    private Weather.Data weather;
    private final FetchQueue queue = new FetchQueue();
    private boolean offline, started;
    private long weatherAt, retryAt;
    private String lastManual = "";

    private final Runnable check = new Runnable() {
        @Override public void run() {
            boolean manualChanged = !Settings.of(ctx).manualZone().equals(lastManual);
            if (RefreshPolicy.shouldRefresh(System.currentTimeMillis(), prayer != null, Cache.prayerIsFromToday(ctx),
                    weatherAt, offline, retryAt, manualChanged)) refreshNow();
            main.postDelayed(this, CHECK_MS);
        }
    };

    PanelController(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
        this.follower = new LocationFollower(this.ctx, (lat, lon) -> fetch(lat, lon));
    }

    void setForecastListener(ForecastListener l) { forecastListener = l; }

    void start() {
        if (started) return;
        started = true;
        prayer = Cache.loadPrayer(ctx);
        if (prayer != null) Prayer.recompute(prayer);
        weather = Cache.loadWeather(ctx);
        weatherAt = Cache.weatherAt(ctx);
        publish();
        forecast = Cache.loadForecast(ctx);
        if (forecast != null && forecastListener != null) forecastListener.onForecast(forecast);
        follower.start();
        main.postDelayed(check, 1000);
    }

    void stop() {
        started = false;
        main.removeCallbacks(check);
        follower.stop();
    }

    /** Fetches for the best position we have now (or the manual zone's centre when there is no fix). */
    void refreshNow() {
        String manual = Settings.of(ctx).manualZone();
        double[] at = FixChoice.choose(Where.get(ctx), manual.isEmpty() ? null : Zones.byCode(manual));
        if (at == null) { publish(); return; }
        fetch(at[0], at[1]);
    }

    /** Fetches now, or queues the position behind the fetch that is running (it is run as soon as that one ends). */
    private void fetch(final double lat, final double lon) {
        if (queue.request(lat, lon)) startFetch(lat, lon);
    }

    private void startFetch(final double lat, final double lon) {
        final String manual = Settings.of(ctx).manualZone();
        lastManual = manual;
        new Thread(() -> {
            Zones.Zone z = manual.isEmpty() ? null : Zones.byCode(manual);
            final Prayer.Data p = z != null ? Prayer.fetchForZone(z) : Prayer.fetch(lat, lon);
            final Weather.Data w = Weather.fetch(ctx, lat, lon);
            final ForecastFetcher.Raw raw = ForecastFetcher.fetch(lat, lon);
            final Forecast fc = raw == null ? null : ForecastParser.parse(raw.forecast, raw.air, System.currentTimeMillis());
            main.post(() -> {
                if (fc != null) {
                    forecast = fc;
                    Cache.saveForecast(ctx, raw);
                    if (forecastListener != null) forecastListener.onForecast(fc);
                }
                if (p != null) { prayer = p; Cache.savePrayer(ctx, p); }
                if (w != null) { weather = w; weatherAt = System.currentTimeMillis(); Cache.saveWeather(ctx, w); }
                offline = p == null || w == null;
                retryAt = offline ? System.currentTimeMillis() + RETRY_MS : 0;
                if (offline) Spike.log("offline: some data could not be refreshed, retrying in 120 s");
                publish();
                double[] next = queue.finished();
                if (next != null) startFetch(next[0], next[1]);
            });
        }, "panel-refresh").start();
    }

    private void publish() {
        listener.onData(prayer, weather, offline, Where.get(ctx) == null && Settings.of(ctx).manualZone().isEmpty());
    }
}
