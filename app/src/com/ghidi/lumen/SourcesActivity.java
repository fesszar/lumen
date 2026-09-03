package com.ghidi.lumen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The inputs, as cards, with names you can change.
 *
 * Renaming used to live only on a long press of a pill on the home screen. That is both
 * undiscoverable and unreliable - a television remote's OK button does not produce a
 * dependable long press. So it gets a screen, reachable from Settings, where the control
 * is visible and OK does the obvious thing.
 */
public class SourcesActivity extends Activity {

    private static class Port {
        final String port; final String inputId;
        Port(String p, String id) { port = p; inputId = id; }
    }

    private final List<View> cards = new ArrayList<View>();
    private LinearLayout rowBox;
    private TextView headline, sub;
    private List<Port> ports = new ArrayList<Port>();
    private int cursor = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        ports = discover();
        setContentView(build());
    }

    private List<Port> discover() {
        List<Port> hdmi = new ArrayList<Port>();
        List<Port> tuner = new ArrayList<Port>();
        final Map<String, Integer> num = new HashMap<String, Integer>();
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                for (TvInputInfo info : tim.getTvInputList()) {
                    if (info.getType() == TvInputInfo.TYPE_HDMI) {
                        int p = portFrom(String.valueOf(info.loadLabel(this)));
                        if (p < 0) p = portFrom(info.getId());
                        if (p < 0) p = hdmi.size() + 1;
                        String label = "HDMI " + p;
                        if (!num.containsKey(label)) { num.put(label, p); hdmi.add(new Port(label, info.getId())); }
                    } else if (info.getType() == TvInputInfo.TYPE_TUNER) {
                        if (tuner.isEmpty()) tuner.add(new Port("Antenna", info.getId()));
                    }
                }
            }
        } catch (Throwable ignored) { }
        Collections.sort(hdmi, new Comparator<Port>() {
            public int compare(Port a, Port b) {
                Integer x = num.get(a.port), y = num.get(b.port);
                return (x == null ? 99 : x) - (y == null ? 99 : y);
            }
        });
        List<Port> out = new ArrayList<Port>();
        out.addAll(hdmi); out.addAll(tuner);
        return out;
    }

    private int portFrom(String s) {
        if (s == null) return -1;
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c >= '1' && c <= '9') return c - '0'; }
        return -1;
    }

    private View build() {
        final Context c = this;
        FrameLayout root = new FrameLayout(c);
        View ground = new View(c);
        ground.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ground.setBackground(Ui.ground(c, 0xFF4F8B68));
        ground.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(ground);

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.px(c, 96), Ui.px(c, 54), Ui.px(c, 96), Ui.px(c, 54));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content);

        TextView kicker = new TextView(c);
        kicker.setText("SOURCES");
        kicker.setLetterSpacing(0.16f);
        kicker.setTextColor(Ui.alphaWhite(0.55f));
        kicker.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        content.addView(kicker);

        View spacer = new View(c);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        content.addView(spacer);

        headline = new TextView(c);
        headline.setTextColor(Color.WHITE);
        headline.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 44));
        headline.setSingleLine(true);
        headline.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(headline);

        sub = new TextView(c);
        sub.setTextColor(Ui.alphaWhite(0.82f));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 22));
        sub.setPadding(0, Ui.px(c, 10), 0, Ui.px(c, 36));
        content.addView(sub);

        rowBox = new LinearLayout(c);
        rowBox.setOrientation(LinearLayout.HORIZONTAL);
        rowBox.setClipChildren(false);
        rowBox.setClipToPadding(false);
        content.addView(rowBox);

        View spacer2 = new View(c);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        content.addView(spacer2);

        LinearLayout foot = new LinearLayout(c);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        foot.addView(hint(c, "◀▶", "Choose an input", false));
        foot.addView(hint(c, "OK", "Rename it", true));
        foot.addView(hint(c, "Back", "Home", true));
        content.addView(foot);

        draw(c);
        return root;
    }

    private View hint(Context c, String glyph, String text, boolean spaced) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (spaced) lp.leftMargin = Ui.px(c, 32);
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
        tlp.leftMargin = Ui.px(c, 9);
        t.setLayoutParams(tlp);
        h.addView(t);
        return h;
    }

    private void draw(final Context c) {
        rowBox.removeAllViews();
        cards.clear();
        for (int i = 0; i < ports.size(); i++) rowBox.addView(card(c, ports.get(i), i));
        for (View v : cards) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        for (int i = 0; i < cards.size(); i++) {
            View v = cards.get(i);
            v.setNextFocusRightId(i < cards.size() - 1 ? cards.get(i + 1).getId() : v.getId());
            v.setNextFocusLeftId(i > 0 ? cards.get(i - 1).getId() : v.getId());
            v.setNextFocusUpId(v.getId());
            v.setNextFocusDownId(v.getId());
        }
        updateHead();
        final int want = Math.min(cursor, cards.size() - 1);
        if (want >= 0) rowBox.post(new Runnable() {
            public void run() { cards.get(want).requestFocus(); }
        });
    }

    private void updateHead() {
        if (ports.isEmpty()) {
            headline.setText("No inputs found");
            sub.setText("This television did not report any HDMI ports or a tuner.");
            return;
        }
        Port p = ports.get(Math.min(cursor, ports.size() - 1));
        String given = Prefs.sourceName(this, p.port);
        headline.setText(given.length() > 0 ? given : p.port);
        sub.setText(given.length() > 0
                ? p.port + "  ·  press OK to change the name"
                : "Press OK to name what is plugged into " + p.port);
    }

    private View card(final Context c, final Port p, final int index) {
        final LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setFocusable(true);
        box.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(c, 320), Ui.px(c, 240));
        if (index > 0) lp.leftMargin = Ui.px(c, 24);
        box.setLayoutParams(lp);
        box.setPadding(Ui.px(c, 30), Ui.px(c, 28), Ui.px(c, 30), Ui.px(c, 26));
        box.setBackground(Ui.glass(c, Prefs.effectiveGlassAlpha(c), 26f, Prefs.highContrast(c)));
        box.setElevation(Ui.px(c, 4));

        final String given = Prefs.sourceName(c, p.port);

        TextView glyph = new TextView(c);
        glyph.setText(given.length() > 0 ? "▣" : "◌");
        glyph.setTextColor(Ui.alphaWhite(given.length() > 0 ? 0.94f : 0.55f));
        glyph.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 44));
        glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(glyph);

        View grow = new View(c);
        grow.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        box.addView(grow);

        TextView name = new TextView(c);
        name.setText(given.length() > 0 ? given : p.port);
        name.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 27));
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(name);

        TextView port = new TextView(c);
        port.setText(given.length() > 0 ? p.port : "Not named yet");
        port.setTextColor(Ui.alphaWhite(0.74f));
        port.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        port.setPadding(0, Ui.px(c, 6), 0, 0);
        port.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        box.addView(port);

        box.setContentDescription((given.length() > 0 ? given + " on " + p.port : p.port + ", not named")
                + ". Press OK to rename.");

        box.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                if (has) { cursor = index; updateHead(); }
                box.setForeground(has
                        ? Ui.ring(c, 26f, Prefs.highContrast(c) ? 6f : 4f, Ui.alphaWhite(0.95f))
                        : Ui.ring(c, 26f, 0f, Color.TRANSPARENT));
                box.setTranslationZ(has ? Ui.px(c, 30) : 0f);
                int ms = Prefs.motionMs(c);
                box.animate().cancel();
                if (ms == 0) { box.setScaleX(has ? 1.05f : 1f); box.setScaleY(has ? 1.05f : 1f); }
                else box.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f)
                        .setDuration(ms).setInterpolator(Ui.EASE).start();
            }
        });
        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { rename(p); }
        });

        cards.add(box);
        return box;
    }

    private void rename(final Port p) {
        final EditText input = new EditText(this);
        input.setText(Prefs.sourceName(this, p.port));
        input.setHint("Sky box, PlayStation, soundbar...");
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);

        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("What is plugged into " + p.port + "?")
                .setView(input)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        Prefs.setSourceName(SourcesActivity.this, p.port, input.getText().toString());
                        draw(SourcesActivity.this);
                    }
                })
                .setNeutralButton("Clear the name", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        Prefs.setSourceName(SourcesActivity.this, p.port, "");
                        draw(SourcesActivity.this);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Switching input is still the home screen's job; this screen only names things. */
    @SuppressWarnings("unused")
    private void switchTo(Port p) {
        if (p.inputId == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    TvContract.buildChannelUriForPassthroughInput(p.inputId));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable ignored) { }
    }
}
