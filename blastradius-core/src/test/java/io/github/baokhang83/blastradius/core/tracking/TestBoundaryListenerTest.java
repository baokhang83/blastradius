package io.github.baokhang83.blastradius.core.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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

        assertEquals(
                new TestIdentity(TestBoundaryListenerTest.class.getName(), null),
                TestBoundaryListener.currentTest(),
                "a nested launcher must restore its enclosing class container when it finishes");
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

        assertEquals(
                new TestIdentity(TestBoundaryListenerTest.class.getName(), null),
                TestBoundaryListener.currentTest(),
                "a nested launcher must restore its enclosing class container when it finishes");
    }

    @Test
    void afterAllRunsUnderTheSyntheticContainerIdentity() {
        TestBoundaryListener listener = new TestBoundaryListener();
        CleanupContainerProbeTest.observedInAfterAll = null;

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(CleanupContainerProbeTest.class))
                .build();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        assertEquals(
                new TestIdentity(CleanupContainerProbeTest.class.getName(), null),
                CleanupContainerProbeTest.observedInAfterAll,
                "@AfterAll must run under the class-level container identity instead of null");
        assertEquals(
                new TestIdentity(TestBoundaryListenerTest.class.getName(), null),
                TestBoundaryListener.currentTest(),
                "a nested launcher must restore its enclosing class container when it finishes");
    }

    @Test
    void beforeEachAndAfterEachKeepTheirOwningTestIdentity() {
        TestBoundaryListener listener = new TestBoundaryListener();
        LifecycleProbeTest.observedDuringLifecycle.clear();

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(LifecycleProbeTest.class))
                .build();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        assertEquals(4, LifecycleProbeTest.observedDuringLifecycle.size());
        assertTrue(LifecycleProbeTest.observedDuringLifecycle.stream().allMatch(identity ->
                identity != null
                        && identity.className().equals(LifecycleProbeTest.class.getName())
                        && identity.methodName() != null));
        assertTrue(LifecycleProbeTest.observedDuringLifecycle.stream()
                .map(TestIdentity::methodName)
                .toList()
                .containsAll(List.of("one", "two")));
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

    /** A probe for class-level cleanup, which runs after the last test has finished. */
    static class CleanupContainerProbeTest {
        static TestIdentity observedInAfterAll;

        @Test
        void one() {
            // no-op
        }

        @Test
        void two() {
            // no-op
        }

        @AfterAll
        static void afterAll() {
            observedInAfterAll = TestBoundaryListener.currentTest();
        }
    }

    /** Proves per-test callbacks remain precise while the container covers only idle windows. */
    static class LifecycleProbeTest {
        static final List<TestIdentity> observedDuringLifecycle = new ArrayList<>();

        @BeforeEach
        void beforeEach() {
            observedDuringLifecycle.add(TestBoundaryListener.currentTest());
        }

        @AfterEach
        void afterEach() {
            observedDuringLifecycle.add(TestBoundaryListener.currentTest());
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
