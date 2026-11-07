package com.glass.headunit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Runs when the unit finishes starting. If the window helper is on it lays the screen out; otherwise it opens the
 * panel on its own when "display over other apps" is granted (Android 12 does not let a receiver open an app without
 * it), and says in the log what is missing.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context ctx, Intent intent) {
        Spike.log("boot: " + intent.getAction());
        BubbleService.apply(ctx);
        // A receiver's process can be reclaimed as soon as onReceive returns, so no delayed work is left here: the
        // window helper is a live service and does its own waiting; without it the panel is opened right now.
        SplitHelperService helper = SplitHelperService.get();
        if (helper != null) { helper.startLayoutAfter(12_000); return; }
        if (android.provider.Settings.canDrawOverlays(ctx)) {
            try {
                ctx.startActivity(new Intent(ctx, PanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Spike.log("boot: the window helper is off, opened the panel only");
            } catch (Exception e) { Spike.log("boot: could not open the panel (" + e.getClass().getSimpleName() + ")"); }
        } else {
            Spike.log("boot: the window helper is off and 'display over other apps' is not granted, so nothing was opened");
        }
    }
}
