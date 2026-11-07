package com.glass.prayerlive;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.Screen;
import androidx.car.app.Session;

/** One car session: it opens the live prayer screen. */
public class LiveSession extends Session {

    @NonNull
    @Override
    public Screen onCreateScreen(@NonNull Intent intent) {
        return new LiveScreen(getCarContext());
    }
}
