package com.glass.headunit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/** Opens a system or other app's screen without crashing when this unit's firmware does not have it. */
final class Intents {

    private Intents() { }

    /** True when the screen opened. On failure it logs, tells the user, and tries the unit's main Settings app. */
    static boolean safeStart(Context ctx, Intent intent, String what) {
        try {
            if (!(ctx instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return true;
        } catch (Exception e) {
            Spike.log("could not open " + what + " (" + e.getClass().getSimpleName() + "): open it from the unit's own Settings");
            Toast.makeText(ctx, "This unit has no direct page for " + what + ". Open it from the unit's Settings.", Toast.LENGTH_LONG).show();
            try {
                Intent s = new Intent(android.provider.Settings.ACTION_SETTINGS);
                if (!(ctx instanceof Activity)) s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(s);
            } catch (Exception ignored) { }
            return false;
        }
    }
}
