package io.github.baokhang83.blastradius.core.tracking;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
        ClassWriter writer = new MetadataClassWriter(reader, loader);
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

    /** Direct method-invocation owner types declared by this class, collected without executing it. */
    Set<String> directInvocationOwners(byte[] bytecode) {
        Set<String> owners = new HashSet<>();
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                            boolean isInterface) {
                        owners.add(owner.replace('/', '.'));
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return Set.copyOf(owners);
    }

    /**
     * Computes target-project stack-map-frame hierarchy relationships from class-file resources
     * rather than {@link Class#forName(String, boolean, ClassLoader)}. A transformer runs while
     * the target loader is defining the class, so defining a referenced nested class here can
     * re-enter that loader and make its later normal definition fail with {@link LinkageError}.
     */
    private static final class MetadataClassWriter extends ClassWriter {

        private final ClassLoader loader;
        private final Map<String, ClassInfo> classes = new HashMap<>();

        MetadataClassWriter(ClassReader reader, ClassLoader loader) {
            super(reader, ClassWriter.COMPUTE_FRAMES);
            this.loader = loader;
        }

        @Override
        protected String getCommonSuperClass(String first, String second) {
            if (first.startsWith("[") || second.startsWith("[")) {
                return "java/lang/Object";
            }
            try {
                if (first.equals(second) || isAssignableFrom(first, second)) {
                    return first;
                }
                if (isAssignableFrom(second, first)) {
                    return second;
                }
                if (classInfo(first).isInterface || classInfo(second).isInterface) {
                    return "java/lang/Object";
                }
                String candidate = first;
                do {
                    candidate = classInfo(candidate).superName;
                } while (!isAssignableFrom(candidate, second));
                return candidate;
            } catch (IOException e) {
                // DependencyTrackingAgent treats an instrumentation failure as an ambient class,
                // preserving the conservative selection fallback. Returning an invented frame
                // type here could instead make the target class fail JVM verification.
                throw new IllegalStateException("could not resolve frame hierarchy from class resources", e);
            }
        }

        private boolean isAssignableFrom(String expectedSupertype, String candidate) throws IOException {
            ArrayDeque<String> pending = new ArrayDeque<>();
            Set<String> seen = new HashSet<>();
            pending.add(candidate);
            while (!pending.isEmpty()) {
                String current = pending.removeFirst();
                if (!seen.add(current)) {
                    continue;
                }
                if (expectedSupertype.equals(current)) {
                    return true;
                }
                ClassInfo info = classInfo(current);
                if (info.superName != null) {
                    pending.addLast(info.superName);
                }
                for (String implementedInterface : info.interfaces) {
                    pending.addLast(implementedInterface);
                }
            }
            return false;
        }

        private ClassInfo classInfo(String internalName) throws IOException {
            ClassInfo known = classes.get(internalName);
            if (known != null) {
                return known;
            }
            if (internalName.startsWith("java/")) {
                try {
                    Class<?> type = Class.forName(internalName.replace('/', '.'), false, loader);
                    Class<?> superclass = type.getSuperclass();
                    Class<?>[] implementedInterfaces = type.getInterfaces();
                    String[] interfaceNames = new String[implementedInterfaces.length];
                    for (int index = 0; index < implementedInterfaces.length; index++) {
                        interfaceNames[index] = implementedInterfaces[index].getName().replace('.', '/');
                    }
                    ClassInfo info = new ClassInfo(
                            type.isInterface(),
                            superclass == null ? null : superclass.getName().replace('.', '/'),
                            interfaceNames);
                    classes.put(internalName, info);
                    return info;
                } catch (ClassNotFoundException e) {
                    throw new IOException("JDK class not found: " + internalName, e);
                }
            }
            String resourceName = internalName + ".class";
            InputStream stream = loader == null
                    ? ClassLoader.getSystemResourceAsStream(resourceName)
                    : loader.getResourceAsStream(resourceName);
            if (stream == null) {
                throw new IOException("class resource not found: " + resourceName);
            }
            try (stream) {
                ClassReader reader = new ClassReader(stream);
                ClassInfo info = new ClassInfo(
                        (reader.getAccess() & Opcodes.ACC_INTERFACE) != 0,
                        reader.getSuperName(),
                        reader.getInterfaces());
                classes.put(internalName, info);
                return info;
            }
        }
    }

    private static final class ClassInfo {
        private final boolean isInterface;
        private final String superName;
        private final String[] interfaces;

        private ClassInfo(boolean isInterface, String superName, String[] interfaces) {
            this.isInterface = isInterface;
            this.superName = superName;
            this.interfaces = interfaces;
        }
    }
}
