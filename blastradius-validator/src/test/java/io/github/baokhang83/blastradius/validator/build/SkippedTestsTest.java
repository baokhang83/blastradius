package io.github.baokhang83.blastradius.validator.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkippedTestsTest {

    @Test
    void normalizesCommaSeparatedValuesAndAppendsNegativeSelectors() {
        SkippedTests skipped = SkippedTests.parse(List.of(
                " org.app.FlakyTest,org.app2.Flaky2Test ", "org.app.FlakyTest"));

        assertEquals(List.of("org.app.FlakyTest", "org.app2.Flaky2Test"), skipped.classes());
        assertEquals("!org.app.FlakyTest,!org.app2.Flaky2Test", skipped.appendTo(null));
        assertEquals("org.app.TargetTest#passes,!org.app.FlakyTest,!org.app2.Flaky2Test",
                skipped.appendTo("org.app.TargetTest#passes"));
    }

    @Test
    void rejectsBlankOrNonClassEntries() {
        assertThrows(IllegalArgumentException.class, () -> SkippedTests.parse(List.of("org.app.FlakyTest,")));
        assertThrows(IllegalArgumentException.class, () -> SkippedTests.parse(List.of("org.app.*")));
        assertThrows(IllegalArgumentException.class, () -> SkippedTests.parse(List.of("FlakyTest")));
    }
}
