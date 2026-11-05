package com.ghidi.lumen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings > Your remote.
 *
 * The flow asks you to press the button rather than pick its name from a list, because the
 * Prime Video key sends KEYCODE_BUTTON_4 and nobody should have to know that. Lumen listens,
 * catches whatever arrives, and shows it back as the button you just pressed.
 *
 * If nothing arrives it says so, rather than spinning: on some sets the branded keys are
 * swallowed by a vendor service before any app sees them, and that is a different problem
 * with a different answer.
 */
public class RemoteActivity extends Activity {

    private static final int STEP_LIST = 0;
    private static final int STEP_LISTEN = 1;
    private static final int STEP_PICK = 2;
    private static final int STEP_DONE = 3;

    private static final long LISTEN_GIVE_UP_MS = 12000;

    private int step = STEP_LIST;
    private int caughtCode = 0;
    private String caughtName = "";
    private String pickedLabel = "";

    private FrameLayout stage;
    private TextView heading, blurb, footerHint;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable giveUp;

    private static class Target {
        final String id, label;
        Target(String id, String label) { this.id = id; this.label = label; }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(shell());
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (step == STEP_LIST) render();   // coming back from the accessibility settings
    }

    private View shell() {
        Context c = this;
        FrameLayout root = new FrameLayout(c);
        root.setBackground(Ui.ground(c, Prefs.bgTint(c) != 0 ? Prefs.bgTint(c) : 0xFF4F8B68));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.px(c, 92), Ui.px(c, 56), Ui.px(c, 92), Ui.px(c, 44));
        col.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView crumb = new TextView(c);
        crumb.setText("SETTINGS  ›  YOUR REMOTE");
        crumb.setLetterSpacing(0.16f);
        crumb.setTextColor(Ui.alphaWhite(0.62f));
        crumb.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        col.addView(crumb);

