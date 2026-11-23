package org.acme.mole;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jboss.logging.Logger;

/**
 * Keeps moles out of the macOS Dock.
 * <p>
 * SWT turns any app without a bundle into a regular Dock app when its Display starts, so every mole would get
 * its own Dock icon. Native macOS builds avoid that with an Info.plist embedded in the executable (see the
 * {@code macos} profile in the mole's pom). JVM moles run inside the {@code java} launcher and can't, so they
 * get a Dock icon briefly until {@link #hideFromDock()} switches them to an accessory app.
 * <p>
 * SWT's Cocoa bindings exist only in the macOS SWT jar, so they are reached through reflection.
 */
@RegisterForReflection(classNames = "org.eclipse.swt.internal.cocoa.NSApplication", methods = true, fields = false)
final class MacOs {

    private static final Logger LOG = Logger.getLogger(MacOs.class);

    /** NSApplicationActivationPolicyAccessory: no Dock icon or menu bar, but windows can still be clicked. */
    private static final long ACCESSORY = 1;

    private MacOs() {
    }

    static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    /** Must run on the UI thread, after the SWT Display exists. Does nothing on other platforms. */
    static void hideFromDock() {
        if (!isMac()) {
            return;
        }
        try {
            Class<?> application = Class.forName("org.eclipse.swt.internal.cocoa.NSApplication");
            Object shared = application.getMethod("sharedApplication").invoke(null);
            application.getMethod("setActivationPolicy", long.class).invoke(shared, ACCESSORY);
        } catch (ReflectiveOperationException e) {
            LOG.warn("Could not hide the mole from the Dock", e);
        }
    }
}
