package org.acme.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

@QuarkusMainTest
@TestProfile(ApocalypseProfile.class)
class ApocalypseCommandTest {

    @Test
    @Launch({ "launch", "--dry-run", "--mode", "NATIVE", "--screen-width", "1920", "--screen-height", "1080" })
    void startsWithTheBottomRowOfTargets(LaunchResult result) {
        List<String> lines = result.getOutputStream().stream().filter(line -> line.contains("-Dmole.id=")).toList();
        // The profile asks for 24 slots, a 6x4 grid, with 80% empty: 5 moles, all in the bottom row
        assertEquals(5, lines.size(), String.join("\n", lines));
        String bottomRow = lines.getFirst().replaceAll(".*(-Dmole\\.y=\\d+).*", "$1");
        assertTrue(lines.stream().allMatch(line -> line.contains(bottomRow + " ")), String.join("\n", lines));
        assertTrue(lines.stream().allMatch(line -> line.contains("-Dmole.target=true")));
        assertTrue(lines.stream().allMatch(line -> line.contains("-Dquarkus.profile=apocalypse")));
    }
}
