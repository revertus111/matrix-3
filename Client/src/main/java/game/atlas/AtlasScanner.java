package game.atlas;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import game.atlas.AtlasSchema.Metadata;
import game.atlas.AtlasSchema.RelationshipRecord;
import game.atlas.AtlasSchema.RelationshipType;
import game.atlas.AtlasSchema.SymbolKind;
import game.atlas.AtlasSchema.SymbolRecord;

/**
 * Offline bytecode scanner for the Client Atlas symbol and relationship catalog.
 */
public final class AtlasScanner {

    private static final String ATLAS_PREFIX = "game/atlas/";
    private static final int READER_FLAGS = ClassReader.SKIP_FRAMES;

    private final AtlasWorkspace workspace;

    public AtlasScanner(AtlasWorkspace workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace cannot be null");
        }
        this.workspace = workspace;
    }

    public ScanResult scan(Path classRoot) throws IOException {
        Path normalizedRoot = requireClassRoot(classRoot);
        List<Path> classFiles = collectClassFiles(normalizedRoot);
        workspace.ensureLayout();

        String fingerprintBefore = AtlasFingerprint.compute(normalizedRoot);
        Path symbolsTemp = workspace.getWorkspaceRoot().resolve(AtlasWorkspace.SYMBOLS_FILE + ".scan.tmp");
        Path relationshipsTemp = workspace.getWorkspaceRoot().resolve(AtlasWorkspace.RELATIONSHIPS_FILE + ".scan.tmp");

        long symbolCount = 0L;
        long relationshipCount = 0L;
        try (BufferedWriter symbols = Files.newBufferedWriter(symbolsTemp, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                BufferedWriter relationships = Files.newBufferedWriter(relationshipsTemp, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ScanCounters counters = new ScanCounters(symbols, relationships);
            for (Path classFile : classFiles) {
                scanClass(workspace.getClientRoot(), normalizedRoot, classFile, counters);
            }
            symbolCount = counters.symbolCount;
            relationshipCount = counters.relationshipCount;
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(symbolsTemp);
            Files.deleteIfExists(relationshipsTemp);
            throw ex;
        }

        String fingerprintAfter = AtlasFingerprint.compute(normalizedRoot);
        if (!fingerprintBefore.equals(fingerprintAfter)) {
            Files.deleteIfExists(symbolsTemp);
            Files.deleteIfExists(relationshipsTemp);
            throw new IOException("Compiled client classes changed during Atlas scan; rebuild and scan again.");
        }

        replaceGeneratedFile(symbolsTemp, workspace.symbolsFile());
        replaceGeneratedFile(relationshipsTemp, workspace.relationshipsFile());

        Metadata metadata = new Metadata(
                AtlasWorkspace.SCHEMA_VERSION,
                fingerprintAfter,
                normalizedRoot.toString(),
                Instant.now().toString(),
                symbolCount,
                relationshipCount);
        workspace.writeMetadata(metadata);

        return new ScanResult(classFiles.size(), symbolCount, relationshipCount, fingerprintAfter);
    }

    private static void scanClass(final Path clientRoot, Path classRoot, Path classFile,
            final ScanCounters counters) throws IOException {
        final String compiledPath = AtlasFingerprint.normalizedRelativePath(classRoot, classFile);
        try (InputStream input = new BufferedInputStream(Files.newInputStream(classFile))) {
            ClassReader reader = new ClassReader(input);
            try {
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    private String owner;
                    private String ownerId;
                    private String sourcePath;

                    @Override
                    public void visit(int version, int access, String name, String signature,
                            String superName, String[] interfaces) {
                        owner = name;
                        sourcePath = resolveJavaSourcePath(clientRoot, name);
                        SymbolKind kind = classKind(access);
                        SymbolRecord classRecord = new SymbolRecord(kind, name, name,
                                "L" + name + ";", signature, compiledPath, sourcePath, access);
                        ownerId = classRecord.getId();
                        counters.writeSymbol(classRecord);

                        if (superName != null) {
                            counters.writeRelationship(structuralRelationship(ownerId,
                                    RelationshipType.EXTENDS, superName, sourcePath));
                        }
                        if (interfaces != null) {
                            for (String interfaceName : interfaces) {
                                counters.writeRelationship(structuralRelationship(ownerId,
                                        RelationshipType.IMPLEMENTS, interfaceName, sourcePath));
                            }
                        }
                        writeSignatureTypeReferences(counters, ownerId, sourcePath, signature, false);
                    }

                    @Override
                    public FieldVisitor visitField(int access, String name, String descriptor,
                            String signature, Object value) {
                        SymbolRecord record = new SymbolRecord(SymbolKind.FIELD, owner, name,
                                descriptor, signature, compiledPath, sourcePath, access);
                        counters.writeSymbol(record);
                        counters.writeRelationship(structuralRelationship(ownerId,
                                RelationshipType.DECLARES, record.getId(), sourcePath));

                        StaticRelationshipAccumulator relationships =
                                new StaticRelationshipAccumulator(record.getId(), sourcePath, counters);
                        relationships.recordDescriptorTypes(descriptor, false);
                        relationships.recordSignatureTypes(signature, true);
                        relationships.recordConstant(value);
                        relationships.flush();
                        return null;
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                            String signature, String[] exceptions) {
                        SymbolKind kind = "<init>".equals(name) ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD;
                        SymbolRecord record = new SymbolRecord(kind, owner, name,
                                descriptor, signature, compiledPath, sourcePath, access);
                        counters.writeSymbol(record);
                        counters.writeRelationship(structuralRelationship(ownerId,
                                RelationshipType.DECLARES, record.getId(), sourcePath));

                        MethodRelationshipVisitor visitor =
                                new MethodRelationshipVisitor(record.getId(), sourcePath, counters);
                        visitor.recordMethodDescriptor(descriptor);
                        visitor.recordSignature(signature);
                        visitor.recordExceptions(exceptions);
                        return visitor;
                    }
                }, READER_FLAGS);
            } catch (UncheckedIOException ex) {
                throw ex.getCause();
            }
        }
    }

    private static RelationshipRecord structuralRelationship(String fromId, RelationshipType type,
            String target, String sourcePath) {
        return new RelationshipRecord(fromId, type, target, sourcePath, null, null, 1, null);
    }

    private static void writeSignatureTypeReferences(ScanCounters counters, String fromId,
            String sourcePath, String signature, boolean typeOnly) {
        if (signature == null || signature.length() == 0) {
            return;
        }
        StaticRelationshipAccumulator accumulator =
                new StaticRelationshipAccumulator(fromId, sourcePath, counters);
        accumulator.recordSignatureTypes(signature, typeOnly);
        accumulator.flush();
    }

    private static String resolveJavaSourcePath(Path clientRoot, String internalName) {
        if (clientRoot == null || internalName == null || internalName.length() == 0) {
            return null;
        }
        String topLevelName = internalName;
        int innerIndex = topLevelName.indexOf('$');
        if (innerIndex >= 0) {
            topLevelName = topLevelName.substring(0, innerIndex);
        }
        Path sourceFile = clientRoot.resolve("src/main/java").resolve(topLevelName + ".java").normalize();
        if (!Files.isRegularFile(sourceFile)) {
            return null;
        }
        return clientRoot.relativize(sourceFile).toString().replace('\\', '/');
    }

    private static SymbolKind classKind(int access) {
        if ((access & Opcodes.ACC_ANNOTATION) != 0) {
            return SymbolKind.ANNOTATION;
        }
        if ((access & Opcodes.ACC_ENUM) != 0) {
            return SymbolKind.ENUM;
        }
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            return SymbolKind.INTERFACE;
        }
        return SymbolKind.CLASS;
    }

    private static List<Path> collectClassFiles(Path classRoot) throws IOException {
        final List<Path> classFiles = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(classRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .filter(path -> !AtlasFingerprint.normalizedRelativePath(classRoot, path).startsWith(ATLAS_PREFIX))
                    .forEach(classFiles::add);
        }

        if (classFiles.isEmpty()) {
            throw new IOException("No compiled client .class files found under: " + classRoot);
        }

        Collections.sort(classFiles, new Comparator<Path>() {
            @Override
            public int compare(Path left, Path right) {
                return AtlasFingerprint.normalizedRelativePath(classRoot, left)
                        .compareTo(AtlasFingerprint.normalizedRelativePath(classRoot, right));
            }
        });
        return classFiles;
    }

    private static Path requireClassRoot(Path classRoot) throws IOException {
        if (classRoot == null) {
            throw new IllegalArgumentException("classRoot cannot be null");
        }
        Path normalized = classRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IOException("Compiled client class directory does not exist: " + normalized);
        }
        return normalized;
    }

    private static void replaceGeneratedFile(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String methodTarget(String owner, String name, String descriptor) {
        SymbolKind kind = "<init>".equals(name) ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD;
        return AtlasSchema.symbolId(kind, owner, name, descriptor);
    }

    private static String fieldTarget(String owner, String name, String descriptor) {
        return AtlasSchema.symbolId(SymbolKind.FIELD, owner, name, descriptor);
    }

    private static String typeTarget(String internalName) {
        return "TYPE:" + internalName;
    }

    private static String dynamicTarget(String name, String descriptor, Handle bootstrapMethodHandle) {
        StringBuilder builder = new StringBuilder(128);
        builder.append("DYNAMIC:").append(name).append(descriptor).append('@');
        if (bootstrapMethodHandle == null) {
            return builder.append("<no-bootstrap>").toString();
        }
        builder.append(bootstrapMethodHandle.getOwner()).append('#')
                .append(bootstrapMethodHandle.getName()).append(bootstrapMethodHandle.getDesc());
        return builder.toString();
    }

    private static String dynamicDetail(Handle bootstrapMethodHandle) {
        if (bootstrapMethodHandle == null) {
            return null;
        }
        return "bootstrapTag=" + bootstrapMethodHandle.getTag()
                + ",bootstrapInterface=" + bootstrapMethodHandle.isInterface();
    }

    private static String constantTarget(Object value) {
        if (value instanceof Integer) {
            return "int:" + value;
        }
        if (value instanceof Long) {
            return "long:" + value;
        }
        if (value instanceof Float) {
            return "float:" + value;
        }
        if (value instanceof Double) {
            return "double:" + value;
        }
        if (value instanceof String) {
            return "string:" + value;
        }
        return null;
    }

    private static boolean isFieldHandle(int tag) {
        return tag == Opcodes.H_GETFIELD || tag == Opcodes.H_GETSTATIC
                || tag == Opcodes.H_PUTFIELD || tag == Opcodes.H_PUTSTATIC;
    }

    private static abstract class RelationshipAccumulator {
        private final String fromId;
        private final String sourcePath;
        private final ScanCounters counters;
        private final Map<String, MutableRelationship> relationships =
                new LinkedHashMap<String, MutableRelationship>();

        private RelationshipAccumulator(String fromId, String sourcePath, ScanCounters counters) {
            this.fromId = fromId;
            this.sourcePath = sourcePath;
            this.counters = counters;
        }

        protected final void record(RelationshipType type, String target, int sourceLine,
                int opcode, String detail) {
            if (target == null || target.length() == 0) {
                return;
            }
            String key = type.name() + "\n" + target;
            MutableRelationship relationship = relationships.get(key);
            Integer lineValue = sourceLine > 0 ? Integer.valueOf(sourceLine) : null;
            Integer opcodeValue = opcode >= 0 ? Integer.valueOf(opcode) : null;
            if (relationship == null) {
                relationships.put(key, new MutableRelationship(type, target, lineValue, opcodeValue, detail));
                return;
            }
            relationship.increment(lineValue, opcodeValue, detail);
        }

        protected final void recordType(Type type, int sourceLine, int opcode) {
            if (type == null) {
                return;
            }
            switch (type.getSort()) {
            case Type.ARRAY:
                recordType(type.getElementType(), sourceLine, opcode);
                break;
            case Type.OBJECT:
                record(RelationshipType.REFERENCES_TYPE, typeTarget(type.getInternalName()),
                        sourceLine, opcode, null);
                break;
            case Type.METHOD:
                Type[] arguments = type.getArgumentTypes();
                for (Type argument : arguments) {
                    recordType(argument, sourceLine, opcode);
                }
                recordType(type.getReturnType(), sourceLine, opcode);
                break;
            default:
                break;
            }
        }

        protected final void recordFieldDescriptor(String descriptor, int sourceLine, int opcode) {
            if (descriptor == null || descriptor.length() == 0) {
                return;
            }
            recordType(Type.getType(descriptor), sourceLine, opcode);
        }

        protected final void recordMethodDescriptor(String descriptor, int sourceLine, int opcode) {
            if (descriptor == null || descriptor.length() == 0) {
                return;
            }
            recordType(Type.getMethodType(descriptor), sourceLine, opcode);
        }

        protected final void recordDescriptorTypes(String descriptor, boolean methodDescriptor) {
            if (methodDescriptor) {
                recordMethodDescriptor(descriptor, -1, -1);
            } else {
                recordFieldDescriptor(descriptor, -1, -1);
            }
        }

        protected final void recordSignatureTypes(String signature, boolean typeOnly) {
            if (signature == null || signature.length() == 0) {
                return;
            }
            SignatureReader reader = new SignatureReader(signature);
            SignatureVisitor visitor = new TypeReferenceSignatureVisitor(this);
            if (typeOnly) {
                reader.acceptType(visitor);
            } else {
                reader.accept(visitor);
            }
        }

        protected final void recordExceptionTypes(String[] exceptions) {
            if (exceptions == null) {
                return;
            }
            for (String exception : exceptions) {
                if (exception != null && exception.length() > 0) {
                    record(RelationshipType.REFERENCES_TYPE, typeTarget(exception), -1, -1, null);
                }
            }
        }

        protected final void recordHandleTypes(Handle handle, int sourceLine, int opcode) {
            if (handle == null) {
                return;
            }
            record(RelationshipType.REFERENCES_TYPE, typeTarget(handle.getOwner()),
                    sourceLine, opcode, null);
            if (isFieldHandle(handle.getTag())) {
                recordFieldDescriptor(handle.getDesc(), sourceLine, opcode);
            } else {
                recordMethodDescriptor(handle.getDesc(), sourceLine, opcode);
            }
        }

        protected final void recordBootstrapValue(Object value, int sourceLine, int opcode) {
            if (value instanceof Type) {
                recordType((Type) value, sourceLine, opcode);
                return;
            }
            if (value instanceof Handle) {
                recordHandleTypes((Handle) value, sourceLine, opcode);
                return;
            }
            if (value instanceof ConstantDynamic) {
                ConstantDynamic dynamic = (ConstantDynamic) value;
                recordFieldDescriptor(dynamic.getDescriptor(), sourceLine, opcode);
                recordHandleTypes(dynamic.getBootstrapMethod(), sourceLine, opcode);
                for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++) {
                    recordBootstrapValue(dynamic.getBootstrapMethodArgument(i), sourceLine, opcode);
                }
                return;
            }
            recordConstant(value, sourceLine, opcode);
        }

        protected final void recordConstant(Object value, int sourceLine, int opcode) {
            String target = constantTarget(value);
            if (target != null) {
                record(RelationshipType.CONSTANT, target, sourceLine, opcode, null);
            }
        }

        protected final void recordConstant(Object value) {
            recordConstant(value, -1, -1);
        }

        protected final void flush() {
            for (MutableRelationship relationship : relationships.values()) {
                counters.writeRelationship(new RelationshipRecord(fromId, relationship.type,
                        relationship.target, sourcePath, relationship.sourceLine,
                        relationship.opcode, relationship.occurrenceCount, relationship.detail));
            }
        }
    }

    private static final class StaticRelationshipAccumulator extends RelationshipAccumulator {
        private StaticRelationshipAccumulator(String fromId, String sourcePath, ScanCounters counters) {
            super(fromId, sourcePath, counters);
        }
    }

    private static final class TypeReferenceSignatureVisitor extends SignatureVisitor {
        private final RelationshipAccumulator accumulator;

        private TypeReferenceSignatureVisitor(RelationshipAccumulator accumulator) {
            super(Opcodes.ASM9);
            this.accumulator = accumulator;
        }

        @Override
        public void visitClassType(String name) {
            accumulator.record(RelationshipType.REFERENCES_TYPE, typeTarget(name), -1, -1, null);
        }
    }

    private static final class MethodRelationshipVisitor extends MethodVisitor {
        private final MethodRelationshipAccumulator accumulator;
        private int currentLine = -1;

        private MethodRelationshipVisitor(String methodId, String sourcePath, ScanCounters counters) {
            super(Opcodes.ASM9);
            accumulator = new MethodRelationshipAccumulator(methodId, sourcePath, counters);
        }

        private void recordMethodDescriptor(String descriptor) {
            accumulator.recordDescriptorTypes(descriptor, true);
        }

        private void recordSignature(String signature) {
            accumulator.recordSignatureTypes(signature, false);
        }

        private void recordExceptions(String[] exceptions) {
            accumulator.recordExceptionTypes(exceptions);
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            currentLine = line;
        }

        @Override
        public void visitInsn(int opcode) {
            switch (opcode) {
            case Opcodes.ICONST_M1:
                accumulator.recordConstant(Integer.valueOf(-1), currentLine, opcode);
                break;
            case Opcodes.ICONST_0:
            case Opcodes.ICONST_1:
            case Opcodes.ICONST_2:
            case Opcodes.ICONST_3:
            case Opcodes.ICONST_4:
            case Opcodes.ICONST_5:
                accumulator.recordConstant(Integer.valueOf(opcode - Opcodes.ICONST_0), currentLine, opcode);
                break;
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
                accumulator.recordConstant(Long.valueOf(opcode - Opcodes.LCONST_0), currentLine, opcode);
                break;
            case Opcodes.FCONST_0:
            case Opcodes.FCONST_1:
            case Opcodes.FCONST_2:
                accumulator.recordConstant(Float.valueOf(opcode - Opcodes.FCONST_0), currentLine, opcode);
                break;
            case Opcodes.DCONST_0:
            case Opcodes.DCONST_1:
                accumulator.recordConstant(Double.valueOf(opcode - Opcodes.DCONST_0), currentLine, opcode);
                break;
            default:
                break;
            }
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                accumulator.recordConstant(Integer.valueOf(operand), currentLine, opcode);
            }
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (type == null || type.length() == 0) {
                return;
            }
            if (type.charAt(0) == '[') {
                accumulator.recordType(Type.getType(type), currentLine, opcode);
            } else {
                accumulator.record(RelationshipType.REFERENCES_TYPE, typeTarget(type),
                        currentLine, opcode, null);
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            RelationshipType type;
            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) {
                type = RelationshipType.READS_FIELD;
            } else if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
                type = RelationshipType.WRITES_FIELD;
            } else {
                return;
            }
            accumulator.record(type, fieldTarget(owner, name, descriptor), currentLine, opcode, null);
            accumulator.record(RelationshipType.REFERENCES_TYPE, typeTarget(owner), currentLine, opcode, null);
            accumulator.recordFieldDescriptor(descriptor, currentLine, opcode);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                boolean isInterface) {
            accumulator.record(RelationshipType.CALLS, methodTarget(owner, name, descriptor),
                    currentLine, opcode, null);
            accumulator.record(RelationshipType.REFERENCES_TYPE, typeTarget(owner), currentLine, opcode, null);
            accumulator.recordMethodDescriptor(descriptor, currentLine, opcode);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                Object... bootstrapMethodArguments) {
            accumulator.record(RelationshipType.DYNAMIC_CALL,
                    dynamicTarget(name, descriptor, bootstrapMethodHandle),
                    currentLine, Opcodes.INVOKEDYNAMIC, dynamicDetail(bootstrapMethodHandle));
            accumulator.recordMethodDescriptor(descriptor, currentLine, Opcodes.INVOKEDYNAMIC);
            accumulator.recordHandleTypes(bootstrapMethodHandle, currentLine, Opcodes.INVOKEDYNAMIC);
            if (bootstrapMethodArguments != null) {
                for (Object argument : bootstrapMethodArguments) {
                    accumulator.recordBootstrapValue(argument, currentLine, Opcodes.INVOKEDYNAMIC);
                }
            }
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof Type) {
                accumulator.recordType((Type) value, currentLine, Opcodes.LDC);
            } else if (value instanceof Handle) {
                accumulator.recordHandleTypes((Handle) value, currentLine, Opcodes.LDC);
            } else if (value instanceof ConstantDynamic) {
                accumulator.recordBootstrapValue(value, currentLine, Opcodes.LDC);
            } else {
                accumulator.recordConstant(value, currentLine, Opcodes.LDC);
            }
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            accumulator.recordType(Type.getType(descriptor), currentLine, Opcodes.MULTIANEWARRAY);
        }

        @Override
        public void visitEnd() {
            accumulator.flush();
        }
    }

    private static final class MethodRelationshipAccumulator extends RelationshipAccumulator {
        private MethodRelationshipAccumulator(String fromId, String sourcePath, ScanCounters counters) {
            super(fromId, sourcePath, counters);
        }
    }

    private static final class MutableRelationship {
        private final RelationshipType type;
        private final String target;
        private Integer sourceLine;
        private Integer opcode;
        private int occurrenceCount = 1;
        private String detail;

        private MutableRelationship(RelationshipType type, String target, Integer sourceLine,
                Integer opcode, String detail) {
            this.type = type;
            this.target = target;
            this.sourceLine = sourceLine;
            this.opcode = opcode;
            this.detail = detail;
        }

        private void increment(Integer line, Integer nextOpcode, String nextDetail) {
            occurrenceCount++;
            if (sourceLine == null && line != null) {
                sourceLine = line;
            }
            if (opcode != null && (nextOpcode == null || !opcode.equals(nextOpcode))) {
                opcode = null;
            }
            if (detail != null && (nextDetail == null || !detail.equals(nextDetail))) {
                detail = null;
            }
        }
    }

    private static final class ScanCounters {
        private final BufferedWriter symbols;
        private final BufferedWriter relationships;
        private long symbolCount;
        private long relationshipCount;

        private ScanCounters(BufferedWriter symbols, BufferedWriter relationships) {
            this.symbols = symbols;
            this.relationships = relationships;
        }

        private void writeSymbol(SymbolRecord record) {
            try {
                symbols.write(AtlasJson.symbol(record));
                symbols.newLine();
                symbolCount++;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private void writeRelationship(RelationshipRecord record) {
            try {
                relationships.write(AtlasJson.relationship(record));
                relationships.newLine();
                relationshipCount++;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    public static final class ScanResult {
        private final int classFileCount;
        private final long symbolCount;
        private final long relationshipCount;
        private final String clientFingerprint;

        private ScanResult(int classFileCount, long symbolCount, long relationshipCount,
                String clientFingerprint) {
            this.classFileCount = classFileCount;
            this.symbolCount = symbolCount;
            this.relationshipCount = relationshipCount;
            this.clientFingerprint = clientFingerprint;
        }

        public int getClassFileCount() {
            return classFileCount;
        }

        public long getSymbolCount() {
            return symbolCount;
        }

        public long getRelationshipCount() {
            return relationshipCount;
        }

        public String getClientFingerprint() {
            return clientFingerprint;
        }
    }
}
