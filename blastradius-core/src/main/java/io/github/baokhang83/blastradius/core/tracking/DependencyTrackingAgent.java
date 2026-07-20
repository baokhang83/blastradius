package io.github.baokhang83.blastradius.core.tracking;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A {@code -javaagent} that observes every class loaded in the JVM it's attached to,
 * attributes each load to the currently-executing test (via the injected supplier — in
 * production, {@link TestBoundaryListener#currentTest()}), and records the class name with a
 * SHA-256 checksum of the loaded bytecode. Bytecode is never modified — {@link #transform}
 * always returns {@code null}. Classes loaded while no test is running (JVM/Surefire bootstrap,
 * etc.) are not attributable to anything and are not recorded. Hidden classes are not class-loader
 * definitions, so the listener uses the agent's loaded-class list at test boundaries to record
 * their stable source name. The persisted selection index consumes class names only.
 */
public final class DependencyTrackingAgent implements ClassFileTransformer {

    private static final String HIDDEN_CLASS_CHECKSUM = "hidden-class-bytecode-unavailable";

    private static volatile DependencyTrackingAgent installedAgent;
    private static volatile Instrumentation instrumentation;

    private final Supplier<TestIdentity> currentTestSupplier;
    private final Map<TestIdentity, Map<String, String>> checksumsByTest = new ConcurrentHashMap<>();

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
        inst.addTransformer(agent);
        if (agentArgs != null && !agentArgs.isBlank()) {
            Path outputFile = Path.of(agentArgs + "." + ProcessHandle.current().pid());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Map<TestIdentity, Map<String, String>> recorded = agent.recordedDependencies();
                if (!recorded.isEmpty()) {
                    new DependencyRecordWriter().write(outputFile, recorded);
                }
            }));
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className != null) {
            TestIdentity currentTest = currentTestSupplier.get();
            if (currentTest != null) {
                checksumsByTest
                        .computeIfAbsent(currentTest, ignored -> new ConcurrentHashMap<>())
                        .put(className.replace('/', '.'), sha256Hex(classfileBuffer));
            }
        }
        return null;
    }

    /** An immutable snapshot of {@code test -> {className -> tracking token}} recorded so far. */
    public Map<TestIdentity, Map<String, String>> recordedDependencies() {
        return checksumsByTest.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> Map.copyOf(e.getValue())));
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
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
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
