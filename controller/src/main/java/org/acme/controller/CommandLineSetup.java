package org.acme.controller;

import io.quarkus.picocli.runtime.PicocliCommandLineFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import picocli.CommandLine;

/** Picocli settings for the whole command line. */
@ApplicationScoped
class CommandLineSetup {

    /** Accept options such as {@code --mode leyden} in any case. */
    @Produces
    CommandLine commandLine(PicocliCommandLineFactory factory) {
        return factory.create().setCaseInsensitiveEnumValuesAllowed(true);
    }
}
