package io.github.baokhang83.blastradius.validator.mutation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MutationValidationConfigTest {

    @Test
    void defaultsBoundEachHistoricalPairAndTheWholeRun() {
        MutationValidationConfig config = MutationValidationConfig.defaults();

        assertEquals(10, config.maxMutationClassesPerPair());
        assertEquals(20, config.maxMutationsPerPair());
        assertEquals(60, config.timeLimitMinutes());
    }

    @Test
    void limitsMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new MutationValidationConfig(null, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MutationValidationConfig(null, 1, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new MutationValidationConfig(null, 1, 1, 0));
    }
}
