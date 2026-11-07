package com.glass.prayer;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.Screen;
import androidx.car.app.Session;

/** One car session: it just opens the prayer list screen. */
public class PrayerSession extends Session {

    @NonNull
    @Override
    public Screen onCreateScreen(@NonNull Intent intent) {
        return new PrayerScreen(getCarContext());
    }
}
