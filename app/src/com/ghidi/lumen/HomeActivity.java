package com.ghidi.lumen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lumen v3 - "Backdrop".
 *
 * One idea: the thing you are about to watch fills the screen, and everything else gets out
 * of its way. The focused entry's artwork is the background; its title, episode and progress
 * are a column of type in the lower left, where the eye already is; the rest of the
 * television is a single rail of small cards along the bottom.
 *
 * v2 drew three bands - a Carry on row, a shelf, a sources strip - each with its own frame
 * and its own focus rules. v3 collapses them into one rail, because on a set with seven apps
 * and three sockets there is no second page to hint at, and three bands of chrome spend the
 * screen on borders rather than on the picture.
 *
 * What did not change: nothing is uninstalled, every setting still applies, the hint line is
 * still permanent, and every focusable stop still carries a spoken label.
 */
public class HomeActivity extends Activity {

    private static final int WATCH_ENTRIES = 4;   // part-finished things, newest app first
    private static final int REQ_TV_LISTINGS = 41;

    // ---- the one model the rail, the hero and the backdrop all read from
    private static final int KIND_WATCH = 0;
    private static final int KIND_APP = 1;
    private static final int KIND_ALL = 2;

    private static class Entry {
        int kind;
        WatchNext.Item watch;     // KIND_WATCH only
        AppEntry app;             // KIND_WATCH and KIND_APP
        String title = "";
        String kicker = "";
        String sub = "";
        String status = "";       // "16 minutes left"
        String ok = "Open";       // what OK will do, named
        float progress = 0f;
        int tint = Color.parseColor("#2A2F39");
        String artUri = "";
    }

    private FrameLayout root;
    private ImageView backdrop;
    private View scrimSide, scrimFoot;
    private LinearLayout rail;
    private HorizontalScrollView railScroll;
    private LinearLayout barRow;
    private LinearLayout sourcesRow;
    private TextView heroKicker, heroTitle, heroSub, heroStatus, heroOk;
    private View heroChip;
    private TextView heroChipText;
    private View progTrack, progFill;
    private View progRow;
    private TextView statusLine;
    private LinearLayout hintLine;

    private final List<Entry> entries = new ArrayList<Entry>();
    private final List<View> railFocusables = new ArrayList<View>();
    private final List<View> barFocusables = new ArrayList<View>();
    private final List<View> sourceFocusables = new ArrayList<View>();
    private final Map<String, Drawable> groundCache = new HashMap<String, Drawable>();

    private List<AppEntry> apps = new ArrayList<AppEntry>();
    private String builtWith = "";
    private boolean launching = false;
    private boolean shelfReady = false;
    private int railGen = 0;
    private int shownIndex = -1;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        long t0 = System.currentTimeMillis();
        setContentView(build());
        builtWith = signature();
        Log.i("LUMEN", "onCreate done in " + (System.currentTimeMillis() - t0) + "ms");

