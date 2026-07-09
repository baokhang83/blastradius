package io.github.baokhang83.blastradius.core.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

class TestBoundaryListenerTest {

    @Test
    void currentTestIsSetDuringExecutionAndClearedAfterwards() {
        TestBoundaryListener listener = new TestBoundaryListener();
        List<TestIdentity> observedWhileRunning = new ArrayList<>();

        TestExecutionListener probe = new TestExecutionListener() {
            @Override
            public void executionStarted(TestIdentifier id) {
                if (id.isTest()) {
                    observedWhileRunning.add(TestBoundaryListener.currentTest());
                }
            }
        };

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(ProbeTest.class))
                .build();
        Launcher launcher = LauncherFactory.create();
        // Registration order matters: listener must run before probe for each event.
        launcher.registerTestExecutionListeners(listener, probe);
        launcher.execute(request);

        assertEquals(1, observedWhileRunning.size());
        assertEquals(ProbeTest.class.getName(), observedWhileRunning.get(0).className());
        assertEquals("probe", observedWhileRunning.get(0).methodName());

        assertNull(TestBoundaryListener.currentTest(), "must be cleared once execution finishes");
    }

    /** A trivial nested class used purely as a probe for the listener under test. */
    static class ProbeTest {
        @Test
        void probe() {
            // no-op
        }
    }
}
