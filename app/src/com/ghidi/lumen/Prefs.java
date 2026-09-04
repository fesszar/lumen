package com.ghidi.lumen;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Everything the settings screen can change. Plain SharedPreferences, no dependencies. */
public final class Prefs {

    private static final String FILE = "lumen";

    public static final String[] BG_NAMES  = { "Adaptive", "Aurora", "Ember", "Slate", "Neutral" };
    public static final int[]    BG_TINTS  = { 0, 0xFF6F61A1, 0xFF8D5A63, 0xFF4F7EA3, 0xFF6B6F78 };

    public static final int[]    GLASS_PCT = { 8, 15, 22, 30 };

    public static final String[] TILE_NAMES = { "Small", "Medium", "Large" };
    public static final int[]    TILE_W     = { 240, 288, 336 };

    /** Text size. Applies to names, the Carry on row and the settings screen. */
    public static final String[] TEXT_NAMES = { "Normal", "Large", "Largest" };
    public static final float[]  TEXT_MULT  = { 1.00f, 1.12f, 1.26f };

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int bgIndex(Context c)     { return clamp(p(c).getInt("bg", 0), BG_NAMES.length); }
    public static int glassIndex(Context c)  { return clamp(p(c).getInt("glass", 1), GLASS_PCT.length); }
    public static int tileIndex(Context c)   { return clamp(p(c).getInt("tile", 1), TILE_NAMES.length); }
    public static int textIndex(Context c)   { return clamp(p(c).getInt("text", 0), TEXT_NAMES.length); }
    public static boolean sourcesVisible(Context c) { return p(c).getBoolean("sources", true); }

    /** The Carry on row above the shelf. */
    public static boolean showRecents(Context c) { return p(c).getBoolean("recents", true); }

    /**
     * Poster art on the Carry on cards. Off means Lumen makes no network requests at all -
     * the only ones it ever makes are for these images.
     */
    public static boolean posterArt(Context c) { return p(c).getBoolean("art", true); }
    public static void setPosterArt(Context c, boolean v) { p(c).edit().putBoolean("art", v).apply(); }

    /** Whether the one-time READ_TV_LISTINGS request has already been put to the person. */
    public static boolean listingsAsked(Context c) { return p(c).getBoolean("askedtv", false); }
    public static void setListingsAsked(Context c, boolean v) { p(c).edit().putBoolean("askedtv", v).apply(); }

    /** The first-boot notice is shown once, then reachable from Settings. */
    public static boolean firstBootDone(Context c) { return p(c).getBoolean("firstboot", false); }
    public static void setFirstBootDone(Context c, boolean v) { p(c).edit().putBoolean("firstboot", v).apply(); }

    public static boolean alwaysShowNames(Context c) { return p(c).getBoolean("names", true); }
    public static boolean reduceMotion(Context c)    { return p(c).getBoolean("reduce", false); }
    public static boolean highContrast(Context c)    { return p(c).getBoolean("contrast", false); }

    // Direct setters. The settings screen now offers a list of values rather than cycling,
    // so it needs to set an index outright instead of stepping to the next one.
    public static void setBg(Context c, int i)       { p(c).edit().putInt("bg", clamp(i, BG_NAMES.length)).apply(); }
    public static void setGlass(Context c, int i)    { p(c).edit().putInt("glass", clamp(i, GLASS_PCT.length)).apply(); }
    public static void setTile(Context c, int i)     { p(c).edit().putInt("tile", clamp(i, TILE_NAMES.length)).apply(); }
    public static void setText(Context c, int i)     { p(c).edit().putInt("text", clamp(i, TEXT_NAMES.length)).apply(); }
    public static void setSources(Context c, boolean v) { p(c).edit().putBoolean("sources", v).apply(); }
    public static void setRecents(Context c, boolean v) { p(c).edit().putBoolean("recents", v).apply(); }
    public static void setNames(Context c, boolean v)   { p(c).edit().putBoolean("names", v).apply(); }
    public static void setReduce(Context c, boolean v)  { p(c).edit().putBoolean("reduce", v).apply(); }
    public static void setContrast(Context c, boolean v){ p(c).edit().putBoolean("contrast", v).apply(); }

    public static float textMult(Context c) { return TEXT_MULT[textIndex(c)]; }
    public static String textName(Context c) { return TEXT_NAMES[textIndex(c)]; }

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
        if (sys <= 0.01f) return 0;
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

