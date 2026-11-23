package org.acme.mole;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import io.quarkiverse.desktop.swt.SwtStartupEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;

/**
 * A single mole: one window, placed where the controller tells it to be.
 * Geometry arrives as system properties (e.g. {@code -Dmole.x=100}) so the
 * window can paint immediately, without waiting for any network round trip.
 * <p>
 * The window shows its PID in the title bar. Its contents show two startup times: how long Quarkus took to
 * start, as in its "started in" log line, and how long it took from being launched to the window's first paint.
 * <p>
 * One mole at a time is the target: it shows the Quarkus logo, and closes when clicked. Clicks on other moles do
 * nothing. A mole that appears during a game, rather than at the start, makes an {@link Entrance}.
 * <p>
 * Once its window has painted, the mole prints {@value #READY} on its own line, so the controller knows it is up.
 * <p>
 * With {@code -Dmole.role=GAME_OVER}, the process shows the {@link GameOverWindow} instead of a mole.
 */
@Singleton
public class MoleWindow {

    private static final Color MOLE_BROWN = new Color(0x8B, 0x5A, 0x2B);
    private static final Color TEXT = new Color(0xFF, 0xF4, 0xE0);
    private static final String QUARKUS_CAPTION = "Quarkus started in";
    private static final String PAINT_CAPTION = "window painted in";
    static final String READY = "mole-ready";

    public enum Role {
        MOLE,
        GAME_OVER
    }

    @ConfigProperty(name = "mole.role", defaultValue = "MOLE")
    Role role;

    /**
     * On macOS, keep JVM moles out of the Dock too, by hiding them again once SWT has launched. Their icons still
     * flash up as each mole starts. Off by default: a Dock full of JVM moles shows that each is its own process.
     * Native moles never get an icon either way.
     */
    @ConfigProperty(name = "mole.macos.hide-dock-icon-after-launch", defaultValue = "false")
    boolean hideDockIconAfterLaunch;

    /**
     * Clicks the mole, as a player would, this long after it opens. The Leyden training run uses it (see the
     * {@code training} profile in application.properties): the mole starts, paints, makes its entrance and is hit,
     * then exits, so the AOT cache learns all of that.
     */
    @ConfigProperty(name = "mole.auto-click-after-millis")
    Optional<Integer> autoClickAfterMillis;

    /** The score, for the game-over screen. */
    @ConfigProperty(name = "mole.message", defaultValue = " ")
    String message;

    /** How opaque the game-over screen's background is, as a percentage; low, so the moles show through. */
    @ConfigProperty(name = "mole.game-over.background-opacity", defaultValue = "20")
    int gameOverOpacity;

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

    /** A target is a mole the player has to hit. */
    @ConfigProperty(name = "mole.target", defaultValue = "false")
    boolean target;

    /** A mole that appears during a game, rather than one of the first fleet; it makes an entrance. */
    @ConfigProperty(name = "mole.replacement", defaultValue = "false")
    boolean replacement;

    @Inject
    EntranceConfig entranceConfig;

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
    private Image logo;
    private Entrance entrance;

    void started(@Observes StartupEvent event) {
        quarkusMillis = QuarkusStartup.millisSinceBoot();
    }

