package io.github.baokhang83.blastradius.validator.cli;

/** Minimal demo subcommand: prints a greeting. Not part of the selection pipeline. */
final class HelloCommand {

    String greeting() {
        return greeting("World");
    }

    String greeting(String target) {
        return "Hello, " + target + "!";
    }
}
