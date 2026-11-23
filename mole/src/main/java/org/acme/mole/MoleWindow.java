package org.acme.mole;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import io.quarkiverse.desktop.swt.SwtStartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Shell;

/**
 * A single mole: one window, placed where the controller tells it to be.
 * Geometry arrives as system properties (e.g. {@code -Dmole.x=100}) so the
 * window can paint immediately, without waiting for any network round trip.
 * <p>
 * The window shows its PID in the title bar and, as its contents, how long it took from being
 * launched to its first paint.
 */
@Singleton
public class MoleWindow {

    private static final Color MOLE_BROWN = new Color(0x8B, 0x5A, 0x2B);
    private static final Color TEXT = new Color(0xFF, 0xF4, 0xE0);
    private static final String CAPTION = "started in";

    @ConfigProperty(name = "mole.id", defaultValue = "0")
    int id;

    @ConfigProperty(name = "mole.x", defaultValue = "100")
    int x;

    @ConfigProperty(name = "mole.y", defaultValue = "100")
    int y;

    @ConfigProperty(name = "mole.width", defaultValue = "200")
    int width;

    @ConfigProperty(name = "mole.height", defaultValue = "200")
    int height;

    /** Borderless windows tile seamlessly (video); bordered ones have a close button (apocalypse). */
    @ConfigProperty(name = "mole.borderless", defaultValue = "false")
    boolean borderless;

    /**
     * When the controller launched this mole, in microseconds since the epoch. Without it (a mole started by hand),
     * the OS's record of the process start time is used, which on Linux is only accurate to 10 ms.
     */
    @ConfigProperty(name = "mole.spawned-at")
    Optional<Long> spawnedAtMicros;

    private Shell shell;
    private String startupTime;
    private Font timeFont;
    private Font captionFont;

    void open(@Observes SwtStartupEvent event) {
        MacOs.hideFromDock();
        // ON_TOP: float above other apps' windows, and take a click even when this mole isn't the active app.
        // NO_FOCUS: a new mole doesn't steal keyboard focus from whatever the user is doing.
        int style = SWT.ON_TOP | SWT.NO_FOCUS | (borderless ? SWT.NO_TRIM : SWT.SHELL_TRIM);
        shell = new Shell(event.display(), style);
        shell.setText("Mole " + id + " (PID " + ProcessHandle.current().pid() + ")");
        shell.setBackground(MOLE_BROWN);
        shell.setBounds(x, y, width, height);
        shell.addPaintListener(this::paint);
        shell.addDisposeListener(e -> {
            if (timeFont != null) {
                timeFont.dispose();
                captionFont.dispose();
            }
        });
        shell.open();
    }

    public Shell shell() {
        return shell;
    }

    private void paint(PaintEvent event) {
        if (startupTime == null) {
            // Measure first, so the time doesn't include the font sizing below
            startupTime = String.format("%.1f ms", millisSinceLaunch());
        }
        Rectangle area = shell.getClientArea();
        GC gc = event.gc;
        if (timeFont == null) {
            timeFont = fitFont(gc, startupTime, area.width * 8 / 10, area.height / 2);
            captionFont = fitFont(gc, CAPTION, area.width * 6 / 10, area.height / 6);
        }
        gc.setForeground(TEXT);

        gc.setFont(captionFont);
        Point caption = gc.textExtent(CAPTION);
        gc.setFont(timeFont);
        Point time = gc.textExtent(startupTime);

        int top = area.y + (area.height - caption.y - time.y) / 2;
        gc.setFont(captionFont);
        gc.drawText(CAPTION, area.x + (area.width - caption.x) / 2, top, true);
        gc.setFont(timeFont);
        gc.drawText(startupTime, area.x + (area.width - time.x) / 2, top + caption.y, true);
    }

    private double millisSinceLaunch() {
        long now = micros(Instant.now());
        long launched = spawnedAtMicros.orElseGet(
                () -> ProcessHandle.current().info().startInstant().map(MoleWindow::micros).orElse(now));
        return (now - launched) / 1000.0;
    }

    private static long micros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant);
    }

    /** The largest system font, in whole points, at which the text fits within the given size. */
    private Font fitFont(GC gc, String text, int maxWidth, int maxHeight) {
        FontData data = shell.getDisplay().getSystemFont().getFontData()[0];
        for (int points = Math.max(maxHeight, 6); ; points--) {
            data.setHeight(points);
            Font font = new Font(shell.getDisplay(), data);
            gc.setFont(font);
            Point extent = gc.textExtent(text);
            if (points == 6 || (extent.x <= maxWidth && extent.y <= maxHeight)) {
                return font;
            }
            font.dispose();
        }
    }
}
