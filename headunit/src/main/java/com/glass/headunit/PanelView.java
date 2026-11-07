package com.glass.headunit;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The right-hand panel, built from plain views over a soft glass backdrop: the prayer strip on top (tap it for the full
 * table), then the song, its progress bar, one line of round media buttons, a small row of round shortcut buttons,
 * then the lyrics. When nothing is playing the prayer table fills the space. The views are rebuilt only when the width
 * class, text size or theme changes; ordinary updates only change text, and the lyrics list scrolls only when the lit
 * line changes.
 */
final class PanelView extends LinearLayout {

    interface Controls { void play(); void pause(); void next(); void previous(); void toggleShuffle(); void toggleRepeat(); }

    private static final String MAPS = "com.google.android.apps.maps";

    private PanelMode mode = PanelMode.REGULAR;
    private int textSize = 1;
    private boolean dark = true;
    private Controls controls;
    private Runnable onPlayer, onSettings, onPrayer, onDrive, onWeather;
    private boolean scoreEnabled = true;
    private EcoScore.Stats score;
    private TextView scoreChip, speedChip;
    private float speedKmh;
    private boolean speedFix;

    private Prayer.Data prayer;
    private Weather.Data weather;
    private boolean offline, locationMissing;
    private NowPlaying now = NowPlaying.idle();
    private long nowReceivedAt;

    private LinearLayout headerBlock, topRow, stripBox, banners, nowBox, songRow, mediaRow, progRow, lyricList, idleBox;
    private CoverView coverView;
    private MaxHeightScrollView headerScroll;
    private TextView weatherText, prayerText, title, artist, bigText, header, elapsedText, durationText, idleNote;
    private IconView shuffleBtn, repeatBtn, prevBtn, mainBtn, nextBtn;
    private View leftSpacer, rightSpacer;
    private BarView mediaBar;
    private PrayerTableView idleTable;
    private TextView idleWeather;
    private ScrollView scroll;
    private Lyrics shownLyrics;
    private final List<TextView> lineViews = new ArrayList<>();
    private int litLine = -2;
    private int tickCount;
    private static boolean toldAboutHelper;

    PanelView(Context ctx) {
        super(ctx);
        build();
    }

    // ---- configuration from the activity ----

    void setControls(Controls c) { controls = c; }
    void setOnPlayer(Runnable r) { onPlayer = r; build(); }
    void setOnSettings(Runnable r) { onSettings = r; build(); }
    void setOnPrayer(Runnable r) { onPrayer = r; }
    void setOnDrive(Runnable r) { onDrive = r; }
    void setOnWeather(Runnable r) { onWeather = r; }

    /** Shows or hides the driving score chip (the setting). */
    void setScoreEnabled(boolean on) {
        if (on == scoreEnabled) return;
        scoreEnabled = on;
        build();
    }

    /** The current speed for the chip; pass hasFix false when there is no reading. */
    void showSpeed(float kmh, boolean hasFix) {
        speedKmh = kmh;
        speedFix = hasFix;
        bindSpeed();
    }

    /** The latest score, or null when there is no trip yet. */
    void showScore(EcoScore.Stats s) {
        score = s;
        bindScore();
    }

    /** Rebuilds only when something that changes the views changed. */
    void setStyle(PanelMode m, int textSize, boolean dark) {
        if (m == mode && textSize == this.textSize && dark == this.dark) return;
        this.mode = m;
        this.textSize = textSize;
        this.dark = dark;
        build();
    }

    PanelMode mode() { return mode; }
    int textSize() { return textSize; }
    boolean isDark() { return dark; }
    Prayer.Data prayer() { return prayer; }
    Weather.Data weather() { return weather; }

    // ---- data in ----

    void showData(Prayer.Data p, Weather.Data w, boolean offline, boolean locationMissing) {
        prayer = p;
        weather = w;
        this.offline = offline;
        this.locationMissing = locationMissing;
        bindTop();
        bindBanners();
        bindLyricsArea();
    }

    void showNowPlaying(NowPlaying n) {
        now = n == null ? NowPlaying.idle() : n;
        nowReceivedAt = SystemClock.elapsedRealtime();
        bindNow();
        bindLyricsArea();
    }

