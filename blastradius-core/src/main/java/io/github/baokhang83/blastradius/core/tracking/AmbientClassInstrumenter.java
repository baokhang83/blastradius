package io.github.baokhang83.blastradius.core.tracking;

import java.util.concurrent.atomic.AtomicBoolean;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Adds lightweight runtime-use callbacks to an already-loaded project class: method entry plus
 * field, type, and class-literal references. The callbacks are filtered by the agent to retain
 * only successfully retransformed project classes.
 */
final class AmbientClassInstrumenter {

    private static final String AGENT_INTERNAL_NAME =
            "io/github/baokhang83/blastradius/core/tracking/DependencyTrackingAgent";

    byte[] instrument(String className, byte[] bytecode, ClassLoader loader) {
        ClassReader reader = new ClassReader(bytecode);
        // COMPUTE_FRAMES, not just COMPUTE_MAXS: every NEW/CHECKCAST/etc. we rewrite in
        // visitTypeInsn shifts bytecode offsets, and COMPUTE_MAXS leaves the class's existing
        // StackMapTable untouched — its Uninitialized(offset) entries (tracking an object
        // between NEW and its <init> call) then point past the instructions ASM just inserted,
        // which JDK 21/25's stricter verifier rejects as "bad offset for Uninitialized" (found
        // self-hosting: SelectMojo.registerTimingRecorder()'s
        // `new TimingHistoryRecorder(existing == null ? new AbstractExecutionListener() : existing, ...)`
        // leaves the outer NEW's Uninitialized(offset) on the stack across the ternary's branch —
        // see AmbientClassInstrumenterTest for a minimal repro of this exact shape). COMPUTE_FRAMES
        // recomputes the whole table from the rewritten bytecode instead of patching stale
        // offsets, at the cost of needing loader to resolve common superclasses below.
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected ClassLoader getClassLoader() {
                // Defaults to this ClassWriter's own (agent/ASM) loader, which can't see a
                // target project's classes when the agent jar and the instrumented class live
                // in different classloader realms (e.g. a Maven plugin realm during self-host)
                // — resolving common superclasses would then throw ClassNotFoundException.
                return loader != null ? loader : super.getClassLoader();
            }
        };
        AtomicBoolean changed = new AtomicBoolean();
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                if ((access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                    return delegate;
                }
                changed.set(true);
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        record(className);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        record(owner.replace('/', '.'));
                        super.visitFieldInsn(opcode, owner, name, descriptor);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        record(type.replace('/', '.'));
                        super.visitTypeInsn(opcode, type);
                    }

                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof Type type
                                && (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)) {
                            record(type.getClassName());
                        }
                        super.visitLdcInsn(value);
                    }

                    @Override
                    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                        record(Type.getType(descriptor).getClassName());
                        super.visitMultiANewArrayInsn(descriptor, numDimensions);
                    }

                    private void record(String dependencyClassName) {
                        super.visitLdcInsn(dependencyClassName);
                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                AGENT_INTERNAL_NAME,
                                "recordAmbientExecution",
                                "(Ljava/lang/String;)V",
                                false);
                    }
                };
            }
        }, 0);
        return changed.get() ? writer.toByteArray() : null;
    }
}
