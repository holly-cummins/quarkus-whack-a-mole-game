package org.acme.mole;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import io.quarkiverse.desktop.swt.SwtStartupEvent;
import io.quarkus.runtime.StartupEvent;
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
 * The window shows its PID in the title bar. Its contents show two startup times: how long Quarkus took to
 * start, as in its "started in" log line, and how long it took from being launched to the window's first paint.
 */
@Singleton
public class MoleWindow {

    private static final Color MOLE_BROWN = new Color(0x8B, 0x5A, 0x2B);
    private static final Color TEXT = new Color(0xFF, 0xF4, 0xE0);
    private static final String QUARKUS_CAPTION = "Quarkus started in";
    private static final String PAINT_CAPTION = "window painted in";

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
    private OptionalDouble quarkusMillis = OptionalDouble.empty();
    private List<String[]> lines;
    private Font timeFont;
    private Font captionFont;

    void started(@Observes StartupEvent event) {
        quarkusMillis = QuarkusStartup.millisSinceBoot();
    }

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
        if (lines == null) {
            // Measure first, so the time doesn't include the font sizing below
            String paintTime = formatMillis(millisSinceLaunch());
            lines = new ArrayList<>();
            quarkusMillis.ifPresent(millis -> lines.add(new String[] { QUARKUS_CAPTION, formatMillis(millis) }));
            lines.add(new String[] { PAINT_CAPTION, paintTime });
        }
        Rectangle area = shell.getClientArea();
        GC gc = event.gc;
        if (timeFont == null) {
            // Each line is a caption above a time; give the times most of the height
            int lineHeight = area.height / lines.size();
            String widestTime = lines.stream().map(line -> line[1]).reduce((a, b) -> a.length() >= b.length() ? a : b).get();
            timeFont = fitFont(gc, widestTime, area.width * 8 / 10, lineHeight * 6 / 10);
            captionFont = fitFont(gc, QUARKUS_CAPTION, area.width * 8 / 10, lineHeight * 3 / 10);
        }
        gc.setForeground(TEXT);

        gc.setFont(captionFont);
        int captionHeight = gc.getFontMetrics().getHeight();
        gc.setFont(timeFont);
        int timeHeight = gc.getFontMetrics().getHeight();

        int y = area.y + (area.height - lines.size() * (captionHeight + timeHeight)) / 2;
        for (String[] line : lines) {
            y = drawCentred(gc, line[0], captionFont, area, y);
            y = drawCentred(gc, line[1], timeFont, area, y);
        }
    }

    /** Draws the text horizontally centred at the given height, and returns the height below it. */
    private static int drawCentred(GC gc, String text, Font font, Rectangle area, int y) {
        gc.setFont(font);
        Point extent = gc.textExtent(text);
        gc.drawText(text, area.x + (area.width - extent.x) / 2, y, true);
        return y + extent.y;
    }

    private static String formatMillis(double millis) {
        return String.format("%.1f ms", millis);
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
