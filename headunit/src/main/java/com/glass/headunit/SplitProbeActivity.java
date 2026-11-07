package com.glass.headunit;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A test screen to run on each unit: does the helper work there, and where does the divider really end up? Each button
 * writes its result to the log (open the log and copy it; the Eonon has no adb).
 */
public class SplitProbeActivity extends Activity {

    private TextView status;
    private boolean sweeping;     // one sweep at a time: overlapping ones gave stale readings

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Split-screen test");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        status = new TextView(this);
        root.addView(status);
        root.addView(button("Toggle split-screen", () -> run(h -> h.toggleSplit())));
        root.addView(button("Open the map app beside this", () -> run(h -> h.openMaps(true))));
        root.addView(button("Measure the divider", () -> run(h -> {
            Rect d = h.dividerBounds();
            Spike.log("probe: model " + android.os.Build.MODEL + ", screen " + h.screen().toShortString()
                    + ", divider " + (d == null ? "none (not split)" : d.toShortString()));
            Spike.log("probe: windows\n" + h.describeWindows());
        })));
        root.addView(button("Drag the divider to 30, 50, 70, 80% and measure (press once, wait ~10 s)", () -> run(h -> {
            if (sweeping) { Spike.log("probe: a sweep is already running"); return; }
            sweeping = true;
            Rect d = h.dividerBounds();
            Rect sc = h.screen();
            float start = d == null ? 0.5f : (sc.width() >= sc.height() ? d.exactCenterX() / sc.width() : d.exactCenterY() / sc.height());
            sweep(h, new float[]{0.3f, 0.5f, 0.7f, 0.8f}, 0, start);
        })));
        root.addView(button("Run the whole layout now", () -> run(h -> h.startLayout())));
        root.addView(button("Window helper settings", () -> Intents.safeStart(this, new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS), "accessibility settings")));
        root.addView(button("Open the log", () -> startActivity(new Intent(this, LogActivity.class))));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        status.setText(SplitHelperService.isRunning() ? "Window helper: on" : "Window helper: OFF. Turn on Glass Panel in the accessibility settings first.");
    }

    private interface WithHelper { void run(SplitHelperService h); }

    private void run(WithHelper w) {
        SplitHelperService h = SplitHelperService.get();
        if (h == null) { Spike.log("probe: the window helper is off"); onResume(); return; }
        w.run(h);
    }

    /** Drags to each target in turn and logs where the divider ended up (the system snaps it to its own positions). */
    private void sweep(final SplitHelperService h, final float[] targets, final int i, final float from) {
        if (i >= targets.length) { Spike.log("probe: sweep finished"); sweeping = false; return; }
        h.dragDivider(from, targets[i], () -> {
            Rect d = h.dividerBounds();
            Rect s = h.screen();
            float now = d == null ? -1 : (s.width() >= s.height() ? d.exactCenterX() / s.width() : d.exactCenterY() / s.height());
            Spike.log(String.format(java.util.Locale.US, "probe: asked %.0f%%, divider now at %.1f%%", targets[i] * 100, now * 100));
            sweep(h, targets, i + 1, now < 0 ? targets[i] : now);
        });
    }

    private Button button(String label, Runnable r) {
        Button bt = new Button(this);
        bt.setAllCaps(false);
        bt.setText(label);
        bt.setOnClickListener(v -> r.run());
        return bt;
    }
}
