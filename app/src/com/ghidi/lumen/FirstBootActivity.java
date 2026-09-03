package com.ghidi.lumen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shown once, on the first boot after Lumen takes the home screen.
 *
 * The person who wakes up to a changed television is often not the person who changed it.
 * They cannot ask the launcher what happened, and a home screen that silently lost its rows
 * reads as a broken television. So: plain language, no jargon, and the way back given the
 * same weight as the way forward rather than buried in a menu.
 */
public class FirstBootActivity extends Activity {

    private View okButton, backButton;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        // Marked done the moment it is SHOWN, not when a button is pressed. Anything else
        // loops: Home re-launches this on every creation, this covers Home, and a press of
        // Back or HOME brings Home round again to re-launch it. "Shown once" has to mean
        // shown once.
        Prefs.setFirstBootDone(this, true);

        setContentView(build());
    }

    private View build() {
        final Context c = this;

        FrameLayout root = new FrameLayout(c);
        View ground = new View(c);
        ground.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ground.setBackground(Ui.ground(c, 0xFF4F8B68));
        ground.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(ground);

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                Ui.px(c, 1120), ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER;
        card.setLayoutParams(clp);
        card.setBackground(Ui.glass(c, Math.max(0.15f, Prefs.effectiveGlassAlpha(c)), 34f,
                Prefs.highContrast(c)));
        card.setPadding(Ui.px(c, 60), Ui.px(c, 56), Ui.px(c, 60), Ui.px(c, 48));
        card.setElevation(Ui.px(c, 30));
        root.addView(card);

        TextView kicker = new TextView(c);
        kicker.setText("YOUR HOME SCREEN HAS CHANGED");
        kicker.setLetterSpacing(0.16f);
        kicker.setTextColor(Ui.alphaWhite(0.60f));
        kicker.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        card.addView(kicker);

        TextView title = new TextView(c);
        title.setText("This TV now opens straight to your apps.");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 50));
        title.setLineSpacing(Ui.px(c, 6), 1f);
        title.setShadowLayer(Ui.px(c, 20), 0f, Ui.px(c, 2), Color.argb(110, 0, 0, 0));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.px(c, 18);
        title.setLayoutParams(tlp);
        card.addView(title);

        TextView body = new TextView(c);
        body.setText("Someone in your house replaced the old home screen with this one. "
                + "Nothing was deleted — every app you had is still installed, and the rows "
                + "of suggested films are simply not shown any more.");
        body.setTextColor(Ui.alphaWhite(0.92f));
        body.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 25));
        body.setLineSpacing(Ui.px(c, 10), 1f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.px(c, 20);
        body.setLayoutParams(blp);
        card.addView(body);

        LinearLayout three = new LinearLayout(c);
        three.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams thlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        thlp.topMargin = Ui.px(c, 34);
        three.setLayoutParams(thlp);
        three.addView(mini(c, "Where your apps went",
                "The ones you use are on the shelf. The rest are behind All apps, at the "
                + "right-hand end.", false));
        three.addView(mini(c, "Where HDMI went",
                "Press down from the shelf. Your ports are listed there, and you can name "
                + "them after what is plugged in.", true));
        three.addView(mini(c, "If you want the old one",
                "Settings, then This launcher, then Home screen owner. It comes straight "
                + "back.", true));
        card.addView(three);

        LinearLayout buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bulp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bulp.topMargin = Ui.px(c, 40);
        buttons.setLayoutParams(bulp);

        okButton = button(c, "Start using it", true);
        backButton = button(c, "Put the old home screen back", false);
        buttons.addView(okButton);
        buttons.addView(backButton);

        View grow = new View(c);
        grow.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        buttons.addView(grow);

        TextView once = new TextView(c);
        once.setText("Shown once. Settings has it again under This launcher.");
        once.setTextColor(Ui.alphaWhite(0.58f));
        once.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 19));
        once.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        buttons.addView(once);
        card.addView(buttons);

        okButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Prefs.setFirstBootDone(FirstBootActivity.this, true);
                finish();
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { explainHowToGoBack(); }
        });

        okButton.setId(View.generateViewId());
        backButton.setId(View.generateViewId());
        okButton.setNextFocusRightId(backButton.getId());
        okButton.setNextFocusLeftId(okButton.getId());
        backButton.setNextFocusLeftId(okButton.getId());
        backButton.setNextFocusRightId(backButton.getId());

        okButton.post(new Runnable() { public void run() { okButton.requestFocus(); } });
        return root;
    }

    private View mini(Context c, String head, String text, boolean spaced) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (spaced) lp.leftMargin = Ui.px(c, 18);
        box.setLayoutParams(lp);
        box.setBackground(Ui.roundRect(c, Ui.alphaWhite(0.10f), 22f, 1f, Ui.alphaWhite(0.22f)));
        box.setPadding(Ui.px(c, 26), Ui.px(c, 24), Ui.px(c, 26), Ui.px(c, 24));

        TextView h = new TextView(c);
        h.setText(head);
        h.setTextColor(Color.WHITE);
        h.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 23));
        box.addView(h);

        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(Ui.alphaWhite(0.88f));
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 20));
        t.setLineSpacing(Ui.px(c, 6), 1f);
        t.setPadding(0, Ui.px(c, 9), 0, 0);
        box.addView(t);
        return box;
    }

    private View button(final Context c, String label, final boolean primary) {
        final TextView b = new TextView(c);
        b.setText(label);
        b.setSingleLine(true);
        b.setFocusable(true);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 24));
        b.setPadding(Ui.px(c, 36), Ui.px(c, 17), Ui.px(c, 36), Ui.px(c, 17));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.px(c, 16);
        b.setLayoutParams(lp);
        paint(b, false, primary);
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean has) { paint(b, has, primary); }
        });
        return b;
    }

    private void paint(TextView b, boolean focused, boolean primary) {
        Context c = this;
        b.setTextColor(primary ? Ui.GROUND : Ui.alphaWhite(0.94f));
        b.setBackground(Ui.roundRect(c,
                primary ? Color.WHITE : Ui.alphaWhite(0.14f), 999f,
                1f, primary ? Color.WHITE : Ui.alphaWhite(0.32f)));
        b.setForeground(focused
                ? Ui.ring(c, 999f, Prefs.highContrast(c) ? 6f : 4f, Ui.alphaWhite(0.95f))
                : Ui.ring(c, 999f, 0f, Color.TRANSPARENT));
        int ms = Prefs.motionMs(c);
        b.animate().cancel();
        if (ms == 0) { b.setScaleX(focused ? 1.03f : 1f); b.setScaleY(focused ? 1.03f : 1f); }
        else b.animate().scaleX(focused ? 1.03f : 1f).scaleY(focused ? 1.03f : 1f)
                .setDuration(ms).setInterpolator(Ui.EASE).start();
    }

    /**
     * Lumen cannot re-enable a disabled system launcher on its own - that needs
     * CHANGE_COMPONENT_ENABLED_STATE, which is signature|privileged. Rather than pretend,
     * it opens the TV's own settings and says plainly what to do there.
     */
    private void explainHowToGoBack() {
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Putting the old home screen back")
                .setMessage("The Google home screen is disabled, not removed. Turning it back on "
                        + "has to be done from the TV's own settings, or from a computer with the "
                        + "one command in the Lumen guide:\n\n"
                        + "adb shell pm enable com.google.android.apps.tv.launcherx\n\n"
                        + "Nothing you have installed is affected either way.")
                .setPositiveButton("Open the TV settings",
                        new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                Intent s = getPackageManager()
                                        .getLaunchIntentForPackage("com.android.tv.settings");
                                if (s == null) s = new Intent(android.provider.Settings.ACTION_SETTINGS);
                                s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                try { startActivity(s); } catch (Throwable ignored) { }
                            }
                        })
                .setNegativeButton("Not now", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        Prefs.setFirstBootDone(this, true);
        finish();
    }
}
