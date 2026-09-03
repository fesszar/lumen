package com.ghidi.lumen;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * What is on the shelf, and in what order.
 *
 * Two halves. The top is the shelf itself, in shelf order, where OK picks an app up and left
 * and right move it - the held tile lifts, so the same two keys never do two things without
 * saying which. The bottom is everything else, still installed and still reachable from All
 * apps; OK there puts an app back on the shelf.
 */
public class AppsOnHomeActivity extends Activity {

    private LinearLayout shelfRow, restRow;
    private TextView counter, instruction, modePill;
    private final List<AppEntry> onShelf = new ArrayList<AppEntry>();
    private final List<AppEntry> offShelf = new ArrayList<AppEntry>();
    private final List<View> shelfViews = new ArrayList<View>();
    private final List<View> restViews = new ArrayList<View>();
    private int total;
    private int held = -1;          // index of the app being moved, or -1
    private int cursor = 0;

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

    private View build() {
        final Context c = this;
        split(c);

        FrameLayout root = new FrameLayout(c);
        View ground = new View(c);
        ground.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ground.setBackground(Ui.ground(c, 0xFF4F7EA3));
        ground.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(ground);

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.px(c, 96), Ui.px(c, 54), Ui.px(c, 96), Ui.px(c, 54));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content);

        // ---- heading
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);

        LinearLayout titles = new LinearLayout(c);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView kicker = new TextView(c);
        kicker.setText("SETTINGS  ›  WHAT IS ON HOME");
        kicker.setLetterSpacing(0.16f);
        kicker.setTextColor(Ui.alphaWhite(0.55f));
        kicker.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        titles.addView(kicker);

        TextView title = new TextView(c);
        title.setText("Apps on the shelf");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 44));
        title.setPadding(0, Ui.px(c, 10), 0, 0);
        titles.addView(title);
        head.addView(titles);

        modePill = new TextView(c);
        modePill.setSingleLine(true);
        modePill.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        modePill.setPadding(Ui.px(c, 26), Ui.px(c, 12), Ui.px(c, 26), Ui.px(c, 12));
        modePill.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        head.addView(modePill);
        content.addView(head);

        instruction = new TextView(c);
        instruction.setTextColor(Ui.alphaWhite(0.88f));
        instruction.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 22));
        instruction.setLineSpacing(Ui.px(c, 6), 1f);
        instruction.setPadding(0, Ui.px(c, 14), 0, Ui.px(c, 28));
        instruction.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        content.addView(instruction);

        // ---- on the shelf
        content.addView(sectionLabel(c, "ON THE SHELF", "left to right, exactly as they appear on Home"));

        final FrameLayout panel = new FrameLayout(c);
        panel.setBackground(Ui.glass(c, Prefs.effectiveGlassAlpha(c), 34f, Prefs.highContrast(c)));
        int pp = Ui.px(c, 26);
        panel.setPadding(pp, pp, pp, pp);
        panel.setClipToPadding(false);
        // Without an outline clip the scrolling row draws past the panel's rounded right edge
        // and tiles float outside the glass. The home shelf already does this; so must this.
        final float panelRadius = Ui.px(c, 34);
        panel.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), panelRadius);
            }
        });
        panel.setClipToOutline(true);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Ui.px(c, 14);
        panel.setLayoutParams(plp);
        content.addView(panel);

        ShelfScrollView hsv = new ShelfScrollView(c);
        hsv.setGutter(Ui.px(c, 26));
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setFocusable(false);
        hsv.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        hsv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.addView(hsv);

        shelfRow = new LinearLayout(c);
        shelfRow.setOrientation(LinearLayout.HORIZONTAL);
        shelfRow.setClipChildren(false);
        shelfRow.setClipToPadding(false);
        int vp = Ui.px(c, 14);
        shelfRow.setPadding(Ui.px(c, 20), vp, Ui.px(c, 20), vp);
        hsv.addView(shelfRow);

        // ---- everything else
        content.addView(sectionLabel(c, "EVERYTHING ELSE",
                "still installed, still reachable from All apps"));

        restRow = new LinearLayout(c);
        restRow.setOrientation(LinearLayout.HORIZONTAL);
        restRow.setClipChildren(false);
        restRow.setClipToPadding(false);
        android.widget.HorizontalScrollView rsv = new android.widget.HorizontalScrollView(c);
        rsv.setHorizontalScrollBarEnabled(false);
        rsv.setFocusable(false);
        rsv.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        rsv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        rsv.setClipChildren(false);
        rsv.setClipToPadding(false);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.px(c, 14);
        rsv.setLayoutParams(rlp);
        rsv.addView(restRow);
        content.addView(rsv);

        View grow = new View(c);
        grow.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        content.addView(grow);

        counter = new TextView(c);
        counter.setTextColor(Ui.alphaWhite(0.62f));
        counter.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 19));
        counter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        content.addView(counter);

        drawAll(c);
        return root;
    }

    private View sectionLabel(Context c, String label, String sub) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(c, 30);
        h.setLayoutParams(lp);
        h.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        TextView a = new TextView(c);
        a.setText(label);
        a.setLetterSpacing(0.16f);
        a.setTextColor(Ui.alphaWhite(0.62f));
        a.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        h.addView(a);

        TextView b = new TextView(c);
        b.setText(sub);
        b.setTextColor(Ui.alphaWhite(0.50f));
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 19));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = Ui.px(c, 14);
        b.setLayoutParams(blp);
        h.addView(b);
        return h;
    }

    /** Visible apps in shelf order, then the hidden ones. */
    private void split(Context c) {
        List<AppEntry> all = AppEntry.loadAll(c);
        total = all.size();
        onShelf.clear();
        offShelf.clear();

        List<String> order = Prefs.order(c);
        List<AppEntry> visible = new ArrayList<AppEntry>();
        for (AppEntry a : all) {
            if (Prefs.isHidden(c, a.pkg)) offShelf.add(a);
            else visible.add(a);
        }
        if (order.isEmpty()) {
            onShelf.addAll(visible);
        } else {
            List<AppEntry> rest = new ArrayList<AppEntry>(visible);
            for (String pkg : order) {
                for (int i = 0; i < rest.size(); i++) {
                    if (rest.get(i).pkg.equals(pkg)) { onShelf.add(rest.remove(i)); break; }
                }
            }
            onShelf.addAll(rest);
        }
    }

    private void persistOrder() {
        List<String> pkgs = new ArrayList<String>();
        for (AppEntry a : onShelf) pkgs.add(a.pkg);
        Prefs.setOrder(this, pkgs);
    }

    // ---- drawing ---------------------------------------------------------

    private void drawAll(final Context c) {
        shelfRow.removeAllViews();
        restRow.removeAllViews();
        shelfViews.clear();
        restViews.clear();

        int w = 196, h = Math.round(196 * 9f / 16f);
        for (int i = 0; i < onShelf.size(); i++) {
            shelfRow.addView(shelfCell(c, onShelf.get(i), i, w, h));
        }
        for (int i = 0; i < offShelf.size(); i++) {
            restRow.addView(restPill(c, offShelf.get(i), i));
        }

        for (View v : shelfViews) if (v.getId() == View.NO_ID) v.setId(View.generateViewId());
        for (View v : restViews)  if (v.getId() == View.NO_ID) v.setId(View.generateViewId());

        int firstRest = restViews.isEmpty() ? View.NO_ID : restViews.get(0).getId();
        for (int i = 0; i < shelfViews.size(); i++) {
            View v = shelfViews.get(i);
            v.setNextFocusRightId(i < shelfViews.size() - 1 ? shelfViews.get(i + 1).getId() : v.getId());
            v.setNextFocusLeftId(i > 0 ? shelfViews.get(i - 1).getId() : v.getId());
            v.setNextFocusDownId(firstRest != View.NO_ID ? firstRest : v.getId());
            v.setNextFocusUpId(v.getId());
        }
        int firstShelf = shelfViews.isEmpty() ? View.NO_ID : shelfViews.get(0).getId();
        for (int i = 0; i < restViews.size(); i++) {
            View v = restViews.get(i);
            v.setNextFocusRightId(i < restViews.size() - 1 ? restViews.get(i + 1).getId() : v.getId());
            v.setNextFocusLeftId(i > 0 ? restViews.get(i - 1).getId() : v.getId());
            v.setNextFocusUpId(firstShelf != View.NO_ID ? firstShelf : v.getId());
            v.setNextFocusDownId(v.getId());
        }

        refreshChrome();

        final int want = Math.min(cursor, shelfViews.size() - 1);
        if (want >= 0) shelfRow.post(new Runnable() {
            public void run() { shelfViews.get(want).requestFocus(); }
        });
    }

    private void refreshChrome() {
        Context c = this;
        boolean moving = held >= 0;
        modePill.setText(moving ? "Moving " + onShelf.get(held).label : "Press OK to move an app");
        modePill.setTextColor(moving ? Ui.GROUND : Ui.alphaWhite(0.88f));
        modePill.setBackground(Ui.roundRect(c, moving ? Color.WHITE : Ui.alphaWhite(0.10f),
                999f, 1f, moving ? Color.WHITE : Ui.alphaWhite(0.22f)));
        instruction.setText(moving
                ? "Left and right move the app you are holding; the others slide out of its way. "
                  + "Press OK again to put it down."
                : "Left and right walk along the shelf. OK picks an app up so you can move it. "
                  + "Down goes to everything else, where OK puts an app back on the shelf.");
        counter.setText(onShelf.size() + " of " + total + " on the shelf   ·   "
                + offShelf.size() + " elsewhere");
    }

    private View shelfCell(final Context c, final AppEntry app, final int index, int w, int h) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (index > 0) clp.leftMargin = Ui.px(c, 18);
        col.setLayoutParams(clp);

        final FrameLayout holder = new FrameLayout(c);
        holder.setLayoutParams(new LinearLayout.LayoutParams(Ui.px(c, w), Ui.px(c, h)));
        holder.setClipChildren(false);

        final Tile t = new Tile(c, app, 14f);
        t.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        t.setFocusable(false);
        holder.addView(t);

        // Position badge. While reordering, the number is the thing that is actually changing.
        TextView pos = new TextView(c);
        FrameLayout.LayoutParams poslp = new FrameLayout.LayoutParams(
                Ui.px(c, 30), Ui.px(c, 30));
        poslp.leftMargin = Ui.px(c, 8);
        poslp.topMargin = Ui.px(c, 8);
        pos.setLayoutParams(poslp);
        pos.setText(String.valueOf(index + 1));
        pos.setGravity(Gravity.CENTER);
        pos.setTextColor(Ui.alphaWhite(0.94f));
        pos.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spFixed(c, 15));
        pos.setBackground(Ui.roundRect(c, Color.argb(110, 0, 0, 0), 999f, 0f, Color.TRANSPARENT));
        pos.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        holder.addView(pos);

        final boolean isHeld = (held == index);
        final FrameLayout focusWrap = new FrameLayout(c);
        focusWrap.setLayoutParams(new LinearLayout.LayoutParams(Ui.px(c, w), Ui.px(c, h)));
        focusWrap.setFocusable(true);
        focusWrap.setClipChildren(false);
        focusWrap.addView(holder);
        focusWrap.setContentDescription(app.label + ", position " + (index + 1)
                + " of " + onShelf.size() + (isHeld ? ", moving. Left and right to move, OK to put down."
                                                    : ". OK to pick up and move."));

        focusWrap.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                if (has) cursor = index;
                paintCell(c, focusWrap, t, index, has);
            }
        });
        focusWrap.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (held == index) {
                    held = -1;
                    persistOrder();
                    v.announceForAccessibility(app.label + " put down at position " + (index + 1));
                } else if (held < 0) {
                    held = index;
                    v.announceForAccessibility("Moving " + app.label
                            + ". Left and right to move, OK to put down.");
                }
                drawAll(c);
            }
        });
        focusWrap.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN || held != index) return false;
                int to = -1;
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT && index < onShelf.size() - 1) to = index + 1;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && index > 0) to = index - 1;
                if (to < 0) return held == index
                        && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT);
                AppEntry moved = onShelf.remove(index);
                onShelf.add(to, moved);
                held = to;
                cursor = to;
                persistOrder();
                v.announceForAccessibility(moved.label + ", position " + (to + 1));
                drawAll(c);
                return true;
            }
        });

        paintCell(c, focusWrap, t, index, false);

        TextView name = new TextView(c);
        name.setText(app.label);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        name.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
        name.setShadowLayer(Ui.px(c, 8), 0f, Ui.px(c, 1), Color.argb(140, 0, 0, 0));
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 19));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                Ui.px(c, w), ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.px(c, 10);
        name.setLayoutParams(nlp);
        name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        col.addView(focusWrap);
        col.addView(name);
        shelfViews.add(focusWrap);
        return col;
    }

    /**
     * The held tile lifts and everything else recedes. Without the lift, left and right look
     * identical whether they are walking the shelf or dragging an app along it.
     */
    private void paintCell(Context c, FrameLayout wrap, Tile t, int index, boolean focused) {
        boolean isHeld = (held == index);
        boolean anyHeld = held >= 0;

        wrap.setForeground(focused
                ? Ui.ring(c, 14f, Prefs.highContrast(c) ? 6f : 4f, Ui.alphaWhite(0.95f))
                : Ui.ring(c, 14f, 0f, Color.TRANSPARENT));
        wrap.setTranslationZ(focused ? Ui.px(c, 40) : 0f);

        float scale = isHeld ? 1.10f : (focused ? 1.06f : 1f);
        float lift = isHeld ? -Ui.px(c, 10) : 0f;
        float alpha = focused ? 1f : (anyHeld ? 0.52f : Tile.RESTING_ALPHA);

        int ms = Prefs.motionMs(c);
        wrap.animate().cancel();
        if (ms == 0) {
            wrap.setScaleX(scale); wrap.setScaleY(scale);
            wrap.setTranslationY(lift); wrap.setAlpha(alpha);
        } else {
            wrap.animate().scaleX(scale).scaleY(scale).translationY(lift).alpha(alpha)
                    .setDuration(ms).setInterpolator(Ui.EASE).start();
        }
    }

    private View restPill(final Context c, final AppEntry app, final int index) {
        final TextView p = new TextView(c);
        p.setText(app.label);
        p.setSingleLine(true);
        p.setFocusable(true);
        p.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        p.setPadding(Ui.px(c, 24), Ui.px(c, 13), Ui.px(c, 24), Ui.px(c, 13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (index > 0) lp.leftMargin = Ui.px(c, 14);
        p.setLayoutParams(lp);
        p.setTextColor(Ui.alphaWhite(0.86f));
        p.setBackground(Ui.roundRect(c, Ui.alphaWhite(0.08f), 999f, 1f, Ui.alphaWhite(0.18f)));
        p.setContentDescription(app.label + ", not on the shelf. OK to add it.");

        p.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) {
                p.setTextColor(Ui.alphaWhite(has ? 1f : 0.86f));
                p.setBackground(Ui.roundRect(c, Ui.alphaWhite(has ? 0.26f : 0.08f), 999f,
                        has ? (Prefs.highContrast(c) ? 6f : 4f) : 1f,
                        Ui.alphaWhite(has ? 0.95f : 0.18f)));
            }
        });
        p.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.toggleHidden(AppsOnHomeActivity.this, app.pkg);   // unhide
                held = -1;
                split(c);
                // A newly added app goes to the end of the shelf, where it can be found.
                persistOrder();
                v.announceForAccessibility(app.label + " added to the shelf");
                cursor = Math.max(0, onShelf.size() - 1);
                drawAll(c);
            }
        });

        restViews.add(p);
        return p;
    }

    @Override
    public void onBackPressed() {
        if (held >= 0) { held = -1; persistOrder(); drawAll(this); return; }
        super.onBackPressed();
    }
}
