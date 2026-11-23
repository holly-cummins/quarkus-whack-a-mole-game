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
 * The end of mole apocalypse: a window over the whole screen, with GAME OVER and the player's score. Clicking it
 * closes it, which ends the game.
 */
final class GameOverWindow {

    private static final Color BACKGROUND = new Color(0x1C, 0x10, 0x08);
    private static final Color HEADLINE = new Color(0xFF, 0x3B, 0x30);
    private static final Color TEXT = new Color(0xFF, 0xF4, 0xE0);
    private static final String TITLE = "GAME OVER";
    private static final String HINT = "Click to finish";
    /** Slightly see-through where the window system allows it (macOS, or Linux with a compositor), to show the moles. */
    private static final int ALPHA = 235;

    private final Shell shell;
    private final String score;
    private Font titleFont;
    private Font scoreFont;
    private Font hintFont;

    private GameOverWindow(Display display, Rectangle bounds, String score) {
        this.score = score;
        shell = new Shell(display, SWT.ON_TOP | SWT.NO_FOCUS | SWT.NO_TRIM);
        shell.setText(TITLE);
        shell.setBackground(BACKGROUND);
        shell.setAlpha(ALPHA);
        shell.setBounds(bounds);
        shell.setCursor(display.getSystemCursor(SWT.CURSOR_HAND));
        shell.addPaintListener(this::paint);
        shell.addListener(SWT.MouseDown, e -> shell.close());
        shell.addDisposeListener(e -> {
            if (titleFont != null) {
                titleFont.dispose();
                scoreFont.dispose();
                hintFont.dispose();
            }
        });
    }

    static void open(Display display, Rectangle bounds, String score) {
        new GameOverWindow(display, bounds, score).shell.open();
    }

    private void paint(PaintEvent event) {
        GC gc = event.gc;
        Rectangle area = shell.getClientArea();
        if (titleFont == null) {
            Display display = shell.getDisplay();
            titleFont = Text.fit(display, gc, TITLE, area.width * 85 / 100, area.height * 30 / 100, SWT.BOLD);
            scoreFont = Text.fit(display, gc, score, area.width * 70 / 100, area.height * 7 / 100, SWT.NORMAL);
            hintFont = Text.fit(display, gc, HINT, area.width * 50 / 100, area.height * 4 / 100, SWT.NORMAL);
        }
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        int y = area.y + area.height * 22 / 100;
        gc.setForeground(HEADLINE);
        y = Text.drawCentred(gc, TITLE, titleFont, area, y);
        gc.setForeground(TEXT);
        y = Text.drawCentred(gc, score, scoreFont, area, y + area.height / 30);
        Text.drawCentred(gc, HINT, hintFont, area, y + area.height / 12);
    }
}
