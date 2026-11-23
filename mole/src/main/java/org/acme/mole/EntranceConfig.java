package org.acme.mole;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * How a replacement mole makes its entrance. Like any Quarkus config, each value can be set as a system property
 * ({@code -Dmole.entrance.ripples.count=5}) or an environment variable ({@code MOLE_ENTRANCE_RIPPLES_COUNT=5}); moles
 * inherit the controller's environment, so setting a variable before starting the controller changes every mole.
 * Colours are hex, as in {@code #4695EB}.
 */
@ConfigMapping(prefix = "mole.entrance")
public interface EntranceConfig {

    /** Milliseconds between animation frames. */
    @WithDefault("16")
    int frameMillis();

    Flash flash();

    Ripples ripples();

    /** The background starts in this colour and fades back to the mole's brown. */
    interface Flash {

        @WithDefault("#FFE066")
        String colour();

        /** How long the fade takes; 0 turns the flash off. */
        @WithDefault("600")
        int millis();
    }

    /** Rings that spread out from the centre of the window, one after another. */
    interface Ripples {

        @WithDefault("#4695EB")
        String colour();

        /** How many rings; 0 turns the ripples off. */
        @WithDefault("3")
        int count();

        /** How long each ring takes to spread to the corners of the window and fade out. */
        @WithDefault("540")
        int millis();

        /** How long after one ring the next one starts. */
        @WithDefault("180")
        int intervalMillis();

        /** How thick a ring starts, as a percentage of the window's shorter side. It thins as it spreads. */
        @WithDefault("7")
        int widthPercent();
    }
}
