package io.github.baokhang83.blastradius.validator.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class HelloCommandTest {

    @Test
    void writesTheGreeting() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        HelloCommand.writeTo(new PrintStream(output));

        assertEquals("Hello, World!\n", output.toString());
    }
}
