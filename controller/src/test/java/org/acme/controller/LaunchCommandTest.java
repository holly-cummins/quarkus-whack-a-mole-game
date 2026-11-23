package org.acme.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

@QuarkusMainTest
class LaunchCommandTest {

    @Test
    @Launch({ "launch", "--dry-run", "--mode", "NATIVE", "4",
            "--screen-width", "400", "--screen-height", "300", "--gap", "0" })
    void dryRunPrintsOneCommandPerTile(LaunchResult result) {
        var lines = result.getOutputStream().stream().filter(line -> line.contains("-Dmole.id=")).toList();
        assertEquals(4, lines.size());
        assertTrue(lines.get(3).contains("-Dmole.id=3 -Dmole.x=200 -Dmole.y=150 -Dmole.width=200 -Dmole.height=150"),
                lines.get(3));
        assertTrue(lines.get(3).matches(".* -Dmole\\.spawned-at=\\d{16}( .*)?"), lines.get(3));
    }
}
