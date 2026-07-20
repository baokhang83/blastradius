package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class FooTest {
    @Test
    void checksFoo() {
        assertEquals(1, new Foo().value());
    }
}
