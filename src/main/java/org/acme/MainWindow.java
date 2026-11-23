package org.acme;

import java.util.List;

import io.quarkiverse.desktop.swt.SwtStartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;



@Singleton
public class MainWindow {

    @Inject
    Library library;

    @ConfigProperty(name = "app.title", defaultValue = "Library")
    String title;

    private Shell shell;

    void open(@Observes SwtStartupEvent event) {
        shell = new Shell(event.display());
        shell.setText(title);
        shell.setLayout(new FillLayout());
        org.eclipse.swt.widgets.List titles = new org.eclipse.swt.widgets.List(shell, SWT.BORDER | SWT.V_SCROLL);
        library.titles().forEach(titles::add);
        shell.setSize(320, 240);
        shell.open();
    }

    public Shell shell() {
        return shell;
    }
}