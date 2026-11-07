package com.glass.aaspike;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.media.MediaBrowserCompat;

/**
 * Receives the "mark as read" / "reply" taps that Android Auto needs on a message-style notification.
 * Also a screen-free test hook: `am broadcast -n com.glass.aaspike/.AlertReceiver -a PROBE --es pkg <package>`.
 */
public class AlertReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("PROBE".equals(intent.getAction())) {
            final String pkg = intent.getStringExtra("pkg");
            final PendingResult pending = goAsync();
            Spike.log("probe " + pkg + ": starting");
            BrowseProbe.load(context.getApplicationContext(), pkg, null, (children, error) -> {
                if (error != null) {
                    Spike.log("RESULT " + pkg + ": " + error);
                } else {
                    StringBuilder sb = new StringBuilder("RESULT " + pkg + ": ALLOWED, " + children.size() + " top-level items:");
                    for (int i = 0; i < Math.min(5, children.size()); i++) {
                        MediaBrowserCompat.MediaItem it = children.get(i);
                        sb.append(" [").append(it.getDescription().getTitle()).append("]");
                    }
                    Spike.log(sb.toString());
                }
                pending.finish();
            });
            return;
        }
        Spike.log("alert action: " + intent.getAction());
    }
}
