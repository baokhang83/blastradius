package io.github.baokhang83.blastradius.validator.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HistoryModeTest {

    @Test
    void parsesCliValuesCaseInsensitively() {
        assertEquals(HistoryMode.ALL_PARENTS, HistoryMode.fromCliValue("all-parents"));
        assertEquals(HistoryMode.FIRST_PARENT, HistoryMode.fromCliValue("FIRST_PARENT"));
    }

    @Test
    void rejectsUnknownCliValues() {
        assertThrows(IllegalArgumentException.class, () -> HistoryMode.fromCliValue("adjacent"));
    }
}
