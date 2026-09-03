package com.ghidi.lumen;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One installed app that can appear on a TV home screen. */
public class AppEntry {

    public final String pkg;
    public final String label;
    public final Drawable art;      // banner if the app has one, else its icon
    public final boolean hasBanner;
    public final int tint;          // dominant colour, used for the ground bloom

    private AppEntry(String pkg, String label, Drawable art, boolean hasBanner, int tint) {
        this.pkg = pkg;
        this.label = label;
        this.art = art;
        this.hasBanner = hasBanner;
        this.tint = tint;
    }

    /** A pinned app that is no longer installed. Draws as an outline, not as a gap. */
    public static AppEntry placeholder(String pkg, String label) {
        return new AppEntry(pkg, label, null, false, Color.parseColor("#2A2F39"));
    }

    /**
     * The shelf is ordered, not alphabetical. These come first, in this order -
     * the things actually opened, rather than whatever sorts to the top.
     */
    private static final String[] PINNED = {
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.disney.disneyplus",
            "com.wbd.stream",
            "com.apple.atve.androidtv.appletv",
            "com.crunchyroll.crunchyroid",
            "com.skyshowtime.skyshowtime.google",
            "com.spotify.tv.android",
            "com.smarterspro.smarterprotv",
            "com.smarterspro.smartersprotv",
            "com.amazon.amazonvideo.livingroom",
            "ru.iptvremote.android.iptv"
    };

    private static int pinRank(String pkg) {
        for (int i = 0; i < PINNED.length; i++) if (PINNED[i].equals(pkg)) return i;
        return Integer.MAX_VALUE;
    }

    /**
     * The shelf: everything, minus what the user hid in Settings, in the user's own order
     * where they have set one. Anything installed but not named in that order follows after,
     * so a newly installed app appears at the end rather than not appearing at all.
     */
    public static List<AppEntry> load(Context c) {
        List<AppEntry> all = loadAll(c);
        List<AppEntry> visible = new ArrayList<AppEntry>();
        for (AppEntry a : all) {
            Prefs.rememberLabel(c, a.pkg, a.label);
            if (!Prefs.isHidden(c, a.pkg)) visible.add(a);
        }

        List<String> order = Prefs.order(c);
        if (order.isEmpty()) return visible;

        Map<String, AppEntry> byPkg = new LinkedHashMap<String, AppEntry>();
        for (AppEntry a : visible) byPkg.put(a.pkg, a);

        List<AppEntry> out = new ArrayList<AppEntry>();
        for (String pkg : order) {
            AppEntry a = byPkg.remove(pkg);
            if (a != null) out.add(a);
            else if (!Prefs.isHidden(c, pkg)) out.add(placeholder(pkg, Prefs.rememberedLabel(c, pkg)));
        }
        out.addAll(byPkg.values());
        return out;
    }

    /** Pinned apps first, then apps with TV banner art, then the rest by name. */
    public static List<AppEntry> loadAll(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, AppEntry> out = new LinkedHashMap<String, AppEntry>();

        collect(c, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, out);
        collect(c, pm, Intent.CATEGORY_LAUNCHER, out);

        List<AppEntry> list = new ArrayList<AppEntry>(out.values());
        Collections.sort(list, new Comparator<AppEntry>() {
            public int compare(AppEntry a, AppEntry b) {
                int ra = pinRank(a.pkg), rb = pinRank(b.pkg);
                if (ra != rb) return ra < rb ? -1 : 1;
                if (a.hasBanner != b.hasBanner) return a.hasBanner ? -1 : 1;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return list;
    }

    private static void collect(Context c, PackageManager pm, String category,
                                Map<String, AppEntry> out) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(category);
        List<ResolveInfo> found = pm.queryIntentActivities(i, 0);
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName())) continue;      // never list ourselves
            if (out.containsKey(pkg)) continue;

            String label = String.valueOf(ri.loadLabel(pm));

            Drawable art = null;
            boolean banner = false;
            try {
                art = ri.activityInfo.loadBanner(pm);
                if (art == null) {
                    ApplicationInfo ai = ri.activityInfo.applicationInfo;
                    art = ai.loadBanner(pm);
                }
                banner = art != null;
            } catch (Throwable ignored) { }

            if (art == null) {
                try { art = ri.loadIcon(pm); } catch (Throwable ignored) { }
            }

            out.put(pkg, new AppEntry(pkg, label, art, banner, dominant(art)));
        }
    }

    /** Cheap dominant colour: sample the art, average it, then push it toward a usable tint. */
    private static int dominant(Drawable d) {
        int fallback = Color.parseColor("#5F7099");
        if (!(d instanceof BitmapDrawable)) return fallback;
        Bitmap b = ((BitmapDrawable) d).getBitmap();
        if (b == null || b.getWidth() == 0 || b.getHeight() == 0) return fallback;

        long r = 0, g = 0, bl = 0;
        int n = 0;
        int stepX = Math.max(1, b.getWidth() / 16);
        int stepY = Math.max(1, b.getHeight() / 16);
        for (int x = 0; x < b.getWidth(); x += stepX) {
            for (int y = 0; y < b.getHeight(); y += stepY) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                r += Color.red(p); g += Color.green(p); bl += Color.blue(p);
                n++;
            }
        }
        if (n == 0) return fallback;

        float[] hsv = new float[3];
        Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (bl / n), hsv);
        hsv[1] = Math.min(0.45f, Math.max(0.18f, hsv[1]));   // keep it a wash, not a poster
        hsv[2] = Math.min(0.62f, Math.max(0.34f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    public Intent launchIntent(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }
}
