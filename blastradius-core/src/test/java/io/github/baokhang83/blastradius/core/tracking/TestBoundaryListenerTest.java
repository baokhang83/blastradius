package io.github.baokhang83.blastradius.core.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

    @Test
    void beforeAllRunsUnderASyntheticContainerIdentity() {
        TestBoundaryListener listener = new TestBoundaryListener();
        List<TestIdentity> observedDuringTests = new ArrayList<>();
        ContainerProbeTest.observedInBeforeAll = null;

        TestExecutionListener probe = new TestExecutionListener() {
            @Override
            public void executionStarted(TestIdentifier id) {
                if (id.isTest()) {
                    observedDuringTests.add(TestBoundaryListener.currentTest());
                }
            }
        };

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(ContainerProbeTest.class))
                .build();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener, probe);
        launcher.execute(request);

        assertEquals(
                new TestIdentity(ContainerProbeTest.class.getName(), null),
                ContainerProbeTest.observedInBeforeAll,
                "@BeforeAll must run under the class-level container identity instead of null");

        assertEquals(2, observedDuringTests.size());
        assertEquals("one", observedDuringTests.get(0).methodName());
        assertEquals("two", observedDuringTests.get(1).methodName());

        assertNull(TestBoundaryListener.currentTest(), "must be cleared once the container finishes");
    }

    /** A trivial nested class used purely as a probe: one @BeforeAll, two tests. */
    static class ContainerProbeTest {
        static TestIdentity observedInBeforeAll;

        @BeforeAll
        static void beforeAll() {
            observedInBeforeAll = TestBoundaryListener.currentTest();
        }

        @Test
        void one() {
            // no-op
        }

        @Test
        void two() {
            // no-op
        }
    }

    @Test
    void nestedClassGetsItsOwnContainerIdentityInsteadOfTheOuterClasss() {
        TestBoundaryListener listener = new TestBoundaryListener();
        NestedContainerProbeTest.observedInOuterBeforeAll = null;
        NestedContainerProbeTest.Inner.observedInInnerBeforeAll = null;

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(NestedContainerProbeTest.class))
                .build();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        assertEquals(
                new TestIdentity(NestedContainerProbeTest.class.getName(), null),
                NestedContainerProbeTest.observedInOuterBeforeAll,
                "the outer class's @BeforeAll must run under the outer class's own identity");
        assertEquals(
                new TestIdentity(NestedContainerProbeTest.Inner.class.getName(), null),
                NestedContainerProbeTest.Inner.observedInInnerBeforeAll,
                "the @Nested class's @BeforeAll must run under its own identity, not the outer class's");
    }

    /** Outer/inner containers each fire their own start/finish; the stack must not conflate them. */
    static class NestedContainerProbeTest {
        static TestIdentity observedInOuterBeforeAll;

        @BeforeAll
        static void beforeAll() {
            observedInOuterBeforeAll = TestBoundaryListener.currentTest();
        }

        @Test
        void outerTest() {
            // no-op
        }

        @Nested
        @TestInstance(TestInstance.Lifecycle.PER_CLASS)
        class Inner {
            static TestIdentity observedInInnerBeforeAll;

            @BeforeAll
            void beforeAll() {
                observedInInnerBeforeAll = TestBoundaryListener.currentTest();
            }

            @Test
            void innerTest() {
                // no-op
            }
        }
    }
}
