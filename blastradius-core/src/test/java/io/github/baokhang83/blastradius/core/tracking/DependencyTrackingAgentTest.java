package io.github.baokhang83.blastradius.core.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

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

    private static String sha256Hex(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
