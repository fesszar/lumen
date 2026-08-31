package com.ghidi.lumen;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** A focusable app tile: art, a white ring on focus, scale and lift. */
public class Tile extends FrameLayout {

    public static final float W = 288f;
    public static final float H = 162f;
    public static final float RADIUS = 18f;

    /** Unfocused tiles recede. Without this the ring alone is easy to lose across a room. */
    public static final float RESTING_ALPHA = 0.68f;

    public final AppEntry app;
    private float radius = RADIUS;

    public Tile(Context c, AppEntry app, float radiusDesign) {
        super(c);
        this.app = app;
        this.radius = radiusDesign;

        setFocusable(true);
        setFocusableInTouchMode(false);
        setClipToOutline(true);
        setBackground(face(false, radiusDesign));
        setForeground(Ui.ring(c, radiusDesign, 0f, Color.TRANSPARENT));
        setElevation(Ui.px(c, 4));
        setAlpha(RESTING_ALPHA);

        // Screen readers announce this. Without it TalkBack reads nothing at all -
        // the tile is a picture with no text in it.
        setContentDescription(app.label);

        Drawable art = app.art;
        if (art != null) {
            ImageView iv = new ImageView(c);
            iv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            iv.setImageDrawable(art);
            iv.setScaleType(app.hasBanner ? ImageView.ScaleType.CENTER_CROP
                                          : ImageView.ScaleType.CENTER_INSIDE);
            iv.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            if (!app.hasBanner) {
                int pad = Ui.px(c, 30);
                iv.setPadding(pad, pad, pad, Ui.px(c, 46));
            }
            addView(iv);
        }

        if (!app.hasBanner) {
            TextView t = new TextView(c);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.BOTTOM;
            lp.bottomMargin = Ui.px(c, 14);
            t.setLayoutParams(lp);
            t.setText(app.label);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            int side = Ui.px(c, 12);
            t.setPadding(side, 0, side, 0);
            t.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
            t.setShadowLayer(Ui.px(c, 10), 0f, Ui.px(c, 2), Color.argb(190, 0, 0, 0));
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
            t.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(t);
        }
    }

    private Drawable face(boolean focused, float r) {
        return Ui.roundRect(getContext(), app.tint, r, 1f,
                Ui.alphaWhite(focused ? 0.55f : 0.16f));
    }

    public void applyFocus(boolean focused) {
        Context c = getContext();
        int ms = Prefs.motionMs(c);

        setBackground(face(focused, radius));
        setForeground(focused
                ? Ui.ring(c, radius, Prefs.highContrast(c) ? 6f : 4f, Ui.alphaWhite(0.95f))
                : Ui.ring(c, radius, 0f, Color.TRANSPARENT));

        // Never bringToFront(): in a LinearLayout that reorders the child and the tile
        // physically jumps to the end of the row. Z-order via translationZ only.
        setTranslationZ(focused ? Ui.px(c, 40) : 0f);

        animate().cancel();
        if (ms == 0) {
            setScaleX(focused ? Ui.FOCUS_SCALE : 1f);
            setScaleY(focused ? Ui.FOCUS_SCALE : 1f);
            setAlpha(focused ? 1f : RESTING_ALPHA);
        } else {
            animate().scaleX(focused ? Ui.FOCUS_SCALE : 1f)
                     .scaleY(focused ? Ui.FOCUS_SCALE : 1f)
                     .alpha(focused ? 1f : RESTING_ALPHA)
                     .setDuration(ms).setInterpolator(Ui.EASE).start();
        }
        setElevation(Ui.px(c, focused ? 30 : 4));
    }
}
