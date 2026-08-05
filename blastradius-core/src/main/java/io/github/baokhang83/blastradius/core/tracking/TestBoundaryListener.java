package io.github.baokhang83.blastradius.core.tracking;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * A JUnit 5 {@link TestExecutionListener} that marks the currently-executing test via a
 * {@link InheritableThreadLocal}, so {@link DependencyTrackingAgent}'s class-load observations can
 * be attributed to the test that triggered them, including classes first loaded by a child thread.
 *
 * <p>The window also opens one level up: while a class's {@code @BeforeAll} runs, no test method
 * is current yet, so its loads run under a synthetic container-level identity instead (see
 * {@link TestIdentity}'s class-level identity convention). Once the class finishes, whatever that
 * identity accumulated is folded into every test method that ran inside it — see
 * {@link DependencyTrackingAgent#unionContainerDependencies}.
 *
 * <p>Tests must await child-thread work before they finish. The identity is copied when a child
 * thread is created, so work that outlives its test cannot be attributed reliably. Tests executing
 * in parallel remain outside this listener's supported model.
 */
public final class TestBoundaryListener implements TestExecutionListener {

    private static final ThreadLocal<Set<Class<?>>> CLASSES_AT_TEST_START = new ThreadLocal<>();
    private static final ThreadLocal<Deque<ContainerFrame>> CONTAINER_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /** The test currently executing on this thread, or {@code null} if none. */
    public static TestIdentity currentTest() {
        return TestExecutionContext.currentTest();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            // First call in this fork snapshots everything already loaded — JVM/Surefire
            // bootstrap plus JUnit Platform's discovery pass, which runs before any test's
            // window opens and can force-load classes the tracker can never re-observe.
            // No-op on every call after the first.
            DependencyTrackingAgent.recordAmbientSnapshot();
            TestIdentity test = toTestIdentity(testIdentifier);
            // Register before publishing this identity to the agent. Computing the record's
            // generated hash code may load JVM support classes; while no test is current those
            // loads are intentionally ignored instead of recursively recording themselves.
            DependencyTrackingAgent.recordTestStarted(test);
            Deque<ContainerFrame> stack = CONTAINER_STACK.get();
            if (!stack.isEmpty()) {
                ContainerFrame frame = stack.peek();
                if (!frame.beforeAllWindowClosed) {
                    // @BeforeAll's window is exactly [container started, first test started) — close
                    // it here so hidden classes any test body loads later are never misattributed
                    // back to the container and unioned into every sibling test.
                    DependencyTrackingAgent.recordHiddenClassesLoadedSince(
                            frame.identity, frame.classesAtStart);
                    frame.beforeAllWindowClosed = true;
                }
                frame.memberTests.add(test);
            }
            TestExecutionContext.start(test);
            CLASSES_AT_TEST_START.set(DependencyTrackingAgent.loadedClasses());
        } else {
            toContainerIdentity(testIdentifier).ifPresent(container -> {
                DependencyTrackingAgent.recordAmbientSnapshot();
                TestExecutionContext.start(container);
                CONTAINER_STACK
                        .get()
                        .push(new ContainerFrame(container, DependencyTrackingAgent.loadedClasses()));
            });
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isTest()) {
            TestIdentity currentTest = TestExecutionContext.currentTest();
            if (currentTest != null) {
                DependencyTrackingAgent.recordHiddenClassesLoadedSince(
                        currentTest, CLASSES_AT_TEST_START.get());
            }
            TestExecutionContext.finish();
            CLASSES_AT_TEST_START.remove();
        } else {
            toContainerIdentity(testIdentifier).ifPresent(container -> {
                Deque<ContainerFrame> stack = CONTAINER_STACK.get();
                if (stack.isEmpty()) {
                    return;
                }
                ContainerFrame frame = stack.pop();
                if (!frame.beforeAllWindowClosed) {
                    // No test ever started under this container (e.g. every test disabled, or a
                    // container with only further @Nested containers) — still close the window so
                    // any class @BeforeAll loaded is captured rather than silently dropped.
                    DependencyTrackingAgent.recordHiddenClassesLoadedSince(
                            frame.identity, frame.classesAtStart);
                }
                DependencyTrackingAgent.unionContainerDependencies(frame.identity, frame.memberTests);
            });
        }
    }

    private static TestIdentity toTestIdentity(TestIdentifier testIdentifier) {
        return testIdentifier.getSource()
                .filter(MethodSource.class::isInstance)
                .map(MethodSource.class::cast)
                .map(source -> new TestIdentity(source.getClassName(), source.getMethodName()))
                .orElseGet(() -> new TestIdentity(testIdentifier.getLegacyReportingName(), null));
    }

    /**
     * A class-backed container gets a synthetic identity ({@link TestIdentity}'s class-level
     * convention: a {@code null} method name). Other container kinds — the engine root, a dynamic
     * {@code @TestFactory} container — carry no {@link ClassSource} and are deliberately left
     * alone: {@code @BeforeAll} only exists on real classes.
     */
    private static Optional<TestIdentity> toContainerIdentity(TestIdentifier testIdentifier) {
        return testIdentifier.getSource()
                .filter(ClassSource.class::isInstance)
                .map(ClassSource.class::cast)
                .map(source -> new TestIdentity(source.getClassName(), null));
    }

    /**
     * One class's {@code @BeforeAll} attribution window, tracked per thread so {@code @Nested}
     * classes — which fire their own container start/finish nested inside the outer one — stack
     * correctly: a test always joins the innermost open container's member set, never an
     * ancestor's.
     */
    private static final class ContainerFrame {
        private final TestIdentity identity;
        private final Set<Class<?>> classesAtStart;
        private final Set<TestIdentity> memberTests = new LinkedHashSet<>();
        private boolean beforeAllWindowClosed;

        ContainerFrame(TestIdentity identity, Set<Class<?>> classesAtStart) {
            this.identity = identity;
            this.classesAtStart = classesAtStart;
        }
    }
}
