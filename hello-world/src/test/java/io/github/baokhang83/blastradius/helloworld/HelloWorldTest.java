package io.github.baokhang83.blastradius.helloworld;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelloWorldTest {

    @Test
    void greetingIsHelloWorld() {
        assertEquals("Hello, World!", HelloWorld.greeting());
    }
}
