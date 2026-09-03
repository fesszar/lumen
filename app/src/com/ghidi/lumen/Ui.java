package com.ghidi.lumen;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** Design values live in 1920x1080 space and are scaled to the real panel. */
public final class Ui {

    /** The one easing curve in the launcher. */
    public static final Interpolator EASE = new PathInterpolator(0.2f, 0.7f, 0.2f, 1f);
    public static final int FOCUS_MS = 220;
    public static final float FOCUS_SCALE = 1.09f;

    // Deliberately dark. White text over a light glass panel over a light ground
    // fails WCAG contrast; the ground is what fixes that, not the text colour.
    public static final int GROUND = Color.parseColor("#14161C");

    // Text roles. Nothing below PRIMARY/SECONDARY is used for text anywhere.
    public static final float TEXT_PRIMARY   = 1.00f;
    public static final float TEXT_SECONDARY = 0.86f;
    public static final float TEXT_TERTIARY  = 0.78f;
    public static final int WHITE = Color.WHITE;

    private static float scale = -1f;

    public static float s(Context c) {
        if (scale < 0f) {
            int w = c.getResources().getDisplayMetrics().widthPixels;
            int h = c.getResources().getDisplayMetrics().heightPixels;
            if (w < h) { int t = w; w = h; h = t; }
            scale = w / 1920f;
        }
        return scale;
    }

    /** design px -> device px */
    public static int px(Context c, float designPx) {
        return Math.round(designPx * s(c));
    }

    public static float sp(Context c, float designPx) {
        // text sized in the same 1920 space, applied as raw pixels, then scaled by the
        // launcher's own text-size setting so "Large" moves every label at once.
        return designPx * s(c) * Prefs.textMult(c);
    }

    /** Text that must not grow with the text-size setting - a fixed-width plate, say. */
    public static float spFixed(Context c, float designPx) {
        return designPx * s(c);
    }

    /**
     * The outline used for a pinned app that is no longer installed. Dashed, so it reads as
     * a gap that is being held open rather than as a tile that failed to draw.
     */
    public static GradientDrawable dashed(Context c, float radiusDesign) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(alphaWhite(0.06f));
        g.setCornerRadius(px(c, radiusDesign));
        g.setStroke(Math.max(1, px(c, 2f)), alphaWhite(0.34f), px(c, 12f), px(c, 9f));
        return g;
    }

    public static int alphaWhite(float a) {
        return Color.argb(Math.round(a * 255f), 255, 255, 255);
    }

    public static GradientDrawable roundRect(Context c, int fill, float radiusDesign,
                                             float strokeDesign, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(px(c, radiusDesign));
        if (strokeDesign > 0f) {
            g.setStroke(Math.max(1, px(c, strokeDesign)), strokeColor);
        }
        return g;
    }

    /**
     * Focus ring. Two strokes, not one: a dark outer line under the white inner line.
     * A plain white ring vanishes against white banner art - Netflix's, for one.
     */
    public static Drawable ring(Context c, float radiusDesign, float strokeDesign, int color) {
        if (strokeDesign <= 0f) {
            GradientDrawable empty = new GradientDrawable();
            empty.setShape(GradientDrawable.RECTANGLE);
            empty.setColor(Color.TRANSPARENT);
            empty.setCornerRadius(px(c, radiusDesign));
            return empty;
        }

        GradientDrawable outer = new GradientDrawable();
        outer.setShape(GradientDrawable.RECTANGLE);
        outer.setColor(Color.TRANSPARENT);
        outer.setCornerRadius(px(c, radiusDesign + 2f));
        outer.setStroke(Math.max(1, px(c, strokeDesign + 3f)), Color.argb(110, 0, 0, 0));

        GradientDrawable inner = new GradientDrawable();
        inner.setShape(GradientDrawable.RECTANGLE);
        inner.setColor(Color.TRANSPARENT);
        inner.setCornerRadius(px(c, radiusDesign));
        inner.setStroke(Math.max(1, px(c, strokeDesign)), color);

        return new LayerDrawable(new Drawable[]{ outer, inner });
    }

    /**
     * The luminous ground. Layered radial gradients, drawn once - never a runtime blur.
     * Android 11 has no RenderEffect, so nothing here asks the GPU to blur anything.
     */
    public static Drawable ground(Context c, int tint) {
        Drawable base = new ColorDrawable(GROUND);

        GradientDrawable bloomA = new GradientDrawable();
        bloomA.setShape(GradientDrawable.RECTANGLE);
        bloomA.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bloomA.setGradientCenter(0.24f, 0.34f);
        bloomA.setGradientRadius(px(c, 1500));
        bloomA.setColors(new int[]{
                withAlpha(lift(tint), 0.42f),
                withAlpha(lift(tint), 0.18f),
                withAlpha(GROUND, 0f)
        });

        GradientDrawable bloomB = new GradientDrawable();
        bloomB.setShape(GradientDrawable.RECTANGLE);
        bloomB.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bloomB.setGradientCenter(0.80f, 0.78f);
        bloomB.setGradientRadius(px(c, 1400));
        bloomB.setColors(new int[]{
                Color.argb(52, 232, 238, 248),
                Color.argb(18, 232, 238, 248),
                Color.argb(0, 232, 238, 248)
        });

        GradientDrawable vignette = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{ Color.argb(18, 255, 255, 255), Color.argb(40, 8, 10, 14), Color.argb(120, 8, 10, 14) });

        return new LayerDrawable(new Drawable[]{ base, bloomA, bloomB, vignette });
    }

    /** Push a dominant colour up in lightness so it reads as light behind glass. */
    public static int lift(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(0.55f, hsv[1] * 1.15f);
        hsv[2] = Math.min(0.46f, Math.max(0.30f, hsv[2] * 1.1f));
        return Color.HSVToColor(hsv);
    }

    public static int withAlpha(int color, float a) {
        return Color.argb(Math.round(a * 255f), Color.red(color), Color.green(color), Color.blue(color));
    }

    /**
     * Glass panel: translucent fill + hairline stroke + a lit top edge.
     * Four flat layers. The lit top edge is what the eye reads as thickness.
     */
    public static Drawable glass(Context c, float fillAlpha, float radiusDesign) {
        return glass(c, fillAlpha, radiusDesign, false);
    }

    /**
     * dark=true is the high-contrast panel. Raising a light panel's opacity makes white text
     * WORSE, not better - measured at 3.36:1. High contrast has to darken the surface instead.
     */
    public static Drawable glass(Context c, float fillAlpha, float radiusDesign, boolean dark) {
        GradientDrawable fill = dark
                ? roundRect(c, Color.argb(200, 8, 10, 14), radiusDesign, 2f, alphaWhite(0.45f))
                : roundRect(c, alphaWhite(fillAlpha), radiusDesign, 1.5f, alphaWhite(0.28f));

        GradientDrawable sheen = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                dark ? new int[]{ alphaWhite(0.10f), Color.TRANSPARENT, Color.TRANSPARENT }
                     : new int[]{ alphaWhite(0.30f), alphaWhite(0.04f), Color.TRANSPARENT });
        sheen.setCornerRadius(px(c, radiusDesign));

        return new LayerDrawable(new Drawable[]{ fill, sheen });
    }

    private Ui() {}
}
