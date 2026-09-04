package com.ghidi.lumen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings, grouped, with every option's values shown as a list.
 *
 * The old screen cycled a value on each OK press. That hides the option set: you cannot see
 * what you are choosing between until you have already pressed past it. Fewer presses is not
 * the goal either - a study of 30 found that minimising keystrokes cut presses by half,
 * changed task time not at all, and made satisfaction significantly worse.
 *
 * So: four named groups, left and right to choose a value, and the values visible while you
 * choose. Nine flat rows became something you can predict the shape of.
 */
public class SettingsActivity extends Activity {

    // ---- the model -------------------------------------------------------

    private static final int T_CHOICE = 0;   // pick one of several values
    private static final int T_ACTION = 1;   // do something

    private static class Opt {
        final int type;
        final String key, title, help;
        final String[] values;
        Opt(int type, String key, String title, String help, String[] values) {
            this.type = type; this.key = key; this.title = title; this.help = help; this.values = values;
        }
    }

    private static class Group {
        final String name;
        final List<Opt> opts = new ArrayList<Opt>();
        Group(String n) { name = n; }
        Group add(Opt o) { opts.add(o); return this; }
    }

    private List<Group> model() {
        List<Group> g = new ArrayList<Group>();

        Group home = new Group("What is on Home");
        home.add(new Opt(T_ACTION, "apps", "Apps on the shelf",
                "Choose which apps appear, and in what order. Everything else stays in All apps.",
                new String[]{ "Choose" }));
        home.add(new Opt(T_CHOICE, "recents", "Carry on",
                "What you were part-way through, above the shelf - one card each from your three "
                + "most recent apps, showing the title, the episode and how far in you got. "
                + "OK goes back to that exact point.",
                new String[]{ "On", "Off" }));
        home.add(new Opt(T_CHOICE, "art", "Poster art",
                "Artwork on the Carry on cards, downloaded from the app that put the row there. "
                + "This is the only time Lumen uses the network. Off means it never does, and "
                + "the cards show the app's own banner instead.",
                new String[]{ "On", "Off" }));
        home.add(new Opt(T_CHOICE, "sources", "Sources strip",
                "HDMI ports and the tuner, under the shelf.",
                new String[]{ "Visible", "Hidden" }));
        home.add(new Opt(T_ACTION, "names_src", "Name your inputs",
                "Call HDMI 2 \u201cPlayStation\u201d. The port number stays underneath, so you "
                + "always know which socket you are switching to.",
                new String[]{ "Open" }));
        home.add(new Opt(T_CHOICE, "names", "App names",
                "Names under every tile, or only under the one in focus.",
                new String[]{ "Always", "Focused only" }));
        g.add(home);

        Group look = new Group("How it looks");
        look.add(new Opt(T_CHOICE, "tile", "Tile size",
                "Larger tiles, fewer per shelf. Sized for how far you sit from the screen.",
                Prefs.TILE_NAMES));
        look.add(new Opt(T_CHOICE, "glass", "Glass strength",
                "How opaque the panels are. Lower is more transparent, not more blurred - "
                + "this television has no blur to give.",
                new String[]{ "8%", "15%", "22%", "30%" }));
        look.add(new Opt(T_CHOICE, "bg", "Background",
                "Adaptive takes its colour from the app in focus. The rest are fixed.",
                Prefs.BG_NAMES));
        g.add(look);

        Group a11y = new Group("Easier to see and use");
        a11y.add(new Opt(T_CHOICE, "contrast", "High contrast",
                "Darkens the panels and thickens the focus ring. Measured at 17.5:1 against "
                + "8.3:1 normally.",
                new String[]{ "Off", "On" }));
        a11y.add(new Opt(T_CHOICE, "reduce", "Reduce motion",
                "Removes the focus animation. Follows the TV's own animation scale when that "
                + "is set to zero.",
                new String[]{ "Off", "On" }));
        a11y.add(new Opt(T_CHOICE, "text", "Text size",
                "Applies to names, the Carry on row and this screen.",
                Prefs.TEXT_NAMES));
        g.add(a11y);

        Group self = new Group("This launcher");
        self.add(new Opt(T_ACTION, "notice", "What changed on this TV",
                "The plain-language notice shown the first time, and how to put the Google "
                + "home screen back.",
                new String[]{ "Show" }));
        self.add(new Opt(T_ACTION, "export", "Save your settings",
                "Writes a small file you can copy to another TV, or keep before a factory reset.",
                new String[]{ "Export" }));
        self.add(new Opt(T_ACTION, "import", "Load saved settings",
                "Reads that file back and applies everything in it.",
                new String[]{ "Import" }));
        self.add(new Opt(T_ACTION, "tv", "Open the TV settings",
                "Picture, sound, network - everything this launcher does not own.",
                new String[]{ "Open" }));
        g.add(self);

        return g;
    }

