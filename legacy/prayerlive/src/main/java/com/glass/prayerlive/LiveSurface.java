package com.glass.prayerlive;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;

/** Receives the car's map surface and paints the renderer on it about 30 times a second. */
final class LiveSurface implements SurfaceCallback {

    private static final long FRAME_MS = 33;

    private final LiveRenderer renderer;
    private final Object lock = new Object();
    private HandlerThread thread;
    private Handler handler;
    private Surface surface;
    private int width, height;
    private final Rect stable = new Rect();

    LiveSurface(LiveRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer c) {
        synchronized (lock) {
            surface = c.getSurface();
            width = c.getWidth();
            height = c.getHeight();
            Spike.log("car surface available " + width + " x " + height + " dpi " + c.getDpi());
            if (thread == null) {
                thread = new HandlerThread("live-render");
                thread.start();
                handler = new Handler(thread.getLooper());
            }
            handler.removeCallbacks(frame);
            handler.post(frame);
        }
    }

    @Override
    public void onStableAreaChanged(@NonNull Rect area) {
        synchronized (lock) { stable.set(area); }
        Spike.log("car stable area " + area.toShortString());
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect area) {
        Spike.log("car visible area " + area.toShortString());
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer c) {
        stop();
        Spike.log("car surface destroyed");
    }

    void stop() {
        synchronized (lock) {
            surface = null;
            if (handler != null) handler.removeCallbacks(frame);
            if (thread != null) { thread.quitSafely(); thread = null; handler = null; }
        }
    }

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            long start = SystemClock.uptimeMillis();
            synchronized (lock) {
                Surface s = surface;
                if (s == null || !s.isValid() || handler == null) return;
                Canvas canvas = null;
                try {
                    canvas = s.lockCanvas(null);
                    renderer.draw(canvas, width, height, stable, System.currentTimeMillis());
                } catch (Exception e) {
                    Spike.log("draw failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
                } finally {
                    if (canvas != null) {
                        try { s.unlockCanvasAndPost(canvas); } catch (Exception ignored) { }
                    }
                }
                handler.postDelayed(this, Math.max(1, FRAME_MS - (SystemClock.uptimeMillis() - start)));
            }
        }
    };
}
