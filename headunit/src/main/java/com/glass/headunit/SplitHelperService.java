package com.glass.headunit;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

/**
 * The window helper: turns split-screen on, opens the map app and the panel side by side, and moves the divider to the
 * user's ratio. It is an accessibility service because that is what lets an app press the system's split-screen action
 * and drag the divider. Every step is logged, and a failed step leaves the panel as it is (full-screen with its own
 * "Open Maps" button). Behaviour differs between units, so SplitProbeActivity measures each one.
 */
public class SplitHelperService extends AccessibilityService {

    /** After a drag, wait for the system's snap animation to end before reading where the divider is. */
    static final long SETTLE_MS = 1500;
    private static volatile SplitHelperService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long layoutStartedAt;

    static boolean isRunning() { return instance != null; }
    static SplitHelperService get() { return instance; }

    @Override
    protected void onServiceConnected() {
        instance = this;
        Spike.log("helper: connected");
        // Android starts an enabled accessibility service by itself at boot: if this is the boot, lay the screen out
        if (SystemClock.elapsedRealtime() < 3 * 60_000L) handler.postDelayed(this::startLayout, 12_000);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }

    @Override
    public boolean onUnbind(Intent i) {
        instance = null;
        return super.onUnbind(i);
    }

    // ---- the whole layout ----

    /** Runs the layout after a delay, on the service's own handler (the service stays alive, a receiver's process may not). */
    void startLayoutAfter(long ms) { handler.postDelayed(this::startLayout, ms); }

    /** Panel and the map app side by side at the user's ratio. Does nothing if it already ran in the last minute. */
    void startLayout() {
        long now = SystemClock.elapsedRealtime();
        if (layoutStartedAt != 0 && now - layoutStartedAt < 60_000L) { Spike.log("helper: layout already started, skipping"); return; }
        layoutStartedAt = now;
        final Settings s = Settings.of(this);
        String map = MapAppRepo.selected(this);
        if (!MapsAutoOpen.shouldOpen(s.autoOpenMaps(), map != null, true, false)) {
            Spike.log("helper: " + (map == null ? "no map app is installed" : "opening the map app is switched off in Settings") + "; opening only the panel");
            openPanel(false);
            return;
        }
        if (dividerBounds() != null) { Spike.log("helper: split-screen already on"); settleThenMove(-1f, 0, 0); return; }
        // First choice: the panel opens the map app beside itself, which makes the system split the screen. This works on
        // units that refuse the toggle action below. If no split appears, fall back to the toggle.
        Spike.log("helper: opening the panel, which opens Google Maps beside itself");
        Intent beside = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (beside != null) {
            beside.putExtra(PanelActivity.EXTRA_BESIDE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(beside); } catch (Exception e) { Spike.log("helper: could not open the panel (" + e.getClass().getSimpleName() + ")"); }
        }
        handler.postDelayed(() -> { if (dividerBounds() == null) toggleLayout(s); }, 6000);
    }

    /** The older route: put one app in front, press the system's split-screen action, then open the other beside it. */
    private void toggleLayout(final Settings s) {
        final boolean panelFirst = "panel".equals(SplitPlan.firstApp(s.swapSides()));
        Spike.log("helper: starting with " + (panelFirst ? "the panel" : "the map app") + " in the first slot");
        if (panelFirst) openPanel(false); else openMaps(false);
        handler.postDelayed(() -> {
            if (!toggleSplit()) { Spike.log("helper: the unit refused the split-screen action; leaving the first app full-screen"); return; }
            handler.postDelayed(() -> {
                if (panelFirst) openMaps(true); else openPanel(true);
                settleThenMove(-1f, 0, 0);
            }, 1200);
        }, 1500);
    }

    /** Waits (up to about 5 s) for a split to appear, then moves the divider to the user's ratio. Call after opening Maps beside the panel. */
    void applyRatioWhenSplit() { waitForDivider(0); }

    private void waitForDivider(int tries) {
        if (dividerBounds() != null) { settleThenMove(-1f, 0, 0); return; }
        if (tries >= 12) { Spike.log("helper: no split appeared, so the ratio was not set"); return; }
        handler.postDelayed(() -> waitForDivider(tries + 1), 400);
    }

    /** The system animates the divider as the split opens: wait until two reads agree, so the drag starts on the divider itself. */
    private void settleThenMove(float previous, int tries, int attempt) {
        handler.postDelayed(() -> {
            Rect d = dividerBounds();
            if (d == null) { Spike.log("helper: the divider went away, so the ratio was not set"); return; }
            Rect screen = screen();
            float c = screen.width() >= screen.height() ? d.exactCenterX() : d.exactCenterY();
            if (!SplitPlan.isSettled(previous, c) && tries < 12) { settleThenMove(c, tries + 1, attempt); return; }
            moveDivider(Settings.of(this), attempt);
        }, 500);
    }

    private void moveDivider(Settings s) { moveDivider(s, 0); }

