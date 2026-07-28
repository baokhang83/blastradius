package io.github.baokhang83.blastradius.core.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.baokhang83.blastradius.core.index.CommitIndexKey;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyTrackingAgentTest {

    private final AtomicReference<TestIdentity> currentTest = new AtomicReference<>();
    private final DependencyTrackingAgent agent = new DependencyTrackingAgent(currentTest::get);

    private static final TestIdentity FOO_TEST = new TestIdentity("com.example.FooTest", "checksAdd");

    @Test
    void classLoadedWhileATestIsRunningIsRecordedUnderThatTest() throws NoSuchAlgorithmException {
        byte[] bytecode = "fake-bytecode-content".getBytes(StandardCharsets.UTF_8);
        currentTest.set(FOO_TEST);

        agent.transform(null, "com/example/Foo", null, null, bytecode);

        Map<String, String> recorded = agent.recordedDependencies().get(FOO_TEST);
        assertEquals(sha256Hex(bytecode), recorded.get("com.example.Foo"));
    }

    @Test
    void classLoadedWithNoTestRunningIsNotRecorded() {
        currentTest.set(null);

        agent.transform(null, "com/example/Foo", null, null, "x".getBytes(StandardCharsets.UTF_8));

        assertTrue(agent.recordedDependencies().isEmpty());
    }

    @Test
    void transformReturnsNullToLeaveBytecodeUnmodified() {
        currentTest.set(FOO_TEST);
        byte[] result = agent.transform(null, "com/example/Foo", null, null, "x".getBytes(StandardCharsets.UTF_8));
        assertNull(result);
    }

    @Test
    void reloadingSameClassWithDifferentBytecodeUpdatesTheChecksum() throws NoSuchAlgorithmException {
        currentTest.set(FOO_TEST);
        agent.transform(null, "com/example/Foo", null, null, "v1".getBytes(StandardCharsets.UTF_8));
        agent.transform(null, "com/example/Foo", null, null, "v2".getBytes(StandardCharsets.UTF_8));

        assertEquals(sha256Hex("v2".getBytes(StandardCharsets.UTF_8)),
                agent.recordedDependencies().get(FOO_TEST).get("com.example.Foo"));
    }

    @Test
    void nullClassNameIsIgnored() {
        currentTest.set(FOO_TEST);
        agent.transform(null, null, null, null, "x".getBytes(StandardCharsets.UTF_8));
        assertTrue(agent.recordedDependencies().isEmpty());
    }

    @Test
    void jdkInvokeBootstrapClassesAreNotTracked() {
        // Found running against a real apache/shenyu build: computing a checksum here is
        // reentrant class-loading work (MessageDigest, ConcurrentHashMap) happening in the
        // middle of the JVM's own MethodHandle/invokedynamic bootstrap sequence for this
        // exact class shape, which threw ClassCircularityError once a second, dynamically
        // self-attached javaagent (Mockito's inline mock maker) was also active in the same
        // fork. These classes are pure JDK platform plumbing, identical for any two commits
        // built with the same JDK, so tracking their checksums has no test-selection value
        // — skipping them removes a real crash risk for free.
        currentTest.set(FOO_TEST);
        agent.transform(null, "java/lang/invoke/MethodHandleImpl$CountingWrapper$1", null, null,
                "x".getBytes(StandardCharsets.UTF_8));
        assertTrue(agent.recordedDependencies().isEmpty());
    }

    @Test
    void executedTestWithNoClassLoadsHasAnEmptyBaseline() {
        agent.recordTestExecution(FOO_TEST);

        assertEquals(Map.of(), agent.recordedDependencies().get(FOO_TEST));
    }

    @Test
    void newlyCreatedHiddenClassUsesItsStableSourceName() throws Exception {
        currentTest.set(FOO_TEST);
        byte[] classFile;
        try (InputStream stream = DependencyTrackingAgentTest.class
                .getResourceAsStream("DependencyTrackingAgentTest.class")) {
            assertTrue(stream != null, "test class bytes must be available as a resource");
            classFile = stream.readAllBytes();
        }

        Class<?> hiddenClass = MethodHandles.lookup().defineHiddenClass(classFile, false).lookupClass();
        agent.recordNewHiddenClasses(FOO_TEST, Set.of(), new Class<?>[] {hiddenClass});

        assertTrue(agent.recordedDependencies().get(FOO_TEST)
                .containsKey(DependencyTrackingAgentTest.class.getName()));
    }

    @Test
    void moduleAwareTransformRecordsTheClass() throws Exception {
        currentTest.set(FOO_TEST);

        agent.transform(Object.class.getModule(), null, "com/example/Foo", null, null, "x".getBytes(StandardCharsets.UTF_8));

        assertTrue(agent.recordedDependencies().get(FOO_TEST).containsKey("com.example.Foo"));
    }

    @Test
    void projectClassIsInstrumentedAtLoadEvenWithNoTestRunning() throws Exception {
        currentTest.set(null);

        byte[] instrumented = agent.transform(
                null, "com/example/SpringBean", null, ownProtectionDomain(), ownClassBytes());

        assertTrue(instrumented != null,
                "a project class must be instrumented on its very first load, whether or not a "
                        + "test window happens to be open at that moment");
        assertTrue(agent.recordedDependencies().isEmpty(), "no test was running, so nothing is attributed yet");
    }

    @Test
    void laterTestReusingAnAlreadyLoadedProjectClassGetsAttributed() throws Exception {
        // Simulates a Spring-managed singleton: TestA's window is the one that happens to
        // trigger the class's only real transform() call; TestB reuses the same already-loaded
        // instance (e.g. via a cached ApplicationContext) without ever reloading it. Only the
        // callback injected into the instrumented bytecode — not a second transform() call —
        // can attribute that reuse to TestB.
        TestIdentity testA = new TestIdentity("com.example.BeanCreatingTest", "buildsContext");
        TestIdentity testB = new TestIdentity("com.example.BeanReusingTest", "reusesCachedContext");

        currentTest.set(testA);
        agent.transform(null, "com/example/SpringBean", null, ownProtectionDomain(), ownClassBytes());

        Field installedAgentField = DependencyTrackingAgent.class.getDeclaredField("installedAgent");
        installedAgentField.setAccessible(true);
        Object previousInstalledAgent = installedAgentField.get(null);
        installedAgentField.set(null, agent);
        try {
            currentTest.set(testB);
            DependencyTrackingAgent.recordAmbientExecution("com.example.SpringBean");
        } finally {
            installedAgentField.set(null, previousInstalledAgent);
        }

        Map<TestIdentity, Map<String, String>> all = agent.recordedDependencies();
        assertTrue(all.get(testA).containsKey("com.example.SpringBean"));
        assertTrue(all.get(testB).containsKey("com.example.SpringBean"),
                "testB must be attributed via the injected runtime-use callback, since it never "
                        + "triggered a transform() call of its own");
    }

    @Test
    void differentTestsAreRecordedSeparately() {
        TestIdentity barTest = new TestIdentity("com.example.BarTest", "checksSubtract");

        currentTest.set(FOO_TEST);
        agent.transform(null, "com/example/Foo", null, null, "a".getBytes(StandardCharsets.UTF_8));
        currentTest.set(barTest);
        agent.transform(null, "com/example/Bar", null, null, "b".getBytes(StandardCharsets.UTF_8));

        Map<TestIdentity, Map<String, String>> all = agent.recordedDependencies();
        assertTrue(all.get(FOO_TEST).containsKey("com.example.Foo"));
        assertFalse(all.get(FOO_TEST).containsKey("com.example.Bar"));
        assertTrue(all.get(barTest).containsKey("com.example.Bar"));
    }

    @Test
    void ambientDependenciesIsEmptyWithNoInstrumentationAttached() throws Exception {
        // loadedClasses() reads the static Instrumentation seam shared by the whole JVM fork.
        // It's genuinely null in a plain unit-test run, but TrackRunner re-forks this very
        // module's own tests with a real -javaagent attached to collect dependency data —
        // so this test must force the seam to its "no agent" state itself rather than assume
        // the ambient JVM matches, or it fails deterministically under that fork. No lambda
        // here: another test in this class self-loads this class's own compiled bytecode as
        // a hidden class, which an invokedynamic call site (as a lambda would add) breaks.
        Field instrumentationField = DependencyTrackingAgent.class.getDeclaredField("instrumentation");
        instrumentationField.setAccessible(true);
        Object previous = instrumentationField.get(null);
        instrumentationField.set(null, null);
        try {
            agent.snapshotAmbientDependencies();

            assertTrue(agent.ambientDependencies().isEmpty());
        } finally {
            instrumentationField.set(null, previous);
        }
    }

    @Test
    void snapshotAmbientDependenciesIsIdempotent() throws Exception {
        Field instrumentationField = DependencyTrackingAgent.class.getDeclaredField("instrumentation");
        instrumentationField.setAccessible(true);
        Object previous = instrumentationField.get(null);
        instrumentationField.set(null, null);
        try {
            agent.snapshotAmbientDependencies();
            agent.snapshotAmbientDependencies();

            assertTrue(agent.ambientDependencies().isEmpty());
        } finally {
            instrumentationField.set(null, previous);
        }
    }

    @Test
    void ambientDependenciesReturnsAnImmutableCopy() {
        Set<String> snapshot = agent.ambientDependencies();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("com.example.Injected"));
    }

    @Test
    void trackingInfrastructureIsNeverAnAmbientInstrumentationCandidate() {
        assertFalse(DependencyTrackingAgent.isAmbientInstrumentationCandidate(DependencyTrackingAgent.class));
        assertFalse(DependencyTrackingAgent.isAmbientInstrumentationCandidate(TestBoundaryListener.class));
        assertTrue(DependencyTrackingAgent.isAmbientInstrumentationCandidate(CommitIndexKey.class));
    }

    @Test
    void reactorModuleJarIsAProjectCodeSourceEvenThoughItIsNotAClassOutputDirectory() {
        // How every downstream module sees a reactor dependency: as a built jar, not as
        // `target/classes`. Left unrecognised, such a class stays ambient in that module's fork and
        // one ambient class forces every module's whole suite to run.
        Path reactorRoot = Path.of("/build/repo");

        assertTrue(DependencyTrackingAgent.isProjectCodeSource(
                Path.of("/build/repo/core/target/core-1.0.0.jar"), reactorRoot));
        assertTrue(DependencyTrackingAgent.isProjectCodeSource(
                Path.of("/build/repo/core/target/classes"), reactorRoot));
    }

    @Test
    void jarOutsideTheReactorRootIsNotAProjectCodeSource() {
        // A dependency resolved from the local Maven repository rather than built by this reactor.
        assertFalse(DependencyTrackingAgent.isProjectCodeSource(
                Path.of("/elsewhere/junit-jupiter-api-5.14.0.jar"), Path.of("/build/repo")));
    }

    @Test
    void aJarIsOnlyRecognisedWhenTheReactorRootIsKnown() {
        // Without the reactor root a jar cannot be told apart from a third-party dependency, so it
        // stays ambient and selection keeps its conservative fallback rather than guessing.
        assertFalse(DependencyTrackingAgent.isProjectCodeSource(
                Path.of("/build/repo/core/target/core-1.0.0.jar"), null));
        assertTrue(DependencyTrackingAgent.isProjectCodeSource(
                Path.of("/build/repo/core/target/classes"), null));
    }

    @Test
    void shutdownHookWritesRecordedDependenciesWhenPresent(@TempDir Path tempDir) {
        Path outputFile = tempDir.resolve("dependencies.json.111");
        Map<TestIdentity, Map<String, String>> recorded = Map.of(FOO_TEST, Map.of("com.example.Foo", "abc123"));

        DependencyTrackingAgent.runShutdownHook(
                outputFile, () -> recorded, Set::of, new DependencyRecordWriter());

        assertTrue(Files.exists(outputFile));
    }

    @Test
    void shutdownHookWritesNothingWhenNoDependenciesWereRecorded(@TempDir Path tempDir) {
        Path outputFile = tempDir.resolve("dependencies.json.111");

        DependencyTrackingAgent.runShutdownHook(outputFile, Map::of, Set::of, new DependencyRecordWriter());

        assertFalse(Files.exists(outputFile));
    }

    @Test
    void shutdownHookWritesACrashMarkerInsteadOfSilentlyLosingDataWhenRecordingFails(@TempDir Path tempDir) {
        // The real failure found running against apache/shenyu: a ClassCircularityError
        // from an unrelated javaagent collision crashed the shutdown-hook thread before
        // it wrote any output, with no trace left behind. A ClassCircularityError is an
        // Error, not an Exception — the defensive catch must be broad enough to see it.
        Path outputFile = tempDir.resolve("dependencies.json.111");

        DependencyTrackingAgent.runShutdownHook(outputFile, () -> {
            throw new ClassCircularityError("java/lang/invoke/MethodHandleImpl$CountingWrapper$1");
        }, Set::of, new DependencyRecordWriter());

        assertFalse(Files.exists(outputFile));
        Path marker = Path.of(outputFile + DependencyRecordWriter.CRASH_MARKER_SUFFIX);
        assertTrue(Files.exists(marker), "expected a crash marker at " + marker);
    }

    private static byte[] ownClassBytes() throws Exception {
        try (InputStream stream = DependencyTrackingAgentTest.class
                .getResourceAsStream("DependencyTrackingAgentTest.class")) {
            assertTrue(stream != null, "test class bytes must be available as a resource");
            return stream.readAllBytes();
        }
    }

    /** Compiled to {@code target/test-classes}: a real project code source for the tests below. */
    private static ProtectionDomain ownProtectionDomain() {
        return DependencyTrackingAgentTest.class.getProtectionDomain();
    }

    private static String sha256Hex(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
