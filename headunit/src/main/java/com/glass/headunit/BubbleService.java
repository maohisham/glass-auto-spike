package com.glass.headunit;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * The optional floating player bubble: a small static round button above other apps. It starts inside the panel's
 * half, can be dragged anywhere (the place is remembered), and a tap opens the player picker in an overlay the size
 * of the panel's half, so the map stays visible. It needs "display over other apps"; without it nothing starts.
 */
public class BubbleService extends Service {

    private WindowManager wm;
    private TextView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private PlayerPicker picker;

    /** Starts or stops the bubble to match the setting and the permission. */
    static void apply(Context ctx) {
        Intent i = new Intent(ctx, BubbleService.class);
        if (Settings.of(ctx).bubble() && android.provider.Settings.canDrawOverlays(ctx)) ctx.startService(i);
        else ctx.stopService(i);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        if (bubble == null) show();
        return START_STICKY;
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private Rect screen() { return wm.getMaximumWindowMetrics().getBounds(); }

    private float effectiveShare() {
        Rect b = screen();
        return SplitMath.effective(Settings.of(this).mapShare(), b.width(), getResources().getDisplayMetrics().density).mapShare;
    }

    private void show() {
        if (!android.provider.Settings.canDrawOverlays(this)) { Spike.log("bubble: no overlay permission"); stopSelf(); return; }
        wm = getSystemService(WindowManager.class);
        Settings s = Settings.of(this);
        int size = dp(64);

        bubble = new TextView(this);
        bubble.setText("Player");
        bubble.setTextSize(11f);
        bubble.setTextColor(Color.BLACK);
        bubble.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(PanelStyle.ACCENT_DARK);
        bubble.setBackground(bg);

        bubbleParams = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        Rect b = screen();
        if (s.bubbleX() >= 0) { bubbleParams.x = s.bubbleX(); bubbleParams.y = s.bubbleY(); }
        else {
            int[] xy = BubbleGeometry.start(b.width(), b.height(), effectiveShare(), s.swapSides(), size, dp(16));
            bubbleParams.x = xy[0];
            bubbleParams.y = xy[1];
        }
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int startX, startY; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY(); startX = bubbleParams.x; startY = bubbleParams.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) moved = true;
                        if (moved) {
                            bubbleParams.x = startX + Math.round(dx);
                            bubbleParams.y = startY + Math.round(dy);
                            wm.updateViewLayout(bubble, bubbleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) Settings.of(BubbleService.this).setBubble(bubbleParams.x, bubbleParams.y);
                        else openPicker();
                        return true;
                }
                return false;
            }
        });
        try {
            wm.addView(bubble, bubbleParams);
            Spike.log("bubble: shown");
        } catch (Exception e) {
            Spike.log("bubble: could not show " + e.getMessage());
            bubble = null;
            stopSelf();
        }
    }

    private void openPicker() {
        if (picker != null) return;
        Settings s = Settings.of(this);
        Rect b = screen();
        int[] r = BubbleGeometry.picker(b.width(), b.height(), effectiveShare(), s.swapSides());
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        picker = new PlayerPicker(this, null, PanelStyle.isDark(s.theme(), night), this::closePicker);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(r[2], r[3], WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = r[0];
        p.y = r[1];
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try { wm.addView(picker, p); } catch (Exception e) { Spike.log("bubble: could not open the picker " + e.getMessage()); picker = null; }
    }

    private void closePicker() {
        if (picker == null) return;
        try { wm.removeView(picker); } catch (Exception ignored) { }
        picker = null;
    }

    @Override
    public void onDestroy() {
        closePicker();
        if (bubble != null) { try { wm.removeView(bubble); } catch (Exception ignored) { } bubble = null; }
        super.onDestroy();
    }
}