        if (!Prefs.firstBootDone(this)) {
            ui.postDelayed(new Runnable() {
                public void run() {
                    if (Prefs.firstBootDone(HomeActivity.this)) return;
                    startActivity(new Intent(HomeActivity.this, FirstBootActivity.class));
                }
            }, 700);
        }
        askForListings();
    }

    private void askForListings() {
        if (WatchNext.permitted(this)) return;
        if (Prefs.listingsAsked(this)) return;
        Prefs.setListingsAsked(this, true);
        ui.postDelayed(new Runnable() {
            public void run() {
                try {
                    requestPermissions(
                            new String[]{"android.permission.READ_TV_LISTINGS"}, REQ_TV_LISTINGS);
                } catch (Throwable ignored) { }
            }
        }, 1400);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] granted) {
        super.onRequestPermissionsResult(req, perms, granted);
        if (req == REQ_TV_LISTINGS) refreshRail();
    }

    @Override
    protected void onResume() {
        super.onResume();
        launching = false;
        String now = signature();
        if (!now.equals(builtWith)) {
            groundCache.clear();
            setContentView(build());
            builtWith = now;
        } else {
            if (shelfReady) clearStatus();
            refreshRail();
        }
    }

    private String signature() {
        return Prefs.bgIndex(this) + "/" + Prefs.glassIndex(this) + "/" + Prefs.tileIndex(this)
                + "/" + Prefs.sourcesVisible(this) + "/" + Prefs.hidden(this).size()
                + "/" + Prefs.alwaysShowNames(this) + "/" + Prefs.reduceMotion(this)
                + "/" + Prefs.highContrast(this) + "/" + Prefs.showRecents(this)
                + "/" + Prefs.textIndex(this) + "/" + Prefs.order(this).size()
                + "/" + Prefs.posterArt(this);
    }

    // ------------------------------------------------------------------ build

    private View build() {
        final Context c = this;
        shelfReady = false;
        shownIndex = -1;
        railFocusables.clear();
        barFocusables.clear();
        sourceFocusables.clear();
        entries.clear();

        root = new FrameLayout(c);
        root.setBackground(new ColorDrawable(Color.parseColor("#07080B")));

        // ---- the picture
        backdrop = new ImageView(c);
        backdrop.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setScaleX(1.24f);
        backdrop.setScaleY(1.24f);
        backdrop.setTranslationX(Ui.px(c, 150));
        backdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(backdrop);

        // Two scrims, not one. The side scrim protects the column of type whatever the
        // picture is doing; the foot scrim protects the rail. High contrast deepens both,
        // which is the setting doing real work rather than only recolouring panels.
        boolean hc = Prefs.highContrast(c);
        scrimSide = new View(c);
        scrimSide.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrimSide.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(hc ? 252 : 245, 7, 8, 11),
                        Color.argb(hc ? 240 : 224, 7, 8, 11),
                        Color.argb(hc ? 150 : 86, 7, 8, 11),
                        Color.argb(hc ? 60 : 26, 7, 8, 11)}));
        scrimSide.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(scrimSide);

        scrimFoot = new View(c);
        scrimFoot.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrimFoot.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{
                        Color.argb(hc ? 250 : 242, 7, 8, 11),
                        Color.argb(hc ? 180 : 140, 7, 8, 11),
                        Color.argb(0, 7, 8, 11)}));
        scrimFoot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(scrimFoot);

        // ---- the content column
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        col.setPadding(Ui.px(c, 76), Ui.px(c, 44), Ui.px(c, 60), Ui.px(c, 36));

        col.addView(topBar(c));

        View gap = new View(c);
        gap.setLayoutParams(new LinearLayout.LayoutParams(1, 0, 1f));
        col.addView(gap);

        col.addView(hero(c));
        col.addView(railBlock(c));
        if (Prefs.sourcesVisible(c)) col.addView(sourcesBlock(c));
        col.addView(footer(c));

        root.addView(col);

        setStatus("Loading your apps");
        loadRailAsync(c);
        return root;
    }

    // ------------------------------------------------------------------ top bar

    /**
     * A fixed-height band, not a weighted row. An earlier pass used a zero-width spacer with
     * layout_weight to push the pills right; inside a horizontal LinearLayout that spacer
     * measured to the full height of the column and shoved every band below it off the
     * bottom of the screen. On a panel that is always 1920x1080 there is nothing to gain
     * from letting these bands negotiate their own height, so they are told.
     */
    private View topBar(final Context c) {
        FrameLayout bar = new FrameLayout(c);
        bar.setClipChildren(false);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 56)));

        TextView word = new TextView(c);
        word.setText("LUMEN");
        word.setLetterSpacing(0.2f);
        word.setTextColor(Ui.alphaWhite(0.62f));
        word.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 19));
        word.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        word.setLayoutParams(wlp);
        bar.addView(word);

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.HORIZONTAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setClipChildren(false);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        rlp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        right.setLayoutParams(rlp);

        barRow = new LinearLayout(c);
        barRow.setOrientation(LinearLayout.HORIZONTAL);
        barRow.setGravity(Gravity.CENTER_VERTICAL);
        barRow.setClipChildren(false);
        barRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        right.addView(barRow);

        barRow.addView(barPill(c, "Apps", "All apps on this television", new Runnable() {
            public void run() { startActivity(new Intent(c, AllAppsActivity.class)); }
        }, false));
        barRow.addView(barPill(c, "Inputs", "Name the HDMI sockets", new Runnable() {
            public void run() { startActivity(new Intent(c, SourcesActivity.class)); }
        }, true));
        barRow.addView(barPill(c, "Settings", "Lumen settings", new Runnable() {
            public void run() { startActivity(new Intent(c, SettingsActivity.class)); }
        }, true));

        TextClock clock = new TextClock(c);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Ui.alphaWhite(0.94f));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 30));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = Ui.px(c, 26);
        clock.setLayoutParams(clp);
        right.addView(clock);

        bar.addView(right);
        return bar;
    }

    private View barPill(final Context c, String label, String spoken,
                         final Runnable go, boolean spaced) {
        final TextView p = new TextView(c);
        p.setText(label);
        p.setTextColor(Ui.alphaWhite(0.80f));
        p.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 22));
        p.setPadding(Ui.px(c, 26), Ui.px(c, 11), Ui.px(c, 26), Ui.px(c, 11));
        p.setFocusable(true);
        p.setContentDescription(label + ". " + spoken + ". Press OK to open.");
        p.setBackground(Ui.roundRect(c, Ui.withAlpha(Color.WHITE, 0.07f), 999f, 1f,
                Ui.alphaWhite(0.16f)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (spaced) lp.leftMargin = Ui.px(c, 12);
        p.setLayoutParams(lp);
        p.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                p.setBackground(Ui.roundRect(c,
                        Ui.withAlpha(Color.WHITE, has ? 0.18f : 0.07f), 999f,
                        has ? 2f : 1f, Ui.alphaWhite(has ? 0.92f : 0.16f)));
                p.setTextColor(Ui.alphaWhite(has ? 1f : 0.80f));
                p.setForeground(has
                        ? Ui.ring(c, 999f, Prefs.highContrast(c) ? 6f : 4f, Ui.alphaWhite(0.95f))
                        : Ui.ring(c, 999f, 0f, Color.TRANSPARENT));
            }
        });
        p.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (!launching) go.run(); }
        });
        p.setId(View.generateViewId());
        barFocusables.add(p);
        return p;
    }

    // ------------------------------------------------------------------ hero

    /**
     * The column of type. Its height is fixed, so moving along the rail changes the words
     * without shifting anything underneath them - a layout that jumps under a person
     * mid-press is the fastest way to make a remote feel broken.
     */
    private View hero(final Context c) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                Ui.px(c, 980), Ui.px(c, 424));
        hlp.bottomMargin = Ui.px(c, 26);
        h.setLayoutParams(hlp);
        h.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        LinearLayout line = new LinearLayout(c);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        heroChipText = new TextView(c);
        heroChipText.setTextColor(Ui.alphaWhite(1f));
        heroChipText.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 17));
        heroChipText.setPadding(Ui.px(c, 15), Ui.px(c, 7), Ui.px(c, 15), Ui.px(c, 7));
        heroChipText.setBackground(Ui.roundRect(c, Ui.withAlpha(Color.WHITE, 0.16f), 8f, 1f,
                Ui.alphaWhite(0.30f)));
        heroChip = heroChipText;
        line.addView(heroChipText);

        heroKicker = new TextView(c);
        heroKicker.setLetterSpacing(0.12f);
        heroKicker.setTextColor(Ui.alphaWhite(0.76f));
        heroKicker.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.leftMargin = Ui.px(c, 13);
        heroKicker.setLayoutParams(klp);
        line.addView(heroKicker);
        h.addView(line);

        heroTitle = new TextView(c);
        heroTitle.setTextColor(Ui.alphaWhite(1f));
        heroTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 76));
        heroTitle.setMaxLines(2);
        // "The One About Matthew Perry" is two lines at 76; "Bleach" is one. Let the type
        // shrink to fit its box instead of letting the box grow into the rail.
        try {
            heroTitle.setAutoSizeTextTypeUniformWithConfiguration(
                    Math.round(Ui.sp(c, 42)), Math.round(Ui.sp(c, 76)),
                    Math.round(Ui.sp(c, 2)), TypedValue.COMPLEX_UNIT_PX);
        } catch (Throwable ignored) { }
        heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroTitle.setShadowLayer(Ui.px(c, 26), 0, Ui.px(c, 5), Color.argb(170, 0, 0, 0));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.px(c, 16);
        heroTitle.setLayoutParams(tlp);
        h.addView(heroTitle);

        heroSub = new TextView(c);
        heroSub.setTextColor(Ui.alphaWhite(0.90f));
        heroSub.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 26));
        heroSub.setMaxLines(1);
        heroSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroSub.setShadowLayer(Ui.px(c, 16), 0, Ui.px(c, 3), Color.argb(150, 0, 0, 0));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.px(c, 14);
        heroSub.setLayoutParams(slp);
        h.addView(heroSub);

        progRow = null;
        LinearLayout pr = new LinearLayout(c);
        pr.setOrientation(LinearLayout.HORIZONTAL);
        pr.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams prlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        prlp.topMargin = Ui.px(c, 22);
        pr.setLayoutParams(prlp);

        FrameLayout track = new FrameLayout(c);
        track.setLayoutParams(new LinearLayout.LayoutParams(Ui.px(c, 420), Ui.px(c, 6)));
        track.setBackground(Ui.roundRect(c, Ui.withAlpha(Color.WHITE, 0.24f), 3f, 0f, 0));
        progFill = new View(c);
        progFill.setLayoutParams(new FrameLayout.LayoutParams(0, Ui.px(c, 6)));
        progFill.setBackground(Ui.roundRect(c, Color.WHITE, 3f, 0f, 0));
        track.addView(progFill);
        progTrack = track;
        pr.addView(track);

        heroStatus = new TextView(c);
        heroStatus.setTextColor(Ui.alphaWhite(0.84f));
        heroStatus.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 22));
        LinearLayout.LayoutParams hslp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hslp.leftMargin = Ui.px(c, 20);
        heroStatus.setLayoutParams(hslp);
        pr.addView(heroStatus);
        progRow = pr;
        h.addView(pr);

        // Not a button. OK already does this, and a focusable button here would mean two
        // places the cursor can be for one action.
        heroOk = new TextView(c);
        heroOk.setTextColor(Ui.alphaWhite(0.96f));
        heroOk.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 23));
        heroOk.setPadding(Ui.px(c, 22), Ui.px(c, 11), Ui.px(c, 24), Ui.px(c, 11));
        heroOk.setBackground(Ui.roundRect(c, Ui.withAlpha(Color.WHITE, 0.14f), 10f, 1f,
                Ui.alphaWhite(0.34f)));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.topMargin = Ui.px(c, 22);
        heroOk.setLayoutParams(olp);
        h.addView(heroOk);

        return h;
    }

    // ------------------------------------------------------------------ rail

    private View railBlock(final Context c) {
        railScroll = new HorizontalScrollView(c);
        railScroll.setHorizontalScrollBarEnabled(false);
        railScroll.setFocusable(false);
        railScroll.setClipChildren(false);
        railScroll.setClipToPadding(false);
        railScroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        railScroll.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // 99 for the card, 26 top and bottom so the 1.26x focus scale is not clipped.
        railScroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 151)));

        rail = new LinearLayout(c);
        rail.setOrientation(LinearLayout.HORIZONTAL);
        rail.setClipChildren(false);
        rail.setClipToPadding(false);
        rail.setPadding(0, Ui.px(c, 26), Ui.px(c, 120), Ui.px(c, 26));
        rail.setGravity(Gravity.CENTER_VERTICAL);
        railScroll.addView(rail);
        return railScroll;
    }

    private void loadRailAsync(final Context c) {
        final int gen = ++railGen;
        new Thread(new Runnable() {
            public void run() {
                final List<AppEntry> loaded = AppEntry.load(c);
                final List<WatchNext.Item> watch =
                        (Prefs.showRecents(c) && WatchNext.permitted(c))
                                ? WatchNext.latestPerApp(c, WATCH_ENTRIES)
                                : new ArrayList<WatchNext.Item>();
                ui.post(new Runnable() {
                    public void run() {
                        if (gen != railGen || rail == null) return;
                        apps = loaded;
                        buildEntries(watch);
                        paintRail(c);
                        shelfReady = true;
                        clearStatus();
                    }
                });
            }
        }, "lumen-rail").start();
    }

    private void refreshRail() {
        if (rail == null) return;
        loadRailAsync(this);
    }

    /**
     * The rail's order is an argument: what you were part-way through comes before what you
     * might start, and the apps keep the order you set in Settings. Inputs are NOT in here -
     * they are a row of their own below, because switching the television to HDMI 2 is a
     * different kind of act from opening an app, and mixing them makes the rail unreadable.
     */
    private void buildEntries(List<WatchNext.Item> watch) {
        entries.clear();
        Map<String, AppEntry> byPkg = new HashMap<String, AppEntry>();
        for (AppEntry a : apps) byPkg.put(a.pkg, a);

        for (WatchNext.Item it : watch) {
            AppEntry app = byPkg.get(it.pkg);
            if (app == null) continue;
            Entry e = new Entry();
            e.kind = KIND_WATCH;
            e.watch = it;
            e.app = app;
            e.title = it.title;
            e.kicker = "CARRY ON";
            e.sub = it.subtitle();
            e.status = it.status();
            e.progress = it.progress();
            e.tint = app.tint;
            e.artUri = it.artUri;
            e.ok = it.progress() > 0.01f ? "OK resumes it" : "OK starts it";
            entries.add(e);
        }

        for (AppEntry a : apps) {
            Entry e = new Entry();
            e.kind = KIND_APP;
            e.app = a;
            e.title = a.label;
            e.kicker = "APP";
            e.sub = "Opens where you left it";
            e.tint = a.tint;
            e.ok = "OK opens it";
            entries.add(e);
        }

        Entry all = new Entry();
        all.kind = KIND_ALL;
        all.title = "All apps";
        all.kicker = "EVERYTHING INSTALLED";
        all.sub = "Nothing was removed from this television";
        all.ok = "OK opens the full grid";
        entries.add(all);
    }

    private void paintRail(final Context c) {
        rail.removeAllViews();
        railFocusables.clear();

        for (int i = 0; i < entries.size(); i++) {
            rail.addView(card(c, entries.get(i), i, i > 0));
        }
        linkFocus();

        if (!railFocusables.isEmpty()) {
            View first = railFocusables.get(0);
            first.requestFocus();
            showEntry(0);
        }
    }

    private View card(final Context c, final Entry e, final int index, boolean spaced) {
        final FrameLayout card = new FrameLayout(c);
        final int w = 176, h = 99;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(c, w), Ui.px(c, h));
        if (spaced) lp.leftMargin = Ui.px(c, 18);
        card.setLayoutParams(lp);
        card.setFocusable(true);
        card.setClipToOutline(true);
        card.setBackground(Ui.roundRect(c, e.tint, 13f, 1f, Ui.alphaWhite(0.16f)));
        card.setAlpha(0.55f);

        String spoken;
        if (e.kind == KIND_WATCH) {
            spoken = e.title
                    + (e.watch.subtitleSpoken().length() > 0 ? ". " + e.watch.subtitleSpoken() : "")
                    + (e.status.length() > 0 ? ". " + e.status : "")
                    + ". On " + e.app.label + ". Press OK to carry on.";
        } else if (e.kind == KIND_APP) {
            spoken = e.title + ". App. Press OK to open.";
        } else {
            spoken = "All apps. Everything installed on this television. Press OK to open.";
        }
        card.setContentDescription(spoken);

        if (e.app != null && e.app.art != null) {
            final ImageView art = new ImageView(c);
            art.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            art.setScaleType(e.app.hasBanner
                    ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.CENTER_INSIDE);
            art.setImageDrawable(e.app.art);
            art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.addView(art);

            // The banner stands in only until the poster lands. Without this the four
            // resume cards wear the same Netflix and Disney+ banners as the app cards
            // further along the rail, and the row reads as eight duplicates.
            if (e.kind == KIND_WATCH && e.artUri != null && e.artUri.length() > 0) {
                ArtCache.load(c, e.artUri, Ui.px(c, w), new ArtCache.Ready() {
                    public void onArt(Bitmap b) {
                        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        art.setImageBitmap(b);
                    }
                });
            }
        } else if (e.kind == KIND_ALL) {
            TextView plus = new TextView(c);
            plus.setText("All apps");
            plus.setTextColor(Ui.alphaWhite(0.92f));
            plus.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 18));
            FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            plp.gravity = Gravity.CENTER;
            plus.setLayoutParams(plp);
            plus.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            card.setBackground(Ui.dashed(c, 13f));
            card.addView(plus);
        }

        if (e.progress > 0.01f) {
            View t = new View(c);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 5));
            tlp.gravity = Gravity.BOTTOM;
            t.setLayoutParams(tlp);
            t.setBackground(new ColorDrawable(Color.argb(140, 0, 0, 0)));
            card.addView(t);

            View f = new View(c);
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    Math.max(Ui.px(c, 4), Math.round(Ui.px(c, w) * e.progress)), Ui.px(c, 5));
            flp.gravity = Gravity.BOTTOM;
            f.setLayoutParams(flp);
            f.setBackground(new ColorDrawable(Color.WHITE));
            card.addView(f);
        }

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                int ms = Prefs.motionMs(c);
                card.animate().cancel();
                float sc = has ? 1.26f : 1f;
                if (ms == 0) {
                    card.setScaleX(sc); card.setScaleY(sc); card.setAlpha(has ? 1f : 0.55f);
                } else {
                    card.animate().scaleX(sc).scaleY(sc).alpha(has ? 1f : 0.55f)
                            .setDuration(ms).setInterpolator(Ui.EASE).start();
                }
                card.setTranslationZ(has ? Ui.px(c, 30) : 0f);
                card.setForeground(has
                        ? Ui.ring(c, 13f, Prefs.highContrast(c) ? 6f : 4f, Ui.alphaWhite(0.96f))
                        : Ui.ring(c, 13f, 0f, Color.TRANSPARENT));
                if (has) showEntry(index);
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { activate(e); }
        });

        card.setId(View.generateViewId());
        railFocusables.add(card);
        return card;
    }

    // ------------------------------------------------------------------ the hero follows focus

    private void showEntry(int index) {
        if (index < 0 || index >= entries.size()) return;
        if (index == shownIndex) return;
        shownIndex = index;
        final Entry e = entries.get(index);
        final Context c = this;

        heroChipText.setText(e.app != null ? e.app.label : "Lumen");
        heroChipText.setVisibility(e.kind == KIND_ALL ? View.GONE : View.VISIBLE);
        heroKicker.setText(e.kicker);
        heroTitle.setText(e.title);
        heroSub.setText(e.sub);
        heroOk.setText(e.ok);

        if (e.progress > 0.01f) {
            progRow.setVisibility(View.VISIBLE);
            heroStatus.setText(e.status);
            ViewGroup.LayoutParams flp = progFill.getLayoutParams();
            flp.width = Math.max(Ui.px(c, 4), Math.round(Ui.px(c, 420) * e.progress));
            progFill.setLayoutParams(flp);
        } else if (e.status.length() > 0) {
            progRow.setVisibility(View.VISIBLE);
            progTrack.setVisibility(View.GONE);
            heroStatus.setText(e.status);
        } else {
            progRow.setVisibility(View.INVISIBLE);
        }
        if (e.progress > 0.01f) progTrack.setVisibility(View.VISIBLE);

        setBackdrop(e);
    }

    /**
     * The picture. A real poster when the row carries one and Poster art is on; otherwise a
     * wash built from the app's own dominant colour, which is what v1 and v2 used as the
     * ground. Either way the scrims stay the same, so the type's contrast does not depend on
     * whether the download landed.
     */
    private void setBackdrop(final Entry e) {
        final Context c = this;
        int fixed = Prefs.bgTint(c);
        final int tint = fixed != 0 ? fixed : e.tint;

        Drawable wash = groundCache.get("t" + tint);
        if (wash == null) { wash = Ui.ground(c, tint); groundCache.put("t" + tint, wash); }
        backdrop.setImageDrawable(null);
        backdrop.setBackground(wash);

        if (e.artUri == null || e.artUri.length() == 0 || !Prefs.posterArt(c)) return;
        final int want = shownIndex;
        int w = getResources().getDisplayMetrics().widthPixels;
        ArtCache.loadBackdrop(c, e.artUri, Math.max(960, w), new ArtCache.Ready() {
            public void onArt(Bitmap b) {
                if (shownIndex != want || backdrop == null) return;   // moved on already
                backdrop.setImageBitmap(b);
            }
        });
    }

    private void activate(Entry e) {
        if (launching) return;
        if (e.kind == KIND_WATCH) { resume(e.watch, e.app); return; }
        if (e.kind == KIND_ALL) { startActivity(new Intent(this, AllAppsActivity.class)); return; }
        launch(e.app);
    }

    // ------------------------------------------------------------------ sources

    private static class Source {
        final String port; final String inputId;
        Source(String port, String id) { this.port = port; inputId = id; }
    }

    private View sourcesBlock(final Context c) {
        sourcesRow = new LinearLayout(c);
        sourcesRow.setOrientation(LinearLayout.HORIZONTAL);
        sourcesRow.setGravity(Gravity.CENTER_VERTICAL);
        sourcesRow.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 56));
        lp.topMargin = Ui.px(c, 10);
        sourcesRow.setLayoutParams(lp);

        TextView lab = new TextView(c);
        lab.setText("INPUTS");
        lab.setLetterSpacing(0.16f);
        lab.setTextColor(Ui.alphaWhite(0.72f));
        lab.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 18));
        lab.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.rightMargin = Ui.px(c, 22);
        lab.setLayoutParams(llp);
        sourcesRow.addView(lab);

        boolean first = true;
        for (Source s : sources()) { sourcesRow.addView(pill(c, s, !first)); first = false; }
        return sourcesRow;
    }

    private List<Source> sources() {
        List<Source> hdmi = new ArrayList<Source>();
        List<Source> tuner = new ArrayList<Source>();
        final Map<String, Integer> ports = new HashMap<String, Integer>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                for (TvInputInfo info : tim.getTvInputList()) {
                    int type = info.getType();
                    if (type == TvInputInfo.TYPE_HDMI) {
                        int port = portFromLabel(String.valueOf(info.loadLabel(this)));
                        if (port < 0) port = portFromLabel(info.getId());
                        if (port < 0) port = hdmi.size() + 1;
                        String label = "HDMI " + port;
                        if (!ports.containsKey(label)) {
                            ports.put(label, port);
                            hdmi.add(new Source(label, info.getId()));
                        }
                    } else if (type == TvInputInfo.TYPE_TUNER) {
                        if (tuner.isEmpty()) tuner.add(new Source("Antenna", info.getId()));
                    }
                }
            }
        } catch (Throwable ignored) { }
        Collections.sort(hdmi, new Comparator<Source>() {
            public int compare(Source a, Source b) {
                Integer pa = ports.get(a.port), pb = ports.get(b.port);
                return (pa == null ? 99 : pa) - (pb == null ? 99 : pb);
            }
        });
        List<Source> out = new ArrayList<Source>();
        out.addAll(hdmi);
        out.addAll(tuner);
        if (out.isEmpty()) out.add(new Source("Inputs", null));
        return out;
    }

    private int portFromLabel(String label) {
        if (label == null) return -1;
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            if (ch >= '1' && ch <= '9') return ch - '0';
        }
        return -1;
    }

    private View pill(final Context c, final Source s, boolean spaced) {
        final String given = Prefs.sourceName(c, s.port);
        final String shown = given.length() > 0 ? s.port + "  ·  " + given : s.port;

        final TextView p = new TextView(c);
        p.setText(shown);
        p.setTextColor(Ui.alphaWhite(0.86f));
        p.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        p.setPadding(Ui.px(c, 24), Ui.px(c, 10), Ui.px(c, 24), Ui.px(c, 10));
        p.setFocusable(true);
        p.setContentDescription(shown + ", switch input. Name it in Settings, Inputs.");
        p.setBackground(Ui.roundRect(c, Ui.withAlpha(Color.WHITE, 0.08f), 999f, 1f,
                Ui.alphaWhite(0.18f)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (spaced) lp.leftMargin = Ui.px(c, 12);
        p.setLayoutParams(lp);
        p.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                p.setBackground(Ui.roundRect(c,
                        Ui.withAlpha(Color.WHITE, has ? 0.20f : 0.08f), 999f,
                        has ? 2f : 1f, Ui.alphaWhite(has ? 0.92f : 0.18f)));
                p.setTextColor(Ui.alphaWhite(has ? 1f : 0.86f));
                p.setForeground(has
                        ? Ui.ring(c, 999f, Prefs.highContrast(c) ? 6f : 4f, Ui.alphaWhite(0.95f))
                        : Ui.ring(c, 999f, 0f, Color.TRANSPARENT));
            }
        });
        p.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { switchTo(s); }
        });
        p.setId(View.generateViewId());
        sourceFocusables.add(p);
        return p;
    }

    private void switchTo(Source s) {
        if (s.inputId != null) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(s.inputId));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Throwable ignored) { }
        }
        Intent f = getPackageManager().getLaunchIntentForPackage("com.tcl.suspension");
        if (f == null) f = getPackageManager().getLaunchIntentForPackage("com.tcl.tv");
        if (f != null) { f.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(f); }
        else setStatus("No input handler on this TV");
    }

    // ------------------------------------------------------------------ focus wiring

    /**
     * Explicit chains, not Android's guesswork. The rail is home; up reaches the bar, down
     * reaches the inputs, and neither direction ever lands nowhere.
     */
    private void linkFocus() {
        View firstRail = railFocusables.isEmpty() ? null : railFocusables.get(0);
        View firstBar = barFocusables.isEmpty() ? null : barFocusables.get(0);
        View firstSrc = sourceFocusables.isEmpty() ? null : sourceFocusables.get(0);

        for (int i = 0; i < railFocusables.size(); i++) {
            View v = railFocusables.get(i);
            if (firstBar != null) v.setNextFocusUpId(firstBar.getId());
            if (firstSrc != null) v.setNextFocusDownId(firstSrc.getId());
            else if (firstBar != null) v.setNextFocusDownId(firstBar.getId());
            if (i > 0) v.setNextFocusLeftId(railFocusables.get(i - 1).getId());
            else v.setNextFocusLeftId(v.getId());
            if (i < railFocusables.size() - 1)
                v.setNextFocusRightId(railFocusables.get(i + 1).getId());
            else v.setNextFocusRightId(v.getId());
        }
        for (int i = 0; i < barFocusables.size(); i++) {
            View v = barFocusables.get(i);
            if (firstRail != null) v.setNextFocusDownId(firstRail.getId());
            if (firstRail != null) v.setNextFocusUpId(v.getId());
            if (i > 0) v.setNextFocusLeftId(barFocusables.get(i - 1).getId());
            if (i < barFocusables.size() - 1)
                v.setNextFocusRightId(barFocusables.get(i + 1).getId());
            else v.setNextFocusRightId(v.getId());
        }
        for (int i = 0; i < sourceFocusables.size(); i++) {
            View v = sourceFocusables.get(i);
            if (firstRail != null) v.setNextFocusUpId(firstRail.getId());
            v.setNextFocusDownId(v.getId());
            if (i > 0) v.setNextFocusLeftId(sourceFocusables.get(i - 1).getId());
            else v.setNextFocusLeftId(v.getId());
            if (i < sourceFocusables.size() - 1)
                v.setNextFocusRightId(sourceFocusables.get(i + 1).getId());
            else v.setNextFocusRightId(v.getId());
        }
    }

    // ------------------------------------------------------------------ footer

    private View footer(final Context c) {
        FrameLayout bar = new FrameLayout(c);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 38));
        blp.topMargin = Ui.px(c, 14);
        bar.setLayoutParams(blp);

        hintLine = new LinearLayout(c);
        hintLine.setOrientation(LinearLayout.HORIZONTAL);
        hintLine.setGravity(Gravity.CENTER_VERTICAL);
        hintLine.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hintLine.addView(hint(c, "◀▶", "Along the rail", false));
        hintLine.addView(hint(c, "▲", "Apps, inputs, settings", true));
        if (Prefs.sourcesVisible(c)) hintLine.addView(hint(c, "▼", "Switch input", true));
        bar.addView(hintLine);

        statusLine = new TextView(c);
        statusLine.setTextColor(Ui.alphaWhite(0.94f));
        statusLine.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 22));
        statusLine.setVisibility(View.GONE);
        statusLine.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bar.addView(statusLine);
        return bar;
    }

    private View hint(Context c, String glyph, String text, boolean spaced) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (spaced) hlp.leftMargin = Ui.px(c, 34);
        h.setLayoutParams(hlp);

        TextView g = new TextView(c);
        g.setText(glyph);
        g.setTextColor(Ui.alphaWhite(0.92f));
        g.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 19));
        h.addView(g);

        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(Ui.alphaWhite(0.70f));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 19));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.px(c, 10);
        t.setLayoutParams(tlp);
        h.addView(t);
        return h;
    }

    private void setStatus(String s) {
        if (statusLine == null) return;
        statusLine.setText(s);
        statusLine.setVisibility(View.VISIBLE);
        if (hintLine != null) hintLine.setVisibility(View.INVISIBLE);
    }

    private void clearStatus() {
        if (statusLine == null) return;
        statusLine.setVisibility(View.GONE);
        if (hintLine != null) hintLine.setVisibility(View.VISIBLE);
    }

    private void announce(String s) {
        View r = getWindow() != null ? getWindow().getDecorView() : null;
        if (r != null) r.announceForAccessibility(s);
    }

    // ------------------------------------------------------------------ going somewhere

    private void dim() {
        if (root == null) return;
        int ms = Prefs.motionMs(this);
        if (ms == 0) { if (rail != null) rail.setAlpha(0.5f); return; }
        if (rail != null) rail.animate().alpha(0.5f).setDuration(ms)
                .setInterpolator(Ui.EASE).start();
    }

    private void resume(WatchNext.Item it, AppEntry app) {
        if (launching) return;
        Intent go = null;
        if (it.intentUri != null && it.intentUri.length() > 0) {
            try {
                go = Intent.parseUri(it.intentUri, Intent.URI_INTENT_SCHEME);
            } catch (Throwable t) {
                try { go = new Intent(Intent.ACTION_VIEW, Uri.parse(it.intentUri)); }
                catch (Throwable ignored) { }
            }
        }
        if (go != null) {
            go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (go.getPackage() == null && go.getComponent() == null) go.setPackage(it.pkg);
        }

        launching = true;
        dim();
        setStatus("Opening " + it.title);
        announce("Opening " + it.title);
        Recents.record(this, it.pkg);

        final Intent first = go;
        final AppEntry fallbackApp = app;
        final String pkg = it.pkg;
        ui.postDelayed(new Runnable() {
            public void run() {
                if (first != null) {
                    try { startActivity(first); return; } catch (Throwable ignored) { }
                }
                Intent plain = fallbackApp != null ? fallbackApp.launchIntent(HomeActivity.this) : null;
                if (plain == null) {
                    plain = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
                    if (plain == null) plain = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (plain != null) plain.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                if (plain != null) { try { startActivity(plain); return; } catch (Throwable ignored) { } }
                launching = false;
                setStatus("Cannot open that");
            }
        }, 90);
    }

    private void launch(final AppEntry app) {
        if (launching || app == null) return;
        Intent i = app.launchIntent(this);
        if (i == null) {
            setStatus("Cannot open " + app.label);
            announce("Cannot open " + app.label);
            ui.postDelayed(new Runnable() { public void run() { clearStatus(); } }, 2500);
            return;
        }
        launching = true;
        dim();
        setStatus("Opening " + app.label);
        announce("Opening " + app.label);
        Recents.record(this, app.pkg);

        final Intent go = i;
        ui.postDelayed(new Runnable() {
            public void run() {
                try { startActivity(go); }
                catch (Throwable t) { launching = false; setStatus("Cannot open " + app.label); }
            }
        }, 90);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (rail != null) { rail.animate().cancel(); rail.setAlpha(1f); }
    }

    @Override
    public void onBackPressed() { /* a home screen has nowhere to go back to */ }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        launching = false;
        clearStatus();
        if (rail != null) rail.setAlpha(1f);
        if (!railFocusables.isEmpty()) railFocusables.get(0).requestFocus();
    }
}
