package com.glass.headunit;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** The app's event log on screen, with a Copy button, so a unit without adb (the Eonon) can still be debugged. */
public class LogActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView view;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            view.setText(Spike.dump());
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        Button copy = new Button(this);
        copy.setAllCaps(false);
        copy.setText("Copy the log");
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Glass Panel log", Spike.dump()));
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
        });
        root.addView(copy);

        view = new TextView(this);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextSize(11f);
        view.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(this);
        sv.addView(view);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(refresh); }
}
