package com.ghidi.lumen;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * The only thing in Lumen that runs when Lumen is not on screen.
 *
 * A launcher receives key events only while it is the focused window, and the entire point
 * of the branded keys is that you press them from inside Netflix. Filtering key events
 * needs an accessibility service; there is no other route without root.
 *
 * It is deliberately almost nothing: it reads no window content, listens for no events, and
 * consumes only the handful of key codes the person has explicitly mapped. Every other key,
 * including every key on every other remote, passes straight through untouched.
 */
public class RemoteService extends AccessibilityService {

    private static final String TAG = "LUMEN";

    /** Whether the person has switched this on in the television's accessibility settings. */
    public static boolean enabled(android.content.Context c) {
        try {
            String on = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(on)) return false;
            String me = c.getPackageName() + "/" + RemoteService.class.getName();
            String meShort = c.getPackageName() + "/." + RemoteService.class.getSimpleName();
            for (String part : on.split(":")) {
                if (part.equalsIgnoreCase(me) || part.equalsIgnoreCase(meShort)) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event == null) return false;
        int code = event.getKeyCode();
        if (RemoteMap.reserved(code)) return false;

        RemoteMap.Binding b = RemoteMap.get(this, code);
        if (b == null) return false;          // not ours - hands off

        // Act once, on the first press down; swallow the matching up so the app underneath
        // never sees half a key.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            Intent go = RemoteMap.intentFor(this, b.target);
            if (go == null) {
                Log.i(TAG, "remote key " + code + " maps to " + b.target + ", which is gone");
                return true;
            }
            try { startActivity(go); }
            catch (Throwable t) { Log.i(TAG, "remote key " + code + " could not start: " + t); }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { /* nothing is read */ }

    @Override
    public void onInterrupt() { }
}
