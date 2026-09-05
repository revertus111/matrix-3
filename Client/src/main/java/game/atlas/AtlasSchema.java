package game.atlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stable data model shared by the offline Client Atlas scanner, persistence,
 * search, and later evidence tooling.
 */
public final class AtlasSchema {

    private AtlasSchema() {
    }

    public enum SymbolKind {
        CLASS,
        INTERFACE,
        ENUM,
        ANNOTATION,
        FIELD,
        METHOD,
        CONSTRUCTOR
    }

    public enum RelationshipType {
        EXTENDS,
        IMPLEMENTS,
        DECLARES,
        REFERENCES_TYPE,
        CALLS,
        DYNAMIC_CALL,
        READS_FIELD,
        WRITES_FIELD,
        CONSTANT,
        LITERAL_ID
    }

    public enum EvidenceStatus {
        VERIFIED("VERIFIED"),
        VERIFIED_STATIC("verified-static"),
        HYPOTHESIS("HYPOTHESIS"),
        UNKNOWN("UNKNOWN");

        private final String wireValue;

        EvidenceStatus(String wireValue) {
            this.wireValue = wireValue;
        }

        public String getWireValue() {
            return wireValue;
        }
    }

    public static final class SymbolRecord {
        private final String id;
        private final SymbolKind kind;
        private final String owner;
        private final String name;
        private final String descriptor;
        private final String signature;
        private final String compiledPath;
        private final String sourcePath;
        private final int access;

        public SymbolRecord(SymbolKind kind, String owner, String name, String descriptor,
                String signature, String compiledPath, int access) {
            this(kind, owner, name, descriptor, signature, compiledPath, null, access);
        }

        public SymbolRecord(SymbolKind kind, String owner, String name, String descriptor,
                String signature, String compiledPath, String sourcePath, int access) {
            this.kind = require(kind, "kind");
            this.owner = requireText(owner, "owner");
            this.name = requireText(name, "name");
            this.descriptor = descriptor == null ? "" : descriptor;
            this.signature = signature;
            this.compiledPath = compiledPath;
            this.sourcePath = sourcePath;
            this.access = access;
            this.id = symbolId(kind, owner, name, this.descriptor);
        }

        public String getId() {
            return id;
        }

        public SymbolKind getKind() {
            return kind;
        }

        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public String getDescriptor() {
            return descriptor;
        }

        public String getSignature() {
            return signature;
        }

        public String getCompiledPath() {
            return compiledPath;
        }

        public String getSourcePath() {
            return sourcePath;
        }

        public int getAccess() {
            return access;
        }
    }

    public static final class RelationshipRecord {
        private final String fromId;
        private final RelationshipType type;
        private final String target;
        private final String sourcePath;
        private final Integer sourceLine;
        private final Integer opcode;
        private final int occurrenceCount;
        private final String detail;

        public RelationshipRecord(String fromId, RelationshipType type, String target, String detail) {
            this(fromId, type, target, null, null, null, 1, detail);
        }

        public RelationshipRecord(String fromId, RelationshipType type, String target,
                String sourcePath, Integer sourceLine, Integer opcode, int occurrenceCount,
                String detail) {
            this.fromId = requireText(fromId, "fromId");
            this.type = require(type, "type");
            this.target = requireText(target, "target");
            if (sourceLine != null && sourceLine.intValue() <= 0) {
                throw new IllegalArgumentException("sourceLine must be positive when present");
            }
            if (opcode != null && opcode.intValue() < 0) {
                throw new IllegalArgumentException("opcode cannot be negative when present");
            }
            if (occurrenceCount <= 0) {
                throw new IllegalArgumentException("occurrenceCount must be positive");
            }
            this.sourcePath = sourcePath;
            this.sourceLine = sourceLine;
            this.opcode = opcode;
            this.occurrenceCount = occurrenceCount;
            this.detail = detail;
        }

        public String getFromId() {
            return fromId;
        }

        public RelationshipType getType() {
            return type;
        }

        public String getTarget() {
            return target;
        }

        public String getSourcePath() {
            return sourcePath;
        }

        public Integer getSourceLine() {
            return sourceLine;
        }

        public Integer getOpcode() {
            return opcode;
        }

        public int getOccurrenceCount() {
            return occurrenceCount;
        }

        public String getDetail() {
            return detail;
        }
    }

    public static final class EvidenceRecord {
        private final String subjectId;
        private final EvidenceStatus status;
        private final String alias;
        private final String claim;
        private final List<String> supportingReferences;
        private final String clientFingerprint;

        public EvidenceRecord(String subjectId, EvidenceStatus status, String alias, String claim,
                List<String> supportingReferences, String clientFingerprint) {
            this.subjectId = requireText(subjectId, "subjectId");
            this.status = require(status, "status");
            this.alias = alias;
            this.claim = requireText(claim, "claim");
            this.supportingReferences = supportingReferences == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<String>(supportingReferences));
            this.clientFingerprint = requireText(clientFingerprint, "clientFingerprint");
        }

        public String getSubjectId() {
            return subjectId;
        }

        public EvidenceStatus getStatus() {
            return status;
        }

        public String getAlias() {
            return alias;
        }

        public String getClaim() {
            return claim;
        }

        public List<String> getSupportingReferences() {
            return supportingReferences;
        }

        public String getClientFingerprint() {
            return clientFingerprint;
        }
    }

    public static final class Metadata {
        private final int schemaVersion;
        private final String clientFingerprint;
        private final String scanRoot;
        private final String generatedAtUtc;
        private final long symbolCount;
        private final long relationshipCount;

        public Metadata(int schemaVersion, String clientFingerprint, String scanRoot,
                String generatedAtUtc, long symbolCount, long relationshipCount) {
            if (schemaVersion <= 0) {
                throw new IllegalArgumentException("schemaVersion must be positive");
            }
            if (symbolCount < 0L || relationshipCount < 0L) {
                throw new IllegalArgumentException("record counts cannot be negative");
            }
            this.schemaVersion = schemaVersion;
            this.clientFingerprint = requireText(clientFingerprint, "clientFingerprint");
            this.scanRoot = requireText(scanRoot, "scanRoot");
            this.generatedAtUtc = requireText(generatedAtUtc, "generatedAtUtc");
            this.symbolCount = symbolCount;
            this.relationshipCount = relationshipCount;
        }

        public int getSchemaVersion() {
            return schemaVersion;
        }

        public String getClientFingerprint() {
            return clientFingerprint;
        }

        public String getScanRoot() {
            return scanRoot;
        }

        public String getGeneratedAtUtc() {
            return generatedAtUtc;
        }

        public long getSymbolCount() {
            return symbolCount;
        }

        public long getRelationshipCount() {
            return relationshipCount;
        }
    }

    public static String symbolId(SymbolKind kind, String owner, String name, String descriptor) {
        require(kind, "kind");
        String normalizedOwner = requireText(owner, "owner");
        String normalizedName = requireText(name, "name");
        String normalizedDescriptor = descriptor == null ? "" : descriptor;

        if (kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE
                || kind == SymbolKind.ENUM || kind == SymbolKind.ANNOTATION) {
            return kind.name() + ":" + normalizedOwner;
        }
        return kind.name() + ":" + normalizedOwner + "#" + normalizedName + normalizedDescriptor;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException(name + " cannot be empty");
        }
        return value;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
        return value;
    }
}
