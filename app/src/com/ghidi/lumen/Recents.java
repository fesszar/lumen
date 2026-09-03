package com.ghidi.lumen;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * The Carry on row.
 *
 * This is deliberately NOT a resume list. Per-title resume state lives in TvProvider's
 * watch_next table, which is gated behind com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA.
 * That permission's protectionLevel on this television is signature|privileged, so a sideloaded
 * launcher can never hold it and would read an empty table forever. MediaSession is no rescue
 * either: the sessions that survive a launch carry metadata: null.
 *
 * What the launcher legitimately knows is which apps it launched and when, because it did the
 * launching. That is all this records.
 */
public final class Recents {

    private static final String FILE = "lumen";
    // NOT "recents": Prefs stores the on/off switch for this row under that key as a boolean,
    // and both live in the same SharedPreferences file. Reading one as the other throws
    // ClassCastException and takes the launcher down on every press.
    private static final String KEY = "recent_list";
    private static final int MAX = 6;
    public static final int SHOWN = 3;

    public static class Item {
        public final String pkg;
        public final long at;
        Item(String pkg, long at) { this.pkg = pkg; this.at = at; }
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Called the moment a launch intent is fired, not when the app reports back. */
    public static void record(Context c, String pkg) {
        if (pkg == null || pkg.length() == 0) return;
        List<Item> items = all(c);
        List<Item> out = new ArrayList<Item>();
        out.add(new Item(pkg, System.currentTimeMillis()));
        for (Item i : items) {
            if (i.pkg.equals(pkg)) continue;
            if (out.size() >= MAX) break;
            out.add(i);
        }
        StringBuilder sb = new StringBuilder();
        for (Item i : out) {
            if (sb.length() > 0) sb.append(';');
            sb.append(i.pkg).append('|').append(i.at);
        }
        p(c).edit().putString(KEY, sb.toString()).apply();
    }

    public static List<Item> all(Context c) {
        List<Item> out = new ArrayList<Item>();
        String raw = p(c).getString(KEY, "");
        if (raw == null || raw.length() == 0) return out;
        for (String part : raw.split(";")) {
            int bar = part.lastIndexOf('|');
            if (bar <= 0) continue;
            try {
                out.add(new Item(part.substring(0, bar), Long.parseLong(part.substring(bar + 1))));
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    public static void clear(Context c) { p(c).edit().remove(KEY).apply(); }

    /**
     * "Just now" / "2 hours ago" / "Yesterday, 21:40" / "Mon, 19:05".
     * Day boundaries come from the calendar, not from subtracting 24 hours - something watched
     * at 00:30 was watched today, not yesterday.
     */
    public static String when(long at) {
        long now = System.currentTimeMillis();
        long diff = now - at;
        if (diff < 0) return "Just now";
        if (diff < 60L * 1000L) return "Just now";
        if (diff < 60L * 60L * 1000L) {
            int m = (int) (diff / (60L * 1000L));
            return m + (m == 1 ? " minute ago" : " minutes ago");
        }

        Calendar then = Calendar.getInstance(); then.setTimeInMillis(at);
        Calendar today = Calendar.getInstance(); today.setTimeInMillis(now);
        boolean sameDay = then.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        if (sameDay) {
            int h = (int) (diff / (60L * 60L * 1000L));
            return h + (h == 1 ? " hour ago" : " hours ago");
        }

        Calendar yesterday = Calendar.getInstance();
        yesterday.setTimeInMillis(now);
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        boolean wasYesterday = then.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR)
                && then.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR);
        String clock = String.format("%02d:%02d",
                then.get(Calendar.HOUR_OF_DAY), then.get(Calendar.MINUTE));
        if (wasYesterday) return "Yesterday, " + clock;

        String[] days = { "", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
        int dow = then.get(Calendar.DAY_OF_WEEK);
        String name = (dow >= 1 && dow <= 7) ? days[dow] : "";
        if (diff < 7L * 24L * 60L * 60L * 1000L && name.length() > 0) return name + ", " + clock;

        return (then.get(Calendar.MONTH) + 1) + "/" + then.get(Calendar.DAY_OF_MONTH);
    }

    private Recents() {}
}
