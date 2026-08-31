package com.ghidi.lumen;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Everything the settings screen can change. Plain SharedPreferences, no dependencies. */
public final class Prefs {

    private static final String FILE = "lumen";

    public static final String[] BG_NAMES  = { "Adaptive", "Aurora", "Ember", "Slate", "Neutral" };
    public static final int[]    BG_TINTS  = { 0, 0xFF6F61A1, 0xFF8D5A63, 0xFF4F7EA3, 0xFF6B6F78 };

    public static final int[]    GLASS_PCT = { 8, 15, 22, 30 };

    public static final String[] TILE_NAMES = { "Small", "Medium", "Large" };
    public static final int[]    TILE_W     = { 240, 288, 336 };

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int bgIndex(Context c)     { return clamp(p(c).getInt("bg", 0), BG_NAMES.length); }
    public static int glassIndex(Context c)  { return clamp(p(c).getInt("glass", 1), GLASS_PCT.length); }
    public static int tileIndex(Context c)   { return clamp(p(c).getInt("tile", 1), TILE_NAMES.length); }
    public static boolean sourcesVisible(Context c) { return p(c).getBoolean("sources", true); }

    /** Accessibility. Names always on and reduced motion are both safe defaults on a TV. */
    public static boolean alwaysShowNames(Context c) { return p(c).getBoolean("names", true); }
    public static boolean reduceMotion(Context c)    { return p(c).getBoolean("reduce", false); }
    public static boolean highContrast(Context c)    { return p(c).getBoolean("contrast", false); }

    public static void toggleNames(Context c)    { p(c).edit().putBoolean("names", !alwaysShowNames(c)).apply(); }
    public static void toggleReduce(Context c)   { p(c).edit().putBoolean("reduce", !reduceMotion(c)).apply(); }
    public static void toggleContrast(Context c) { p(c).edit().putBoolean("contrast", !highContrast(c)).apply(); }

    /** High contrast overrides the glass setting - panels go opaque enough to guarantee AA. */
    public static float effectiveGlassAlpha(Context c) {
        return highContrast(c) ? 0.34f : glassAlpha(c);
    }

    /**
     * Motion follows the TV's own animation scale as well as the launcher's toggle.
     * A launcher cannot WRITE Settings.Global without WRITE_SECURE_SETTINGS, which only
     * a system app gets - but it can and should READ it and obey.
     */
    public static int motionMs(Context c) {
        if (reduceMotion(c)) return 0;
        float sys = systemAnimationScale(c);
        if (sys <= 0.01f) return 0;                       // TV set to "no animations"
        return Math.round(Ui.FOCUS_MS * Math.min(1f, sys));
    }

    public static float systemAnimationScale(Context c) {
        try {
            return android.provider.Settings.Global.getFloat(c.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        } catch (Throwable t) {
            return 1f;
        }
    }

    /** What the settings row shows, so the two controls never look like they disagree. */
    public static String motionLabel(Context c) {
        if (reduceMotion(c)) return "On";
        float sys = systemAnimationScale(c);
        if (sys <= 0.01f) return "On, from the TV";
        if (sys < 0.99f)  return "Off, TV at " + trim(sys) + "x";
        return "Off";
    }

    private static String trim(float f) {
        String s = String.valueOf(f);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static void cycleBg(Context c)    { p(c).edit().putInt("bg", next(bgIndex(c), BG_NAMES.length)).apply(); }
    public static void cycleGlass(Context c) { p(c).edit().putInt("glass", next(glassIndex(c), GLASS_PCT.length)).apply(); }
    public static void cycleTile(Context c)  { p(c).edit().putInt("tile", next(tileIndex(c), TILE_NAMES.length)).apply(); }
    public static void toggleSources(Context c) { p(c).edit().putBoolean("sources", !sourcesVisible(c)).apply(); }

    public static String bgName(Context c)    { return BG_NAMES[bgIndex(c)]; }
    public static int    bgTint(Context c)    { return BG_TINTS[bgIndex(c)]; }
    public static int    glassPct(Context c)  { return GLASS_PCT[glassIndex(c)]; }
    public static float  glassAlpha(Context c){ return glassPct(c) / 100f; }
    public static int    tileWidth(Context c) { return TILE_W[tileIndex(c)]; }
    public static int    tileHeight(Context c){ return Math.round(tileWidth(c) * 9f / 16f); }
    public static String tileName(Context c)  { return TILE_NAMES[tileIndex(c)]; }

    /** Packages the user has taken off the shelf. They stay in All apps. */
    public static Set<String> hidden(Context c) {
        return new LinkedHashSet<String>(p(c).getStringSet("hidden", new HashSet<String>()));
    }

    public static boolean isHidden(Context c, String pkg) {
        return p(c).getStringSet("hidden", new HashSet<String>()).contains(pkg);
    }

    public static void toggleHidden(Context c, String pkg) {
        Set<String> s = hidden(c);
        if (!s.remove(pkg)) s.add(pkg);
        p(c).edit().putStringSet("hidden", new HashSet<String>(s)).apply();
    }

    public static int shownCount(Context c, int total) {
        return Math.max(0, total - hidden(c).size());
    }

    private static int clamp(int v, int len) { return (v < 0 || v >= len) ? 0 : v; }
    private static int next(int v, int len)  { return (v + 1) % len; }

    private Prefs() {}
}
