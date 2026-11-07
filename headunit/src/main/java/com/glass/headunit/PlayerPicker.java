package com.glass.headunit;

import android.content.Context;
import android.support.v4.media.MediaBrowserCompat;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Shown over the panel (or in the bubble's overlay): pick a player, browse its library where it allows that, search
 * for a song, or replay a recent one. Built from plain views.
 */
final class PlayerPicker extends LinearLayout {

    private final Context ctx;
    private final MediaFollower follower;
    private final PlayerSwitcher switcher;
    private final Runnable onClose;
    private final boolean dark;
    private final LinearLayout content;
    private final TextView heading;
    private final Button back;
    private final List<String> stack = new ArrayList<>();   // parent ids of the browse pages, null = a player's root
    private String browsingPkg;
    private String notice;       // one-off message at the top of the main page, e.g. after a failed check

    PlayerPicker(Context ctx, MediaFollower followerOrNull, boolean dark, Runnable onClose) {
        super(ctx);
        this.ctx = ctx;
        this.follower = followerOrNull;
        this.switcher = new PlayerSwitcher(ctx, followerOrNull);
        this.onClose = onClose;
        this.dark = dark;
        setOrientation(VERTICAL);
        setBackground(new GlassBackground(dark));
        setPadding(24, 24, 24, 24);
        setClickable(true); // swallow touches so the panel below is not pressed through it

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(HORIZONTAL);
        back = button("Back", this::goBack);
        back.setVisibility(GONE);
        head.addView(back);
        heading = text("Players", 20, true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(heading, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        head.addView(button("Close", () -> this.onClose.run()));
        addView(head);

        content = new LinearLayout(ctx);
        content.setOrientation(VERTICAL);
        ScrollView sv = new ScrollView(ctx);
        sv.addView(content);
        addView(sv, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        showMain();
    }

    /** Back inside the browse pages; false when already on the main page (so the caller can close the picker). */
    boolean handleBack() {
        if (browsingPkg == null) return false;
        goBack();
        return true;
    }

    // ---- main page ----

    private void showMain() {
        browsingPkg = null;
        stack.clear();
        back.setVisibility(GONE);
        heading.setText("Players");
        content.removeAllViews();
        if (notice != null) { content.addView(text(notice, 14, false)); notice = null; }

        String current = follower != null && follower.controller() != null ? follower.controller().getPackageName() : null;
        List<String[]> players = PlayerList.order(Proxy.players(ctx));
        if (players.isEmpty()) content.addView(text("No media apps found on this unit.", 16, false));
        for (final String[] p : players) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(HORIZONTAL);
            Button pick = button((p[0].equals(current) ? "● " : "") + p[1], () -> {
                switcher.switchTo(p[0]);
                onClose.run();
            });
            row.addView(pick, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            int status = BrowseStatus.get(ctx, p[0]);
            if (status == BrowseStatus.ALLOWED) row.addView(button("Browse", () -> browse(p[0], p[1], null)));
            else if (status == BrowseStatus.UNKNOWN) row.addView(button("Check browse", () -> probe(p[0], p[1])));
            content.addView(row);
            if (status == BrowseStatus.REFUSED) {
                content.addView(text("   " + p[1] + " keeps its library closed: use search or recent below.", 13, false));
                content.addView(button("Check " + p[1] + " again", () -> probe(p[0], p[1])));
            }
        }

        content.addView(text("Search and play", 18, true));
        final EditText q = new EditText(ctx);
        q.setHint("Song or artist");
        q.setSingleLine();
        q.setInputType(InputType.TYPE_CLASS_TEXT);
        q.setTextColor(PanelStyle.fg(dark));
        q.setHintTextColor(PanelStyle.dim(dark));
        content.addView(q);
        content.addView(button("Search and play in " + (current == null ? "the first player" : Proxy.label(ctx, current)), () -> {
            String query = q.getText().toString().trim();
            if (query.isEmpty()) return;
            String pkg = current != null ? current : (players.isEmpty() ? null : players.get(0)[0]);
            if (pkg == null) return;
            switcher.playSong(pkg, query, "");
            onClose.run();
        }));

        List<History.Entry> recent = History.recent(ctx, 12);
        content.addView(text("Recent", 18, true));
        if (recent.isEmpty()) content.addView(text("Songs you play will show up here.", 13, false));
        for (final History.Entry e : recent) {
            content.addView(button(e.title + (e.artist.isEmpty() ? "" : " — " + e.artist), () -> {
                switcher.playSong(e.pkg, e.title, e.artist);
                onClose.run();
            }));
        }
    }

    // ---- browse pages ----

    private void probe(final String pkg, final String label) {
        content.removeAllViews();
        content.addView(text("Checking whether " + label + " lets us browse…", 16, false));
        BrowseProbe.load(ctx, pkg, null, (items, error) -> {
            int verdict = BrowseStatus.fromError(error);
            BrowseStatus.set(ctx, pkg, verdict);
            if (error == null) { showItems(pkg, label, null, items); return; }
            if (verdict == BrowseStatus.UNKNOWN) notice = "Could not check " + label + " (" + error + "). Try again in a moment.";
            showMain();
        });
    }

    private void browse(final String pkg, final String label, final String parentId) {
        content.removeAllViews();
        content.addView(text("Loading…", 16, false));
        BrowseProbe.load(ctx, pkg, parentId, (items, error) -> {
            if (error != null) {
                int verdict = BrowseStatus.fromError(error);
                if (verdict == BrowseStatus.REFUSED) BrowseStatus.set(ctx, pkg, verdict);
                else notice = "Could not load that list (" + error + "). Try again.";
                showMain();
                return;
            }
            showItems(pkg, label, parentId, items);
        });
    }

    private void showItems(final String pkg, final String label, String parentId, List<MediaBrowserCompat.MediaItem> items) {
        if (browsingPkg == null || stack.isEmpty() || !java.util.Objects.equals(stack.get(stack.size() - 1), parentId)) stack.add(parentId);
        browsingPkg = pkg;
        back.setVisibility(VISIBLE);
        heading.setText(label);
        content.removeAllViews();
        if (items.isEmpty()) content.addView(text("Nothing here.", 16, false));
        for (final MediaBrowserCompat.MediaItem it : items) {
            CharSequence t = it.getDescription().getTitle();
            String name = t == null ? "(untitled)" : t.toString();
            if (it.isBrowsable()) {
                content.addView(button(name + "  ›", () -> browse(pkg, label, it.getMediaId())));
            } else {
                content.addView(button(name, () -> { switcher.playItem(pkg, it.getMediaId()); onClose.run(); }));
            }
        }
    }

    private void goBack() {
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            String parent = stack.remove(stack.size() - 1);
            browse(browsingPkg, heading.getText().toString(), parent);
        } else {
            showMain();
        }
    }

    // ---- small view helpers ----

    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(PanelStyle.fg(dark));
        t.setPadding(0, bold ? 24 : 8, 0, 8);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String label, Runnable r) {
        Button b = Glass.textButton(ctx, label, dark, r);
        b.setMinHeight(Glass.dp(ctx, 52));
        return b;
    }
}