    /** Once a second: the countdown and the song's bar move; banners are re-checked every 5 s (permissions can change while it runs). */
    void tick() {
        if (prayer != null) Prayer.recompute(prayer);
        bindTop();
        bindProgress();
        if (now.pkg == null) bindLyricsArea();
        if (++tickCount % 5 == 0) bindBanners();
    }

    // ---- building ----

    private float sp(float base) { return PanelStyle.sp(base, textSize, mode); }

    private int dp(float v) { return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics())); }

    private TextView text(float baseSp, boolean bold, int color) {
        TextView t = new TextView(getContext());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(baseSp));
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setTextColor(color);
        return t;
    }

    private int iconDp(int base) { return Math.round(base * (mode == PanelMode.COMPACT ? 0.92f : 1f)); }

    private IconView icon(Icon i, String description, int sizeDp, IconView.Style style, Runnable r) {
        return new IconView(getContext(), i, description, dark, iconDp(sizeDp), style, r);
    }

    /** A tappable area inside a glass card that lights up while pressed. */
    private LinearLayout tapArea(String description, View content, Runnable onClick) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackground(Glass.pressOverlay(getContext(), dark, 12));
        box.setClickable(true);
        box.setContentDescription(description);
        box.setOnClickListener(v -> onClick.run());
        box.addView(content);
        return box;
    }

    private LayoutParams gapped(int leftDp) {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(leftDp);
        return lp;
    }

    private void build() {
        removeAllViews();
        lineViews.clear();
        shownLyrics = null;
        litLine = -2;
        setOrientation(VERTICAL);
        int pad = dp(mode == PanelMode.COMPACT ? 12 : 20);
        setPadding(pad, pad, pad, pad);
        setBackground(new GlassBackground(dark));
        int fg = PanelStyle.fg(dark), dim = PanelStyle.dim(dark), accent = PanelStyle.accent(dark);

        // everything above the lyrics scrolls if the panel is short, so the controls are never clipped
        headerBlock = new LinearLayout(getContext());
        headerBlock.setOrientation(VERTICAL);
        headerScroll = new MaxHeightScrollView(getContext());
        headerScroll.setVerticalScrollBarEnabled(false);
        headerScroll.addView(headerBlock);
        headerScroll.setMaxHeightPx(PanelStyle.headerMaxPx(getHeight()));
        addView(headerScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // top row: the three round shortcuts (the driving score chip joins them on the right)
        topRow = new LinearLayout(getContext());
        topRow.setOrientation(HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(0, 0, 0, dp(10));
        boolean firstIcon = true;
        if (onPlayer != null) { topRow.addView(icon(Icon.PLAYER, "Choose a player", 44, IconView.Style.NORMAL, () -> onPlayer.run()), gapped(0)); firstIcon = false; }
        if (onSettings != null) { topRow.addView(icon(Icon.SETTINGS, "Settings", 44, IconView.Style.NORMAL, () -> onSettings.run()), gapped(firstIcon ? 0 : 12)); firstIcon = false; }
        if (mapsInstalled()) { topRow.addView(icon(Icon.MAPS, "Open Maps", 44, IconView.Style.NORMAL, this::openMaps), gapped(firstIcon ? 0 : 12)); }
        topRow.addView(new View(getContext()), new LayoutParams(0, 1, 1f));
        scoreChip = null;
        speedChip = null;
        if (scoreEnabled) {
            speedChip = text(16, true, fg);
            speedChip.setGravity(Gravity.CENTER);
            speedChip.setPadding(dp(16), dp(10), dp(16), dp(10));
            speedChip.setBackground(Glass.button(getContext(), dark, false, false, 22));
            speedChip.setContentDescription("Current speed");
            LayoutParams sp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            sp.rightMargin = dp(10);
            topRow.addView(speedChip, sp);
            scoreChip = text(16, true, dim);
            scoreChip.setGravity(Gravity.CENTER);
            scoreChip.setPadding(dp(16), dp(10), dp(16), dp(10));
            scoreChip.setBackground(Glass.button(getContext(), dark, false, false, 22));
            scoreChip.setClickable(true);
            scoreChip.setContentDescription("Open the driving score");
            scoreChip.setOnClickListener(v -> { if (onDrive != null) onDrive.run(); });
            topRow.addView(scoreChip);
        }
        topRow.setVisibility(topRow.getChildCount() <= 1 ? GONE : VISIBLE);
        headerBlock.addView(topRow);

        // prayer strip: tap for the full table
        stripBox = new LinearLayout(getContext());
        stripBox.setOrientation(mode == PanelMode.COMPACT ? VERTICAL : HORIZONTAL);
        stripBox.setPadding(dp(4), dp(4), dp(4), dp(4));
        stripBox.setBackground(Glass.button(getContext(), dark, false, false, 14));
        weatherText = text(16, false, fg);
        prayerText = text(16, true, accent);
        // two tappable halves: the weather opens the forecast, the prayer opens the table
        LinearLayout weatherBox = tapArea("Open the weather", weatherText, () -> { if (onWeather != null) onWeather.run(); });
        // the mosque icon sits at the left of the prayer text
        LinearLayout prayerLine = new LinearLayout(getContext());
        prayerLine.setOrientation(HORIZONTAL);
        prayerLine.setGravity(mode == PanelMode.COMPACT ? Gravity.CENTER_VERTICAL | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.END);
        TextView mosque = text(20, false, fg);      // the emoji keeps its own colours
        mosque.setText("🕌");
        mosque.setContentDescription("Prayer");
        prayerLine.addView(mosque);
        LayoutParams pl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        pl.leftMargin = dp(8);
        prayerLine.addView(prayerText, pl);
        LinearLayout prayerBox = tapArea("Open the prayer times", prayerLine, () -> { if (onPrayer != null) onPrayer.run(); });
        if (mode == PanelMode.COMPACT) {
            stripBox.addView(prayerBox, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            stripBox.addView(weatherBox, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        } else {
            stripBox.addView(weatherBox, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            stripBox.addView(prayerBox, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.4f));
        }
        headerBlock.addView(stripBox);

        banners = new LinearLayout(getContext());
        banners.setOrientation(VERTICAL);
        headerBlock.addView(banners);

        nowBox = new LinearLayout(getContext());
        nowBox.setOrientation(VERTICAL);
        nowBox.setPadding(0, dp(12), 0, dp(4));
        title = text(20, true, fg);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        artist = text(15, false, dim);
        artist.setMaxLines(1);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        songRow = new LinearLayout(getContext());
        songRow.setOrientation(HORIZONTAL);
        songRow.setGravity(Gravity.CENTER_VERTICAL);
        coverView = new CoverView(getContext(), dark, Math.round(60 * (mode == PanelMode.COMPACT ? 0.92f : 1f)));
        songRow.addView(coverView);
        LinearLayout words = new LinearLayout(getContext());
        words.setOrientation(VERTICAL);
        words.addView(title);
        words.addView(artist);
        LayoutParams wl = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        wl.leftMargin = dp(12);
        songRow.addView(words, wl);
        nowBox.addView(songRow);

        // progress: elapsed, bar with a knob, length
        progRow = new LinearLayout(getContext());
        progRow.setOrientation(HORIZONTAL);
        progRow.setGravity(Gravity.CENTER_VERTICAL);
        progRow.setPadding(0, dp(10), 0, 0);
        elapsedText = text(12, false, dim);
        durationText = text(12, false, dim);
        mediaBar = new BarView(getContext(), dark, true);
        progRow.addView(elapsedText);
        LayoutParams barLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        barLp.leftMargin = dp(8);
        barLp.rightMargin = dp(8);
        progRow.addView(mediaBar, barLp);
        progRow.addView(durationText);
        nowBox.addView(progRow);

        // one line of media controls: shuffle, previous, play or pause, next (a spacer keeps play centred beside shuffle)
        mediaRow = new LinearLayout(getContext());
        mediaRow.setOrientation(HORIZONTAL);
        mediaRow.setGravity(Gravity.CENTER);
        mediaRow.setPadding(0, dp(10), 0, 0);
        shuffleBtn = icon(Icon.SHUFFLE, "Shuffle", 42, IconView.Style.NORMAL, () -> { if (controls != null) controls.toggleShuffle(); });
        prevBtn = icon(Icon.PREV, "Previous", 50, IconView.Style.NORMAL, () -> { if (controls != null) controls.previous(); });
        mainBtn = icon(Icon.PLAY, "Play", 62, IconView.Style.MAIN, () -> { if (controls != null) { if (now.playing) controls.pause(); else controls.play(); } });
        nextBtn = icon(Icon.NEXT, "Next", 50, IconView.Style.NORMAL, () -> { if (controls != null) controls.next(); });
        repeatBtn = icon(Icon.REPEAT, "Repeat off", 42, IconView.Style.NORMAL, () -> { if (controls != null) controls.toggleRepeat(); });
        // invisible spacers keep play centred when only one of shuffle and repeat is offered
        leftSpacer = new View(getContext());
        rightSpacer = new View(getContext());
        mediaRow.addView(shuffleBtn);
        mediaRow.addView(leftSpacer, new LayoutParams(Glass.dp(getContext(), iconDp(42)), 1));
        mediaRow.addView(prevBtn, gapped(10));
        mediaRow.addView(mainBtn, gapped(10));
        mediaRow.addView(nextBtn, gapped(10));
        mediaRow.addView(rightSpacer, new LayoutParams(Glass.dp(getContext(), iconDp(42)) + dp(10), 1));
        mediaRow.addView(repeatBtn, gapped(10));
        nowBox.addView(mediaRow);

        headerBlock.addView(nowBox);

        scroll = new ScrollView(getContext());
        scroll.setVerticalScrollBarEnabled(false);
        lyricList = new LinearLayout(getContext());
        lyricList.setOrientation(VERTICAL);
        scroll.addView(lyricList);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        bigText = text(26, true, fg);
        bigText.setGravity(Gravity.CENTER);
        addView(bigText, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        // nothing playing: the prayer table fills the space
        idleBox = new LinearLayout(getContext());
        idleBox.setOrientation(VERTICAL);
        idleTable = new PrayerTableView(getContext(), dark, textSize, mode);
        idleNote = text(15, false, dim);
        idleWeather = text(15, false, dim);
        idleWeather.setPadding(dp(10), dp(14), dp(10), dp(10));
        idleWeather.setBackground(Glass.pressOverlay(getContext(), dark, 12));
        idleWeather.setClickable(true);
        idleWeather.setContentDescription("Open the weather");
        idleWeather.setOnClickListener(v -> { if (onWeather != null) onWeather.run(); });
        idleBox.addView(idleNote);
        idleBox.addView(idleTable);
        idleBox.addView(idleWeather);
        ScrollView idleScroll = new ScrollView(getContext());
        idleScroll.setVerticalScrollBarEnabled(false);
        idleScroll.addView(idleBox);
        idleScroll.setTag("idle");
        addView(idleScroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        bindTop();
        bindBanners();
        bindNow();
        bindLyricsArea();
        bindScore();
        bindSpeed();
    }

    private void bindSpeed() {
        if (speedChip == null) return;
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(SpeedFormat.text(speedKmh, speedFix));
        int numberEnd = sb.length();
        sb.append(" km/h");
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.68f), numberEnd, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.ForegroundColorSpan(PanelStyle.dim(dark)), numberEnd, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        speedChip.setText(sb);
    }

    private void bindScore() {
        if (scoreChip == null) return;
        if (score == null || score.grade.isEmpty()) {
            scoreChip.setText("Score  --");
            scoreChip.setTextColor(PanelStyle.dim(dark));
        } else {
            scoreChip.setText("Score  " + score.score);
            scoreChip.setTextColor(PanelStyle.scoreColor(score.score));
        }
    }

    private View idleScroll() { return (View) idleBox.getParent(); }

    // ---- binding ----

    private void bindTop() {
        weatherText.setText(weather == null ? "" : weather.emoji + " " + Math.round(weather.tempC) + "°C"
                + (weather.city.isEmpty() ? "" : "  " + weather.city));
        prayerText.setText(prayer == null ? "" : prayer.justStarted
                ? "● " + prayer.currentName + " time now"
                : prayer.nextName + " " + Prayer.to12(prayer.nextTime) + "  ·  in " + Prayer.untilNext(prayer));
        stripBox.setVisibility(prayer == null && weather == null ? GONE : VISIBLE);
    }

    private void bindBanners() {
        banners.removeAllViews();
        if (!MediaFollower.hasMediaAccess(getContext())) {
            Button b = Glass.textButton(getContext(), "Allow media access (to show lyrics)", dark,
                    () -> Intents.safeStart(getContext(), new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), "notification access"));
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            banners.addView(b, lp);
        }
        if (!mapsInstalled()) {
            TextView t = text(14, false, PanelStyle.accent(dark));
            t.setText("Google Maps isn't installed or is disabled on this unit.");
            banners.addView(t);
        }
        if (locationMissing) {
            TextView t = text(13, false, PanelStyle.dim(dark));
            t.setText("Location unavailable" + (prayer == null ? "" : " — showing the last known place"));
            banners.addView(t);
        }
        if (offline) {
            TextView t = text(13, false, PanelStyle.dim(dark));
            t.setText("Offline — showing saved data");
            banners.addView(t);
        }
    }

    /** One tap to Google Maps: beside the panel when the screen is split, full-screen when the split did not start. */
    void openMaps() {
        Intent i = getContext().getPackageManager().getLaunchIntentForPackage(MAPS);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        Intents.safeStart(getContext(), i, "Google Maps");
        // The system splits the screen 50:50: the window helper moves the divider to the user's ratio
        SplitHelperService helper = SplitHelperService.get();
        if (helper != null) helper.applyRatioWhenSplit();
        else if (!toldAboutHelper) {
            toldAboutHelper = true;
            android.widget.Toast.makeText(getContext(), "Turn on the window helper (Settings, Setup) to set the map/panel ratio.", android.widget.Toast.LENGTH_LONG).show();
            Spike.log("ratio not applied: the window helper is off");
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (headerScroll != null) headerScroll.setMaxHeightPx(PanelStyle.headerMaxPx(h));
    }

    private boolean mapsInstalled() {
        try {
            return getContext().getPackageManager().getApplicationInfo(MAPS, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private void bindNow() {
        boolean idle = now.pkg == null;
        coverView.setCover(now.cover);
        coverView.setVisibility(idle ? GONE : VISIBLE);
        mainBtn.setIcon(now.playing ? Icon.PAUSE : Icon.PLAY);
        mainBtn.setContentDescription(now.playing ? "Pause" : "Play");
        boolean showShuffle = !idle && now.shuffleAvailable;
        boolean showRepeat = !idle && now.repeatAvailable;
        shuffleBtn.setVisibility(showShuffle ? VISIBLE : GONE);
        repeatBtn.setVisibility(showRepeat ? VISIBLE : GONE);
        leftSpacer.setVisibility(!showShuffle && showRepeat ? VISIBLE : GONE);
        rightSpacer.setVisibility(showShuffle && !showRepeat ? VISIBLE : GONE);
        repeatBtn.setIcon(now.repeatMode == android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_ONE ? Icon.REPEAT_ONE : Icon.REPEAT);
        repeatBtn.setStyle(RepeatMode.isOn(now.repeatMode) ? IconView.Style.ON : IconView.Style.NORMAL);
        repeatBtn.setContentDescription(RepeatMode.label(now.repeatMode));
        shuffleBtn.setStyle(now.shuffleOn ? IconView.Style.ON : IconView.Style.NORMAL);
        shuffleBtn.setContentDescription(now.shuffleOn ? "Shuffle on" : "Shuffle off");
        mediaRow.setVisibility(idle ? GONE : VISIBLE);
        progRow.setVisibility(!idle && ControlsPolicy.showProgress(now.pkg, now.durationMs) ? VISIBLE : GONE);
        if (idle) {
            title.setText("Nothing playing");
            artist.setText("");
        } else {
            title.setText(now.title.isEmpty() ? now.playerLabel : now.title);
            artist.setText(now.artist);
        }
        artist.setVisibility(artist.getText().length() == 0 ? GONE : VISIBLE);
        bindProgress();
    }

    /** The song's bar moves between updates: the position we were told plus the time since, while it plays. */
    private void bindProgress() {
        if (progRow.getVisibility() != VISIBLE) return;
        long pos = MediaProgress.extrapolate(now.positionMs, nowReceivedAt, SystemClock.elapsedRealtime(), now.playing, now.durationMs);
        mediaBar.setFraction(MediaProgress.fraction(pos, now.durationMs));
        elapsedText.setText(MediaProgress.format(pos));
        durationText.setText(MediaProgress.format(now.durationMs));
    }

    /** Decides what fills the space under the controls: lyrics, the song large, or the prayer table when nothing plays. */
    private void bindLyricsArea() {
        boolean idle = now.pkg == null;
        stripBox.setVisibility(idle || (prayer == null && weather == null) ? GONE : VISIBLE);
        boolean haveLyrics = !idle && !now.lyricsLoading && now.lyrics != null;
        scroll.setVisibility(haveLyrics ? VISIBLE : GONE);
        bigText.setVisibility(!idle && !haveLyrics ? VISIBLE : GONE);
        idleScroll().setVisibility(idle ? VISIBLE : GONE);
        nowBox.setVisibility(VISIBLE);
        if (idle) {
            title.setVisibility(VISIBLE);
            bindIdle();
            return;
        }
        if (!haveLyrics) {
            // no lyrics (yet): the song stays up top with its cover, and the space says what is going on
            title.setVisibility(VISIBLE);
            artist.setVisibility(artist.getText().length() == 0 ? GONE : VISIBLE);
            bigText.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(24));
            bigText.setText(now.lyricsLoading ? "Finding lyrics…" : "No lyrics for this track");
            return;
        }
        title.setVisibility(VISIBLE);
        artist.setVisibility(artist.getText().length() == 0 ? GONE : VISIBLE);
        if (now.lyrics != shownLyrics) rebuildLines();
        int idx = now.lyrics.lastNonEmptyAtOrBefore(now.line);
        light(idx, idx < 0);
    }

    private void bindIdle() {
        if (prayer == null) {
            idleNote.setVisibility(VISIBLE);
            idleNote.setText("Prayer times appear once the unit is online and has a location.");
            idleTable.setVisibility(GONE);
        } else {
            idleNote.setVisibility(GONE);
            idleTable.setVisibility(VISIBLE);
            idleTable.bind(prayer, System.currentTimeMillis());
        }
        idleWeather.setText(weather == null ? "" : weather.emoji + " " + Math.round(weather.tempC) + "°C  " + weather.city + "  ·  forecast");
        idleWeather.setVisibility(weather == null ? GONE : VISIBLE);
    }

    private void rebuildLines() {
        lyricList.removeAllViews();
        lineViews.clear();
        shownLyrics = now.lyrics;
        litLine = -2;
        header = text(20, true, PanelStyle.fg(dark));
        header.setText(now.title + (now.artist.isEmpty() ? "" : "\n" + now.artist));
        header.setPadding(0, dp(8), 0, dp(16));
        lyricList.addView(header);
        for (String l : now.lyrics.lines) {
            TextView t = text(18, false, PanelStyle.dim(dark));
            t.setText(l.isEmpty() ? " " : l);
            t.setPadding(0, dp(6), 0, dp(6));
            lyricList.addView(t);
            lineViews.add(t);
        }
        // room at the end so the last line can be centred too
        View pad = new View(getContext());
        lyricList.addView(pad, new LayoutParams(1, dp(160)));
    }

    /** Lights one line (or the header while the song has not reached its first line) and scrolls to it, only when it changed. */
    private void light(int idx, boolean headerLit) {
        int key = headerLit ? -1 : idx;
        if (key == litLine) return;
        litLine = key;
        int fg = PanelStyle.fg(dark), dim = PanelStyle.dim(dark);
        header.setAlpha(headerLit ? 1f : 0.5f);
        for (int i = 0; i < lineViews.size(); i++) {
            TextView t = lineViews.get(i);
            boolean lit = !headerLit && i == idx;
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(lit ? 26 : 18));
            t.setTypeface(Typeface.DEFAULT, lit ? Typeface.BOLD : Typeface.NORMAL);
            t.setTextColor(lit ? fg : dim);
        }
        final View target = headerLit || idx < 0 ? header : lineViews.get(idx);
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, target.getTop() - (scroll.getHeight() - target.getHeight()) / 2)));
    }
}
