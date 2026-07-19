package example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class BarTest {
    @Test
    void checksBar() {
        assertEquals(2, new Bar().value());
    }
}