    private void moveDivider(Settings s, int attempt) {
        Rect screen = screen();
        float share = SplitMath.effective(s.mapShare(), screen.width(), getResources().getDisplayMetrics().density).mapShare;
        Rect d = dividerBounds();
        if (d == null) { Spike.log("helper: no divider found, cannot set the ratio"); return; }
        boolean landscape = screen.width() >= screen.height();
        // the unit decides which side the second app lands on, so the map's share goes to the side the map really is on
        Boolean mapFirst = mapIsFirst(d, landscape);
        boolean mapAtEnd = mapFirst == null ? s.swapSides() : !mapFirst;
        float target = SplitPlan.dividerFraction(share, mapAtEnd);
        float now = landscape ? d.exactCenterX() / screen.width() : d.exactCenterY() / screen.height();
        Spike.log(String.format(java.util.Locale.US, "helper: map share %.0f%%, map is %s, divider at %.0f%% -> target %.0f%%",
                share * 100, mapFirst == null ? "on an unknown side (using the swap setting)" : (mapFirst ? "first" : "last"), now * 100, target * 100));
        if (!SplitPlan.needsDrag(now, target)) { Spike.log(String.format(java.util.Locale.US, "helper: divider already near %.0f%%, leaving it", now * 100)); return; }
        // after the drag, check the divider really arrived; try once more if the first drag missed
        dragDivider(now, target, attempt == 0 ? () -> settleThenMove(-1f, 0, 1) : null);
    }

    /** Moves the divider to the ratio right now when the screen is already split (used when Settings changes the ratio). */
    void applyRatioIfSplit() {
        if (dividerBounds() != null) settleThenMove(-1f, 0, 0);
    }

    /** Whether the map app's window is before the divider (left or top); null when its window cannot be found. */
    private Boolean mapIsFirst(Rect divider, boolean landscape) {
        String map = MapAppRepo.selected(this);
        if (map == null) return null;
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                android.view.accessibility.AccessibilityNodeInfo root = w.getRoot();
                if (root == null) continue;
                boolean mine = map.contentEquals(root.getPackageName() == null ? "" : root.getPackageName());
                if (!mine) continue;
                Rect r = new Rect();
                w.getBoundsInScreen(r);
                return SplitPlan.mapIsFirst(landscape ? r.exactCenterX() : r.exactCenterY(), landscape ? divider.exactCenterX() : divider.exactCenterY());
            }
        } catch (Exception e) { Spike.log("helper: could not find the map window (" + e.getClass().getSimpleName() + ")"); }
        return null;
    }

    // ---- single steps (also used by SplitProbeActivity) ----

    boolean toggleSplit() {
        boolean ok = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN);
        Spike.log("helper: toggle split-screen -> " + (ok ? "accepted" : "refused"));
        return ok;
    }

    void openMaps(boolean adjacent) {
        String map = MapAppRepo.selected(this);
        if (map == null) { Spike.log("helper: no map app to open"); return; }
        launch(map, adjacent);
    }

    void openPanel(boolean adjacent) { launch(getPackageName(), adjacent); }

    private void launch(String pkg, boolean adjacent) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) { Spike.log("helper: cannot launch " + pkg); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | (adjacent ? Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK : 0));
        try {
            startActivity(i);
            Spike.log("helper: launched " + pkg + (adjacent ? " beside the other app" : ""));
        } catch (Exception e) {
            Spike.log("helper: could not launch " + pkg + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** The system's divider window, or null when the screen is not split. */
    Rect dividerBounds() {
        try {
            List<AccessibilityWindowInfo> ws = getWindows();
            for (AccessibilityWindowInfo w : ws) {
                if (w.getType() == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER) {
                    Rect r = new Rect();
                    w.getBoundsInScreen(r);
                    return r;
                }
            }
        } catch (Exception e) { Spike.log("helper: could not read windows (" + e.getClass().getSimpleName() + ")"); }
        return null;
    }

    /** Every window the helper can see, one line each, for the probe screen. */
    String describeWindows() {
        StringBuilder sb = new StringBuilder();
        try {
            for (AccessibilityWindowInfo w : getWindows()) {
                Rect r = new Rect();
                w.getBoundsInScreen(r);
                sb.append("type ").append(w.getType()).append(" ").append(r.toShortString()).append(w.isActive() ? " active" : "").append('\n');
            }
        } catch (Exception e) { sb.append("error ").append(e.getMessage()); }
        return sb.toString();
    }

    Rect screen() { return getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds(); }

    /** Drags the divider from one position to another (fractions of the screen) with a swipe; done runs afterwards. */
    void dragDivider(float fromFraction, float toFraction, Runnable done) {
        Rect b = screen();
        float[] g = SplitPlan.drag(b.width(), b.height(), fromFraction, toFraction);
        Path p = new Path();
        p.moveTo(g[0], g[1]);
        p.lineTo(g[2], g[3]);
        GestureDescription gd = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p, 0, 600)).build();
        boolean sent = dispatchGesture(gd, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription d) {
                Spike.log(String.format(java.util.Locale.US, "helper: dragged the divider %.0f%% -> %.0f%%", fromFraction * 100, toFraction * 100));
                if (done != null) handler.postDelayed(done, SETTLE_MS);
            }
            @Override public void onCancelled(GestureDescription d) {
                Spike.log("helper: the divider drag was cancelled");
                if (done != null) handler.postDelayed(done, SETTLE_MS);
            }
        }, null);
        if (!sent) { Spike.log("helper: the unit refused the divider gesture"); if (done != null) done.run(); }
    }
}
