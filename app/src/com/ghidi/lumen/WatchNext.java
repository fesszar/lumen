package com.ghidi.lumen;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What you were actually watching, per app.
 *
 * Android keeps this in TvProvider's watch_next_program table: title, episode, how far in you
 * got, artwork, and an intent that resumes the exact thing. Reading it needs only
 * READ_TV_LISTINGS, which is a normal runtime permission - NOT the privileged
 * ACCESS_ALL_EPG_DATA I originally assumed. Measured on this television: 84 rows across seven
 * apps, Netflix included.
 *
 * The trap that cost a day: "adb shell content query" returns nothing here, because the shell
 * package owns no rows and cannot be granted the permission. An empty result from the shell
 * says nothing at all about what an app can see.
 */
public final class WatchNext {

    /** watch_next_type values, from TvContract.WatchNextPrograms. */
    private static final int TYPE_CONTINUE = 0;
    private static final int TYPE_NEXT     = 1;

    public static class Item {
        public String pkg;
        public String title = "";
        public String episodeTitle = "";
        public String season = "";
        public String episode = "";
        public String artUri = "";
        public String intentUri = "";
        public long positionMs = 0;
        public long durationMs = 0;
        public long engagedAt = 0;
        public int type = TYPE_CONTINUE;

        /** "24 minutes left", or the episode number when the app only offers "next". */
        public String status() {
            if (type == TYPE_CONTINUE && durationMs > 0 && positionMs > 0) {
                long leftMs = durationMs - positionMs;
                if (leftMs < 60000) return "Nearly finished";
                int mins = (int) Math.round(leftMs / 60000.0);
                if (mins < 60) return mins + " minutes left";
                int h = mins / 60, m = mins % 60;
                return m == 0 ? h + (h == 1 ? " hour left" : " hours left")
                              : h + "h " + m + "m left";
            }
            if (type == TYPE_NEXT) return "Next episode";
            if (durationMs > 0) {
                int mins = (int) Math.round(durationMs / 60000.0);
                return mins + " minutes";
            }
            return "";
        }

        /**
         * The line under the title, on screen: "S4 E22  ·  So Long, and Thanks for All the
         * Red Snapper". Short season and episode markers, because "Season 4, Episode 22"
         * spends twenty characters saying what six say, and what it pushes off the end of
         * the line is the episode's name - the part that tells you which one it is.
         */
        public String subtitle() {
            return sub(false);
        }

        /** The same line, said in full, for the screen reader. */
        public String subtitleSpoken() {
            return sub(true);
        }

        private String sub(boolean spoken) {
            StringBuilder sb = new StringBuilder();
            if (season.length() > 0 && episode.length() > 0) {
                sb.append(spoken ? "Season " + season + ", Episode " + episode
                                 : "S" + season + " E" + episode);
            } else if (episode.length() > 0) {
                sb.append(spoken ? "Episode " + episode : "E" + episode);
            }
            if (episodeTitle.length() > 0) {
                if (sb.length() > 0) sb.append(spoken ? ". " : "  \u00b7  ");
                sb.append(episodeTitle);
            }
            return sb.toString();
        }

        public float progress() {
            if (durationMs <= 0) return 0f;
            return Math.max(0f, Math.min(1f, positionMs / (float) durationMs));
        }
    }

    /**
     * The most recent row for each app, newest app first, at most {@code max} of them.
     * One card per app - the point is "where was I in each of these", not a list of episodes.
     */
    public static List<Item> latestPerApp(Context c, int max) {
        Map<String, Item> best = new LinkedHashMap<String, Item>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(
                    Uri.parse("content://android.media.tv/watch_next_program"),
                    null, null, null, null);
            if (cur == null) return new ArrayList<Item>();

            int iPkg = cur.getColumnIndex("package_name");
            int iTitle = cur.getColumnIndex("title");
            int iEpTitle = cur.getColumnIndex("episode_title");
            int iSeason = cur.getColumnIndex("season_display_number");
            int iEpisode = cur.getColumnIndex("episode_display_number");
            int iArt = cur.getColumnIndex("poster_art_uri");
            int iThumb = cur.getColumnIndex("thumbnail_uri");
            int iIntent = cur.getColumnIndex("intent_uri");
            int iPos = cur.getColumnIndex("last_playback_position_millis");
            int iDur = cur.getColumnIndex("duration_millis");
            int iEng = cur.getColumnIndex("last_engagement_time_utc_millis");
            int iType = cur.getColumnIndex("watch_next_type");

            while (cur.moveToNext()) {
                Item it = new Item();
                it.pkg = str(cur, iPkg);
                if (it.pkg.length() == 0) continue;
                it.title = str(cur, iTitle);
                if (it.title.length() == 0) continue;
                it.episodeTitle = str(cur, iEpTitle);
                it.season = str(cur, iSeason);
                it.episode = str(cur, iEpisode);
                it.artUri = str(cur, iArt);
                if (it.artUri.length() == 0) it.artUri = str(cur, iThumb);
                it.intentUri = str(cur, iIntent);
                it.positionMs = num(cur, iPos);
                it.durationMs = num(cur, iDur);
                it.engagedAt = num(cur, iEng);
                it.type = (int) num(cur, iType);

                Item cur2 = best.get(it.pkg);
                if (cur2 == null || better(it, cur2)) best.put(it.pkg, it);
            }
        } catch (Throwable ignored) {
            return new ArrayList<Item>();
        } finally {
            if (cur != null) try { cur.close(); } catch (Throwable ignored) { }
        }

        List<Item> out = new ArrayList<Item>(best.values());
        Collections.sort(out, new Comparator<Item>() {
            public int compare(Item a, Item b) {
                return a.engagedAt == b.engagedAt ? 0 : (a.engagedAt > b.engagedAt ? -1 : 1);
            }
        });
        while (out.size() > max) out.remove(out.size() - 1);
        return out;
    }

    /**
     * Something you are part-way through beats something merely queued up, even if the queued
     * one was touched more recently - "carry on" means carry on.
     */
    private static boolean better(Item candidate, Item incumbent) {
        boolean cResume = candidate.type == TYPE_CONTINUE && candidate.positionMs > 0;
        boolean iResume = incumbent.type == TYPE_CONTINUE && incumbent.positionMs > 0;
        if (cResume != iResume) return cResume;
        return candidate.engagedAt > incumbent.engagedAt;
    }

    private static String str(Cursor c, int i) {
        if (i < 0) return "";
        try { String s = c.getString(i); return s == null ? "" : s; }
        catch (Throwable t) { return ""; }
    }

    private static long num(Cursor c, int i) {
        if (i < 0) return 0;
        try { return c.getLong(i); } catch (Throwable t) { return 0; }
    }

    public static boolean permitted(Context c) {
        try {
            return c.checkSelfPermission("android.permission.READ_TV_LISTINGS")
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return false; }
    }

    private WatchNext() {}
}