    public static String bgName(Context c)    { return BG_NAMES[bgIndex(c)]; }
    public static int    bgTint(Context c)    { return BG_TINTS[bgIndex(c)]; }
    public static int    glassPct(Context c)  { return GLASS_PCT[glassIndex(c)]; }
    public static float  glassAlpha(Context c){ return glassPct(c) / 100f; }
    public static int    tileWidth(Context c) { return TILE_W[tileIndex(c)]; }
    public static int    tileHeight(Context c){ return Math.round(tileWidth(c) * 9f / 16f); }
    public static String tileName(Context c)  { return TILE_NAMES[tileIndex(c)]; }

    // ------------------------------------------------------------- source names

    /**
     * "HDMI 2" is the socket's name. "PlayStation 5" is the user's. The port stays as the
     * subtitle so nobody loses track of which physical socket they are switching to.
     */
    public static String sourceName(Context c, String port) {
        return p(c).getString("src_" + port, "");
    }

    public static void setSourceName(Context c, String port, String name) {
        if (name == null) name = "";
        p(c).edit().putString("src_" + port, name.trim()).apply();
    }

    // ------------------------------------------------------------- hidden apps

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

    // ------------------------------------------------------------- shelf order

    /**
     * The user's own ordering of the shelf, as a package list. Anything installed but not
     * named here follows in the default order, so a new app appears rather than vanishing.
     */
    public static java.util.List<String> order(Context c) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        String raw = p(c).getString("order", "");
        if (raw == null || raw.length() == 0) return out;
        for (String s : raw.split(";")) if (s.length() > 0) out.add(s);
        return out;
    }

    public static void setOrder(Context c, java.util.List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String s : pkgs) { if (sb.length() > 0) sb.append(';'); sb.append(s); }
        p(c).edit().putString("order", sb.toString()).apply();
    }

    /**
     * Remembered labels. When a pinned app is uninstalled its name is gone from PackageManager,
     * so the outline left in its place would have nothing to say. We record the label the last
     * time we saw the app installed.
     */
    public static void rememberLabel(Context c, String pkg, String label) {
        if (pkg == null || label == null || label.length() == 0) return;
        if (label.equals(p(c).getString("lab_" + pkg, ""))) return;
        p(c).edit().putString("lab_" + pkg, label).apply();
    }

    public static String rememberedLabel(Context c, String pkg) {
        String s = p(c).getString("lab_" + pkg, "");
        return s.length() > 0 ? s : pkg;
    }

    // ------------------------------------------------------------- export / import

    /**
     * Written to the app's own external files directory, which needs no permission on API 30.
     * Plain key=value so it can be read, edited and copied to another television by hand.
     */
    public static File exportFile(Context c) {
        File dir = c.getExternalFilesDir(null);
        if (dir == null) dir = c.getFilesDir();
        return new File(dir, "lumen-settings.txt");
    }

    public static String exportSettings(Context c) {
        try {
            File f = exportFile(c);
            FileWriter w = new FileWriter(f);
            for (Map.Entry<String, ?> e : p(c).getAll().entrySet()) {
                Object v = e.getValue();
                if (v instanceof Set) {
                    StringBuilder sb = new StringBuilder();
                    for (Object o : (Set<?>) v) { if (sb.length() > 0) sb.append(','); sb.append(o); }
                    w.write("set:" + e.getKey() + "=" + sb + "\n");
                } else if (v instanceof Boolean) {
                    w.write("bool:" + e.getKey() + "=" + v + "\n");
                } else if (v instanceof Integer) {
                    w.write("int:" + e.getKey() + "=" + v + "\n");
                } else {
                    w.write("str:" + e.getKey() + "=" + v + "\n");
                }
            }
            w.close();
            return f.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean importSettings(Context c) {
        File f = exportFile(c);
        if (!f.exists()) return false;
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));
            SharedPreferences.Editor e = p(c).edit();
            String line;
            while ((line = r.readLine()) != null) {
                int colon = line.indexOf(':'), eq = line.indexOf('=');
                if (colon < 0 || eq < colon) continue;
                String type = line.substring(0, colon);
                String key = line.substring(colon + 1, eq);
                String val = line.substring(eq + 1);
                if (type.equals("bool")) e.putBoolean(key, Boolean.parseBoolean(val));
                else if (type.equals("int")) {
                    try { e.putInt(key, Integer.parseInt(val)); } catch (NumberFormatException ignored) { }
                } else if (type.equals("set")) {
                    Set<String> s = new HashSet<String>();
                    for (String x : val.split(",")) if (x.length() > 0) s.add(x);
                    e.putStringSet(key, s);
                } else e.putString(key, val);
            }
            r.close();
            e.apply();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int clamp(int v, int len) { return (v < 0 || v >= len) ? 0 : v; }

    private Prefs() {}
}
