package game.atlas;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;

import game.atlas.AtlasMappingQueue.MappingBundle;
import game.atlas.AtlasMappingQueue.MappingPlan;
import game.atlas.AtlasMappingQueue.PrioritySymbol;
import game.atlas.AtlasScanner.ScanResult;

/**
 * One-shot offline verification for the semantic mapping/coverage queue.
 *
 * The verifier rebuilds only the generated Atlas index, preserves curated
 * evidence, validates deterministic whole-scope partitioning, and writes the
 * next bounded assistant mapping bundle.
 */
public final class AtlasMappingVerifier {

    private static final String REPORT_FILE = "mapping-check.txt";

    private final AtlasWorkspace workspace;
    private final Path classRoot;

    public AtlasMappingVerifier(AtlasWorkspace workspace, Path classRoot) {
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
            VerificationResult result = new AtlasMappingVerifier(workspace, classRoot).run();
            System.out.println(result.getReport());
            System.out.println("Report: " + result.getReportPath());
            System.out.println("Next bundle: " + result.getNextBundlePath());
        } catch (Exception ex) {
            System.err.println("Client Atlas mapping check failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public VerificationResult run() throws IOException {
        ScanResult scan = new AtlasScanner(workspace).scan(classRoot);
        AtlasInvestigationIndex index = AtlasInvestigationIndex.load(workspace, classRoot);
        require(index.getSymbolCount() > 0L, "current Atlas index contains no symbols");

        AtlasEvidenceStore store = new AtlasEvidenceStore(workspace);
        AtlasMappingQueue queue = new AtlasMappingQueue(index, store);
        MappingPlan first = queue.build();
        MappingPlan second = queue.build();

        StringBuilder report = new StringBuilder(2048);
        report.append("Client Atlas mapping queue verification\n\n");
        report.append("Scan classes: ").append(scan.getClassFileCount()).append('\n');
        report.append("Atlas symbols: ").append(index.getSymbolCount()).append('\n');
        report.append("Atlas relationships: ").append(index.getRelationshipCount()).append('\n');
        report.append("Mapping fingerprint: ").append(first.getFingerprint()).append("\n\n");

        int partitioned = first.getRuntimeVerifiedCount()
                + first.getStaticVerifiedCount()
                + first.getHypothesisCount()
                + first.getUnknownCount()
                + first.getStaleCount();
        require(partitioned == first.getScopedSymbolCount(),
                "coverage states do not partition the scoped symbol set");
        report.append("PASS  Coverage states partition every scoped symbol exactly once\n");

        Set<String> owners = new HashSet<String>();
        int bundleSymbols = 0;
        for (MappingBundle bundle : first.getBundles()) {
            require(bundle.getOwners().size() > 0,
                    "mapping bundle contains no owners: " + bundle.getId());
            require(bundle.getOwners().size() <= AtlasMappingQueue.MAX_OWNERS_PER_BUNDLE,
                    "mapping bundle exceeds owner cap: " + bundle.getId());
            bundleSymbols += bundle.getSymbolCount();
            for (String owner : bundle.getOwners()) {
                require(AtlasMappingQueue.isMappingScopeOwner(owner),
                        "Atlas-owned tooling leaked into semantic mapping scope: " + owner);
                require(owners.add(owner),
                        "owner appears in more than one mapping bundle: " + owner);
            }
            require(bundle.getPrioritySymbols().size() <= AtlasMappingQueue.MAX_PRIORITY_SYMBOLS,
                    "priority symbol export exceeds cap: " + bundle.getId());
        }
        require(owners.size() == first.getOwnerCount(),
                "mapping bundles do not cover every scoped owner exactly once");
        require(bundleSymbols == first.getScopedSymbolCount(),
                "bundle symbol totals do not equal scoped symbol coverage");
        report.append("PASS  Structural bundles cover each scoped owner and symbol exactly once\n");
        report.append("PASS  Bundle owner/symbol output remains bounded\n");
        report.append("PASS  Atlas/Client Console tooling is excluded from self-mapping scope\n");

        require(first.deterministicKey().equals(second.deterministicKey()),
                "mapping queue changed across identical consecutive builds");
        report.append("PASS  Queue ordering + structural clustering are deterministic\n");

        MappingBundle next = first.getNextBundle();
        if (first.getPendingSymbolCount() > 0) {
            require(next != null, "pending semantic symbols exist but no next bundle was produced");
            require(!next.getPrioritySymbols().isEmpty(),
                    "next mapping bundle has pending symbols but no priority exact IDs");
            for (PrioritySymbol symbol : next.getPrioritySymbols()) {
                require(symbol.getSymbolId() != null && symbol.getSymbolId().length() > 0,
                        "priority mapping symbol is missing exact Atlas ID");
            }
        }

        workspace.ensureLayout();
        Path nextBundlePath = workspace.getWorkspaceRoot().resolve(AtlasMappingQueue.NEXT_BUNDLE_FILE);
        queue.writeNextBundle(first, nextBundlePath);
        require(Files.isRegularFile(nextBundlePath), "next mapping bundle export was not written");
        long nextBytes = Files.size(nextBundlePath);
        require(nextBytes > 0L, "next mapping bundle export is empty");
        require(nextBytes <= 262144L,
                "next mapping bundle export exceeds 256 KiB bounded assistant target");
        report.append("PASS  Next assistant bundle export is exact-ID based and <= 256 KiB\n");

        report.append("\nCoverage snapshot\n");
        report.append("  Scoped symbols: ").append(first.getScopedSymbolCount()).append('\n');
        report.append("  Verified runtime: ").append(first.getRuntimeVerifiedCount()).append('\n');
        report.append("  Verified static: ").append(first.getStaticVerifiedCount()).append('\n');
        report.append("  Hypotheses: ").append(first.getHypothesisCount()).append('\n');
        report.append("  Stale: ").append(first.getStaleCount()).append('\n');
        report.append("  Unknown: ").append(first.getUnknownCount()).append('\n');
        report.append("  Orphan evidence: ").append(first.getOrphanEvidenceCount()).append('\n');
        report.append("  Owners: ").append(first.getOwnerCount()).append('\n');
        report.append("  Bundles: ").append(first.getBundles().size()).append('\n');
        if (next != null) {
            report.append("  Next: ").append(next.getId())
                    .append(" seed=").append(next.getSeedOwner())
                    .append(" owners=").append(next.getOwners().size())
                    .append(" symbols=").append(next.getSymbolCount()).append('\n');
        }
        report.append("\nBUNDLE 5A MAPPING QUEUE CHECK: PASS\n");

        Path reportPath = workspace.getWorkspaceRoot().resolve(REPORT_FILE);
        Files.write(reportPath, report.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        return new VerificationResult(report.toString(), reportPath, nextBundlePath);
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }

    public static final class VerificationResult {
        private final String report;
        private final Path reportPath;
        private final Path nextBundlePath;

        private VerificationResult(String report, Path reportPath, Path nextBundlePath) {
            this.report = report;
            this.reportPath = reportPath;
            this.nextBundlePath = nextBundlePath;
        }

        public String getReport() {
            return report;
        }

        public Path getReportPath() {
            return reportPath;
        }

        public Path getNextBundlePath() {
            return nextBundlePath;
        }
    }
}
