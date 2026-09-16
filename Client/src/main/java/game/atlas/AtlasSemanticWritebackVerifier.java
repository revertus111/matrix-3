package game.atlas;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import game.atlas.AtlasEvidenceStore.BatchResult;
import game.atlas.AtlasSchema.EvidenceRecord;
import game.atlas.AtlasSchema.EvidenceStatus;
import game.atlas.AtlasSemanticWriteback.ApplyResult;

/** One-shot offline gate for Bundle 5B semantic batch writeback. */
public final class AtlasSemanticWritebackVerifier {

    private static final String REPORT_FILE = "semantic-writeback-check.txt";
    private static final int CAPACITY_PROBE_RECORDS = 5201;

    private final AtlasWorkspace workspace;
    private final Path classRoot;

    public AtlasSemanticWritebackVerifier(AtlasWorkspace workspace, Path classRoot) {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace cannot be null");
        }
        if (classRoot == null) {
            throw new IllegalArgumentException("classRoot cannot be null");
        }
        this.workspace = workspace;
        this.classRoot = classRoot.toAbsolutePath().normalize();
    }

    public static void main(String[] args) {
        try {
            Path clientRoot = AtlasWorkspace.findClientRoot(Paths.get("."));
            AtlasWorkspace workspace = new AtlasWorkspace(clientRoot);
            Path classRoot = args.length > 0
                    ? Paths.get(args[0]).toAbsolutePath().normalize()
                    : workspace.defaultClassRoot();
            VerificationResult result = new AtlasSemanticWritebackVerifier(workspace, classRoot).run();
            System.out.println(result.getReport());
            System.out.println("Report: " + result.getReportPath());
        } catch (Exception ex) {
            System.err.println("Client Atlas semantic writeback check failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public VerificationResult run() throws IOException {
        AtlasInvestigationIndex index = AtlasInvestigationIndex.load(workspace, classRoot);
        require(index.getSymbols().size() > CAPACITY_PROBE_RECORDS + 8,
                "current Atlas index is too small for the scalable evidence capacity probe");
        String fingerprint = index.getMetadata().getClientFingerprint();
        Path tempRoot = Files.createTempDirectory("matrix3-client-atlas-writeback-");
        StringBuilder report = new StringBuilder(2048);

        try {
            AtlasWorkspace isolatedWorkspace = new AtlasWorkspace(tempRoot.resolve("Client"));
            isolatedWorkspace.ensureLayout();
            AtlasEvidenceStore store = new AtlasEvidenceStore(isolatedWorkspace);
            AtlasSemanticWriteback writeback = new AtlasSemanticWriteback(isolatedWorkspace, index);

            String subject0 = index.getSymbols().get(0).getId();
            String subject1 = index.getSymbols().get(1).getId();
            String subject2 = index.getSymbols().get(2).getId();
            String subject3 = index.getSymbols().get(3).getId();

            List<EvidenceRecord> valid = Arrays.asList(
                    record(subject0, EvidenceStatus.HYPOTHESIS, "Verifier hypothesis",
                            "Plausible mapping used only to verify batch writeback.",
                            Collections.singletonList("static:5b-verifier-hypothesis"), fingerprint),
                    record(subject1, EvidenceStatus.VERIFIED_STATIC, "Verifier static",
                            "Static mapping used only to verify batch writeback.",
                            Collections.singletonList("source:game/verifier.java:1"), fingerprint),
                    record(subject2, EvidenceStatus.VERIFIED, "Verifier runtime",
                            "Runtime mapping used only to verify batch writeback.",
                            Collections.singletonList("runtime:5b-verifier-session"), fingerprint));

            Path validFile = tempRoot.resolve("valid-writeback.jsonl");
            AtlasSemanticWriteback.writeWriteback(validFile, fingerprint, "VERIFY-5B", valid);
            ApplyResult first = writeback.apply(validFile);
            require(first.getBatchResult().getInsertedCount() == 3,
                    "first semantic batch did not insert all three records");
            require(store.load().size() == 3, "semantic batch did not persist three records");

            ApplyResult second = writeback.apply(validFile);
            require(second.getBatchResult().getUpdatedCount() == 3,
                    "reapplying the same exact-ID batch did not deterministically update three records");
            require(store.load().size() == 3,
                    "reapplying the same exact-ID batch created duplicates");
            report.append("PASS  Exact-ID assistant batch applies atomically and upserts deterministically\n");

            byte[] baseline = Files.readAllBytes(isolatedWorkspace.evidenceFile());

            final Path mixedInvalid = tempRoot.resolve("mixed-invalid.jsonl");
            List<EvidenceRecord> mixed = Arrays.asList(
                    record(subject3, EvidenceStatus.HYPOTHESIS, null,
                            "Valid row that must not partially apply before the invalid row.",
                            Collections.singletonList("static:5b-verifier-atomic"), fingerprint),
                    record("METHOD:game/DoesNotExist#missing()V", EvidenceStatus.HYPOTHESIS, null,
                            "Unknown exact ID must reject the whole batch.",
                            Collections.singletonList("static:5b-verifier-unknown"), fingerprint));
            AtlasSemanticWriteback.writeWriteback(mixedInvalid, fingerprint, "VERIFY-ATOMIC", mixed);
            expectIOException(new IoAction() {
                @Override
                public void run() throws IOException {
                    writeback.apply(mixedInvalid);
                }
            }, "mixed valid/unknown writeback");
            require(Arrays.equals(baseline, Files.readAllBytes(isolatedWorkspace.evidenceFile())),
                    "invalid mixed batch partially modified evidence.jsonl");

            final Path staleFile = tempRoot.resolve("stale-writeback.jsonl");
            String staleFingerprint = "stale-" + fingerprint;
            AtlasSemanticWriteback.writeWriteback(staleFile, staleFingerprint, "VERIFY-STALE",
                    Collections.singletonList(record(subject0, EvidenceStatus.HYPOTHESIS, null,
                            "Stale writeback must not apply.",
                            Collections.singletonList("static:5b-verifier-stale"), staleFingerprint)));
            expectIOException(new IoAction() {
                @Override
                public void run() throws IOException {
                    writeback.apply(staleFile);
                }
            }, "stale fingerprint writeback");
            require(Arrays.equals(baseline, Files.readAllBytes(isolatedWorkspace.evidenceFile())),
                    "stale batch modified evidence.jsonl");

            final Path duplicateFile = tempRoot.resolve("duplicate-writeback.jsonl");
            EvidenceRecord duplicate = record(subject0, EvidenceStatus.HYPOTHESIS, null,
                    "Duplicate row must reject the file.",
                    Collections.singletonList("static:5b-verifier-duplicate"), fingerprint);
            writeRaw(duplicateFile, Arrays.asList(
                    header(fingerprint, "VERIFY-DUPLICATE", 2),
                    AtlasJson.evidence(duplicate),
                    AtlasJson.evidence(duplicate)));
            expectIOException(new IoAction() {
                @Override
                public void run() throws IOException {
                    writeback.apply(duplicateFile);
                }
            }, "duplicate-subject writeback");

            final Path badStatusFile = tempRoot.resolve("bad-status-writeback.jsonl");
            String badStatusLine = AtlasJson.evidence(record(subject0, EvidenceStatus.HYPOTHESIS, null,
                    "Invalid classification parser probe.",
                    Collections.singletonList("static:5b-verifier-status"), fingerprint))
                    .replace("\"status\":\"HYPOTHESIS\"", "\"status\":\"AUTOMATICALLY_VERIFIED\"");
            writeRaw(badStatusFile, Arrays.asList(
                    header(fingerprint, "VERIFY-STATUS", 1), badStatusLine));
            expectIOException(new IoAction() {
                @Override
                public void run() throws IOException {
                    writeback.apply(badStatusFile);
                }
            }, "invalid-classification writeback");
            require(Arrays.equals(baseline, Files.readAllBytes(isolatedWorkspace.evidenceFile())),
                    "invalid classification/duplicate validation modified evidence.jsonl");
            report.append("PASS  Stale fingerprints, unknown IDs, duplicate subjects and invalid classifications reject before mutation\n");

            final Path fakeRuntimeFile = tempRoot.resolve("fake-runtime-writeback.jsonl");
            AtlasSemanticWriteback.writeWriteback(fakeRuntimeFile, fingerprint, "VERIFY-PROOF-RUNTIME",
                    Collections.singletonList(record(subject0, EvidenceStatus.VERIFIED, null,
                            "Runtime verification cannot be promoted from source evidence alone.",
                            Collections.singletonList("source:game/verifier.java:2"), fingerprint)));
            expectIOException(new IoAction() {
                @Override
                public void run() throws IOException {
                    writeback.apply(fakeRuntimeFile);
                }
            }, "VERIFIED writeback without runtime proof reference");

            final Path fakeStaticFile = tempRoot.resolve("fake-static-writeback.jsonl");
            AtlasSemanticWriteback.writeWriteback(fakeStaticFile, fingerprint, "VERIFY-PROOF-STATIC",
                    Collections.singletonList(record(subject1, EvidenceStatus.VERIFIED_STATIC, null,
                            "Static verification requires explicit static/source/data evidence.",
                            Collections.singletonList("runtime:wrong-proof-kind"), fingerprint)));
            expectIOException(new IoAction() {
                @Override
                public void run() throws IOException {
                    writeback.apply(fakeStaticFile);
                }
            }, "verified-static writeback without static proof reference");
            require(Arrays.equals(baseline, Files.readAllBytes(isolatedWorkspace.evidenceFile())),
                    "proof-discipline rejection modified evidence.jsonl");
            report.append("PASS  Batch writeback cannot silently promote VERIFIED or verified-static without matching proof references\n");

            AtlasWorkspace capacityWorkspace = new AtlasWorkspace(tempRoot.resolve("CapacityClient"));
            capacityWorkspace.ensureLayout();
            AtlasEvidenceStore capacityStore = new AtlasEvidenceStore(capacityWorkspace);
            List<EvidenceRecord> capacityRecords = new ArrayList<EvidenceRecord>(CAPACITY_PROBE_RECORDS);
            for (int i = 0; i < CAPACITY_PROBE_RECORDS; i++) {
                capacityRecords.add(record(index.getSymbols().get(i).getId(), EvidenceStatus.HYPOTHESIS, null,
                        "Bundle 5B capacity verifier record " + i + ".",
                        Collections.singletonList("static:5b-capacity-verifier"), fingerprint));
            }
            BatchResult capacityResult = capacityStore.upsertBatch(index, fingerprint, capacityRecords);
            require(capacityResult.getInsertedCount() == CAPACITY_PROBE_RECORDS,
                    "capacity batch did not insert every probe record");
            require(capacityStore.load().size() == CAPACITY_PROBE_RECORDS,
                    "evidence store did not retain more than the old 5000-record ceiling");
            require(AtlasEvidenceStore.MAX_RECORDS > index.getSymbols().size(),
                    "new evidence store bound does not cover the current whole-client symbol count");
            require(AtlasEvidenceStore.MAX_RECORDS == 50000,
                    "evidence store bound changed unexpectedly from the reviewed 50000-record limit");
            report.append("PASS  Scalable evidence store exceeds the old 5000-record ceiling and remains explicitly bounded at 50000\n");

            Path snapshotA = tempRoot.resolve("snapshot-a.jsonl");
            Path snapshotB = tempRoot.resolve("snapshot-b.jsonl");
            writeback.writeSnapshot(snapshotA);
            writeback.writeSnapshot(snapshotB);
            byte[] snapshotBytes = Files.readAllBytes(snapshotA);
            require(Arrays.equals(snapshotBytes, Files.readAllBytes(snapshotB)),
                    "semantic snapshot export is not deterministic");
            String snapshotText = new String(snapshotBytes, StandardCharsets.UTF_8);
            require(snapshotText.contains("\"recordType\":\"atlas-semantic-snapshot\""),
                    "semantic snapshot header is missing");
            require(!snapshotText.contains("\"recordType\":\"symbol\"")
                    && !snapshotText.contains("\"fromId\""),
                    "semantic snapshot accidentally exported generated structural records");
            report.append("PASS  Semantic snapshot export is deterministic and contains curated knowledge only\n");

            report.append("\nBUNDLE 5B SEMANTIC WRITEBACK CHECK: PASS\n");
        } finally {
            deleteTree(tempRoot);
        }

        workspace.ensureLayout();
        Path reportPath = workspace.getWorkspaceRoot().resolve(REPORT_FILE);
        Files.write(reportPath, report.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        return new VerificationResult(report.toString(), reportPath);
    }

    private static EvidenceRecord record(String subjectId, EvidenceStatus status, String alias,
            String claim, List<String> references, String fingerprint) {
        return new EvidenceRecord(subjectId, status, alias, claim, references, fingerprint);
    }

    private static String header(String fingerprint, String bundleId, int recordCount) {
        return "{\"recordType\":" + AtlasJson.quote(AtlasSemanticWriteback.WRITEBACK_RECORD_TYPE)
                + ",\"formatVersion\":" + AtlasSemanticWriteback.FORMAT_VERSION
                + ",\"clientFingerprint\":" + AtlasJson.quote(fingerprint)
                + ",\"bundleId\":" + AtlasJson.quote(bundleId)
                + ",\"recordCount\":" + recordCount + "}";
    }

    private static void writeRaw(Path path, List<String> lines) throws IOException {
        Files.write(path, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void expectIOException(IoAction action, String description) throws IOException {
        try {
            action.run();
        } catch (IOException expected) {
            return;
        }
        throw new IOException(description + " unexpectedly succeeded");
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }

    private interface IoAction {
        void run() throws IOException;
    }

    public static final class VerificationResult {
        private final String report;
        private final Path reportPath;

        private VerificationResult(String report, Path reportPath) {
            this.report = report;
            this.reportPath = reportPath;
        }

        public String getReport() {
            return report;
        }

        public Path getReportPath() {
            return reportPath;
        }
    }
}
