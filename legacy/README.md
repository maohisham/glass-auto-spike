# Legacy: the Android Auto experiments

These three modules came first and are kept for reference. They are not part of the default build.

- `app` (Glass Auto): a media card for Android Auto with lyrics, album cover and prayer alerts.
- `prayer` (Prayer Times): a Car App Library prayer list.
- `prayerlive` (Prayer Live): a navigation-category screen with weather animation.

The app moved to the head unit panel (`../headunit`) because the phone overheated while projecting to the car.
To build one again, uncomment its `include` line in the root `settings.gradle`.
