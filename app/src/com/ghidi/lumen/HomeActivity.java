package com.ghidi.lumen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeActivity extends Activity {

    private View ground;
    private LinearLayout row;
    private LinearLayout sourcesBox;
    private LinearLayout recentsRow;
    private View recentsBlock;
    private View settingsButton;
    private TextView statusLine;
    private LinearLayout hintLine;
    private FrameLayout shelfPanel;

    private final Map<String, Drawable> groundCache = new HashMap<String, Drawable>();
    private final List<View> shelfFocusables = new ArrayList<View>();
    private final List<View> recentFocusables = new ArrayList<View>();
    private List<AppEntry> apps = new ArrayList<AppEntry>();
    private String builtWith = "";
    private boolean launching = false;
    // False from the moment the screen is built until the shelf has actually loaded. Without
    // it onResume - which runs immediately after build() - wipes the loading line before the
    // background load has had a chance to finish.
    private boolean shelfReady = false;
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

        // Re-checked inside the delay: the flag is set as soon as the notice appears, so a
        // second HomeActivity created while it is on screen must not queue another one.
        if (!Prefs.firstBootDone(this)) {
            ui.postDelayed(new Runnable() {
                public void run() {
                    if (Prefs.firstBootDone(HomeActivity.this)) return;
                    startActivity(new Intent(HomeActivity.this, FirstBootActivity.class));
                }
            }, 700);
        }
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
            refreshRecents();
        }
    }

    /** Anything here changing means the home screen has to be rebuilt. */
    private String signature() {
        return Prefs.bgIndex(this) + "/" + Prefs.glassIndex(this) + "/" + Prefs.tileIndex(this)
                + "/" + Prefs.sourcesVisible(this) + "/" + Prefs.hidden(this).size()
                + "/" + Prefs.alwaysShowNames(this) + "/" + Prefs.reduceMotion(this)
                + "/" + Prefs.highContrast(this) + "/" + Prefs.showRecents(this)
                + "/" + Prefs.textIndex(this) + "/" + Prefs.order(this).size();
    }

    // ------------------------------------------------------------------ build

    private View build() {
        final Context c = this;
        shelfReady = false;
        shelfFocusables.clear();
        recentFocusables.clear();

        FrameLayout rootFrame = new FrameLayout(c);

        ground = new View(c);
        ground.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ground.setBackground(Ui.ground(c, Color.parseColor("#5F7099")));
        ground.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        rootFrame.addView(ground);

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(Ui.px(c, 96), Ui.px(c, 54), Ui.px(c, 96), Ui.px(c, 54));
        content.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootFrame.addView(content);

        content.addView(topBar(c));

        View spacerTop = new View(c);
        spacerTop.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        content.addView(spacerTop);

        if (Prefs.showRecents(c)) {
            recentsBlock = recentsBlock(c);
            content.addView(recentsBlock);
        } else {
            recentsBlock = null;
            recentsRow = null;
        }

        content.addView(shelf(c));

        View gap = new View(c);
        gap.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 30)));
        content.addView(gap);

        if (Prefs.sourcesVisible(c)) content.addView(sourcesRow(c));
        else sourcesBox = null;

        View spacerBottom = new View(c);
        spacerBottom.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        content.addView(spacerBottom);

        content.addView(bottomBar(c));

        // Only now does the status line exist. Calling setStatus() from skeleton() - which
        // runs while the shelf is being built, several lines above this - silently did
        // nothing, because the view it writes into had not been created yet.
        setStatus("Loading your apps");

        // The shelf is drawn at its final size straight away and filled in a moment later,
        // so nothing moves under the user mid-press while banners load.
        loadShelfAsync(c);
        return rootFrame;
    }

    private View topBar(final Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 56)));

        TextView home = new TextView(c);
        home.setText("HOME");
        home.setLetterSpacing(0.16f);
        home.setTextColor(Ui.alphaWhite(Ui.TEXT_TERTIARY));
        home.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        bar.addView(home);

        View grow = new View(c);
        grow.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        bar.addView(grow);

        final TextView gear = new TextView(c);
        gear.setId(View.generateViewId());
        gear.setText("Settings");
        gear.setSingleLine(true);
        gear.setFocusable(true);
        gear.setContentDescription("Launcher settings");
        gear.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        gear.setPadding(Ui.px(c, 26), Ui.px(c, 12), Ui.px(c, 26), Ui.px(c, 12));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.rightMargin = Ui.px(c, 22);
        gear.setLayoutParams(glp);
        gear.setTextColor(Ui.alphaWhite(Ui.TEXT_SECONDARY));
        gear.setBackground(Ui.roundRect(c, Ui.alphaWhite(0.12f), 999f, 1f, Ui.alphaWhite(0.24f)));
        gear.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                gear.setTextColor(Ui.alphaWhite(has ? 1f : Ui.TEXT_SECONDARY));
                gear.setBackground(Ui.roundRect(c, Ui.alphaWhite(has ? 0.30f : 0.12f), 999f,
                        has ? (Prefs.highContrast(c) ? 6f : 4f) : 1f,
                        Ui.alphaWhite(has ? 0.95f : 0.24f)));
            }
        });
        gear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
            }
        });
        settingsButton = gear;
        bar.addView(gear);

        TextClock clock = new TextClock(c);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm a");
        clock.setTextColor(Ui.alphaWhite(Ui.TEXT_SECONDARY));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 26));
        bar.addView(clock);

        return bar;
    }

    // ------------------------------------------------------------------ carry on

    private View recentsBlock(final Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.bottomMargin = Ui.px(c, 30);
        box.setLayoutParams(blp);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);

        TextView lab = new TextView(c);
        lab.setText("CARRY ON");
        lab.setLetterSpacing(0.16f);
        lab.setTextColor(Ui.alphaWhite(Ui.TEXT_TERTIARY));
        lab.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        lab.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        head.addView(lab);

        TextView sub = new TextView(c);
        sub.setText("where you left off");
        sub.setTextColor(Ui.alphaWhite(0.46f));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 18));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.px(c, 14);
        slp.bottomMargin = Ui.px(c, 1);
        sub.setLayoutParams(slp);
        sub.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        head.addView(sub);
        box.addView(head);

        recentsRow = new LinearLayout(c);
        recentsRow.setOrientation(LinearLayout.HORIZONTAL);
        recentsRow.setClipChildren(false);
        recentsRow.setClipToPadding(false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.px(c, 14);
        recentsRow.setLayoutParams(rlp);
        box.addView(recentsRow);
        return box;
    }

    /**
     * Rebuilt on every resume, because the row's whole job is to reflect what just happened.
     * With nothing launched yet the block is removed rather than left as empty frames.
     */
    private void refreshRecents() {
        if (recentsRow == null || recentsBlock == null) return;
        final Context c = this;
        recentsRow.removeAllViews();
        recentFocusables.clear();

        Map<String, AppEntry> byPkg = new HashMap<String, AppEntry>();
        for (AppEntry a : apps) byPkg.put(a.pkg, a);

        int added = 0;
        for (Recents.Item item : Recents.all(c)) {
            if (added >= Recents.SHOWN) break;
            AppEntry app = byPkg.get(item.pkg);
            if (app == null) continue;                 // uninstalled since; skip silently
            recentsRow.addView(recentCard(c, app, Recents.when(item.at), added > 0));
            added++;
        }

        recentsBlock.setVisibility(added == 0 ? View.GONE : View.VISIBLE);
        linkFocus();
    }

    private View recentCard(final Context c, final AppEntry app, String when, boolean spaced) {
        final LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setFocusable(true);
        card.setClipToOutline(true);
        card.setContentDescription(app.label + ", opened " + when + ". Open");
        int pad = Ui.px(c, 20);
        card.setPadding(pad, pad, Ui.px(c, 26), pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                Ui.px(c, 372), ViewGroup.LayoutParams.WRAP_CONTENT);
        if (spaced) lp.leftMargin = Ui.px(c, 20);
        card.setLayoutParams(lp);
        card.setBackground(Ui.glass(c, Prefs.effectiveGlassAlpha(c), 22f, Prefs.highContrast(c)));
        card.setElevation(Ui.px(c, 4));

        FrameLayout art = new FrameLayout(c);
        art.setLayoutParams(new LinearLayout.LayoutParams(Ui.px(c, 116), Ui.px(c, 66)));
        art.setClipToOutline(true);
        art.setBackground(Ui.roundRect(c, app.tint, 11f, 1f, Ui.alphaWhite(0.18f)));
        if (app.art != null) {
            android.widget.ImageView iv = new android.widget.ImageView(c);
            iv.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setImageDrawable(app.art);
            iv.setScaleType(app.hasBanner ? android.widget.ImageView.ScaleType.CENTER_CROP
                                          : android.widget.ImageView.ScaleType.CENTER_INSIDE);
            if (!app.hasBanner) { int p2 = Ui.px(c, 12); iv.setPadding(p2, p2, p2, p2); }
            iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            art.addView(iv);
        }
        card.addView(art);

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.px(c, 20);
        text.setLayoutParams(tlp);

        TextView name = new TextView(c);
        name.setText(app.label);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 25));
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        text.addView(name);

        final TextView ago = new TextView(c);
        ago.setText(when);
        ago.setSingleLine(true);
        ago.setTextColor(Ui.alphaWhite(Ui.TEXT_TERTIARY));
        ago.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 19));
        ago.setPadding(0, Ui.px(c, 5), 0, 0);
        ago.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        text.addView(ago);
        card.addView(text);

        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                card.setForeground(has
                        ? Ui.ring(c, 22f, Prefs.highContrast(c) ? 6f : 4f, Ui.alphaWhite(0.95f))
                        : Ui.ring(c, 22f, 0f, Color.TRANSPARENT));
                ago.setTextColor(Ui.alphaWhite(has ? 0.90f : Ui.TEXT_TERTIARY));
                card.setTranslationZ(has ? Ui.px(c, 30) : 0f);
                int ms = Prefs.motionMs(c);
                card.animate().cancel();
                if (ms == 0) { card.setScaleX(has ? 1.035f : 1f); card.setScaleY(has ? 1.035f : 1f); }
                else card.animate().scaleX(has ? 1.035f : 1f).scaleY(has ? 1.035f : 1f)
                        .setDuration(ms).setInterpolator(Ui.EASE).start();
                if (has) setGround(app);
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { launch(app, null); }
        });

        card.setId(View.generateViewId());
        recentFocusables.add(card);
        return card;
    }

    // ------------------------------------------------------------------ shelf

    private View shelf(final Context c) {
        final FrameLayout shelf = new FrameLayout(c);
        shelfPanel = shelf;
        shelf.setBackground(Ui.glass(c, Prefs.effectiveGlassAlpha(c), 40f, Prefs.highContrast(c)));

        final float shelfRadius = Ui.px(c, 40);
        shelf.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), shelfRadius);
            }
        });
        shelf.setClipToOutline(true);
        shelf.setClipToPadding(false);
        int p = Ui.px(c, 30);
        shelf.setPadding(p, p, p, p);
        shelf.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final int gutter = Ui.px(c, 30);
        ShelfScrollView hsv = new ShelfScrollView(c);
        hsv.setGutter(gutter);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setFocusable(false);
        hsv.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        hsv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hsv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        int vpad = Ui.px(c, 12);
        row.setPadding(gutter, vpad, gutter, vpad);
        hsv.addView(row);
        shelf.addView(hsv);

        skeleton(c);
        return shelf;
    }

    /**
     * Placeholder tiles at the exact final size, drawn before PackageManager is asked for
     * anything. Loading banners for two dozen apps takes hundreds of milliseconds; without
     * this the shelf pops into existence and the layout jumps.
     */
    private void skeleton(Context c) {
        row.removeAllViews();
        for (int i = 0; i < 6; i++) {
            View v = new View(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Ui.px(c, Prefs.tileWidth(c)), Ui.px(c, Prefs.tileHeight(c)));
            if (i > 0) lp.leftMargin = Ui.px(c, 24);
            v.setLayoutParams(lp);
            v.setBackground(Ui.roundRect(c, Ui.alphaWhite(0.10f), Tile.RADIUS, 1f, Ui.alphaWhite(0.14f)));
            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(v);
        }
    }

    private void loadShelfAsync(final Context c) {
        new Thread(new Runnable() {
            public void run() {
                final List<AppEntry> loaded = AppEntry.load(c);
                ui.post(new Runnable() {
                    public void run() {
                        apps = loaded;
                        shelfReady = true;
                        populate(c);
                        refreshRecents();
                        linkFocus();
                        clearStatus();
                        if (!shelfFocusables.isEmpty()) shelfFocusables.get(0).requestFocus();
                    }
                });
            }
        }, "lumen-shelf").start();
    }

    /** Tile plus optional caption underneath, as one column in the row. */
    private View wrap(Context c, View tile, String label) {
        if (!Prefs.alwaysShowNames(c)) return tile;

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.addView(tile);

        TextView t = new TextView(c);
        t.setText(label);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        // Measured 4.41:1 against the glass panel at 86% white - below the 4.5 AA floor.
        // Full white plus a shadow puts it at 8.30:1.
        t.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
        t.setShadowLayer(Ui.px(c, 8), 0f, Ui.px(c, 1), Color.argb(140, 0, 0, 0));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                Ui.px(c, Prefs.tileWidth(c)), ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.px(c, 12);
        t.setLayoutParams(tlp);
        col.addView(t);
        return col;
    }

    private void populate(final Context c) {
        row.removeAllViews();
        shelfFocusables.clear();

        for (int i = 0; i < apps.size(); i++) {
            final AppEntry app = apps.get(i);
            final boolean missing = app.art == null && app.launchIntent(c) == null;
            final Tile t = new Tile(c, app, Tile.RADIUS, missing);
            t.setLayoutParams(new LinearLayout.LayoutParams(
                    Ui.px(c, Prefs.tileWidth(c)), Ui.px(c, Prefs.tileHeight(c))));

            t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    t.applyFocus(has);
                    if (has) setGround(app);
                }
            });
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (missing) { removeFromShelf(app); return; }
                    launch(app, t);
                }
            });

            // wrap() returns the tile ITSELF when names are set to "Focused only". Overwriting
            // its layout params with WRAP_CONTENT then lets the tile inflate to its banner's
            // intrinsic size - one app ends up filling the screen. Only the wrapper column may
            // be WRAP_CONTENT; a bare tile keeps its fixed size.
            View cell = wrap(c, t, app.label);
            LinearLayout.LayoutParams lp = (cell == t)
                    ? new LinearLayout.LayoutParams(Ui.px(c, Prefs.tileWidth(c)), Ui.px(c, Prefs.tileHeight(c)))
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = Ui.px(c, 24);
            cell.setLayoutParams(lp);
            row.addView(cell);
            shelfFocusables.add(t);
        }

        addAllAppsTile(c);
    }

    /** The only thing OK does on an outline tile: stop holding the gap open. */
    private void removeFromShelf(AppEntry app) {
        List<String> order = Prefs.order(this);
        order.remove(app.pkg);
        Prefs.setOrder(this, order);
        announce(app.label + " removed from the shelf");
        groundCache.clear();
        setContentView(build());
        builtWith = signature();
    }

    private void addAllAppsTile(final Context c) {
        final FrameLayout t = new FrameLayout(c);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.px(c, Prefs.tileWidth(c)), Ui.px(c, Prefs.tileHeight(c))));
        t.setFocusable(true);
        t.setClipToOutline(true);
        t.setBackground(Ui.glass(c, Prefs.effectiveGlassAlpha(c) + 0.04f, Tile.RADIUS, Prefs.highContrast(c)));
        t.setElevation(Ui.px(c, 4));
        t.setContentDescription("All apps, " + apps.size() + " installed");

        TextView label = new TextView(c);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.CENTER;
        label.setLayoutParams(flp);
        label.setText("All apps");
        label.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 26));
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        t.addView(label);

        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                int ms = Prefs.motionMs(c);
                t.setForeground(has ? Ui.ring(c, Tile.RADIUS, Prefs.highContrast(c) ? 6f : 4f,
                                              Ui.alphaWhite(0.95f))
                                    : Ui.ring(c, Tile.RADIUS, 0f, Color.TRANSPARENT));
                t.setTranslationZ(has ? Ui.px(c, 40) : 0f);
                t.animate().cancel();
                if (ms == 0) {
                    t.setScaleX(has ? Ui.FOCUS_SCALE : 1f);
                    t.setScaleY(has ? Ui.FOCUS_SCALE : 1f);
                } else {
                    t.animate().scaleX(has ? Ui.FOCUS_SCALE : 1f)
                               .scaleY(has ? Ui.FOCUS_SCALE : 1f)
                               .setDuration(ms).setInterpolator(Ui.EASE).start();
                }
                t.setElevation(Ui.px(c, has ? 30 : 4));
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, AllAppsActivity.class));
            }
        });

        View cell = wrap(c, t, "All apps");
        LinearLayout.LayoutParams lp = (cell == t)
                ? new LinearLayout.LayoutParams(Ui.px(c, Prefs.tileWidth(c)), Ui.px(c, Prefs.tileHeight(c)))
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.px(c, 24);
        cell.setLayoutParams(lp);
        row.addView(cell);
        shelfFocusables.add(t);
    }

    /**
     * Explicit focus chain. Geometric focus search gets unreliable once children are scaled
     * and z-translated inside a scrolling container - it can stall or ping-pong between two
     * tiles. Naming every neighbour by id makes it deterministic.
     */
    private void linkFocus() {
        for (View v : shelfFocusables) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        for (View v : recentFocusables) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());

        List<View> pills = new ArrayList<View>();
        if (sourcesBox != null) {
            for (int i = 1; i < sourcesBox.getChildCount(); i++) {
                View v = sourcesBox.getChildAt(i);
                if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
                pills.add(v);
            }
        }

        boolean hasRecents = !recentFocusables.isEmpty();
        int settingsId = settingsButton != null ? settingsButton.getId() : View.NO_ID;
        int shelfUpId = hasRecents ? recentFocusables.get(0).getId() : settingsId;
        int downFromShelf = pills.isEmpty() ? View.NO_ID : pills.get(0).getId();

        for (int i = 0; i < shelfFocusables.size(); i++) {
            View v = shelfFocusables.get(i);
            v.setNextFocusRightId(i < shelfFocusables.size() - 1
                    ? shelfFocusables.get(i + 1).getId() : v.getId());
            v.setNextFocusLeftId(i > 0 ? shelfFocusables.get(i - 1).getId() : v.getId());
            v.setNextFocusUpId(shelfUpId != View.NO_ID ? shelfUpId : v.getId());
            v.setNextFocusDownId(downFromShelf != View.NO_ID ? downFromShelf : v.getId());
        }

        for (int i = 0; i < recentFocusables.size(); i++) {
            View v = recentFocusables.get(i);
            v.setNextFocusRightId(i < recentFocusables.size() - 1
                    ? recentFocusables.get(i + 1).getId() : v.getId());
            v.setNextFocusLeftId(i > 0 ? recentFocusables.get(i - 1).getId() : v.getId());
            v.setNextFocusUpId(settingsId != View.NO_ID ? settingsId : v.getId());
            v.setNextFocusDownId(shelfFocusables.isEmpty() ? v.getId() : shelfFocusables.get(0).getId());
        }

        if (settingsButton != null) {
            int down = hasRecents ? recentFocusables.get(0).getId()
                    : (shelfFocusables.isEmpty() ? settingsId : shelfFocusables.get(0).getId());
            settingsButton.setNextFocusDownId(down);
            settingsButton.setNextFocusUpId(settingsId);
            settingsButton.setNextFocusLeftId(settingsId);
            settingsButton.setNextFocusRightId(settingsId);
        }

        for (int i = 0; i < pills.size(); i++) {
            View v = pills.get(i);
            v.setNextFocusRightId(i < pills.size() - 1 ? pills.get(i + 1).getId() : v.getId());
            v.setNextFocusLeftId(i > 0 ? pills.get(i - 1).getId() : v.getId());
            v.setNextFocusUpId(shelfFocusables.isEmpty() ? v.getId() : shelfFocusables.get(0).getId());
            v.setNextFocusDownId(v.getId());
        }
    }

    // ------------------------------------------------------------------ sources

    private View sourcesRow(final Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);

        TextView lab = new TextView(c);
        lab.setText("SOURCES");
        lab.setLetterSpacing(0.16f);
        lab.setTextColor(Ui.alphaWhite(Ui.TEXT_TERTIARY));
        lab.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.rightMargin = Ui.px(c, 24);
        lab.setLayoutParams(llp);
        lab.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(lab);

        sourcesBox = box;
        for (Source s : sources()) box.addView(pill(c, s));
        return box;
    }

    private static class Source {
        final String port; final String inputId;
        Source(String port, String id) { this.port = port; inputId = id; }
    }

    /** Real inputs only: HDMI ports in port order, then the tuner. */
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
                        // TvInputInfo.getHdmiDeviceInfo() and android.hardware.hdmi are @hide -
                        // not in the public SDK, so the port comes from the label, then the id.
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

    /**
     * "HDMI 2 - PlayStation 5". The port is the socket's name; the device is the user's.
     * Both are shown, because dropping the port number would lose the one fact that tells
     * you which physical socket you are about to switch to.
     */
    private View pill(final Context c, final Source s) {
        final String given = Prefs.sourceName(c, s.port);
        final String shown = given.length() > 0 ? s.port + "  ·  " + given : s.port;

        final TextView p = new TextView(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.px(c, 14);
        p.setLayoutParams(lp);
        p.setText(shown);
        p.setSingleLine(true);
        p.setContentDescription(given.length() > 0
                ? given + " on " + s.port + ", switch input"
                : s.port + ", switch input. Hold OK to name it");
        p.setTextColor(Ui.alphaWhite(Ui.TEXT_SECONDARY));
        p.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        p.setPadding(Ui.px(c, 28), Ui.px(c, 14), Ui.px(c, 28), Ui.px(c, 14));
        p.setBackground(Ui.roundRect(c, Ui.alphaWhite(0.12f), 999f, 1f, Ui.alphaWhite(0.24f)));
        p.setFocusable(true);

        p.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                p.setBackground(Ui.roundRect(c, Ui.alphaWhite(has ? 0.30f : 0.12f), 999f,
                        has ? (Prefs.highContrast(c) ? 6f : 4f) : 1f,
                        Ui.alphaWhite(has ? 0.95f : 0.24f)));
                p.setTextColor(Ui.alphaWhite(has ? 1f : Ui.TEXT_SECONDARY));
            }
        });
        p.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { switchTo(s); }
        });
        // Naming an input is a rare, deliberate act, so it lives on the long press rather
        // than costing a visible control on the home screen.
        p.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { renameSource(s); return true; }
        });
        return p;
    }

    private void renameSource(final Source s) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(Prefs.sourceName(this, s.port));
        input.setHint("Sky box, PlayStation, soundbar...");
        input.setSingleLine(true);

        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("What is plugged into " + s.port + "?")
                .setView(input)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        Prefs.setSourceName(HomeActivity.this, s.port, input.getText().toString());
                        groundCache.clear();
                        setContentView(build());
                        builtWith = signature();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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

    // ------------------------------------------------------------------ bottom bar

    /**
     * The hint line, permanently. Depth is what defeats people on a television - a study of
     * 30 found completion falling 100% / 55% / 21% across one, two and three levels. Naming
     * the three directions costs one line and removes the guessing. It is not a dismissible
     * first-run tour, because the person who needs it is rarely the person who set this up.
     */
    private View bottomBar(final Context c) {
        FrameLayout bar = new FrameLayout(c);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 36)));

        hintLine = new LinearLayout(c);
        hintLine.setOrientation(LinearLayout.HORIZONTAL);
        hintLine.setGravity(Gravity.CENTER_VERTICAL);
        hintLine.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hintLine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        hintLine.addView(hint(c, "▲", "Settings", false));
        if (Prefs.sourcesVisible(c)) hintLine.addView(hint(c, "▼", "Sources", true));
        hintLine.addView(hint(c, "▶", "More apps", true));
        bar.addView(hintLine);

        // The launch line sits in the same band, replacing the hints while an app opens.
        statusLine = new TextView(c);
        statusLine.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        statusLine.setGravity(Gravity.CENTER_VERTICAL);
        statusLine.setSingleLine(true);
        statusLine.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
        statusLine.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        statusLine.setVisibility(View.GONE);
        // Not a live region: the announcement is made explicitly, once, so the screen reader
        // does not read the same sentence over itself.
        statusLine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.addView(statusLine);

        return bar;
    }

    private View hint(Context c, String glyph, String text, boolean spaced) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (spaced) lp.leftMargin = Ui.px(c, 34);
        h.setLayoutParams(lp);

        TextView g = new TextView(c);
        g.setText(glyph);
        g.setTextColor(Ui.alphaWhite(0.92f));
        g.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 17));
        h.addView(g);

        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(Ui.alphaWhite(0.62f));
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
        View root = getWindow() != null ? getWindow().getDecorView() : null;
        if (root != null) root.announceForAccessibility(s);
    }

    // ------------------------------------------------------------------ launch

    private void setGround(AppEntry app) {
        if (ground == null) return;
        int fixed = Prefs.bgTint(this);
        if (fixed != 0) {
            Drawable d = groundCache.get("fixed");
            if (d == null) { d = Ui.ground(this, fixed); groundCache.put("fixed", d); }
            ground.setBackground(d);
            return;
        }
        Drawable d = groundCache.get(app.pkg);
        if (d == null) { d = Ui.ground(this, app.tint); groundCache.put(app.pkg, d); }
        ground.setBackground(d);
    }

    /**
     * Launch feedback. One to four seconds pass between the press and the app's own splash.
     * The old build put a Toast there, which sighted users miss and the screen reader reads
     * over itself. Instead: the tile presses in, the shelf dims, the line names the app, and
     * the announcement is made exactly once.
     */
    private void launch(final AppEntry app, final Tile tile) {
        if (launching) return;
        Intent i = app.launchIntent(this);
        if (i == null) {
            setStatus("Cannot open " + app.label);
            announce("Cannot open " + app.label);
            ui.postDelayed(new Runnable() { public void run() { clearStatus(); } }, 2500);
            return;
        }
        launching = true;

        if (tile != null) tile.applyPressed();
        if (shelfPanel != null) {
            int ms = Prefs.motionMs(this);
            shelfPanel.animate().cancel();
            if (ms == 0) shelfPanel.setAlpha(0.55f);
            else shelfPanel.animate().alpha(0.55f).setDuration(ms).setInterpolator(Ui.EASE).start();
        }
        setStatus("Opening " + app.label);
        announce("Opening " + app.label);

        Recents.record(this, app.pkg);

        final Intent go = i;
        ui.postDelayed(new Runnable() {
            public void run() {
                try { startActivity(go); }
                catch (Throwable t) {
                    launching = false;
                    setStatus("Cannot open " + app.label);
                }
            }
        }, 90);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (shelfPanel != null) { shelfPanel.animate().cancel(); shelfPanel.setAlpha(1f); }
    }

    @Override
    public void onBackPressed() { /* a home screen has nowhere to go back to */ }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        launching = false;
        clearStatus();
        if (shelfPanel != null) shelfPanel.setAlpha(1f);
        if (!shelfFocusables.isEmpty()) shelfFocusables.get(0).requestFocus();
    }
}
