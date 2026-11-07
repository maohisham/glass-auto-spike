package com.glass.headunit;

import android.app.Application;

import java.io.File;

/** Starts the on-disk log first thing in every process, and writes any crash into it before the process dies. */
public class GlassApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Spike.attachFile(new File(getFilesDir(), "panel.log"));
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try { Spike.log("CRASH on " + t.getName() + ": " + android.util.Log.getStackTraceString(e)); } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(t, e);
        });
        Spike.log("process started");
    }
}
