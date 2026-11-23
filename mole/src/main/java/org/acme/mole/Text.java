package org.acme.mole;

import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

/** Sizing and placing text in a window. */
final class Text {

    private static final int MIN_POINTS = 6;

    private Text() {
    }

    /**
     * The largest system font, in whole points, at which the text fits within the given size, or the smallest size
     * tried if nothing fits. The caller disposes of the font.
     */
    static Font fit(Display display, GC gc, String text, int maxWidth, int maxHeight, int style) {
        FontData data = display.getSystemFont().getFontData()[0];
        data.setStyle(style);
        // Text grows with its point size, so binary search for the largest size that fits
        int low = MIN_POINTS;
        int high = Math.max(maxHeight, MIN_POINTS);
        while (low < high) {
            int points = (low + high + 1) / 2;
            if (fits(display, gc, data, points, text, maxWidth, maxHeight)) {
                low = points;
            } else {
                high = points - 1;
            }
        }
        data.setHeight(low);
        return new Font(display, data);
    }

    private static boolean fits(Display display, GC gc, FontData data, int points, String text, int maxWidth,
            int maxHeight) {
        data.setHeight(points);
        Font font = new Font(display, data);
        try {
            gc.setFont(font);
            Point extent = gc.textExtent(text);
            return extent.x <= maxWidth && extent.y <= maxHeight;
        } finally {
            gc.setFont(null);
            font.dispose();
        }
    }

    /** Draws the text horizontally centred at the given height, and returns the height below it. */
    static int drawCentred(GC gc, String text, Font font, Rectangle area, int y) {
        gc.setFont(font);
        Point extent = gc.textExtent(text);
        gc.drawText(text, area.x + (area.width - extent.x) / 2, y, true);
        return y + extent.y;
    }
}
