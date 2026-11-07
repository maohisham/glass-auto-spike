package com.glass.prayerlive;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import java.util.Calendar;

/**
 * A navigation-category screen with no route: it only owns the map surface, which LiveSurface paints. The template
 * itself carries just a Refresh button. Prayer times, weather and location come from the same sources as Prayer Times.
 */
public class LiveScreen extends Screen {

    private static final long CHECK_MS = 60_000;
    private static final long REFETCH_MS = 30 * 60_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final LiveRenderer renderer = new LiveRenderer();
    private final LiveSurface surface = new LiveSurface(renderer);
    private long fetchedAt;
    private int fetchedDay = -1;
    private boolean loading;

    private final Runnable check = new Runnable() {
        @Override public void run() {
            int day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
            if (System.currentTimeMillis() - fetchedAt > REFETCH_MS || day != fetchedDay) load();
            main.postDelayed(this, CHECK_MS);
        }
    };

    public LiveScreen(@NonNull CarContext ctx) {
        super(ctx);
        getLifecycle().addObserver((LifecycleEventObserver) (src, event) -> {
            if (event == Lifecycle.Event.ON_CREATE) {
                getCarContext().getCarService(AppManager.class).setSurfaceCallback(surface);
                load();
                main.postDelayed(check, CHECK_MS);
            } else if (event == Lifecycle.Event.ON_DESTROY) {
                main.removeCallbacks(check);
                getCarContext().getCarService(AppManager.class).setSurfaceCallback(null);
                surface.stop();
            }
        });
    }

    /** Fetches off the main thread and hands the result to the renderer. */
    private void load() {
        if (loading) return;
        loading = true;
        final CarContext ctx = getCarContext();
        new Thread(() -> {
            double[] at = Where.get(ctx);
            Prayer.Data p = null;
            Weather.Data w = null;
            if (at != null) {
                p = Prayer.fetch(at[0], at[1]);
                w = Weather.fetch(ctx, at[0], at[1]);
            }
            final double[] fix = at;
            final Prayer.Data fp = p;
            final Weather.Data fw = w;
            main.post(() -> {
                loading = false;
                String msg = fix == null ? "No location yet. Open Prayer Live on your phone and allow location."
                        : fp == null ? "Could not load prayer times. Check the phone's internet." : "";
                renderer.setData(fp, fw, msg);
                if (fp != null) {
                    fetchedAt = System.currentTimeMillis();
                    fetchedDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
                }
            });
        }, "live-load").start();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        return new NavigationTemplate.Builder()
                .setActionStrip(new ActionStrip.Builder()
                        .addAction(new Action.Builder().setTitle("Refresh").setOnClickListener(this::load).build())
                        .build())
                .setBackgroundColor(CarColor.createCustom(0xFF0A1226, 0xFF0A1226))
                .build();
    }
}
