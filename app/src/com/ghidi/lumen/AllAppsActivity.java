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
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class AllAppsActivity extends Activity {

    private static final int COLS = 8;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        final Context c = this;
        List<AppEntry> apps = AppEntry.loadAll(c);

        FrameLayout rootFrame = new FrameLayout(c);
        View ground = new View(c);
        ground.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ground.setBackground(Ui.ground(c, Color.parseColor("#6F61A1")));
        rootFrame.addView(ground);

        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.px(c, 96), Ui.px(c, 54), Ui.px(c, 96), Ui.px(c, 54));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        rootFrame.addView(content);

        TextView kicker = new TextView(c);
        kicker.setText("ALL APPS");
        kicker.setLetterSpacing(0.16f);
        kicker.setTextColor(Ui.alphaWhite(Ui.TEXT_TERTIARY));
        kicker.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 21));
        content.addView(kicker);

        TextView title = new TextView(c);
        title.setText(apps.size() + " installed");
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 40));
        title.setPadding(0, Ui.px(c, 8), 0, Ui.px(c, 30));
        content.addView(title);

        FrameLayout panel = new FrameLayout(c);
        panel.setBackground(Ui.glass(c, Prefs.effectiveGlassAlpha(c), 40f, Prefs.highContrast(c)));
        int pp = Ui.px(c, 40);
        panel.setPadding(pp, pp, pp, pp);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        content.addView(panel);

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFocusable(false);
        sv.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        sv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        panel.addView(sv);

        GridLayout grid = new GridLayout(c);
        grid.setColumnCount(COLS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        sv.addView(grid);

        // width available inside the panel, split into COLS columns with a 24 gap
        int inner = Math.round(1920 - 2 * 96 - 2 * 40);
        int gap = 24;
        final int cellW = Math.round((inner - (COLS - 1) * gap) / (float) COLS);
        final int cellH = Math.round(cellW * 9f / 16f);

        for (int i = 0; i < apps.size(); i++) {
            final AppEntry app = apps.get(i);

            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setClipChildren(false);
            cell.setClipToPadding(false);

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = Ui.px(c, cellW);
            glp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            glp.rightMargin = (i % COLS == COLS - 1) ? 0 : Ui.px(c, gap);
            glp.bottomMargin = Ui.px(c, gap);
            cell.setLayoutParams(glp);

            final Tile t = new Tile(c, app, 16f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    Ui.px(c, cellW), Ui.px(c, cellH));
            t.setLayoutParams(tlp);

            final TextView name = new TextView(c);
            name.setText(app.label);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
            name.setShadowLayer(Ui.px(c, 8), 0f, Ui.px(c, 1), Color.argb(140, 0, 0, 0));
            name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(c, 19));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = Ui.px(c, 10);
            name.setLayoutParams(nlp);

            t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                public void onFocusChange(View v, boolean has) {
                    t.applyFocus(has);
                    name.setTextColor(Ui.alphaWhite(Ui.TEXT_PRIMARY));
                }
            });
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Intent i2 = app.launchIntent(AllAppsActivity.this);
                    if (i2 != null) startActivity(i2);
                    else Toast.makeText(AllAppsActivity.this,
                            "Cannot open " + app.label, Toast.LENGTH_SHORT).show();
                }
            });

            cell.addView(t);
            cell.addView(name);
            grid.addView(cell);

            if (i == 0) t.requestFocus();
        }

        setContentView(rootFrame);
    }
}
