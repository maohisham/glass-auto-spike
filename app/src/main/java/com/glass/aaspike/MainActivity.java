package com.glass.aaspike;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;

import java.util.List;

/** Throwaway test screen for the Android Auto media card spike. */
public class MainActivity extends Activity {

    private static final String CHANNEL = "spike_alerts";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView logView;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            logView.setText(Spike.dump());
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 48, 32, 32);

        TextView title = new TextView(this);
        title.setText("Glass Auto spike (throwaway)\n"
                + "1. Tap Start card.  2. Open Android Auto with developer 'Unknown sources' on.  "
                + "3. Find 'Glass Auto' in the media apps.");
        title.setTextSize(14f);
        root.addView(title);

        root.addView(button("Start card", v -> {
            startService(new Intent(this, SpikeService.class));
            Spike.log("service start requested");
        }));
        root.addView(button("Calibration card: ON / OFF (test picture for the car)", v -> {
            android.content.SharedPreferences sp = getSharedPreferences("cal", MODE_PRIVATE);
            boolean on = !sp.getBoolean("on", false);
            sp.edit().putBoolean("on", on).apply();
            Spike.log("calibration card " + (on ? "ON" : "OFF"));
        }));
        root.addView(button("Location: allow all the time (for travel updates)", v -> {
            boolean bg = checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            Spike.log("background location is " + (bg ? "ALLOWED" : "not allowed yet: choose Location, then Allow all the time"));
            if (!bg) startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
        }));
        root.addView(button("Prayer alert, real format, in 20 s", v -> {
            Toast.makeText(this, "Alert in 20 s: switch to Android Auto now", Toast.LENGTH_LONG).show();
            Spike.log("real-format prayer alert scheduled in 20 s");
            final Context app = getApplicationContext();
            handler.postDelayed(() -> PrayerAlert.post(app, "Zohor", "1:10 PM", "Petaling Jaya"), 20_000);
        }));
        root.addView(button("Preview the card picture (sample data)", v -> showPreview()));
        root.addView(button("Allow notification access (needed to mirror players)", v -> startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        root.addView(button("Prayer alert as message, in 20 s", v -> alertLater(true)));
        root.addView(button("Prayer alert as plain notification, in 20 s", v -> alertLater(false)));

        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(11f);
        logView.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(logView);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); Where.get(this); handler.post(refresh); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(refresh); }

    private Button button(String text, android.view.View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setOnClickListener(l);
        return btn;
    }

    private void showPreview() {
        Prayer.Data d = new Prayer.Data();
        d.times.put("Subuh", "05:48"); d.times.put("Zohor", "13:10"); d.times.put("Asar", "16:25");
        d.times.put("Maghrib", "19:15"); d.times.put("Isyak", "20:27");
        d.syuruk = "07:00"; d.nextName = "Zohor"; d.nextTime = "13:10";
        d.nextMs = System.currentTimeMillis() + 33 * 60000L;
        Weather.Data w = new Weather.Data();
        w.tempC = 31; w.emoji = "🌤"; w.city = "Petaling Jaya";
        android.widget.ImageView iv = new android.widget.ImageView(this);
        d.currentName = "Zohor"; d.currentTime = "13:10"; d.nextName = "Asar"; d.nextTime = "16:25";
        d.nextMs = System.currentTimeMillis() + 135 * 60000L;
        iv.setImageBitmap(CardArt.card(640, d, w));
        new android.app.AlertDialog.Builder(this).setView(iv).show();
    }


    private void alertLater(boolean messageStyle) {
        Toast.makeText(this, "Alert in 20 s: switch to Android Auto now", Toast.LENGTH_LONG).show();
        Spike.log("alert (" + (messageStyle ? "message" : "plain") + ") scheduled in 20 s");
        final Context app = getApplicationContext();
        handler.postDelayed(() -> sendAlert(app, messageStyle), 20_000);
    }

    private static void sendAlert(Context ctx, boolean messageStyle) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Prayer alerts", NotificationManager.IMPORTANCE_HIGH));
        }
        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        if (messageStyle) {
            Person sender = new Person.Builder().setName("Glass Auto").build();
            nb.setStyle(new NotificationCompat.MessagingStyle(sender)
                    .addMessage("Zohor prayer time now", System.currentTimeMillis(), sender))
              .setCategory(NotificationCompat.CATEGORY_MESSAGE);
            PendingIntent read = PendingIntent.getBroadcast(ctx, 1,
                    new Intent(ctx, AlertReceiver.class).setAction("MARK_READ"), PendingIntent.FLAG_IMMUTABLE);
            nb.addAction(new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_agenda, "Mark as read", read)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false).build());
            PendingIntent reply = PendingIntent.getBroadcast(ctx, 2,
                    new Intent(ctx, AlertReceiver.class).setAction("REPLY"), PendingIntent.FLAG_MUTABLE);
            nb.addAction(new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Reply", reply)
                    .addRemoteInput(new RemoteInput.Builder("reply").setLabel("Reply").build())
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setShowsUserInterface(false).build());
        } else {
            nb.setContentTitle("Zohor").setContentText("Prayer time now").setCategory(NotificationCompat.CATEGORY_REMINDER);
        }
        try {
            nm.notify(messageStyle ? 11 : 12, nb.build());
            Spike.log("alert (" + (messageStyle ? "message" : "plain") + ") posted; did it show on the car screen?");
        } catch (SecurityException e) {
            Spike.log("alert blocked: notification permission not granted");
        }
    }
}
