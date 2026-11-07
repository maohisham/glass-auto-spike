package com.glass.prayer;

import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.CarText;
import androidx.car.app.model.ForegroundCarColorSpan;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.lifecycle.Lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The prayer list: the city and temperature in the header, one row per prayer, the next one in green.
 * Position comes from the phone (the car template cannot read it), then JAKIM e-Solat and Open-Meteo.
 */
public class PrayerScreen extends Screen {

    private static final long TICK_MS = 30_000;
    private static final long REFETCH_MS = 30 * 60_000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private Prayer.Data prayer;
    private Weather.Data weather;
    private boolean loading = true;
    private boolean noLocation;
    private long fetchedAt;
    private int fetchedDay = -1;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (prayer != null) {
                Prayer.recompute(prayer);
                int day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR);
                if (day != fetchedDay || System.currentTimeMillis() - fetchedAt > REFETCH_MS) load();
            }
            invalidate();
            main.postDelayed(this, TICK_MS);
        }
    };

    public PrayerScreen(@NonNull CarContext ctx) {
        super(ctx);
        getLifecycle().addObserver((androidx.lifecycle.LifecycleEventObserver) (src, event) -> {
            if (event == Lifecycle.Event.ON_START) { load(); main.postDelayed(tick, TICK_MS); }
            else if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_DESTROY) main.removeCallbacks(tick);
        });
    }

    /** Fetches off the main thread, then redraws. */
    private void load() {
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
                noLocation = fix == null;
                if (fp != null) {
                    prayer = fp;
                    fetchedAt = System.currentTimeMillis();
                    fetchedDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR);
                }
                if (fw != null) weather = fw;
                loading = false;
                invalidate();
            });
        }, "prayer-load").start();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        if (prayer == null) {
            if (loading) {
                return new ListTemplate.Builder().setLoading(true).setTitle("Prayer Times").setHeaderAction(Action.APP_ICON).build();
            }
            String msg = noLocation
                    ? "No location yet. Open Prayer Times on your phone and allow location."
                    : "Could not load prayer times. Check the phone's internet connection.";
            return new MessageTemplate.Builder(msg).setTitle("Prayer Times").setHeaderAction(Action.APP_ICON).build();
        }

        List<String[]> rows = new ArrayList<>();
        boolean syurukDone = prayer.syuruk.isEmpty();
        for (Map.Entry<String, String> e : prayer.times.entrySet()) {
            rows.add(new String[]{e.getKey(), Prayer.to12(e.getValue())});
            if (!syurukDone) { rows.add(new String[]{"Syuruk", Prayer.to12(prayer.syuruk)}); syurukDone = true; }
        }

        ItemList.Builder list = new ItemList.Builder();
        for (String[] r : rows) {
            boolean next = r[0].equals(prayer.nextName);
            String line = r[0] + "   " + r[1];
            Row.Builder row = new Row.Builder().setTitle(next ? green(line) : new CarText.Builder(line).build());
            if (next) row.addText(prayer.justStarted ? "Time now" : "Next prayer, in " + Prayer.untilNext(prayer));
            list.addItem(row.build());
        }
        return new ListTemplate.Builder()
                .setSingleList(list.build())
                .setTitle(header())
                .setHeaderAction(Action.APP_ICON)
                .build();
    }

    /** "Petaling Jaya   31°C"; falls back to the zone code when the city lookup failed. */
    private String header() {
        String place = weather != null && !weather.city.isEmpty() ? weather.city : prayer.zoneCode;
        return weather == null ? place : place + "   " + Math.round(weather.tempC) + "°C";
    }

    private static CarText green(String text) {
        SpannableString s = new SpannableString(text);
        s.setSpan(ForegroundCarColorSpan.create(CarColor.GREEN), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return new CarText.Builder(s).build();
    }
}
