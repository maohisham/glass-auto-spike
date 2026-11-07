package com.glass.aaspike;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;

/**
 * The alert shown when a prayer time arrives. It is a message-style notification, because that is the kind Android
 * Auto shows on the car screen and reads aloud: "Zohor 1:10 PM · Petaling Jaya". It also appears on the phone.
 */
final class PrayerAlert {

    private static final String CHANNEL = "prayer_alerts";

    private PrayerAlert() { }

    static void post(Context ctx, String prayer, String time12, String place) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Prayer alerts", NotificationManager.IMPORTANCE_HIGH));
        }
        String text = "Masuk waktu " + prayer + " " + time12 + (place.isEmpty() ? "" : " bagi kawasan " + place);
        Person sender = new Person.Builder().setName("Waktu solat").build();

        // Android Auto only shows a message notification that offers both of these actions
        PendingIntent read = PendingIntent.getBroadcast(ctx, 1,
                new Intent(ctx, AlertReceiver.class).setAction("MARK_READ"), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent reply = PendingIntent.getBroadcast(ctx, 2,
                new Intent(ctx, AlertReceiver.class).setAction("REPLY"), PendingIntent.FLAG_MUTABLE);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(prayer + "  ·  " + time12)
                .setContentText(text)
                .setStyle(new NotificationCompat.MessagingStyle(sender)
                        .setConversationTitle(prayer + "  ·  " + time12)
                        .addMessage(text, System.currentTimeMillis(), sender))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setTimeoutAfter(15 * 60_000L)
                .addAction(new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_agenda, "Mark as read", read)
                        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                        .setShowsUserInterface(false).build())
                .addAction(new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Reply", reply)
                        .addRemoteInput(new RemoteInput.Builder("reply").setLabel("Reply").build())
                        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                        .setShowsUserInterface(false).build());
        try {
            nm.notify(100 + Math.abs(prayer.hashCode() % 50), nb.build());
            Spike.log("prayer alert posted: " + text);
        } catch (SecurityException e) {
            Spike.log("prayer alert NOT posted: notification permission is not granted");
        }
    }
}
