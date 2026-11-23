package org.acme.controller;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine.Command;

@TopCommand
@Command(name = "moles", mixinStandardHelpOptions = true,
        description = "Launches and coordinates a fleet of mole windows.",
        subcommands = { LaunchCommand.class })
public class MolesCommand {
}
