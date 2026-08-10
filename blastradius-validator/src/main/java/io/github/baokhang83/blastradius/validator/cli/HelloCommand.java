package io.github.baokhang83.blastradius.validator.cli;

import java.io.PrintStream;

final class HelloCommand {

    private HelloCommand() {
    }

    static void writeTo(PrintStream output) {
        output.println("Hello, World!");
    }
}
