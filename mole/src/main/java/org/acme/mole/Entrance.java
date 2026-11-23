package org.acme.mole;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Shell;

/**
 * A replacement mole's entrance: the background flashes and fades back to its usual colour, while rings ripple out
 * from the centre. It only animates a window that has already been shown, so it never delays the first paint.
 */
final class Entrance {

    private final EntranceConfig config;
    private final Shell shell;
    private final Color background;
    private final Color flashColour;
    private final Color rippleColour;
    private final long totalMillis;
    private long startNanos;
    private double elapsedMillis;

    Entrance(EntranceConfig config, Shell shell, Color background) {
        this.config = config;
        this.shell = shell;
        this.background = background;
        this.flashColour = parseColour(config.flash().colour());
        this.rippleColour = parseColour(config.ripples().colour());
        EntranceConfig.Ripples ripples = config.ripples();
        long rippleMillis = ripples.count() > 0 ? ripples.millis() + (long) (ripples.count() - 1) * ripples.intervalMillis() : 0;
        this.totalMillis = Math.max(config.flash().millis(), rippleMillis);
        this.elapsedMillis = totalMillis;
    }

    /** Starts the animation; call once the window is open. */
    void start() {
        if (totalMillis > 0) {
            startNanos = System.nanoTime();
            elapsedMillis = 0;
            frame();
        }
    }

    private void frame() {
        if (shell.isDisposed()) {
            return;
        }
        elapsedMillis = Math.min(totalMillis, (System.nanoTime() - startNanos) / 1e6);
        if (config.flash().millis() > 0) {
            double progress = Math.min(1, elapsedMillis / config.flash().millis());
            shell.setBackground(blend(flashColour, background, easeOut(progress)));
        }
        shell.redraw();
        if (elapsedMillis < totalMillis) {
            shell.getDisplay().timerExec(Math.max(1, config.frameMillis()), this::frame);
        }
    }

    /** Draws the rings that are spreading at the moment; call from the window's paint listener, before the contents. */
    void paint(GC gc, Rectangle area) {
        EntranceConfig.Ripples ripples = config.ripples();
        if (elapsedMillis >= totalMillis || ripples.count() <= 0 || ripples.millis() <= 0) {
            return;
        }
        int centreX = area.x + area.width / 2;
        int centreY = area.y + area.height / 2;
        double maxRadius = Math.hypot(area.width, area.height) / 2;
        double maxWidth = Math.min(area.width, area.height) * ripples.widthPercent() / 100.0;
        gc.setAntialias(SWT.ON);
        gc.setForeground(rippleColour);
        for (int ring = 0; ring < ripples.count(); ring++) {
            double t = (elapsedMillis - (double) ring * ripples.intervalMillis()) / ripples.millis();
            if (t <= 0 || t >= 1) {
                continue;
            }
            int radius = (int) (easeOut(t) * maxRadius);
            gc.setAlpha((int) (255 * (1 - t)));
            gc.setLineWidth(Math.max(1, (int) ((1 - t) * maxWidth)));
            gc.drawOval(centreX - radius, centreY - radius, radius * 2, radius * 2);
        }
        gc.setAlpha(255);
        gc.setLineWidth(1);
    }

    private static double easeOut(double progress) {
        return 1 - Math.pow(1 - progress, 3);
    }

    private static Color blend(Color from, Color to, double amount) {
        return new Color(
                (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
                (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
                (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }

    /** Parses a hex colour such as {@code #4695EB}; the {@code #} is optional. */
    static Color parseColour(String hex) {
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        if (!digits.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Colours are six hex digits, as in #4695EB, not " + hex);
        }
        int rgb = Integer.parseInt(digits, 16);
        return new Color(rgb >> 16, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }
}