        heading = new TextView(c);
        heading.setTextColor(Ui.alphaWhite(1f));
        heading.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 46));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.px(c, 12);
        heading.setLayoutParams(hlp);
        col.addView(heading);

        blurb = new TextView(c);
        blurb.setTextColor(Ui.alphaWhite(0.86f));
        blurb.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 22));
        blurb.setLineSpacing(Ui.px(c, 6), 1f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                Ui.px(c, 1240), ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.px(c, 12);
        blurb.setLayoutParams(blp);
        col.addView(blurb);

        stage = new FrameLayout(c);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = Ui.px(c, 32);
        stage.setLayoutParams(slp);
        col.addView(stage);

        footerHint = new TextView(c);
        footerHint.setTextColor(Ui.alphaWhite(0.66f));
        footerHint.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = Ui.px(c, 20);
        footerHint.setLayoutParams(flp);
        col.addView(footerHint);

        root.addView(col);
        return root;
    }

    // ------------------------------------------------------------------ steps

    private void render() {
        stage.removeAllViews();
        switch (step) {
            case STEP_LISTEN: renderListen(); break;
            case STEP_PICK:   renderPick();   break;
            case STEP_DONE:   renderDone();   break;
            default:          renderList();   break;
        }
    }

    private void renderList() {
        final Context c = this;
        heading.setText("The app buttons on your remote");
        blurb.setText("TCL wired the branded keys to their apps through a service that came "
                + "with advertising attached, and that service is switched off. Point the keys "
                + "wherever you like instead.");
        footerHint.setText("▲▼  Move    OK  Change a button    Back  Settings");

        ScrollView sv = new ScrollView(c);
        sv.setFocusable(false);
        sv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);

        if (!RemoteService.enabled(c)) list.addView(enableCard(c));

        List<RemoteMap.Binding> bound = RemoteMap.all(c);
        for (final RemoteMap.Binding b : bound) {
            list.addView(row(c, RemoteMap.keyName(b.code), "Opens " + b.label, "Change",
                    new Runnable() {
                        public void run() { RemoteMap.remove(c, b.code); beginListen(); }
                    }));
        }

        list.addView(row(c, bound.isEmpty() ? "Set up a button" : "Set up another button",
                bound.isEmpty()
                        ? "Press OK, then press the button on your remote you want to change."
                        : "Press OK, then press the next one you want to change.",
                "Start", new Runnable() { public void run() { beginListen(); } }));

        if (!bound.isEmpty()) {
            final RemoteMap.Binding last = bound.get(bound.size() - 1);
            list.addView(row(c, "Forget " + RemoteMap.keyName(last.code),
                    "The key goes back to doing nothing at all.", "Forget",
                    new Runnable() {
                        public void run() { RemoteMap.remove(c, last.code); render(); }
                    }));
        }

        stage.addView(sv);
        focusFirst(list);
    }

    /**
     * The permission, asked for once and named honestly. Lumen cannot turn this on for you -
     * the television will not let any app enable its own accessibility service, which is the
     * right rule - so this takes you to the switch.
     */
    private View enableCard(final Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setPadding(Ui.px(c, 28), Ui.px(c, 24), Ui.px(c, 28), Ui.px(c, 24));
        card.setBackground(Ui.roundRect(c, Color.parseColor("#33D4A55A"), 18f, 1f,
                Color.parseColor("#88E0C188")));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(c, 18);
        card.setLayoutParams(lp);

        TextView t = new TextView(c);
        t.setText("Switch on “Lumen remote keys” first");
        t.setTextColor(Ui.alphaWhite(1f));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 27));
        card.addView(t);

        TextView d = new TextView(c);
        d.setText("A button you press inside Netflix never reaches a launcher, so Lumen needs "
                + "the television’s permission to watch for keys while another app is on "
                + "screen. It only watches for the keys you set up here. It doesn’t read "
                + "what is on screen and it doesn’t send anything anywhere. Press OK and "
                + "the switch is under Accessibility, called Lumen remote keys.");
        d.setTextColor(Ui.alphaWhite(0.88f));
        d.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        d.setLineSpacing(Ui.px(c, 5), 1f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                Ui.px(c, 1180), ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Ui.px(c, 8);
        d.setLayoutParams(dlp);
        card.addView(d);

        card.setContentDescription("Switch on Lumen remote keys first. " + d.getText()
                + " Press OK to open the switch.");
        card.setOnFocusChangeListener(ringer(c, 18f, card));
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openAccessibilitySettings(); }
        });
        return card;
    }

    /**
     * Getting to the accessibility switch on this television.
     *
     * The documented intent, ACTION_ACCESSIBILITY_SETTINGS, resolves here to Google's
     * frameworkpackagestubs - a placeholder whose entire job is to show "You don't have an
     * app that can do this". Measured on the TCL: the real screen is TCL's own OEM-link
     * activity. So the candidates are tried in order and anything resolving to the stub is
     * skipped, because a stub that resolves is worse than no match at all.
     */
    private void openAccessibilitySettings() {
        String[] actions = {
                "android.settings.ACCESSIBILITY_TV_OEM_LINK",
                Settings.ACTION_ACCESSIBILITY_SETTINGS
        };
        for (String a : actions) {
            Intent i = new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            android.content.pm.ResolveInfo r = getPackageManager().resolveActivity(i, 0);
            if (r == null || r.activityInfo == null) continue;
            if (r.activityInfo.packageName.contains("frameworkpackagestubs")) continue;
            try { startActivity(i); return; } catch (Throwable ignored) { }
        }
        // Explicit, in case the action is not declared but the screen is still there.
        try {
            Intent i = new Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setClassName("com.android.tv.settings",
                    "com.android.tv.settings.oemlink.AccessibilitySettingsActivity");
            startActivity(i);
            return;
        } catch (Throwable ignored) { }
        // Last resort: the front door, and say where to go once inside.
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            toast("Go to System, then Accessibility, then Lumen remote keys");
        } catch (Throwable ignored) {
            toast("This television won’t open its accessibility settings from here");
        }
    }

    private void toast(String s) {
        try {
            android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) { }
    }

    private void beginListen() {
        step = STEP_LISTEN;
        caughtCode = 0;
        render();
        giveUp = new Runnable() {
            public void run() {
                if (step != STEP_LISTEN || caughtCode != 0) return;
                heading.setText("That button isn’t reaching Lumen");
                blurb.setText("Nothing arrived. On some sets the maker’s own software takes the "
                        + "branded keys before any app can see them. If that is what’s "
                        + "happening here, pointing the key somewhere else can’t help and "
                        + "it needs that software switched back on. Press Back to give up, or "
                        + "press a different button to try again.");
            }
        };
        ui.postDelayed(giveUp, LISTEN_GIVE_UP_MS);
    }

    private void renderListen() {
        Context c = this;
        heading.setText("Press the button you want to change");
        blurb.setText("Whatever key arrives next is the one you’re setting up, so you "
                + "never have to know what it’s called. The arrows, OK, Back, Home and "
                + "volume are left alone, because the television needs them.");
        footerHint.setText("Press a button on your remote    Back  Give up");

        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER);
        wrap.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View dot = new View(c);
        dot.setLayoutParams(new LinearLayout.LayoutParams(Ui.px(c, 104), Ui.px(c, 104)));
        dot.setBackground(Ui.roundRect(c, Color.parseColor("#387AC496"), 999f, 2f,
                Color.parseColor("#DDA8DCBD")));
        wrap.addView(dot);

        TextView t = new TextView(c);
        t.setText("Listening…");
        t.setTextColor(Ui.alphaWhite(0.94f));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 34));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.px(c, 28);
        t.setLayoutParams(tlp);
        wrap.addView(t);

        stage.addView(wrap);
        stage.setFocusableInTouchMode(true);
        stage.requestFocus();
    }

    private void renderPick() {
        final Context c = this;
        heading.setText("What should " + caughtName.toLowerCase() + " open?");
        blurb.setText("Caught it. That key sends " + KeyEvent.keyCodeToString(caughtCode)
                + ". Anything on the television can go here: an app, an HDMI socket, the "
                + "aerial, or Lumen’s own home screen.");
        footerHint.setText("◀▶▲▼  Move    OK  Use this one    Back  Give up");

        ScrollView sv = new ScrollView(c);
        sv.setFocusable(false);
        sv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        sv.addView(grid);

        List<Target> targets = targets();
        LinearLayout line = null;
        for (int i = 0; i < targets.size(); i++) {
            if (i % 4 == 0) {
                line = new LinearLayout(c);
                line.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                llp.bottomMargin = Ui.px(c, 16);
                line.setLayoutParams(llp);
                grid.addView(line);
            }
            line.addView(targetTile(c, targets.get(i), i % 4 > 0));
        }
        stage.addView(sv);
        focusFirst(grid);
    }

    private List<Target> targets() {
        List<Target> out = new ArrayList<Target>();
        for (AppEntry a : AppEntry.loadAll(this)) out.add(new Target("app:" + a.pkg, a.label));
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                for (TvInputInfo info : tim.getTvInputList()) {
                    if (info.getType() == TvInputInfo.TYPE_HDMI
                            || info.getType() == TvInputInfo.TYPE_TUNER) {
                        String lab = String.valueOf(info.loadLabel(this));
                        String given = Prefs.sourceName(this, lab);
                        out.add(new Target("input:" + info.getId(),
                                given.length() > 0 ? lab + " · " + given : lab));
                    }
                }
            }
        } catch (Throwable ignored) { }
        out.add(new Target("home", "Lumen home"));
        return out;
    }

    private View targetTile(final Context c, final Target t, boolean spaced) {
        final LinearLayout tile = new LinearLayout(c);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setPadding(Ui.px(c, 14), Ui.px(c, 18), Ui.px(c, 14), Ui.px(c, 18));
        tile.setBackground(Ui.roundRect(c, Ui.withAlpha(Color.WHITE, 0.07f), 16f, 1f,
                Ui.alphaWhite(0.15f)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (spaced) lp.leftMargin = Ui.px(c, 16);
        tile.setLayoutParams(lp);

        TextView n = new TextView(c);
        n.setText(t.label);
        n.setSingleLine(true);
        n.setEllipsize(android.text.TextUtils.TruncateAt.END);
        n.setGravity(Gravity.CENTER);
        n.setTextColor(Ui.alphaWhite(0.94f));
        n.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 22));
        tile.addView(n);

        tile.setContentDescription(t.label + ". Press OK and "
                + caughtName.toLowerCase() + " will open this.");
        tile.setOnFocusChangeListener(ringer(c, 16f, tile));
        tile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                RemoteMap.put(c, caughtCode, t.id, t.label);
                pickedLabel = t.label;
                step = STEP_DONE;
                render();
            }
        });
        return tile;
    }

    private void renderDone() {
        Context c = this;
        heading.setText("Done");
        blurb.setText(RemoteService.enabled(c)
                ? "It works everywhere now. Press it inside Netflix, or while you’re on "
                  + "HDMI 2, and the television still switches over."
                : "Saved. It will only work on Lumen’s own screen until you switch on "
                  + "Lumen remote keys under Accessibility. Press Back and the card at the top "
                  + "takes you there.");
        footerHint.setText("OK  Set up another button    Back  Done");

        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER);
        wrap.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView tick = new TextView(c);
        tick.setText("✓");
        tick.setTextColor(Color.parseColor("#A8DCBD"));
        tick.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 92));
        wrap.addView(tick);

        TextView t = new TextView(c);
        t.setText(caughtName + " opens " + pickedLabel);
        t.setTextColor(Ui.alphaWhite(1f));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 36));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.px(c, 20);
        t.setLayoutParams(tlp);
        wrap.addView(t);

        stage.addView(wrap);
        stage.setFocusableInTouchMode(true);
        stage.requestFocus();
    }

    // ------------------------------------------------------------------ plumbing

    private View.OnFocusChangeListener ringer(final Context c, final float radius,
                                              final View v) {
        return new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean has) {
                v.setForeground(has
                        ? Ui.ring(c, radius, Prefs.highContrast(c) ? 6f : 4f,
                                  Ui.alphaWhite(0.95f))
                        : Ui.ring(c, radius, 0f, Color.TRANSPARENT));
                v.setTranslationZ(has ? Ui.px(c, 20) : 0f);
            }
        };
    }

    private View row(final Context c, String title, String help, String value,
                     final Runnable go) {
        final LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setFocusable(true);
        r.setPadding(Ui.px(c, 26), Ui.px(c, 20), Ui.px(c, 26), Ui.px(c, 20));
        r.setBackground(Ui.roundRect(c, Ui.withAlpha(Color.WHITE, 0.055f), 18f, 1f,
                Ui.alphaWhite(0.13f)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(c, 12);
        r.setLayoutParams(lp);

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(Ui.alphaWhite(1f));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 26));
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(t);
        TextView val = new TextView(c);
        val.setText(value);
        val.setTextColor(Color.parseColor("#A8DCBD"));
        val.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 24));
        top.addView(val);
        r.addView(top);

        TextView h = new TextView(c);
        h.setText(help);
        h.setTextColor(Ui.alphaWhite(0.82f));
        h.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                Ui.px(c, 1180), ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = Ui.px(c, 5);
        h.setLayoutParams(hlp);
        r.addView(h);

        r.setContentDescription(title + ", " + value + ". " + help);
        r.setOnFocusChangeListener(ringer(c, 18f, r));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { go.run(); }
        });
        return r;
    }

    private void focusFirst(final ViewGroup g) {
        g.post(new Runnable() {
            public void run() {
                for (int i = 0; i < g.getChildCount(); i++) {
                    View v = g.getChildAt(i);
                    if (v.isFocusable()) { v.requestFocus(); return; }
                    if (v instanceof ViewGroup) {
                        ViewGroup vg = (ViewGroup) v;
                        for (int j = 0; j < vg.getChildCount(); j++) {
                            if (vg.getChildAt(j).isFocusable()) {
                                vg.getChildAt(j).requestFocus(); return;
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * The capture itself. While listening, the first key that is not one the television
     * needs becomes the mapping. Everything else on this screen behaves normally.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (step == STEP_LISTEN && event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == KeyEvent.KEYCODE_BACK) { step = STEP_LIST; render(); return true; }
            if (RemoteMap.reserved(code)) {
                heading.setText("The television needs that one");
                blurb.setText(RemoteMap.keyName(code) + " moves around, selects, or controls "
                        + "the set, so Lumen leaves it alone. Press one of the app buttons "
                        + "instead, or press Back to give up.");
                return true;
            }
            if (giveUp != null) ui.removeCallbacks(giveUp);
            caughtCode = code;
            caughtName = RemoteMap.keyName(code);
            step = STEP_PICK;
            render();
            return true;
        }
        if (step == STEP_DONE && event.getAction() == KeyEvent.ACTION_DOWN
                && (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
                    || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            beginListen();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (step == STEP_LIST) { super.onBackPressed(); return; }
        if (giveUp != null) ui.removeCallbacks(giveUp);
        step = STEP_LIST;
        render();
    }
}
