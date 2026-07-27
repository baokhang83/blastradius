package io.github.baokhang83.blastradius.core.tracking;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A {@code -javaagent} that observes every class loaded in the JVM it's attached to,
 * attributes each load to the currently-executing test (via the injected supplier — in
 * production, {@link TestBoundaryListener#currentTest()}), and records the class name with a
 * SHA-256 checksum of the loaded bytecode. A project class that discovery loaded before the first
 * test can be retransformed with runtime-use callbacks, so its later execution is attributed to
 * the test that actually uses it. Classes loaded while no test is running (JVM/Surefire bootstrap,
 * etc.) are retained as ambient when they cannot be safely instrumented. Hidden classes are not
 * class-loader definitions, so the listener uses the agent's loaded-class list at test boundaries
 * to record their stable source name. The persisted selection index consumes class names only.
 */
public final class DependencyTrackingAgent implements ClassFileTransformer {

    private static final String HIDDEN_CLASS_CHECKSUM = "hidden-class-bytecode-unavailable";
    private static final String TRACKING_PACKAGE_PREFIX =
            "io.github.baokhang83.blastradius.core.tracking.";

    /**
     * Computing a checksum can initialize JDK security-provider classes. Those class loads call
     * this transformer again, so they must not recursively trigger another checksum computation.
     */
    private static final ThreadLocal<Boolean> RECORDING_CLASS_LOAD = ThreadLocal.withInitial(() -> false);

    /**
     * Initialized while the agent class is loaded, before {@link #premain(String, Instrumentation)}
     * registers the transformer. Security-provider initialization must never happen from within
     * {@link #transform(ClassLoader, String, Class, ProtectionDomain, byte[])}.
     */
    private static final MessageDigest SHA_256 = initializeSha256();
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private static volatile DependencyTrackingAgent installedAgent;
    private static volatile Instrumentation instrumentation;

    private final Supplier<TestIdentity> currentTestSupplier;
    private final Map<TestIdentity, Map<String, String>> checksumsByTest = new ConcurrentHashMap<>();
    private final Set<String> ambientDependencies = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingAmbientRetransformations = ConcurrentHashMap.newKeySet();
    private final Set<String> transformedAmbientClasses = ConcurrentHashMap.newKeySet();
    private final Map<String, String> ambientChecksums = new ConcurrentHashMap<>();
    private final AmbientClassInstrumenter ambientClassInstrumenter = new AmbientClassInstrumenter();
    private final AtomicBoolean ambientSnapshotTaken = new AtomicBoolean(false);

    public DependencyTrackingAgent() {
        this(TestBoundaryListener::currentTest);
    }

    /** Visible for testing: inject a fake "current test" source instead of the real listener. */
    DependencyTrackingAgent(Supplier<TestIdentity> currentTestSupplier) {
        this.currentTestSupplier = currentTestSupplier;
    }

    /**
     * Entry point for {@code -javaagent:agent.jar=<outputFilePathPrefix>}. If an output
     * path prefix is supplied, a shutdown hook writes the recorded dependencies to a
     * file unique to this JVM ({@code <prefix>.<pid>}) when the JVM exits — necessary
     * because the agent runs in a subprocess a parent process can only inspect after
     * that subprocess has fully exited (research.md #1). {@link DependencyRecordReader}
     * merges every sibling file back into one map once the whole build has finished.
     *
     * <p>Attachment is done via {@code JAVA_TOOL_OPTIONS} (see {@code MavenBuildRunner}),
     * which every JVM launch picks up — including the outer {@code mvn} process itself,
     * not just the forked Surefire JVM(s) that actually run tests, and — for target
     * projects configured with {@code reuseForks=false} — every one of the many
     * sequential per-test-class JVMs Surefire spawns. A per-JVM file (rather than one
     * shared file with a read-merge-write on every shutdown) avoids a real race: Surefire
     * does not wait for a fork's OS process to fully exit before starting the next one,
     * so two sibling JVMs' shutdown hooks can genuinely overlap, and a shared file left
     * only the last few writers' data intact once one hook's write raced another's read.
     * A JVM that recorded no tests (e.g. the outer {@code mvn} process) writes nothing.
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        DependencyTrackingAgent agent = new DependencyTrackingAgent();
        installedAgent = agent;
        instrumentation = inst;
        inst.addTransformer(agent, true);
        if (agentArgs != null && !agentArgs.isBlank()) {
            Path outputFile = Path.of(agentArgs + "." + ProcessHandle.current().pid());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Map<TestIdentity, Map<String, String>> recorded = agent.recordedDependencies();
                if (!recorded.isEmpty()) {
                    new DependencyRecordWriter().write(outputFile, recorded, agent.ambientDependencies());
                }
            }));
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null || RECORDING_CLASS_LOAD.get()) {
            return null;
        }

        String dottedClassName = className.replace('/', '.');
        if (classBeingRedefined != null && pendingAmbientRetransformations.contains(dottedClassName)) {
            try {
                byte[] instrumented = ambientClassInstrumenter.instrument(dottedClassName, classfileBuffer);
                if (instrumented != null) {
                    ambientChecksums.put(dottedClassName, sha256Hex(classfileBuffer));
                    transformedAmbientClasses.add(dottedClassName);
                }
                return instrumented;
            } catch (RuntimeException ignored) {
                // An uninstrumentable discovery-loaded class stays ambient and therefore safe.
                return null;
            }
        }

        TestIdentity currentTest = currentTestSupplier.get();
        if (currentTest == null) {
            return null;
        }

        RECORDING_CLASS_LOAD.set(true);
        try {
            checksumsByTest
                    .computeIfAbsent(currentTest, ignored -> new ConcurrentHashMap<>())
                    .put(dottedClassName, sha256Hex(classfileBuffer));
        } finally {
            RECORDING_CLASS_LOAD.remove();
        }
        return null;
    }

    /**
     * Records that a test executed even if it never causes a class-load event while it is active.
     *
     * <p>An empty dependency map is meaningful: it is a baseline for an existing test, not an
     * absent baseline for a newly added test. Selection must preserve that distinction or it
     * conservatively re-runs every test whose dependencies happened to be loaded before the test
     * began.
     */
    static void recordTestStarted(TestIdentity test) {
        DependencyTrackingAgent currentAgent = installedAgent;
        if (currentAgent != null) {
            currentAgent.recordTestExecution(test);
        }
    }

    void recordTestExecution(TestIdentity test) {
        checksumsByTest.computeIfAbsent(test, ignored -> new ConcurrentHashMap<>());
    }

    /**
     * Called once, before the very first test in a fork starts (see {@link TestBoundaryListener}).
     * Everything already loaded at that point — JVM/Surefire bootstrap, JUnit Platform's
     * discovery pass walking every test class's signatures — got exactly one, unattributed
     * {@link #transform} call each and can never be re-observed, so no single test can be
     * blamed for depending on them. Recording them as fork-wide {@code ambientDependencies}
     * lets selection fall back rather than silently reporting {@code NO_MATCH} when one of
     * them changes. Idempotent: only the first caller's snapshot is taken.
     */
    static void recordAmbientSnapshot() {
        DependencyTrackingAgent currentAgent = installedAgent;
        if (currentAgent != null) {
            currentAgent.snapshotAmbientDependencies();
        }
    }

    void snapshotAmbientDependencies() {
        if (ambientSnapshotTaken.compareAndSet(false, true)) {
            Set<Class<?>> loadedClasses = loadedClasses();
            for (Class<?> loadedClass : loadedClasses) {
                ambientDependencies.add(loadedClass.getName());
            }
            retransformProjectAmbientClasses(loadedClasses);
        }
    }

    /** Called from bytecode injected into an already-loaded project class. */
    public static void recordAmbientExecution(String className) {
        DependencyTrackingAgent currentAgent = installedAgent;
        if (currentAgent != null) {
            currentAgent.recordAmbientExecutionForCurrentTest(className);
        }
    }

    private void recordAmbientExecutionForCurrentTest(String className) {
        TestIdentity currentTest = currentTestSupplier.get();
        String checksum = ambientChecksums.get(className);
        if (currentTest != null && checksum != null) {
            checksumsByTest
                    .computeIfAbsent(currentTest, ignored -> new ConcurrentHashMap<>())
                    .putIfAbsent(className, checksum);
        }
    }

    private void retransformProjectAmbientClasses(Set<Class<?>> loadedClasses) {
        Instrumentation currentInstrumentation = instrumentation;
        if (currentInstrumentation == null || !currentInstrumentation.isRetransformClassesSupported()) {
            return;
        }
        for (Class<?> loadedClass : loadedClasses) {
            if (!isAmbientInstrumentationCandidate(loadedClass)
                    || !currentInstrumentation.isModifiableClass(loadedClass)) {
                continue;
            }
            String className = loadedClass.getName();
            pendingAmbientRetransformations.add(className);
            try {
                currentInstrumentation.retransformClasses(loadedClass);
                if (transformedAmbientClasses.remove(className)) {
                    ambientDependencies.remove(className);
                }
            } catch (Exception | LinkageError ignored) {
                // Retain the ambient class so selection continues to use its safe fallback.
            } finally {
                pendingAmbientRetransformations.remove(className);
            }
        }
    }

    static boolean isAmbientInstrumentationCandidate(Class<?> loadedClass) {
        if (loadedClass.getName().startsWith(TRACKING_PACKAGE_PREFIX)
                || loadedClass.isArray()
                || loadedClass.isPrimitive()
                || loadedClass.getProtectionDomain() == null
                || loadedClass.getProtectionDomain().getCodeSource() == null) {
            return false;
        }
        try {
            URI location = loadedClass.getProtectionDomain().getCodeSource().getLocation().toURI();
            if (!"file".equals(location.getScheme())) {
                return false;
            }
            String path = Path.of(location).normalize().toString().replace('\\', '/');
            return path.endsWith("/target/classes")
                    || path.endsWith("/target/test-classes")
                    || path.endsWith("/build/classes/java/main")
                    || path.endsWith("/build/classes/java/test");
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return false;
        }
    }

    /** An immutable snapshot of {@code test -> {className -> tracking token}} recorded so far. */
    public Map<TestIdentity, Map<String, String>> recordedDependencies() {
        return checksumsByTest.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> Map.copyOf(e.getValue())));
    }

    /** Class names loaded before the first test's tracking window opened in this fork. */
    public Set<String> ambientDependencies() {
        return Set.copyOf(ambientDependencies);
    }

    static Set<Class<?>> loadedClasses() {
        Instrumentation currentInstrumentation = instrumentation;
        if (currentInstrumentation == null) {
            return Set.of();
        }
        return Arrays.stream(currentInstrumentation.getAllLoadedClasses())
                .<Class<?>>map(loadedClass -> loadedClass)
                .collect(Collectors.toUnmodifiableSet());
    }

    static void recordHiddenClassesLoadedSince(TestIdentity test, Set<Class<?>> classesAtTestStart) {
        DependencyTrackingAgent currentAgent = installedAgent;
        Instrumentation currentInstrumentation = instrumentation;
        if (currentAgent != null && currentInstrumentation != null) {
            currentAgent.recordNewHiddenClasses(test, classesAtTestStart, currentInstrumentation.getAllLoadedClasses());
        }
    }

    void recordNewHiddenClasses(TestIdentity test, Set<Class<?>> classesAtTestStart, Class<?>[] allLoadedClasses) {
        Set<Class<?>> baseline = classesAtTestStart == null ? Set.of() : classesAtTestStart;
        for (Class<?> loadedClass : allLoadedClasses) {
            if (loadedClass.isHidden() && !baseline.contains(loadedClass)) {
                String sourceClassName = sourceClassName(loadedClass);
                if (sourceClassName != null) {
                    checksumsByTest
                            .computeIfAbsent(test, ignored -> new ConcurrentHashMap<>())
                            .putIfAbsent(sourceClassName, HIDDEN_CLASS_CHECKSUM);
                }
            }
        }
    }

    private static String sha256Hex(byte[] bytes) {
        byte[] hash;
        synchronized (SHA_256) {
            hash = SHA_256.digest(bytes);
        }
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            int unsigned = Byte.toUnsignedInt(b);
            sb.append(HEX_DIGITS[unsigned >>> 4]);
            sb.append(HEX_DIGITS[unsigned & 0x0f]);
        }
        return sb.toString();
    }

    private static MessageDigest initializeSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a required JDK algorithm", e);
        }
    }

    private static String sourceClassName(Class<?> hiddenClass) {
        String hiddenName = hiddenClass.getName();
        int suffixSeparator = hiddenName.lastIndexOf('/');
        return suffixSeparator > 0 ? hiddenName.substring(0, suffixSeparator) : null;
    }
}
