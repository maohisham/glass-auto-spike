package com.glass.prayer;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

/** Entry point Android Auto binds to. Spike: any host is allowed, which is fine for a sideloaded debug build. */
public class PrayerCarService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new PrayerSession();
    }
}
