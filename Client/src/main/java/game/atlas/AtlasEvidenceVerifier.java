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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import game.atlas.AtlasEvidenceStore.EvidenceView;
import game.atlas.AtlasSchema.EvidenceRecord;
import game.atlas.AtlasSchema.EvidenceStatus;

/**
 * One-shot verification for Bundle 3B curated evidence/knowledge behavior.
 *
 * The check uses an isolated temporary Atlas workspace for evidence writes, so
 * the developer's real evidence.jsonl is never modified.
 */
public final class AtlasEvidenceVerifier {

    private static final String REPORT_FILE = "knowledge-check.txt";

    private final AtlasWorkspace workspace;
    private final Path classRoot;

    public AtlasEvidenceVerifier(AtlasWorkspace workspace, Path classRoot) {
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
            VerificationResult result = new AtlasEvidenceVerifier(workspace, classRoot).run();
            System.out.println(result.getReport());
            System.out.println("Report: " + result.getReportPath());
        } catch (Exception ex) {
            System.err.println("Client Atlas knowledge check failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public VerificationResult run() throws IOException {
        AtlasInvestigationIndex index = AtlasInvestigationIndex.load(workspace, classRoot);
        require(index.getSymbolCount() > 0L, "current Atlas index contains no symbols");

        String subjectId = index.getSymbols().get(0).getId();
        String currentFingerprint = index.getMetadata().getClientFingerprint();
        Path tempRoot = Files.createTempDirectory("matrix3-client-atlas-knowledge-");
        StringBuilder report = new StringBuilder(1536);

        try {
            AtlasWorkspace isolatedWorkspace = new AtlasWorkspace(tempRoot.resolve("Client"));
            isolatedWorkspace.ensureLayout();
            AtlasEvidenceStore store = new AtlasEvidenceStore(isolatedWorkspace);

            List<String> references = Arrays.asList(
                    "source:game/example.java:10",
                    "trace:example-session:event-42",
                    "source:game/example.java:10");
            String alias = "Verifier alias \"quoted\" \\ path";
            String note = "Verifier note line 1\nline 2 with escaped content.";

            EvidenceRecord created = store.upsert(index, subjectId,
                    EvidenceStatus.VERIFIED_STATIC, alias, note, references);
            require(created.getClientFingerprint().equals(currentFingerprint),
                    "new evidence did not bind to the current Atlas fingerprint");
            require(created.getSupportingReferences().size() == 2,
                    "supporting-reference dedupe did not preserve two unique references");

            EvidenceRecord reopened = store.get(subjectId);
            require(reopened != null, "evidence record was not persisted");
            require(alias.equals(reopened.getAlias()), "alias did not round-trip through JSONL");
            require(note.equals(reopened.getClaim()), "note/claim did not round-trip through JSONL");
            require(reopened.getStatus() == EvidenceStatus.VERIFIED_STATIC,
                    "evidence classification did not round-trip through JSONL");
            require(reopened.getSupportingReferences().equals(created.getSupportingReferences()),
                    "supporting references did not round-trip through JSONL");
            report.append("PASS  Alias + note/claim + classification + references JSONL round-trip\n");

            EvidenceView currentView = store.getView(subjectId, index);
            require(currentView != null && currentView.isCurrent(),
                    "fresh evidence did not evaluate as CURRENT");
            require(currentView.getWarning() == null,
                    "fresh evidence unexpectedly produced a warning");
            report.append("PASS  Current-fingerprint evidence evaluates CURRENT\n");

            EvidenceView staleView = AtlasEvidenceStore.evaluate(reopened,
                    "different-client-fingerprint", true);
            require(staleView.isStaleFingerprint(),
                    "fingerprint mismatch did not mark evidence stale");
            require("STALE_FINGERPRINT".equals(staleView.getFreshnessStatus()),
                    "stale evidence status was not explicit");
            require(staleView.getWarning() != null && staleView.getWarning().length() > 0,
                    "stale evidence did not expose a warning");
            report.append("PASS  Fingerprint mismatch produces explicit stale-evidence warning\n");

            EvidenceView missingView = AtlasEvidenceStore.evaluate(reopened,
                    currentFingerprint, false);
            require(!missingView.isSubjectPresent(),
                    "missing-subject simulation did not mark subject absent");
            require("SUBJECT_NOT_PRESENT".equals(missingView.getFreshnessStatus()),
                    "missing subject status was not explicit");
            report.append("PASS  Missing exact subject is retained and flagged for review\n");

            isolatedWorkspace.initialize(classRoot);
            EvidenceRecord preserved = store.get(subjectId);
            require(preserved != null, "Atlas initialize/rescan contract erased curated evidence");
            require(alias.equals(preserved.getAlias()) && note.equals(preserved.getClaim()),
                    "curated evidence changed across Atlas initialize/rescan contract");
            report.append("PASS  Curated evidence survives generated Atlas initialize/rescan\n");

            store.upsert(index, subjectId, EvidenceStatus.HYPOTHESIS,
                    "Updated verifier alias", "Updated verifier note",
                    Collections.singletonList("manual:verification-update"));
            List<EvidenceRecord> afterUpdate = store.load();
            require(afterUpdate.size() == 1,
                    "same-subject upsert created duplicate curated records");
            require(afterUpdate.get(0).getStatus() == EvidenceStatus.HYPOTHESIS,
                    "same-subject upsert did not replace the record");
            require(store.search("updated verifier alias", index).size() == 1,
                    "curated alias search did not find the updated record");
            require(store.search("manual:verification-update", index).size() == 1,
                    "supporting-reference search did not find the updated record");
            report.append("PASS  Deterministic same-subject upsert + curated knowledge search\n");

            require(store.delete(subjectId), "evidence delete did not report a removed record");
            require(store.load().isEmpty(), "evidence delete did not persist");
            report.append("PASS  Curated record delete persists atomically\n");
            report.append("\nBUNDLE 3B KNOWLEDGE CHECK: PASS\n");
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