    // ---- state -----------------------------------------------------------

    private List<Group> groups;
    private int group = 0;
    private LinearLayout groupList, rowList;
    private TextView groupTitle, previewNote;
    private LinearLayout previewShelf;
    private final List<View> rowViews = new ArrayList<View>();
    private final List<View> groupViews = new ArrayList<View>();
    private int focusedRow = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        groups = model();
        setContentView(build());
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
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(Ui.px(c, 96), Ui.px(c, 54), Ui.px(c, 96), Ui.px(c, 54));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content);

        // ---------------- left: title, the four groups, live preview
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                Ui.px(c, 520), ViewGroup.LayoutParams.MATCH_PARENT);
        llp.rightMargin = Ui.px(c, 56);
        left.setLayoutParams(llp);

        left.addView(kicker(c, "LAUNCHER"));
        left.addView(heading(c, "Settings"));
        left.addView(body(c, "Every option shows its choices in a list. Nothing cycles on OK — "
                + "you should never have to press a button four times to see what the options were."));

        groupList = new LinearLayout(c);
        groupList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gllp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gllp.topMargin = Ui.px(c, 30);
        groupList.setLayoutParams(gllp);
        left.addView(groupList);

        View grow = new View(c);
        grow.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        left.addView(grow);
        left.addView(preview(c));
        content.addView(left);

        // ---------------- right: the selected group's options
        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        right.setClipChildren(false);
        right.setClipToPadding(false);

        groupTitle = new TextView(c);
        groupTitle.setTextColor(Color.WHITE);
        groupTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 26));
        groupTitle.setPadding(0, 0, 0, Ui.px(c, 16));
        groupTitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        right.addView(groupTitle);

        ScrollView scroller = new ScrollView(c);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setFocusable(false);
        scroller.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        scroller.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        right.addView(scroller);

        rowList = new LinearLayout(c);
        rowList.setOrientation(LinearLayout.VERTICAL);
        rowList.setClipChildren(false);
        rowList.setClipToPadding(false);
        scroller.addView(rowList);

        right.addView(footer(c));
        content.addView(right);

        buildGroups(c);
        buildRows(c);
        return root;
    }

    // ---- left column pieces ---------------------------------------------

    private TextView kicker(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setLetterSpacing(0.16f);
        t.setTextColor(Ui.alphaWhite(Ui.TEXT_TERTIARY));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        t.setPadding(0, 0, 0, Ui.px(c, 14));
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
        t.setPadding(0, Ui.px(c, 14), 0, 0);
        return t;
    }

    /**
     * The four groups, as real focusable rows.
     *
     * They used to be clickable only, which on a television means unreachable: the option
     * rows swallow left and right to change values, so nothing could ever move focus into
     * this list. Up from the first option now lands here, down and up walk the groups, and
     * right goes back into the options.
     */
    private void buildGroups(final Context c) {
        groupList.removeAllViews();
        groupViews.clear();
        for (int i = 0; i < groups.size(); i++) {
            final int idx = i;
            final boolean on = (i == group);
            final LinearLayout r = new LinearLayout(c);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setFocusable(true);
            r.setPadding(Ui.px(c, 22), Ui.px(c, 15), Ui.px(c, 22), Ui.px(c, 15));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.px(c, 9);
            r.setLayoutParams(lp);
            r.setContentDescription(groups.get(i).name + ", "
                    + groups.get(i).opts.size() + " options. Right to open them.");

            // A bar down the leading edge. The background tint alone could not answer "which
            // group is open" once focus moved into the options - the two states looked almost
            // the same from a sofa.
            final View accent = new View(c);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    Ui.px(c, 5), Ui.px(c, 30));
            alp.rightMargin = Ui.px(c, 16);
            accent.setLayoutParams(alp);
            r.addView(accent);

            final TextView t = new TextView(c);
            t.setText(groups.get(i).name);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 23));
            t.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            r.addView(t);

            final TextView n = new TextView(c);
            n.setText(String.valueOf(groups.get(i).opts.size()));
            n.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 19));
            n.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            r.addView(n);

            paintGroup(r, accent, t, n, on, false);
            r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    paintGroup(r, accent, t, n, idx == group, has);
                    // Switching on focus, not on OK: the options appear as you walk the list,
                    // so you can see what a group holds before committing to it.
                    if (has && idx != group) { group = idx; focusedRow = 0; buildRows(c, false); repaintGroups(); }
                }
            });
            r.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!rowViews.isEmpty()) rowViews.get(0).requestFocus();
                }
            });
            groupViews.add(r);
            groupList.addView(r);
        }
        linkGroupFocus();
    }

    /**
     * Three states, told apart three ways.
     *
     * Selected but not focused - the group whose options are on the right - keeps a bright
     * accent bar and a lifted plate. Focused adds the ring the whole launcher uses. Neither
     * carries the load alone, because a tint difference of 14% white is invisible across a
     * room, which is what this looked like before.
     */
    private void paintGroup(LinearLayout r, View accent, TextView t, TextView n,
                            boolean selected, boolean focused) {
        Context c = this;
        float fill = selected ? (focused ? 0.30f : 0.22f) : (focused ? 0.16f : 0.05f);
        r.setBackground(Ui.roundRect(c, Ui.alphaWhite(fill), 16f, 1f,
                Ui.alphaWhite(selected ? 0.50f : 0.12f)));
        r.setForeground(focused
                ? Ui.ring(c, 16f, Prefs.highContrast(c) ? 6f : 4f, Ui.alphaWhite(0.95f))
                : Ui.ring(c, 16f, 0f, Color.TRANSPARENT));
        accent.setBackground(Ui.roundRect(c,
                selected ? Ui.alphaWhite(0.95f) : Ui.alphaWhite(0.10f), 3f, 0f, Color.TRANSPARENT));
        t.setTextColor(Ui.alphaWhite(selected ? 1f : (focused ? 0.96f : 0.80f)));
        n.setTextColor(Ui.alphaWhite(selected ? 0.88f : (focused ? 0.80f : 0.52f)));
        r.setTranslationZ(focused ? Ui.px(c, 16) : 0f);
    }

    private void repaintGroups() {
        for (int i = 0; i < groupViews.size(); i++) {
            LinearLayout r = (LinearLayout) groupViews.get(i);
            View accent = r.getChildAt(0);
            TextView t = (TextView) r.getChildAt(1);
            TextView n = (TextView) r.getChildAt(2);
            paintGroup(r, accent, t, n, i == group, r.hasFocus());
        }
    }

    private void linkGroupFocus() {
        for (View v : groupViews) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        for (int i = 0; i < groupViews.size(); i++) {
            View v = groupViews.get(i);
            v.setNextFocusDownId(i < groupViews.size() - 1 ? groupViews.get(i + 1).getId() : v.getId());
            v.setNextFocusUpId(i > 0 ? groupViews.get(i - 1).getId() : v.getId());
            v.setNextFocusLeftId(v.getId());
            v.setNextFocusRightId(rowViews.isEmpty() ? v.getId() : rowViews.get(0).getId());
        }
    }

    private View preview(Context c) {
        // Not a control, and it must not look like one. It used to wear the same rounded
        // plate and border as the focusable group rows above it, which is a promise the
        // screen cannot keep: there is nothing to press here. A rule and a label instead.
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, Ui.px(c, 22), 0, 0);
        box.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        View rule = new View(c);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(c, 1)));
        rlp.bottomMargin = Ui.px(c, 18);
        rule.setLayoutParams(rlp);
        rule.setBackground(new android.graphics.drawable.ColorDrawable(Ui.alphaWhite(0.14f)));
        box.addView(rule);

        TextView h = new TextView(c);
        h.setText("HOW IT WILL LOOK");
        h.setLetterSpacing(0.14f);
        h.setTextColor(Ui.alphaWhite(0.55f));
        h.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 16));
        box.addView(h);

        previewShelf = new LinearLayout(c);
        previewShelf.setOrientation(LinearLayout.HORIZONTAL);
        previewShelf.setPadding(Ui.px(c, 16), Ui.px(c, 14), Ui.px(c, 16), Ui.px(c, 14));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Ui.px(c, 14);
        previewShelf.setLayoutParams(plp);
        box.addView(previewShelf);

        previewNote = new TextView(c);
        previewNote.setTextColor(Ui.alphaWhite(0.72f));
        previewNote.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 17));
        previewNote.setPadding(0, Ui.px(c, 12), 0, 0);
        box.addView(previewNote);
        return box;
    }

    /** The preview answers the two settings whose effect is otherwise invisible until you leave. */
    private void refreshPreview() {
        Context c = this;
        if (previewShelf == null) return;
        boolean hc = Prefs.highContrast(c);
        previewShelf.setBackground(hc
                ? Ui.roundRect(c, Color.argb(200, 8, 10, 14), 18f, 1.5f, Ui.alphaWhite(0.55f))
                : Ui.roundRect(c, Ui.alphaWhite(Prefs.glassAlpha(c)), 18f, 1.5f, Ui.alphaWhite(0.22f)));

        previewShelf.removeAllViews();
        int w = Math.round(Prefs.tileWidth(c) * 0.30f);
        int h = Math.round(w * 9f / 16f);
        for (int i = 0; i < 4; i++) {
            View t = new View(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(c, w), Ui.px(c, h));
            if (i > 0) lp.leftMargin = Ui.px(c, 11);
            t.setLayoutParams(lp);
            if (i == 0) {
                t.setBackground(Ui.roundRect(c, 0xFF7E4A52, 9f, 0f, Color.TRANSPARENT));
                t.setForeground(Ui.ring(c, 9f, hc ? 5f : 4f, Ui.alphaWhite(0.95f)));
            } else {
                t.setBackground(Ui.roundRect(c, Ui.alphaWhite(0.16f), 9f, 0f, Color.TRANSPARENT));
                t.setAlpha(Tile.RESTING_ALPHA);
            }
            previewShelf.addView(t);
        }
        previewNote.setText(hc ? "High contrast · measured 17.5:1"
                              : "Normal · measured 8.3:1  ·  " + Prefs.tileName(c)
                                + ", " + Prefs.tileWidth(c) + "px");
    }

    private View footer(Context c) {
        LinearLayout f = new LinearLayout(c);
        f.setOrientation(LinearLayout.HORIZONTAL);
        f.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, 40));
        lp.topMargin = Ui.px(c, 10);
        f.setLayoutParams(lp);
        f.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        f.addView(hint(c, "▲▼", "Move", false));
        f.addView(hint(c, "◀", "Back to the groups", true));
        f.addView(hint(c, "OK", "Change it", true));
        f.addView(hint(c, "Back", "Home", true));
        return f;
    }

    private View hint(Context c, String glyph, String text, boolean spaced) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (spaced) lp.leftMargin = Ui.px(c, 30);
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

    // ---- rows ------------------------------------------------------------

    private void buildRows(final Context c) { buildRows(c, true); }

    private void buildRows(final Context c, final boolean focusFirst) {
        rowList.removeAllViews();
        rowViews.clear();
        groupTitle.setText(groups.get(group).name);

        List<Opt> opts = groups.get(group).opts;
        for (int i = 0; i < opts.size(); i++) rowList.addView(row(c, opts.get(i), i));

        for (int i = 0; i < rowViews.size(); i++) {
            View v = rowViews.get(i);
            if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        }
        for (int i = 0; i < rowViews.size(); i++) {
            View v = rowViews.get(i);
            v.setNextFocusDownId(i < rowViews.size() - 1 ? rowViews.get(i + 1).getId() : v.getId());
            // Up from the first option is the only way back to the group list, because left
            // and right are spent on changing values.
            v.setNextFocusUpId(i > 0 ? rowViews.get(i - 1).getId()
                    : (groupViews.isEmpty() ? v.getId() : groupViews.get(group).getId()));
            v.setNextFocusLeftId(v.getId());
            v.setNextFocusRightId(v.getId());
        }
        linkGroupFocus();
        refreshPreview();

        if (focusFirst) rowList.post(new Runnable() {
            public void run() {
                if (!rowViews.isEmpty() && focusedRow < rowViews.size())
                    rowViews.get(focusedRow).requestFocus();
            }
        });
    }

    private View row(final Context c, final Opt o, final int index) {
        final LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setFocusable(true);
        r.setPadding(Ui.px(c, 28), Ui.px(c, 20), Ui.px(c, 28), Ui.px(c, 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(c, 12);
        r.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(c);
        title.setText(o.title);
        title.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 25));
        title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        head.addView(title);

        final TextView value = new TextView(c);
        value.setText(currentValue(o));
        value.setSingleLine(true);
        value.setTextColor(Ui.alphaWhite(Ui.TEXT_SECONDARY));
        value.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        value.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        head.addView(value);
        r.addView(head);

        TextView help = new TextView(c);
        help.setText(o.help);
        // Measured 4.41:1 at 86% white against the focused row's lighter plate.
        // Full white plus a shadow clears the 4.5 AA floor.
        help.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
        help.setShadowLayer(Ui.px(c, 8), 0f, Ui.px(c, 1), Color.argb(150, 0, 0, 0));
        help.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 19));
        help.setLineSpacing(Ui.px(c, 4), 1f);
        help.setPadding(0, Ui.px(c, 5), 0, 0);
        help.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.addView(help);

        // The pills are drawn only for the row in focus. Drawing every row's values at once
        // is a wall of chips; drawing none of them is the cycling behaviour being replaced.
        final LinearLayout pills = new LinearLayout(c);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Ui.px(c, 14);
        pills.setLayoutParams(plp);
        pills.setVisibility(View.GONE);
        pills.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.addView(pills);

        r.setTag(new Object[]{ o, value, pills });
        paint(r, false);
        r.setContentDescription(describe(o));

        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                paint((LinearLayout) v, has);
                pills.setVisibility(has ? View.VISIBLE : View.GONE);
                if (has) { focusedRow = index; drawPills(c, o, pills); }
            }
        });
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { activate(c, o, r, pills); }
        });
        r.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                // Left is BACK, everywhere, always. It used to decrement a value, which left
                // the way out of a nested list with nowhere to go but up from the first row -
                // and nobody guesses that.
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    if (!groupViews.isEmpty()) groupViews.get(group).requestFocus();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { activate(c, o, r, pills); return true; }
                return false;
            }
        });

        rowViews.add(r);
        return r;
    }

    private void drawPills(Context c, Opt o, LinearLayout pills) {
        pills.removeAllViews();
        int cur = currentIndex(o);
        for (int i = 0; i < o.values.length; i++) {
            boolean sel = (o.type == T_CHOICE) && i == cur;
            TextView t = new TextView(c);
            t.setText(o.values[i]);
            t.setSingleLine(true);
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
            t.setPadding(Ui.px(c, 20), Ui.px(c, 9), Ui.px(c, 20), Ui.px(c, 9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = Ui.px(c, 10);
            t.setLayoutParams(lp);
            t.setTextColor(sel ? Ui.GROUND : Ui.alphaWhite(0.88f));
            t.setBackground(Ui.roundRect(c, sel ? Color.WHITE : Ui.alphaWhite(0.10f), 999f,
                    1f, sel ? Color.WHITE : Ui.alphaWhite(0.22f)));
            t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            pills.addView(t);
        }
    }

    private void paint(LinearLayout r, boolean focused) {
        Context c = this;
        r.setBackground(Ui.roundRect(c, Ui.alphaWhite(focused ? 0.20f : 0.07f), 22f,
                1f, Ui.alphaWhite(focused ? 0.50f : 0.15f)));
        r.setForeground(focused
                ? Ui.ring(c, 22f, Prefs.highContrast(c) ? 6f : 4f, Ui.alphaWhite(0.95f))
                : Ui.ring(c, 22f, 0f, Color.TRANSPARENT));
        r.setTranslationZ(focused ? Ui.px(c, 20) : 0f);
        Object[] tag = (Object[]) r.getTag();
        if (tag != null) ((TextView) tag[1]).setTextColor(Ui.alphaWhite(focused ? 1f : Ui.TEXT_SECONDARY));
    }

    // ---- values ----------------------------------------------------------

    private int currentIndex(Opt o) {
        if (o.key.equals("recents"))  return Prefs.showRecents(this) ? 0 : 1;
        if (o.key.equals("art"))      return Prefs.posterArt(this) ? 0 : 1;
        if (o.key.equals("sources"))  return Prefs.sourcesVisible(this) ? 0 : 1;
        if (o.key.equals("names"))    return Prefs.alwaysShowNames(this) ? 0 : 1;
        if (o.key.equals("tile"))     return Prefs.tileIndex(this);
        if (o.key.equals("glass"))    return Prefs.glassIndex(this);
        if (o.key.equals("bg"))       return Prefs.bgIndex(this);
        if (o.key.equals("contrast")) return Prefs.highContrast(this) ? 1 : 0;
        if (o.key.equals("reduce"))   return Prefs.reduceMotion(this) ? 1 : 0;
        if (o.key.equals("text"))     return Prefs.textIndex(this);
        return 0;
    }

    private String currentValue(Opt o) {
        if (o.type == T_ACTION) {
            if (o.key.equals("apps")) {
                int total = AppEntry.loadAll(this).size();
                return Prefs.shownCount(this, total) + " of " + total;
            }
            return o.values[0];
        }
        if (o.key.equals("reduce")) return Prefs.motionLabel(this);
        return o.values[currentIndex(o)];
    }

    private String describe(Opt o) {
        String how;
        if (o.type != T_CHOICE) how = " Press OK to open.";
        else if (o.values.length == 2) how = " Press OK to switch it.";
        else how = " Press OK to choose from " + o.values.length + " values.";
        return o.title + ", " + currentValue(o) + ". " + o.help + how
                + " Left goes back to the groups.";
    }

    private void apply(Opt o, int idx) {
        if (o.key.equals("recents"))       Prefs.setRecents(this, idx == 0);
        else if (o.key.equals("art"))      { Prefs.setPosterArt(this, idx == 0);
                                             if (idx != 0) ArtCache.clear(this); }
        else if (o.key.equals("sources"))  Prefs.setSources(this, idx == 0);
        else if (o.key.equals("names"))    Prefs.setNames(this, idx == 0);
        else if (o.key.equals("tile"))     Prefs.setTile(this, idx);
        else if (o.key.equals("glass"))    Prefs.setGlass(this, idx);
        else if (o.key.equals("bg"))       Prefs.setBg(this, idx);
        else if (o.key.equals("contrast")) Prefs.setContrast(this, idx == 1);
        else if (o.key.equals("reduce"))   Prefs.setReduce(this, idx == 1);
        else if (o.key.equals("text"))     Prefs.setText(this, idx);
    }

    /**
     * What OK and right do to an option.
     *
     * A two-value option is a switch and both values are already on screen, so it flips in
     * one press - making a toggle cost three presses to satisfy a navigation rule would be a
     * bad trade. Anything longer opens the list, which is the same pattern the source picker
     * uses and the only one that behaves on this television's remote.
     */
    private void activate(final Context c, final Opt o, final View row, final LinearLayout pills) {
        if (o.type == T_ACTION) { doAction(c, o); return; }
        final TextView value = (TextView) ((Object[]) row.getTag())[1];
        if (o.values.length == 2) {
            step(c, o, currentIndex(o) == 0 ? +1 : -1, value, pills, row);
            return;
        }
        final int cur = currentIndex(o);
        final String[] arr = new String[o.values.length];
        for (int i = 0; i < o.values.length; i++) {
            arr[i] = (i == cur ? "\u2022  " : "    ") + o.values[i];
        }
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(o.title)
                .setItems(arr, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int which) {
                        step(c, o, which - currentIndex(o), value, pills, row);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void step(Context c, Opt o, int delta, TextView value, LinearLayout pills, View row) {
        int next = currentIndex(o) + delta;
        if (next < 0) next = 0;
        if (next > o.values.length - 1) next = o.values.length - 1;
        if (next == currentIndex(o) && delta != 0) return;
        apply(o, next);
        value.setText(currentValue(o));
        row.setContentDescription(describe(o));
        drawPills(c, o, pills);
        refreshPreview();
        row.announceForAccessibility(o.title + ", " + currentValue(o));

        // Text size and high contrast change this screen too, so it has to be rebuilt.
        if (o.key.equals("text") || o.key.equals("contrast")) {
            final int keepGroup = group, keepRow = focusedRow;
            setContentView(build());
            group = keepGroup; focusedRow = keepRow;
            buildGroups(this); buildRows(this);
        }
    }

    private void doAction(Context c, Opt o) {
        if (o.key.equals("apps")) {
            startActivity(new Intent(this, AppsOnHomeActivity.class));
        } else if (o.key.equals("names_src")) {
            startActivity(new Intent(this, SourcesActivity.class));
        } else if (o.key.equals("notice")) {
            startActivity(new Intent(this, FirstBootActivity.class));
        } else if (o.key.equals("export")) {
            String path = Prefs.exportSettings(this);
            say(path != null ? "Saved to " + path : "Could not write the settings file");
        } else if (o.key.equals("import")) {
            boolean ok = Prefs.importSettings(this);
            say(ok ? "Settings loaded" : "No saved settings file found");
            if (ok) { groups = model(); setContentView(build()); }
        } else if (o.key.equals("tv")) {
            Intent s = getPackageManager().getLaunchIntentForPackage("com.android.tv.settings");
            if (s == null) s = new Intent(android.provider.Settings.ACTION_SETTINGS);
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(s); } catch (Throwable t) { say("No settings app on this TV"); }
        }
    }

    /** A dialog rather than a Toast: on a TV a Toast is easy to miss and hard to read. */
    private void say(String msg) {
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (rowList != null) {
            buildRows(this, false);
            // Coming back from Apps on the shelf, the notice or the TV's own settings, nothing
            // holds focus - the remote appears dead until a direction is pressed. Put focus
            // back where it was.
            rowList.post(new Runnable() {
                public void run() {
                    View cur = getCurrentFocus();
                    if (cur == null && !rowViews.isEmpty()) {
                        int i = Math.min(Math.max(focusedRow, 0), rowViews.size() - 1);
                        rowViews.get(i).requestFocus();
                    }
                }
            });
        }
    }
}
