package com.ghidi.lumen;

import android.content.Context;
import android.graphics.Rect;
import android.widget.HorizontalScrollView;

/**
 * Keeps a gutter around the focused child.
 *
 * The stock behaviour scrolls a focused child flush to the edge of the viewport. Because a
 * focused tile is scaled to 1.09 and carries a 4px ring, "flush" means the first tile is cut
 * off on the left and the last one is cut off on the right. Inflating the rectangle before
 * the scroll is computed keeps the whole focused tile, ring included, inside the viewport.
 */
public class ShelfScrollView extends HorizontalScrollView {

    private int gutter = 0;

    public ShelfScrollView(Context c) { super(c); }

    public void setGutter(int px) { gutter = px; }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        Rect r = new Rect(rect);
        r.left -= gutter;
        r.right += gutter;
        return super.computeScrollDeltaToGetChildRectOnScreen(r);
    }
}
