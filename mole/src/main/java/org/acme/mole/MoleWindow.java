package org.acme.mole;

import io.quarkiverse.desktop.swt.SwtStartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Shell;

/**
 * A single mole: one window, placed where the controller tells it to be.
 * Geometry arrives as system properties (e.g. {@code -Dmole.x=100}) so the
 * window can paint immediately, without waiting for any network round trip.
 */
@Singleton
public class MoleWindow {

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

    private Shell shell;

    void open(@Observes SwtStartupEvent event) {
        shell = new Shell(event.display(), borderless ? SWT.NO_TRIM : SWT.SHELL_TRIM);
        shell.setText("Mole " + id);
        shell.setBackground(new Color(0x8B, 0x5A, 0x2B));
        shell.setBounds(x, y, width, height);
        shell.open();
    }

    public Shell shell() {
        return shell;
    }
}
