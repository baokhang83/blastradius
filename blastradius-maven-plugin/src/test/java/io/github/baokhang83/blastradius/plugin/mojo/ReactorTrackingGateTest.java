package io.github.baokhang83.blastradius.plugin.mojo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

class ReactorTrackingGateTest {

    @Test
    void allowsOnlyTheFirstModuleInOneReactorToTrackACommit() {
        Properties sessionProperties = new Properties();

        assertTrue(ReactorTrackingGate.claim(sessionProperties, "/workspace/blastradius", "abc123"));
        assertFalse(ReactorTrackingGate.claim(sessionProperties, "/workspace/blastradius", "abc123"));
    }

    @Test
    void keepsIndependentReactorsAndCommitsIndependent() {
        Properties sessionProperties = new Properties();

        assertTrue(ReactorTrackingGate.claim(sessionProperties, "/workspace/one", "abc123"));
        assertTrue(ReactorTrackingGate.claim(sessionProperties, "/workspace/two", "abc123"));
        assertTrue(ReactorTrackingGate.claim(sessionProperties, "/workspace/one", "def456"));
    }
}
