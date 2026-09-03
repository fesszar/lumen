package com.ghidi.lumen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything installed, in a grid.
 *
 * This screen used to be the odd one out: launching from here left no trace in Carry on,
 * gave no feedback at all, and reported failure through a Toast - the exact thing the home
 * screen stopped doing. Opening an app is opening an app, wherever you pressed OK.
 */
public class AllAppsActivity extends Activity {

    private static final int COLS = 8;

    private final List<View> tiles = new ArrayList<View>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView statusLine;
    private LinearLayout hintLine;
    private FrameLayout panel;
    private boolean launching = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        launching = false;
        clearStatus();
        if (panel != null) panel.setAlpha(1f);
    }

    private View build() {
        final Context c = this;
        tiles.clear();
        List<AppEntry> apps = AppEntry.loadAll(c);

        FrameLayout rootFrame = new FrameLayout(c);
        View ground = new View(c);
        ground.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ground.setBackground(Ui.ground(c, Color.parseColor("#6F61A1")));
        ground.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        rootFrame.addView(ground);

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.px(c, 96), Ui.px(c, 54), Ui.px(c, 96), Ui.px(c, 54));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        rootFrame.addView(content);

        TextView kicker = new TextView(c);
        kicker.setText("ALL APPS");
        kicker.setLetterSpacing(0.16f);
        kicker.setTextColor(Ui.alphaWhite(Ui.TEXT_TERTIARY));
        kicker.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        kicker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        content.addView(kicker);

        TextView title = new TextView(c);
        title.setText(apps.size() + " installed");
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 40));
        title.setPadding(0, Ui.px(c, 8), 0, Ui.px(c, 30));
        content.addView(title);

        panel = new FrameLayout(c);
        panel.setBackground(Ui.glass(c, Prefs.effectiveGlassAlpha(c), 40f, Prefs.highContrast(c)));
        int pp = Ui.px(c, 40);
        panel.setPadding(pp, pp, pp, pp);
        panel.setClipToPadding(false);
        // Same outline clip the shelf uses, so a scaled tile cannot draw past the rounded edge.
        final float panelRadius = Ui.px(c, 40);
        panel.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), panelRadius);
            }
        });
        panel.setClipToOutline(true);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        panel.setLayoutParams(plp);
        content.addView(panel);

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFocusable(false);
        sv.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        sv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        panel.addView(sv);

        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(COLS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        sv.addView(grid);

        int inner = Math.round(1920 - 2 * 96 - 2 * 40);
        int gap = 24;
        final int cellW = Math.round((inner - (COLS - 1) * gap) / (float) COLS);
        final int cellH = Math.round(cellW * 9f / 16f);

        for (int i = 0; i < apps.size(); i++) {
            final AppEntry app = apps.get(i);

            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setClipChildren(false);
            cell.setClipToPadding(false);

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = Ui.px(c, cellW);
            glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            glp.rightMargin = (i % COLS == COLS - 1) ? 0 : Ui.px(c, gap);
            glp.bottomMargin = Ui.px(c, gap);
            cell.setLayoutParams(glp);

            final Tile t = new Tile(c, app, 16f);
            t.setLayoutParams(new LinearLayout.LayoutParams(Ui.px(c, cellW), Ui.px(c, cellH)));

            final TextView name = new TextView(c);
            name.setText(app.label);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
            name.setShadowLayer(Ui.px(c, 8), 0f, Ui.px(c, 1), Color.argb(140, 0, 0, 0));
            name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 19));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = Ui.px(c, 10);
            name.setLayoutParams(nlp);

            t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) { t.applyFocus(has); }
            });
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { launch(app, t); }
            });

            cell.addView(t);
            cell.addView(name);
            grid.addView(cell);
            tiles.add(t);
        }

        content.addView(bottomBar(c));

        // Deferred: grabbing focus while the view is still being constructed is what caused
        // the first-launch crash on the home screen.
        grid.post(new Runnable() {
            public void run() { if (!tiles.isEmpty()) tiles.get(0).requestFocus(); }
        });
        return rootFrame;
    }

    private View bottomBar(Context c) {
        FrameLayout bar = new FrameLayout(c);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 36));
        blp.topMargin = Ui.px(c, 18);
        bar.setLayoutParams(blp);

        hintLine = new LinearLayout(c);
        hintLine.setOrientation(LinearLayout.HORIZONTAL);
        hintLine.setGravity(Gravity.CENTER_VERTICAL);
        hintLine.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        hintLine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        hintLine.addView(hint(c, "OK", "Open", false));
        hintLine.addView(hint(c, "Back", "Home", true));
        bar.addView(hintLine);

        statusLine = new TextView(c);
        statusLine.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        statusLine.setGravity(Gravity.CENTER_VERTICAL);
        statusLine.setSingleLine(true);
        statusLine.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
        statusLine.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        statusLine.setVisibility(View.GONE);
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

    /** Identical to the home screen's, because opening an app is opening an app. */
    private void launch(final AppEntry app, final Tile tile) {
        if (launching) return;
        Intent i = app.launchIntent(this);
        if (i == null) {
            setStatus("Cannot open " + app.label);
            getWindow().getDecorView().announceForAccessibility("Cannot open " + app.label);
            ui.postDelayed(new Runnable() { public void run() { clearStatus(); } }, 2500);
            return;
        }
        launching = true;
        if (tile != null) tile.applyPressed();
        if (panel != null) {
            int ms = Prefs.motionMs(this);
            panel.animate().cancel();
            if (ms == 0) panel.setAlpha(0.55f);
            else panel.animate().alpha(0.55f).setDuration(ms).setInterpolator(Ui.EASE).start();
        }
        setStatus("Opening " + app.label);
        getWindow().getDecorView().announceForAccessibility("Opening " + app.label);

        // The one thing this screen never did: an app opened from here now shows up in
        // Carry on, exactly as it would if it had been opened from the shelf.
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
        if (panel != null) { panel.animate().cancel(); panel.setAlpha(1f); }
    }
}
