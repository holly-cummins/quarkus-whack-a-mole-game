package org.acme.mole;

import java.lang.reflect.Field;
import java.util.OptionalDouble;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.jboss.logging.Logger;

/**
 * Reads the start time Quarkus uses for its own "started in" log line.
 * <p>
 * Quarkus has no public API for this. The timestamp is a public field, {@code bootStartTime}, on an instance held in a
 * private static field of the internal class {@code io.quarkus.bootstrap.runner.Timing}, so it is read by reflection.
 * If a Quarkus upgrade moves it, this returns empty and the mole shows only its own paint timing.
 */
@RegisterForReflection(classNames = "io.quarkus.bootstrap.runner.Timing", fields = true, methods = false)
final class QuarkusStartup {

    private static final Logger LOG = Logger.getLogger(QuarkusStartup.class);

    private QuarkusStartup() {
    }

    /** Milliseconds since Quarkus started booting, measured the same way as its "started in" log line. */
    static OptionalDouble millisSinceBoot() {
        try {
            Class<?> timing = Class.forName("io.quarkus.bootstrap.runner.Timing");
            Field main = timing.getDeclaredField("main");
            main.setAccessible(true);
            long bootStart = timing.getField("bootStartTime").getLong(main.get(null));
            if (bootStart < 0) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of((System.nanoTime() - bootStart) / 1e6);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.debug("Could not read Quarkus's start time", e);
            return OptionalDouble.empty();
        }
    }
}
