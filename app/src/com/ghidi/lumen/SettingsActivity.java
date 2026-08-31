package com.ghidi.lumen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Six settings, not sixty. Built to the canvas spec: 440px left column, glass rows on the right,
 * 4px white focus ring, one easing curve.
 */
public class SettingsActivity extends Activity {

    private LinearLayout rows;
    private int focus = 0;

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
        if (rows != null) refresh();
    }

    private View build() {
        final Context c = this;

        FrameLayout root = new FrameLayout(c);
        View ground = new View(c);
        ground.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ground.setBackground(Ui.ground(c, 0xFF4F8B68));
        root.addView(ground);

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(Ui.px(c, 96), Ui.px(c, 54), Ui.px(c, 96), Ui.px(c, 54));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content);

        // ---- left column ----
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                Ui.px(c, 440), ViewGroup.LayoutParams.MATCH_PARENT);
        llp.rightMargin = Ui.px(c, 60);
        left.setLayoutParams(llp);

        left.addView(kicker(c, "LAUNCHER"));
        left.addView(heading(c, "Settings"));
        left.addView(body(c, "Everything this launcher can change, on one screen. Anything "
                + "needing a submenu of a submenu belongs in the TV's own settings, which this "
                + "links to at the bottom."));

        View grow = new View(c);
        grow.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        left.addView(grow);
        left.addView(ownerCard(c));
        content.addView(left);

        // ---- right column ----
        ScrollView scroller = new ScrollView(c);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFocusable(false);
        scroller.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scroller.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        content.addView(scroller);

        rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        rows.setClipToPadding(false);
        scroller.addView(rows);

        for (int i = 0; i < TITLES.length; i++) rows.addView(row(c, i));
        linkRows();
        refresh();

        rows.post(new Runnable() {
            public void run() { if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus(); }
        });
        return root;
    }

    private TextView kicker(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setLetterSpacing(0.16f);
        t.setTextColor(Ui.alphaWhite(Ui.TEXT_TERTIARY));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        t.setPadding(0, 0, 0, Ui.px(c, 16));
        return t;
    }

    private TextView heading(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 44));
        t.setShadowLayer(Ui.px(c, 20), 0f, Ui.px(c, 2), Color.argb(90, 0, 0, 0));
        return t;
    }

    private TextView body(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(Ui.alphaWhite(Ui.TEXT_SECONDARY));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        t.setLineSpacing(Ui.px(c, 8), 1f);
        t.setPadding(0, Ui.px(c, 16), 0, 0);
        return t;
    }

    private View ownerCard(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.glass(c, Prefs.effectiveGlassAlpha(c), 26f, Prefs.highContrast(c)));
        int p = Ui.px(c, 28);
        card.setPadding(p, Ui.px(c, 26), p, Ui.px(c, 26));

        TextView h = new TextView(c);
        h.setText("Home screen owner");
        h.setTextColor(Color.WHITE);
        h.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        card.addView(h);

        TextView d = new TextView(c);
        d.setText("Lumen is active. The Google launcher is disabled, not removed — "
                + "one command brings it back.");
        d.setTextColor(Ui.alphaWhite(Ui.TEXT_SECONDARY));
        d.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        d.setLineSpacing(Ui.px(c, 6), 1f);
        d.setPadding(0, Ui.px(c, 10), 0, 0);
        card.addView(d);
        return card;
    }

    private static final String[] TITLES = {
            "Apps on Home", "Background", "Glass strength", "Tile size", "Sources strip",
            "App names", "Reduce motion", "High contrast", "Open the TV settings"
    };
    private static final String[] HELP = {
            "Choose what appears on the shelf. Everything else stays in All apps.",
            "Adaptive follows the focused app. The rest are fixed.",
            "Opacity of every panel. Lower is more transparent, not more blurred.",
            "Larger tiles, fewer per shelf. Sized for your viewing distance.",
            "HDMI ports and the tuner, under the app shelf.",
            "Show every app's name under its tile, or only the focused one.",
            "Removes the focus animation. Also follows the TV's own animation scale.",
            "Denser panels and a thicker focus ring, for maximum legibility.",
            "Picture, sound, network — everything this launcher does not own."
    };
    private static final int LAST = TITLES.length - 1;

    private View row(final Context c, final int i) {
        final LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        int px = Ui.px(c, 32), py = Ui.px(c, 22);
        r.setPadding(px, py, px, py);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(c, 14);
        if (i == LAST) lp.topMargin = Ui.px(c, 18);
        r.setLayoutParams(lp);

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(c);
        title.setText(TITLES[i]);
        title.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 26));
        text.addView(title);

        TextView help = new TextView(c);
        help.setText(HELP[i]);
        // Measured 4.41:1 at 86% white against the focused row's lighter plate.
        // Full white plus a shadow clears the 4.5 AA floor.
        help.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
        help.setShadowLayer(Ui.px(c, 8), 0f, Ui.px(c, 1), Color.argb(150, 0, 0, 0));
        help.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        help.setPadding(0, Ui.px(c, 6), 0, 0);
        text.addView(help);
        r.addView(text);

        TextView value = new TextView(c);
        value.setTextColor(Ui.alphaWhite(Ui.TEXT_SECONDARY));
        value.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 22));
        value.setSingleLine(true);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = Ui.px(c, 40);
        value.setLayoutParams(vlp);
        r.addView(value);
        r.setTag(value);
        r.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        help.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        value.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        paint(r, false);

        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                paint((LinearLayout) v, has);
                if (has) focus = i;
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { activate(i); }
        });
        return r;
    }

    private void paint(LinearLayout r, boolean focused) {
        Context c = this;
        r.setBackground(Ui.roundRect(c, Ui.alphaWhite(focused ? 0.26f : 0.10f), 22f,
                1f, Ui.alphaWhite(focused ? 0.55f : 0.20f)));
        r.setForeground(focused
                ? Ui.ring(c, 22f, Prefs.highContrast(c) ? 6f : 4f, Ui.alphaWhite(0.95f))
                : Ui.ring(c, 22f, 0f, Color.TRANSPARENT));
        int ms = Prefs.motionMs(c);
        r.animate().cancel();
        if (ms == 0) {
            r.setScaleX(focused ? 1.012f : 1f);
            r.setScaleY(focused ? 1.012f : 1f);
        } else {
            r.animate().scaleX(focused ? 1.012f : 1f).scaleY(focused ? 1.012f : 1f)
                    .setDuration(ms).setInterpolator(Ui.EASE).start();
        }
        r.setTranslationZ(focused ? Ui.px(c, 20) : 0f);
        TextView value = (TextView) r.getTag();
        if (value != null) value.setTextColor(Ui.alphaWhite(focused ? 1f : Ui.TEXT_SECONDARY));
    }

    private void linkRows() {
        for (int i = 0; i < rows.getChildCount(); i++) {
            View v = rows.getChildAt(i);
            if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        }
        for (int i = 0; i < rows.getChildCount(); i++) {
            View v = rows.getChildAt(i);
            v.setNextFocusDownId(i < rows.getChildCount() - 1
                    ? rows.getChildAt(i + 1).getId() : v.getId());
            v.setNextFocusUpId(i > 0 ? rows.getChildAt(i - 1).getId() : v.getId());
            v.setNextFocusLeftId(v.getId());
            v.setNextFocusRightId(v.getId());
        }
    }

    private void refresh() {
        int total = AppEntry.loadAll(this).size();
        setValue(0, Prefs.shownCount(this, total) + " of " + total);
        setValue(1, Prefs.bgName(this));
        setValue(2, Prefs.glassPct(this) + "%");
        setValue(3, Prefs.tileName(this) + " — " + Prefs.tileWidth(this) + "px");
        setValue(4, Prefs.sourcesVisible(this) ? "Visible" : "Hidden");
        setValue(5, Prefs.alwaysShowNames(this) ? "Always" : "On focus");
        setValue(6, Prefs.motionLabel(this));
        setValue(7, Prefs.highContrast(this) ? "On" : "Off");
        setValue(8, "Open");
    }

    private void setValue(int i, String s) {
        if (i >= rows.getChildCount()) return;
        View rowView = rows.getChildAt(i);
        TextView v = (TextView) rowView.getTag();
        if (v != null) v.setText(s);
        rowView.setContentDescription(TITLES[i] + ", " + s + ". " + HELP[i]);
    }

    private void activate(int i) {
        switch (i) {
            case 0: startActivity(new Intent(this, AppsOnHomeActivity.class)); return;
            case 1: Prefs.cycleBg(this); break;
            case 2: Prefs.cycleGlass(this); break;
            case 3: Prefs.cycleTile(this); break;
            case 4: Prefs.toggleSources(this); break;
            case 5: Prefs.toggleNames(this); break;
            case 6: Prefs.toggleReduce(this); break;
            case 7: Prefs.toggleContrast(this); break;
            case 8:
                Intent s = getPackageManager().getLaunchIntentForPackage("com.android.tv.settings");
                if (s == null) s = new Intent(android.provider.Settings.ACTION_SETTINGS);
                s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(s); }
                catch (Throwable t) { Toast.makeText(this, "No settings app", Toast.LENGTH_SHORT).show(); }
                return;
        }
        refresh();
    }
}
