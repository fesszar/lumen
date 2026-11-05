package com.ghidi.lumen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Poster art for the Carry on row.
 *
 * This is the only part of Lumen that touches the network, and it is the only reason the app
 * asks for INTERNET at all. It fetches nothing but the artwork URLs the television's own
 * watch-next database already holds, it sends nothing, and turning "Poster art" off in
 * Settings means not a single request is made.
 *
 * No image library: a URL, a stream, a decode, a file. Cached on disk by URL hash so a poster
 * is fetched once and survives a reboot.
 */
public final class ArtCache {

    private static final int MEM_MAX = 12;
    /**
     * Backdrops are decoded at panel width, so each one is a few megabytes against this
     * television's 2 GB. Two: the one on screen and the one you are about to move onto.
     * They get their own map so a backdrop never evicts the twelve small posters.
     */
    private static final int BACK_MAX = 2;
    private static final int DISK_MAX_FILES = 40;
    private static final int TIMEOUT_MS = 8000;

    private static final Map<String, Bitmap> MEM =
            new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
                    return size() > MEM_MAX;
                }
            };

    private static final Map<String, Bitmap> BACK =
            new LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
                    return size() > BACK_MAX;
                }
            };

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Handler UI = new Handler(Looper.getMainLooper());

    public interface Ready { void onArt(Bitmap b); }

    /** A poster at card size. */
    public static void load(Context c, String url, int reqW, Ready cb) {
        fetch(c, url, reqW, cb, MEM, "c");
    }

    /**
     * The same picture at panel width, for the backdrop behind everything. Same file on
     * disk, decoded larger and held in its own small map.
     */
    public static void loadBackdrop(Context c, String url, int reqW, Ready cb) {
        fetch(c, url, reqW, cb, BACK, "b");
    }

    private static void fetch(final Context c, final String url, final int reqW,
                              final Ready cb, final Map<String, Bitmap> cache,
                              final String scope) {
        if (url == null || url.length() == 0 || !Prefs.posterArt(c)) return;
        final String file = key(url);
        final String ck = scope + reqW + ":" + file;

        synchronized (cache) {
            Bitmap hit = cache.get(ck);
            if (hit != null && !hit.isRecycled()) { cb.onArt(hit); return; }
        }

        POOL.execute(new Runnable() {
            public void run() {
                Bitmap b = null;
                try {
                    File f = new File(dir(c), file);
                    if (!f.exists() || f.length() == 0) download(url, f);
                    if (f.exists() && f.length() > 0) b = decode(f, reqW);
                } catch (Throwable ignored) { }
                if (b == null) return;
                final Bitmap out = b;
                synchronized (cache) { cache.put(ck, out); }
                UI.post(new Runnable() { public void run() { cb.onArt(out); } });
            }
        });
    }

    private static void download(String url, File to) throws Exception {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream fos = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Lumen");
            if (conn.getResponseCode() != 200) return;
            in = conn.getInputStream();
            File tmp = new File(to.getAbsolutePath() + ".part");
            fos = new FileOutputStream(tmp);
            byte[] buf = new byte[16384];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 4 * 1024 * 1024) break;   // a poster, not a film
                fos.write(buf, 0, n);
            }
            fos.flush();
            fos.close();
            fos = null;
            if (!tmp.renameTo(to)) tmp.delete();
        } finally {
            if (fos != null) try { fos.close(); } catch (Throwable ignored) { }
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
            if (conn != null) conn.disconnect();
        }
    }

    /** Decode at roughly the size we will draw, so a 3840px poster is not held in memory. */
    private static Bitmap decode(File f, int reqW) {
        BitmapFactory.Options probe = new BitmapFactory.Options();
        probe.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), probe);
        int sample = 1;
        if (reqW > 0 && probe.outWidth > reqW) {
            while (probe.outWidth / (sample * 2) >= reqW) sample *= 2;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
    }

    private static File dir(Context c) {
        File d = new File(c.getCacheDir(), "art");
        if (!d.exists()) d.mkdirs();
        trim(d);
        return d;
    }

    private static void trim(File d) {
        File[] fs = d.listFiles();
        if (fs == null || fs.length <= DISK_MAX_FILES) return;
        java.util.Arrays.sort(fs, new java.util.Comparator<File>() {
            public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (int i = 0; i < fs.length - DISK_MAX_FILES; i++) fs[i].delete();
    }

    private static String key(String url) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return String.valueOf(url.hashCode());
        }
    }

    /** Called when the setting is turned off, so nothing lingers. */
    public static void clear(Context c) {
        synchronized (MEM) { MEM.clear(); }
        synchronized (BACK) { BACK.clear(); }
        File d = new File(c.getCacheDir(), "art");
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    private ArtCache() {}
}
