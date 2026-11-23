package org.acme.controller;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Which game to play, and how. The defaults are for whack-a-mole; the {@code apocalypse} profile in
 * {@code application.properties} switches to mole apocalypse. Start the controller with
 * {@code -Dquarkus.profile=apocalypse}, or set {@code QUARKUS_PROFILE=apocalypse}, to use it.
 */
@ConfigMapping(prefix = "moles")
public interface GameConfig {

    @WithDefault("whack-a-mole")
    Game game();

    /** Roughly how many slots the grid should have; {@code --count} overrides it. */
    @WithDefault("12")
    int count();

    /** Percentage of slots to leave empty at the start; {@code --sparseness} overrides it. */
    @WithDefault("0")
    int sparseness();
}
