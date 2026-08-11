package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HelloMojoTest {

    @Test
    void exposesTheHelloWorldMessage() {
        assertEquals("Hello, world!", HelloMojo.message());
    }
}
