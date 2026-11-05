package com.ghidi.lumen;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.tv.TvContract;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What each key on the remote should open.
 *
 * TCL wires the branded keys - Netflix, Prime Video and the rest - to their apps through a
 * service that came with advertising attached. With that service off the keys still emit
 * their key codes; nothing is listening for them. This maps a key code to anything on the
 * television, and {@link RemoteService} does the listening.
 *
 * Stored as one string per key under "key_<code>", so a mapping is a line in the same
 * preferences file as everything else and leaves with the app.
 */
public final class RemoteMap {

    private static final String FILE = "lumen";
    private static final String PREFIX = "key_";

    /** Keys the television needs to keep. Mapping these would make the remote unusable. */
    private static final int[] RESERVED = {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_TV_INPUT,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_ASSIST
    };

    public static class Binding {
        public final int code;
        public final String target;   // "app:<pkg>" | "input:<id>" | "home"
        public final String label;    // what to show
        public Binding(int code, String target, String label) {
            this.code = code; this.target = target; this.label = label;
        }
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean reserved(int code) {
        for (int r : RESERVED) if (r == code) return true;
        return false;
    }

    public static void put(Context c, int code, String target, String label) {
        p(c).edit().putString(PREFIX + code, target + "\u0001" + label).apply();
    }

    public static void remove(Context c, int code) {
        p(c).edit().remove(PREFIX + code).apply();
    }

    public static Binding get(Context c, int code) {
        String v = p(c).getString(PREFIX + code, null);
        if (v == null) return null;
        int i = v.indexOf('\u0001');
        if (i < 0) return new Binding(code, v, v);
        return new Binding(code, v.substring(0, i), v.substring(i + 1));
    }

    public static List<Binding> all(Context c) {
        List<Binding> out = new ArrayList<Binding>();
        for (Map.Entry<String, ?> e : p(c).getAll().entrySet()) {
            if (!e.getKey().startsWith(PREFIX)) continue;
            try {
                int code = Integer.parseInt(e.getKey().substring(PREFIX.length()));
                Binding b = get(c, code);
                if (b != null) out.add(b);
            } catch (Throwable ignored) { }
        }
        return out;
    }

    /**
     * A name a person would recognise. KeyEvent.keyCodeToString gives "KEYCODE_BUTTON_4",
     * which is true and useless; the branded keys on a TV remote almost always arrive as
     * one of these, so they get the name that is printed on the button where we can guess
     * it, and the raw code where we cannot.
     */
    public static String keyName(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_1: return "App button 1";
            case KeyEvent.KEYCODE_BUTTON_2: return "App button 2";
            case KeyEvent.KEYCODE_BUTTON_3: return "App button 3";
            case KeyEvent.KEYCODE_BUTTON_4: return "App button 4";
            case KeyEvent.KEYCODE_PROG_RED: return "Red button";
            case KeyEvent.KEYCODE_PROG_GREEN: return "Green button";
            case KeyEvent.KEYCODE_PROG_YELLOW: return "Yellow button";
            case KeyEvent.KEYCODE_PROG_BLUE: return "Blue button";
            case KeyEvent.KEYCODE_GUIDE: return "Guide button";
            case KeyEvent.KEYCODE_INFO: return "Info button";
            case KeyEvent.KEYCODE_BOOKMARK: return "Bookmark button";
            case KeyEvent.KEYCODE_TV: return "TV button";
            default: break;
        }
        String s = KeyEvent.keyCodeToString(code);
        if (s != null && s.startsWith("KEYCODE_")) {
            s = s.substring(8).replace('_', ' ').toLowerCase();
            if (s.length() > 0) s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
            return s + " button";
        }
        return "Key " + code;
    }

    /** Turns a stored target back into something startable. Null if it has gone away. */
    public static Intent intentFor(Context c, String target) {
        if (target == null) return null;
        if (target.equals("home")) {
            Intent i = new Intent(c, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            return i;
        }
        if (target.startsWith("app:")) {
            String pkg = target.substring(4);
            Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return i;
        }
        if (target.startsWith("input:")) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW,
                        TvContract.buildChannelUriForPassthroughInput(target.substring(6)));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return i;
            } catch (Throwable ignored) { return null; }
        }
        return null;
    }

    private RemoteMap() {}
}
