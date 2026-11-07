package com.glass.headunit;

import android.content.Context;

/** Remembers which players let this app browse their library, so the probe (which launches the app) runs once. */
final class BrowseStatus {

    static final int UNKNOWN = 0, ALLOWED = 1, REFUSED = 2;

    private BrowseStatus() { }

    static int get(Context c, String pkg) {
        return c.getApplicationContext().getSharedPreferences("browse", Context.MODE_PRIVATE).getInt(pkg, UNKNOWN);
    }

    static void set(Context c, String pkg, int status) {
        c.getApplicationContext().getSharedPreferences("browse", Context.MODE_PRIVATE).edit().putInt(pkg, status).apply();
    }

    /**
     * What a probe's error text means. Only a real refusal is remembered as a verdict: a timeout (the app may just not
     * have started yet) or an error in one folder says nothing about whether browsing is allowed.
     */
    static int fromError(String error) {
        if (error == null) return ALLOWED;
        if (error.contains("REFUSED") || error.startsWith("no media browser service")) return REFUSED;
        return UNKNOWN;
    }
}
