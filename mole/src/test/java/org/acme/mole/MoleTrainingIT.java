package org.acme.mole;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * The Leyden training run. With {@code -Dleyden}, Quarkus starts the mole jar for this test while the JVM records what
 * it does, and once the test is over, builds the AOT cache ({@code target/quarkus-app/app.aot}) from the recording.
 * <p>
 * The {@code training} profile (see application.properties) makes the mole a target that makes an entrance, then
 * clicks itself, so it plays a whole mole's life and exits. This test waits for that, so the recording covers it all.
 * It needs a display, so it only runs for Leyden builds.
 */
@QuarkusIntegrationTest
@EnabledIfSystemProperty(named = "mole.training", matches = "true")
class MoleTrainingIT {

    private static final Duration LIFETIME = Duration.ofSeconds(30);

    @Test
    void moleMakesItsEntranceAndClosesWhenHit() throws Exception {
        // Quarkus starts the mole as a child of this test's JVM
        List<ProcessHandle> moles = ProcessHandle.current().descendants()
                .filter(process -> process.info().commandLine().orElse("").contains("quarkus-run.jar"))
                .toList();
        assertFalse(moles.isEmpty(), "The mole isn't running");
        for (ProcessHandle mole : moles) {
            mole.onExit().get(LIFETIME.toSeconds(), TimeUnit.SECONDS);
            assertTrue(!mole.isAlive(), "The mole should have clicked itself and closed");
        }
    }
}