    void open(@Observes SwtStartupEvent event) {
        MacOs.hideFromDock();
        if (hideDockIconAfterLaunch) {
            MacOs.hideFromDockOnceLaunched(event.display());
        }
        if (role == Role.GAME_OVER) {
            GameOverWindow.open(event.display(), new Rectangle(x, y, width, height), message.strip(), gameOverOpacity);
            return;
        }
        // ON_TOP: float above other apps' windows, and take a click even when this mole isn't the active app.
        // NO_FOCUS: a new mole doesn't steal keyboard focus from whatever the user is doing.
        int style = SWT.ON_TOP | SWT.NO_FOCUS | (borderless ? SWT.NO_TRIM : SWT.SHELL_TRIM);
        shell = new Shell(event.display(), style);
        shell.setText("PID " + ProcessHandle.current().pid());
        shell.setBackground(MOLE_BROWN);
        shell.setBounds(x, y, width, height);
        shell.addPaintListener(this::paint);
        shell.addDisposeListener(e -> {
            if (timeFont != null) {
                timeFont.dispose();
                captionFont.dispose();
            }
            if (logo != null) {
                logo.dispose();
            }
        });
        if (target) {
            logo = loadLogo();
            shell.setCursor(event.display().getSystemCursor(SWT.CURSOR_CROSS));
            shell.addListener(SWT.MouseDown, e -> shell.close());
        }
        shell.open();
        if (replacement) {
            entrance = new Entrance(entranceConfig, shell, MOLE_BROWN);
            entrance.start();
        }
        autoClickAfterMillis.ifPresent(delay -> event.display().timerExec(delay, () -> {
            if (!shell.isDisposed()) {
                // The same listeners a real click runs; a target closes
                shell.notifyListeners(SWT.MouseDown, new Event());
            }
        }));
    }

    private Image loadLogo() {
        try (InputStream in = MoleWindow.class.getResourceAsStream("/quarkus-logo.png")) {
            return new Image(shell.getDisplay(), in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void paint(PaintEvent event) {
        if (lines == null) {
            // Measure first, so the time doesn't include the font sizing below
            String paintTime = formatMillis(millisSinceLaunch());
            lines = new ArrayList<>();
            quarkusMillis.ifPresent(millis -> lines.add(new String[] { QUARKUS_CAPTION, formatMillis(millis) }));
            lines.add(new String[] { PAINT_CAPTION, paintTime });
            // Once this paint is done; asyncExec runs after the current event
            shell.getDisplay().asyncExec(() -> {
                System.out.println(READY);
                System.out.flush();
            });
        }
        Rectangle area = shell.getClientArea();
        GC gc = event.gc;
        if (entrance != null) {
            entrance.paint(gc, area);
        }
        if (logo != null) {
            area = drawLogo(gc, area);
        }
        if (timeFont == null) {
            // Each line is a caption above a time; give the times most of the height
            int lineHeight = area.height / lines.size();
            String widestTime = lines.stream().map(line -> line[1]).reduce((a, b) -> a.length() >= b.length() ? a : b).get();
            timeFont = Text.fit(shell.getDisplay(), gc, widestTime, area.width * 8 / 10, lineHeight * 6 / 10, SWT.NORMAL);
            captionFont = Text.fit(shell.getDisplay(), gc, QUARKUS_CAPTION, area.width * 8 / 10, lineHeight * 3 / 10,
                    SWT.NORMAL);
        }
        gc.setForeground(TEXT);

        gc.setFont(captionFont);
        int captionHeight = gc.getFontMetrics().getHeight();
        gc.setFont(timeFont);
        int timeHeight = gc.getFontMetrics().getHeight();

        int y = area.y + (area.height - lines.size() * (captionHeight + timeHeight)) / 2;
        for (String[] line : lines) {
            y = Text.drawCentred(gc, line[0], captionFont, area, y);
            y = Text.drawCentred(gc, line[1], timeFont, area, y);
        }
    }

    /** Draws the logo, scaled to fit the top of the window, and returns the area left below it for the text. */
    private Rectangle drawLogo(GC gc, Rectangle area) {
        Rectangle bounds = logo.getBounds();
        int size = Math.min(area.width * 8 / 10, area.height * 55 / 100);
        int margin = area.height / 20;
        gc.setAntialias(SWT.ON);
        gc.setInterpolation(SWT.HIGH);
        gc.drawImage(logo, 0, 0, bounds.width, bounds.height,
                area.x + (area.width - size) / 2, area.y + margin, size, size);
        int used = margin + size;
        return new Rectangle(area.x, area.y + used, area.width, area.height - used);
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
}
