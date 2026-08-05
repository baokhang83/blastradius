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
 * <p>The window also opens one level up: a class container owns a synthetic identity (see
 * {@link TestIdentity}'s class-level identity convention) while its lifecycle code runs outside a
 * test method. Once the class finishes, the container's accumulated dependencies are folded into
 * every test method that ran directly inside it — see
 * {@link DependencyTrackingAgent#unionContainerDependencies}. This intentionally attributes
 * between-test and cleanup work to every sibling, which is conservative: it can widen selection
 * but cannot hide a dependency.
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
                closeContainerWindow(frame);
                frame.memberTests.add(test);
            }
            TestExecutionContext.start(test);
            CLASSES_AT_TEST_START.set(DependencyTrackingAgent.loadedClasses());
        } else {
            toContainerIdentity(testIdentifier).ifPresent(container -> {
                DependencyTrackingAgent.recordAmbientSnapshot();
                Deque<ContainerFrame> stack = CONTAINER_STACK.get();
                if (!stack.isEmpty()) {
                    // A nested class suspends its parent's lifecycle window. Flush hidden classes
                    // first, then start the inner container so nested test bodies never leak into
                    // the parent bucket.
                    closeContainerWindow(stack.peek());
                }
                TestExecutionContext.start(container);
                stack.push(new ContainerFrame(container, DependencyTrackingAgent.loadedClasses()));
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
            rearmCurrentContainerWindow();
        } else {
            toContainerIdentity(testIdentifier).ifPresent(container -> {
                Deque<ContainerFrame> stack = CONTAINER_STACK.get();
                if (stack.isEmpty()) {
                    return;
                }
                ContainerFrame frame = stack.pop();
                closeContainerWindow(frame);
                DependencyTrackingAgent.unionContainerDependencies(frame.identity, frame.memberTests);
                rearmCurrentContainerWindow();
            });
        }
    }

    /** Records hidden classes from one container-owned lifecycle interval and resets its baseline. */
    private static void closeContainerWindow(ContainerFrame frame) {
        DependencyTrackingAgent.recordHiddenClassesLoadedSince(frame.identity, frame.classesAtWindowStart);
        frame.classesAtWindowStart = DependencyTrackingAgent.loadedClasses();
    }

    /** Restores the innermost container after a child test or nested container has finished. */
    private static void rearmCurrentContainerWindow() {
        Deque<ContainerFrame> stack = CONTAINER_STACK.get();
        if (stack.isEmpty()) {
            TestExecutionContext.finish();
            return;
        }
        ContainerFrame frame = stack.peek();
        TestExecutionContext.start(frame.identity);
        frame.classesAtWindowStart = DependencyTrackingAgent.loadedClasses();
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
     * One class's lifecycle attribution state, tracked per thread so {@code @Nested} classes
     * stack correctly. Each hidden-class baseline covers one container-owned interval: before the
     * first test, between tests, or after the last test. A test always joins the innermost open
     * container's member set, never an ancestor's.
     */
    private static final class ContainerFrame {
        private final TestIdentity identity;
        private Set<Class<?>> classesAtWindowStart;
        private final Set<TestIdentity> memberTests = new LinkedHashSet<>();

        ContainerFrame(TestIdentity identity, Set<Class<?>> classesAtStart) {
            this.identity = identity;
            this.classesAtWindowStart = classesAtStart;
        }
    }
}
