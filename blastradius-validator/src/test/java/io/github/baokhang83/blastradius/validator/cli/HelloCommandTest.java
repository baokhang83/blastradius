package io.github.baokhang83.blastradius.validator.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HelloCommandTest {

    @Test
    void greetsTheWorldByDefault() {
        assertEquals("Hello, World!", new HelloCommand().greeting());
    }

    @Test
    void greetsANamedTargetWhenGiven() {
        assertEquals("Hello, Blastradius!", new HelloCommand().greeting("Blastradius"));
    }
}
