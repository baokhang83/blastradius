package io.github.baokhang83.blastradius.core.tracking;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class AmbientClassInstrumenterTest {

    private final AmbientClassInstrumenter instrumenter = new AmbientClassInstrumenter();

    @Test
    void instrumentingAConstructorArgumentThatBranchesOnAnotherConstructorProducesVerifiableBytecode()
            throws Exception {
        // NestedConstructorArgConstruction's `new Wrapper(flag ? new ArrayList<>() : existing)`
        // leaves the outer NEW's Uninitialized(offset) on the operand stack while evaluating the
        // ternary argument, which itself branches through a second NEW. That's the exact shape
        // blastradius's own SelectMojo.registerTimingRecorder() has —
        // `new TimingHistoryRecorder(existing == null ? new AbstractExecutionListener() : existing, ...)`
        // — where COMPUTE_MAXS alone leaves the original StackMapTable's Uninitialized(offset)
        // entry pointing at the pre-instrumentation bytecode position once visitTypeInsn's
        // injected callbacks shift it, which JDK 21/25's verifier rejects as "bad offset for
        // Uninitialized". A simpler single-branch `if (flag) return new X(); return new Y();`
        // shape does NOT reproduce this — confirmed by reverting to COMPUTE_MAXS and rerunning.
        byte[] original;
        try (InputStream stream = NestedConstructorArgConstruction.class
                .getResourceAsStream("AmbientClassInstrumenterTest$NestedConstructorArgConstruction.class")) {
            assertNotNull(stream, "fixture class bytes must be available as a resource");
            original = stream.readAllBytes();
        }

        byte[] instrumented = instrumenter.instrument(NestedConstructorArgConstruction.class.getName(), original,
                NestedConstructorArgConstruction.class.getClassLoader());

        assertNotNull(instrumented, "expected the instrumenter to have rewritten the method");
        // defineHiddenClass performs full JVM bytecode verification — unlike a bare
        // instrument() call, this is what actually catches a corrupt StackMapTable.
        assertNotNull(MethodHandles.lookup().defineHiddenClass(instrumented, false).lookupClass());
    }

    @Test
    void instrumentingANestedClassReferenceDoesNotReenterItsDefiningLoader() throws Exception {
        byte[] original;
        try (InputStream stream = FrameMergingNestedType.class
                .getResourceAsStream("AmbientClassInstrumenterTest$FrameMergingNestedType.class")) {
            assertNotNull(stream, "fixture class bytes must be available as a resource");
            original = stream.readAllBytes();
        }

        ClassLoader rejectingLoader = new ClassLoader(FrameMergingNestedType.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(FrameMergingNestedType.Child.class.getName())) {
                    throw new AssertionError("frame computation must not load a nested project class");
                }
                return super.loadClass(name, resolve);
            }
        };

        assertDoesNotThrow(() -> instrumenter.instrument(
                FrameMergingNestedType.class.getName(), original, rejectingLoader));
    }

    static final class Wrapper {
        final Object value;

        Wrapper(Object value) {
            this.value = value;
        }
    }

    static final class NestedConstructorArgConstruction {
        static Object make(boolean flag, Object existing) {
            return new Wrapper(flag ? new ArrayList<>() : existing);
        }
    }

    static final class FrameMergingNestedType {
        static final class Child extends Thread {}

        static Thread choose(boolean child) {
            return child ? new Child() : new Thread();
        }
    }
}
