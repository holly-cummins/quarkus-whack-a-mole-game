package org.acme.controller;

import io.quarkus.test.junit.QuarkusTestProfile;

/** Runs a test with the apocalypse profile, as {@code -Dquarkus.profile=apocalypse} would. */
public class ApocalypseProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "apocalypse";
    }
}
