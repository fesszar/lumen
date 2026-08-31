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
    private TextView caption, hint;
    private LinearLayout row;
    private LinearLayout sourcesBox;
    private View settingsButton;
    private final Map<String, Drawable> groundCache = new HashMap<String, Drawable>();
    private final List<View> shelfFocusables = new ArrayList<View>();
    private List<AppEntry> apps = new ArrayList<AppEntry>();
    private String builtWith = "";

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        String now = signature();
        if (!now.equals(builtWith)) {
            groundCache.clear();
            setContentView(build());
            builtWith = now;
        }
    }

    /** Anything here changing means the home screen has to be rebuilt. */
    private String signature() {
        return Prefs.bgIndex(this) + "/" + Prefs.glassIndex(this) + "/" + Prefs.tileIndex(this)
                + "/" + Prefs.sourcesVisible(this) + "/" + Prefs.hidden(this).size()
                + "/" + Prefs.alwaysShowNames(this) + "/" + Prefs.reduceMotion(this)
                + "/" + Prefs.highContrast(this);
    }

    // ------------------------------------------------------------------ build

    private View build() {
        final Context c = this;
        shelfFocusables.clear();

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

        View captionView = captionBlock(c);

        content.addView(topBar(c));

        View spacerTop = new View(c);
        spacerTop.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        content.addView(spacerTop);

        content.addView(shelf(c));
        content.addView(captionView);

        View gap = new View(c);
        gap.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 26)));
        content.addView(gap);

        if (Prefs.sourcesVisible(c)) content.addView(sourcesRow(c));
        else sourcesBox = null;

        View spacerBottom = new View(c);
        spacerBottom.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        content.addView(spacerBottom);

        linkFocus();

        row.post(new Runnable() {
            public void run() {
                if (!shelfFocusables.isEmpty()) shelfFocusables.get(0).requestFocus();
            }
        });
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
                        has ? 4f : 1f, Ui.alphaWhite(has ? 0.95f : 0.24f)));
                if (has) say("Launcher settings", "Apps on Home, background, accessibility");
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

    private View shelf(final Context c) {
        final FrameLayout shelf = new FrameLayout(c);
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

        // The gutter is what stops the first and last tiles being cropped by the scroll.
        final int gutter = Ui.px(c, 30);
        ShelfScrollView hsv = new ShelfScrollView(c);
        hsv.setGutter(gutter);
        hsv.setHorizontalScrollBarEnabled(false);
        // Otherwise the scroll container itself is a focusable, unlabelled stop.
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
        // Left and right padding give the scaled focus ring room at both ends of the row.
        row.setPadding(gutter, vpad, gutter, vpad);
        hsv.addView(row);
        shelf.addView(hsv);

        populate(c);
        return shelf;
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
        // Full white plus a shadow puts it at 5.2:1.
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
        apps = AppEntry.load(c);

        for (int i = 0; i < apps.size(); i++) {
            final AppEntry app = apps.get(i);
            final Tile t = new Tile(c, app, Tile.RADIUS);
            t.setLayoutParams(new LinearLayout.LayoutParams(
                    Ui.px(c, Prefs.tileWidth(c)), Ui.px(c, Prefs.tileHeight(c))));

            t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    t.applyFocus(has);
                    if (has) { say(app.label, "Open"); setGround(app); }
                }
            });
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { launch(app); }
            });

            View cell = wrap(c, t, app.label);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = Ui.px(c, 24);
            cell.setLayoutParams(lp);
            row.addView(cell);
            shelfFocusables.add(t);
        }

        addAllAppsTile(c);
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
                if (has) say("All apps", apps.size() + " installed");
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, AllAppsActivity.class));
            }
        });

        View cell = wrap(c, t, "All apps");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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

        List<View> pills = new ArrayList<View>();
        if (sourcesBox != null) {
            for (int i = 1; i < sourcesBox.getChildCount(); i++) {
                View v = sourcesBox.getChildAt(i);
                if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
                pills.add(v);
            }
        }

        int upId = settingsButton != null ? settingsButton.getId() : View.NO_ID;
        int downId = pills.isEmpty() ? View.NO_ID : pills.get(0).getId();

        for (int i = 0; i < shelfFocusables.size(); i++) {
            View v = shelfFocusables.get(i);
            v.setNextFocusRightId(i < shelfFocusables.size() - 1
                    ? shelfFocusables.get(i + 1).getId() : v.getId());
            v.setNextFocusLeftId(i > 0 ? shelfFocusables.get(i - 1).getId() : v.getId());
            v.setNextFocusUpId(upId != View.NO_ID ? upId : v.getId());
            v.setNextFocusDownId(downId != View.NO_ID ? downId : v.getId());
        }

        if (settingsButton != null && !shelfFocusables.isEmpty()) {
            settingsButton.setNextFocusDownId(shelfFocusables.get(0).getId());
            settingsButton.setNextFocusUpId(settingsButton.getId());
            settingsButton.setNextFocusLeftId(settingsButton.getId());
            settingsButton.setNextFocusRightId(settingsButton.getId());
        }

        for (int i = 0; i < pills.size(); i++) {
            View v = pills.get(i);
            v.setNextFocusRightId(i < pills.size() - 1 ? pills.get(i + 1).getId() : v.getId());
            v.setNextFocusLeftId(i > 0 ? pills.get(i - 1).getId() : v.getId());
            v.setNextFocusUpId(shelfFocusables.isEmpty() ? v.getId() : shelfFocusables.get(0).getId());
            v.setNextFocusDownId(v.getId());
        }
    }

    private View captionBlock(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.px(c, 26), 0, 0);

        caption = new TextView(c);
        caption.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
        caption.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 34));
        caption.setSingleLine(true);
        caption.setShadowLayer(Ui.px(c, 20), 0f, Ui.px(c, 2), Color.argb(150, 0, 0, 0));
        // The tile already announces itself; a live region here would double every utterance.
        caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(caption);

        hint = new TextView(c);
        hint.setTextColor(Ui.alphaWhite(Ui.TEXT_SECONDARY));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        hint.setSingleLine(true);
        hint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(hint);

        return box;
    }

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
        final String label; final String inputId;
        Source(String l, String id) { label = l; inputId = id; }
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
                Integer pa = ports.get(a.label), pb = ports.get(b.label);
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

    private View pill(final Context c, final Source s) {
        final TextView p = new TextView(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.px(c, 14);
        p.setLayoutParams(lp);
        p.setText(s.label);
        p.setSingleLine(true);
        p.setContentDescription(s.label + ", switch input");
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
                if (has) say(s.label, "Switch input");
            }
        });
        p.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { switchTo(s); }
        });
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
        else Toast.makeText(this, "No input handler on this TV", Toast.LENGTH_SHORT).show();
    }

    private void say(String title, String sub) {
        if (caption != null) caption.setText(title);
        if (hint != null) hint.setText(sub);
    }

    private void setGround(AppEntry app) {
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

    private void launch(AppEntry app) {
        Intent i = app.launchIntent(this);
        if (i != null) startActivity(i);
        else Toast.makeText(this, "Cannot open " + app.label, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() { /* a home screen has nowhere to go back to */ }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!shelfFocusables.isEmpty()) shelfFocusables.get(0).requestFocus();
    }
}
