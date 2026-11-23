package org.acme.mole;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * The end of mole apocalypse: GAME OVER and the player's score, which stay up until the controller is stopped.
 * <p>
 * SWT can only make a whole window see-through, text included, so this is two windows. A very transparent overlay
 * covers the screen, so the moles show through it, and it swallows the player's clicks. A solid card in the middle
 * carries the text, so it stays readable. Neither can be closed by clicking or by the window system: players clicking
 * frantically at the last moles would dismiss it before seeing their score.
 */
final class GameOverWindow {

    private static final Color BACKGROUND = new Color(0x1C, 0x10, 0x08);
    private static final Color HEADLINE = new Color(0xFF, 0x3B, 0x30);
    private static final Color TEXT = new Color(0xFF, 0xF4, 0xE0);
    private static final String TITLE = "GAME OVER";

    private final Shell overlay;
    private final Shell card;
    private final String score;
    private Font titleFont;
    private Font scoreFont;

    /**
     * @param overlayOpacity how opaque the overlay is, as a percentage. It only takes effect where the window system
     *                       supports see-through windows: always on macOS, and on Linux with a compositing window
     *                       manager.
     */
    private GameOverWindow(Display display, Rectangle screen, String score, int overlayOpacity) {
        this.score = score;
        overlay = new Shell(display, SWT.ON_TOP | SWT.NO_FOCUS | SWT.NO_TRIM);
        overlay.setText(TITLE);
        overlay.setBackground(BACKGROUND);
        overlay.setAlpha(Math.clamp(overlayOpacity, 0, 100) * 255 / 100);
        overlay.setBounds(screen);
        keepOpen(overlay);

        card = new Shell(display, SWT.ON_TOP | SWT.NO_FOCUS | SWT.NO_TRIM);
        card.setText(TITLE);
        card.setBackground(BACKGROUND);
        int width = screen.width * 70 / 100;
        int height = screen.height * 40 / 100;
        card.setBounds(screen.x + (screen.width - width) / 2, screen.y + (screen.height - height) / 2, width, height);
        card.addPaintListener(this::paint);
        card.addDisposeListener(e -> {
            if (titleFont != null) {
                titleFont.dispose();
                scoreFont.dispose();
            }
        });
        keepOpen(card);
    }

    static void open(Display display, Rectangle screen, String score, int overlayOpacity) {
        GameOverWindow window = new GameOverWindow(display, screen, score, overlayOpacity);
        // The overlay first, so the card opens on top of it
        window.overlay.open();
        window.card.open();
    }

    /** Refuses requests to close, such as a keyboard shortcut; stopping the process still disposes the window. */
    private static void keepOpen(Shell shell) {
        shell.addListener(SWT.Close, event -> event.doit = false);
    }

    private void paint(PaintEvent event) {
        GC gc = event.gc;
        Rectangle area = card.getClientArea();
        if (titleFont == null) {
            Display display = card.getDisplay();
            titleFont = Text.fit(display, gc, TITLE, area.width * 90 / 100, area.height * 55 / 100, SWT.BOLD);
            scoreFont = Text.fit(display, gc, score, area.width * 85 / 100, area.height * 16 / 100, SWT.NORMAL);
        }
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        gc.setFont(titleFont);
        int titleHeight = gc.getFontMetrics().getHeight();
        gc.setFont(scoreFont);
        int scoreHeight = gc.getFontMetrics().getHeight();
        int y = area.y + (area.height - titleHeight - scoreHeight) / 2;
        gc.setForeground(HEADLINE);
        y = Text.drawCentred(gc, TITLE, titleFont, area, y);
        gc.setForeground(TEXT);
        Text.drawCentred(gc, score, scoreFont, area, y);
    }
}
