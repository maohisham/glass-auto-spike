package com.glass.headunit;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.v4.media.MediaBrowserCompat;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Shown over the panel (or in the bubble's overlay): pick a player from round-cornered cards with their app icons, then
 * search for a song, replay a recent one, or browse the player's library where it allows that. Tapping a card starts
 * that player and keeps the list open so the next step is one tap away.
 */
final class PlayerPicker extends LinearLayout {

    private enum Tab { SEARCH, RECENT, LIBRARY }

    private final Context ctx;
    private final MediaFollower follower;
    private final PlayerSwitcher switcher;
    private final Runnable onClose;
    private final boolean dark;
    private final LinearLayout cards, moreCards, tabs, content;
    private final Button moreToggle;
    private final List<String> stack = new ArrayList<>();   // parent ids of the library pages, null = the player's root
    private String selectedPkg, selectedLabel = "";
    private Tab tab = Tab.SEARCH;
    private boolean moreOpen;
    private boolean browsing;

    PlayerPicker(Context ctx, MediaFollower followerOrNull, boolean dark, Runnable onClose) {
        super(ctx);
        this.ctx = ctx;
        this.follower = followerOrNull;
        this.switcher = new PlayerSwitcher(ctx, followerOrNull);
        this.onClose = onClose;
        this.dark = dark;
        setOrientation(VERTICAL);
        setBackground(new GlassBackground(dark));
        int pad = Glass.dp(ctx, 16);
        setPadding(pad, pad, pad, pad);
        setClickable(true); // swallow touches so the panel below is not pressed through it

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(new IconView(ctx, Icon.BACK, "Back", dark, 44, IconView.Style.NORMAL, () -> { if (!handleBack()) onClose.run(); }));
        TextView title = label("Players", 20, true, PanelStyle.fg(dark));
        LayoutParams tp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = Glass.dp(ctx, 12);
        head.addView(title, tp);
        addView(head);

        cards = new LinearLayout(ctx);
        cards.setOrientation(HORIZONTAL);
        HorizontalScrollView cardScroll = new HorizontalScrollView(ctx);
        cardScroll.setHorizontalScrollBarEnabled(false);
        cardScroll.addView(cards);
        LayoutParams cl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cl.topMargin = Glass.dp(ctx, 12);
        addView(cardScroll, cl);

        moreToggle = pill("More apps", false, () -> { moreOpen = !moreOpen; refreshPlayers(); });
        moreCards = new LinearLayout(ctx);
        moreCards.setOrientation(HORIZONTAL);
        HorizontalScrollView moreScroll = new HorizontalScrollView(ctx);
        moreScroll.setHorizontalScrollBarEnabled(false);
        moreScroll.addView(moreCards);
        LinearLayout moreBox = new LinearLayout(ctx);
        moreBox.setOrientation(VERTICAL);
        moreBox.addView(moreToggle, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        moreBox.addView(moreScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LayoutParams ml = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ml.topMargin = Glass.dp(ctx, 8);
        addView(moreBox, ml);

        tabs = new LinearLayout(ctx);
        tabs.setOrientation(HORIZONTAL);
        LayoutParams tl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tl.topMargin = Glass.dp(ctx, 14);
        addView(tabs, tl);

        content = new LinearLayout(ctx);
        content.setOrientation(VERTICAL);
        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(content);
        LayoutParams sl = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
        sl.topMargin = Glass.dp(ctx, 10);
        addView(sv, sl);

        if (follower != null && follower.controller() != null) selectedPkg = follower.controller().getPackageName();
        refreshPlayers();
        showTab(Tab.SEARCH);
    }

    /** Back inside the library pages; false when already at the top (so the caller can close the picker). */
    boolean handleBack() {
        if (!browsing || stack.size() <= 1) return false;
        goBack();
        return true;
    }

    // ---- players ----

    private void refreshPlayers() {
        List<String[]> all = Proxy.players(ctx);
        PlayerList.Split split = PlayerList.split(all, Proxy.activePackages(ctx));
        if (selectedPkg == null && !split.main.isEmpty()) selectedPkg = split.main.get(0)[0];
        cards.removeAllViews();
        moreCards.removeAllViews();
        if (split.main.isEmpty() && split.more.isEmpty()) {
            cards.addView(label("No media apps found on this unit.", 15, false, PanelStyle.dim(dark)));
        }
        for (String[] p : split.main) cards.addView(card(p[0], p[1]));
        for (String[] p : split.more) {
            if (p[0].equals(selectedPkg)) selectedLabel = p[1];
            moreCards.addView(card(p[0], p[1]));
        }
        for (String[] p : split.main) if (p[0].equals(selectedPkg)) selectedLabel = p[1];
        ((View) moreToggle.getParent()).setVisibility(split.more.isEmpty() ? GONE : VISIBLE);
        moreCards.setVisibility(moreOpen ? VISIBLE : GONE);
        ((View) moreCards.getParent()).setVisibility(moreOpen ? VISIBLE : GONE);
        moreToggle.setText(moreOpen ? "Fewer apps" : "More apps (" + split.more.size() + ")");
        ((View) moreToggle.getParent()).setVisibility(split.more.isEmpty() ? GONE : VISIBLE);
    }

    private View card(final String pkg, final String name) {
        boolean selected = pkg.equals(selectedPkg);
        LinearLayout c = new LinearLayout(ctx);
        c.setOrientation(VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Glass.dp(ctx, 10);
        c.setPadding(pad, pad, pad, pad);
        c.setBackground(Glass.button(ctx, dark, false, selected, 16));
        c.setClickable(true);
        c.setContentDescription(name);
        c.setOnClickListener(v -> selectPlayer(pkg, name));
        c.addView(appIcon(pkg, 44), new LayoutParams(Glass.dp(ctx, 44), Glass.dp(ctx, 44)));
        TextView t = label(name, 12, selected, PanelStyle.fg(dark));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams tl = new LayoutParams(Glass.dp(ctx, 92), LayoutParams.WRAP_CONTENT);
        tl.topMargin = Glass.dp(ctx, 6);
        c.addView(t, tl);
        LayoutParams lp = new LayoutParams(Glass.dp(ctx, 114), LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Glass.dp(ctx, 8);
        c.setLayoutParams(lp);
        return c;
    }

    /** Tapping a card starts that player and keeps the list open, so searching in it is the next tap. */
    private void selectPlayer(String pkg, String name) {
        boolean changed = !pkg.equals(selectedPkg);
        selectedPkg = pkg;
        selectedLabel = name;
        if (changed || follower == null || follower.controller() == null || !pkg.equals(follower.controller().getPackageName())) switcher.switchTo(pkg);
        refreshPlayers();
        showTab(tab);
    }

    // ---- tabs ----

    private void showTab(Tab t) {
        tab = t;
        browsing = false;
        stack.clear();
        tabs.removeAllViews();
        tabs.addView(pill("Search", t == Tab.SEARCH, () -> showTab(Tab.SEARCH)), tabParams(0));
        tabs.addView(pill("Recent", t == Tab.RECENT, () -> showTab(Tab.RECENT)), tabParams(8));
        tabs.addView(pill("Library", t == Tab.LIBRARY, () -> showTab(Tab.LIBRARY)), tabParams(8));
        content.removeAllViews();
        if (t == Tab.SEARCH) showSearch();
        else if (t == Tab.RECENT) showRecent();
        else showLibrary();
    }

    private LayoutParams tabParams(int leftDp) {
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Glass.dp(ctx, leftDp);
        return lp;
    }

    private void showSearch() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final EditText q = new EditText(ctx);
        q.setHint("Song or artist");
        q.setSingleLine();
        q.setInputType(InputType.TYPE_CLASS_TEXT);
        q.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        q.setTextColor(PanelStyle.fg(dark));
        q.setHintTextColor(PanelStyle.dim(dark));
        q.setBackground(Glass.button(ctx, dark, false, false, 16));
        int pad = Glass.dp(ctx, 14);
        q.setPadding(pad, pad, pad, pad);
        row.addView(q, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        final Runnable go = () -> {
            String query = q.getText().toString().trim();
            if (query.isEmpty() || selectedPkg == null) return;
            switcher.playSong(selectedPkg, query, "");
            onClose.run();
        };
        IconView search = new IconView(ctx, Icon.SEARCH, "Search and play", dark, 48, IconView.Style.MAIN, go);
        LayoutParams sp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        sp.leftMargin = Glass.dp(ctx, 10);
        row.addView(search, sp);
        q.setOnEditorActionListener((v, actionId, e) -> { go.run(); return true; });
        content.addView(row);
        content.addView(label(selectedPkg == null ? "Pick a player above." : "Plays in " + selectedLabel + ". Say the song or artist with the keyboard's microphone.", 13, false, PanelStyle.dim(dark)),
                gapParams(10));
    }

    private void showRecent() {
        List<History.Entry> recent = History.recent(ctx, 25);
        if (recent.isEmpty()) {
            content.addView(label("Songs you play will show up here.", 14, false, PanelStyle.dim(dark)));
            return;
        }
        for (final History.Entry e : recent) {
            content.addView(row(e.title, e.artist, e.pkg, () -> { switcher.playSong(e.pkg, e.title, e.artist); onClose.run(); }), gapParams(8));
        }
    }

    // ---- library ----

    private void showLibrary() {
        if (selectedPkg == null) { content.addView(label("Pick a player above.", 14, false, PanelStyle.dim(dark))); return; }
        int status = BrowseStatus.get(ctx, selectedPkg);
        if (status == BrowseStatus.ALLOWED) { browse(selectedPkg, selectedLabel, null); return; }
        if (status == BrowseStatus.REFUSED) {
            content.addView(label(selectedLabel + " keeps its library closed to other apps. Use Search or Recent instead.", 14, false, PanelStyle.dim(dark)));
            content.addView(Glass.textButton(ctx, "Check again", dark, () -> probe(selectedPkg, selectedLabel)), gapParams(10));
            return;
        }
        probe(selectedPkg, selectedLabel);
    }

    private void probe(final String pkg, final String name) {
        content.removeAllViews();
        content.addView(label("Checking whether " + name + " lets us browse…", 14, false, PanelStyle.dim(dark)));
        BrowseProbe.load(ctx, pkg, null, (items, error) -> {
            int verdict = BrowseStatus.fromError(error);
            BrowseStatus.set(ctx, pkg, verdict);
            if (!pkg.equals(selectedPkg) || tab != Tab.LIBRARY) return;     // the user moved on
            content.removeAllViews();
            if (error == null) { stack.clear(); showItems(pkg, name, null, items); return; }
            if (verdict == BrowseStatus.REFUSED) { showLibrary(); return; }
            content.addView(label("Could not check " + name + " (" + error + ").", 14, false, PanelStyle.dim(dark)));
            content.addView(Glass.textButton(ctx, "Try again", dark, () -> probe(pkg, name)), gapParams(10));
        });
    }

    private void browse(final String pkg, final String name, final String parentId) {
        content.removeAllViews();
        content.addView(label("Loading…", 14, false, PanelStyle.dim(dark)));
        BrowseProbe.load(ctx, pkg, parentId, (items, error) -> {
            if (!pkg.equals(selectedPkg) || tab != Tab.LIBRARY) return;
            content.removeAllViews();
            if (error != null) {
                if (BrowseStatus.fromError(error) == BrowseStatus.REFUSED) BrowseStatus.set(ctx, pkg, BrowseStatus.REFUSED);
                content.addView(label("Could not load that list (" + error + ").", 14, false, PanelStyle.dim(dark)));
                content.addView(Glass.textButton(ctx, "Try again", dark, () -> browse(pkg, name, parentId)), gapParams(10));
                return;
            }
            showItems(pkg, name, parentId, items);
        });
    }

    private void showItems(final String pkg, final String name, String parentId, List<MediaBrowserCompat.MediaItem> items) {
        if (stack.isEmpty() || !java.util.Objects.equals(stack.get(stack.size() - 1), parentId)) stack.add(parentId);
        browsing = true;
        content.removeAllViews();
        if (items.isEmpty()) content.addView(label("Nothing here.", 14, false, PanelStyle.dim(dark)));
        for (final MediaBrowserCompat.MediaItem it : items) {
            CharSequence t = it.getDescription().getTitle(), sub = it.getDescription().getSubtitle();
            String title = t == null ? "(untitled)" : t.toString();
            if (it.isBrowsable()) {
                content.addView(row(title, sub == null ? "" : sub.toString(), null, () -> browse(pkg, name, it.getMediaId())), gapParams(8));
            } else {
                content.addView(row(title, sub == null ? "" : sub.toString(), null, () -> { switcher.playItem(pkg, it.getMediaId()); onClose.run(); }), gapParams(8));
            }
        }
    }

    private void goBack() {
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            String parent = stack.remove(stack.size() - 1);
            browse(selectedPkg, selectedLabel, parent);
        }
    }

    // ---- small view helpers ----

    private LayoutParams gapParams(int topDp) {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Glass.dp(ctx, topDp);
        return lp;
    }

    private TextView label(String s, float sp, boolean bold, int color) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setTextColor(color);
        return t;
    }

    /** A rounded glass pill; lit with the accent when selected. */
    private Button pill(String text, boolean selected, Runnable r) {
        Button b = new Button(ctx);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(selected ? PanelStyle.accent(dark) : PanelStyle.fg(dark));
        b.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        b.setBackground(Glass.button(ctx, dark, false, selected, 22));
        b.setMinHeight(Glass.dp(ctx, 44));
        b.setMinimumHeight(Glass.dp(ctx, 44));
        b.setPadding(Glass.dp(ctx, 18), 0, Glass.dp(ctx, 18), 0);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    /** A glass row: optional app icon, a title and a quieter second line. */
    private View row(String title, String subtitle, String iconPkg, Runnable onClick) {
        LinearLayout r = new LinearLayout(ctx);
        r.setOrientation(HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Glass.dp(ctx, 12);
        r.setPadding(pad, pad, pad, pad);
        r.setBackground(Glass.button(ctx, dark, false, false, 14));
        r.setClickable(true);
        r.setOnClickListener(v -> onClick.run());
        if (iconPkg != null) {
            LayoutParams ip = new LayoutParams(Glass.dp(ctx, 36), Glass.dp(ctx, 36));
            ip.rightMargin = Glass.dp(ctx, 12);
            r.addView(appIcon(iconPkg, 36), ip);
        }
        LinearLayout words = new LinearLayout(ctx);
        words.setOrientation(VERTICAL);
        TextView t = label(title, 16, true, PanelStyle.fg(dark));
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = label(subtitle, 13, false, PanelStyle.dim(dark));
            s.setSingleLine();
            s.setEllipsize(TextUtils.TruncateAt.END);
            words.addView(s);
        }
        r.addView(words, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }

    private View appIcon(String pkg, int sizeDp) {
        ImageView iv = new ImageView(ctx);
        Drawable d = null;
        try { d = ctx.getPackageManager().getApplicationIcon(pkg); } catch (Exception ignored) { }
        iv.setImageDrawable(d);
        final float radius = Glass.dp(ctx, sizeDp * 0.24f);
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        return iv;
    }
}
