package game.atlas;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import game.atlas.AtlasAssistantExportEngine.ExportResult;
import game.atlas.AtlasQueryEngine.QueryResult;
import game.atlas.AtlasRelationshipQueryEngine.RelationshipQueryResult;
import game.atlas.AtlasScanner.ScanResult;
import game.atlas.AtlasSchema.Metadata;
import game.atlas.AtlasSearchEngine.SearchResult;
import game.atlas.AtlasStructuralVerifier.VerificationResult;

/**
 * Offline Client Atlas entry point. Normal game startup remains game.RS3Applet.
 */
public final class ClientAtlasMain {

    private ClientAtlasMain() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Exception ex) {
            System.err.println("Client Atlas failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        if (args.length == 0) {
            ClientAtlasControl.launch();
            return;
        }

        String command = args[0].toLowerCase(Locale.ROOT);
        if ("help".equals(command) || "--help".equals(command) || "-h".equals(command)) {
            printUsage();
            return;
        }

        Path clientRoot = AtlasWorkspace.findClientRoot(Paths.get("."));
        AtlasWorkspace workspace = new AtlasWorkspace(clientRoot);

        if ("scan".equals(command)) {
            Path classRoot = classRoot(workspace, args, 1);
            ScanResult result = new AtlasScanner(workspace).scan(classRoot);
            System.out.println("Client Atlas scan complete.");
            System.out.println("Class files: " + result.getClassFileCount());
            System.out.println("Symbols: " + result.getSymbolCount());
            System.out.println("Relationships: " + result.getRelationshipCount());
            System.out.println("Client fingerprint: " + result.getClientFingerprint());
            return;
        }

        if ("verify-structural".equals(command)) {
            Path classRoot = classRoot(workspace, args, 1);
            VerificationResult result = new AtlasStructuralVerifier(workspace, classRoot).run();
            System.out.println(result.getReport());
            System.out.println("Report: " + result.getReportPath());
            return;
        }

        if ("verify-search".equals(command)) {
            Path classRoot = classRoot(workspace, args, 1);
            AtlasInvestigationVerifier.VerificationResult result =
                    new AtlasInvestigationVerifier(workspace, classRoot).run();
            System.out.println(result.getReport());
            System.out.println("Report: " + result.getReportPath());
            return;
        }

        if ("init".equals(command)) {
            Path classRoot = classRoot(workspace, args, 1);
            Metadata metadata = workspace.initialize(classRoot);
            System.out.println("Client Atlas workspace initialized.");
            printMetadata(workspace, metadata);
            return;
        }

        if ("status".equals(command)) {
            Path classRoot = classRoot(workspace, args, 1);
            Metadata metadata = workspace.readMetadata();
            printMetadata(workspace, metadata);
            System.out.println("Current fingerprint: " + workspace.isCurrent(classRoot));
            return;
        }

        if ("search".equals(command)) {
            requireArgument(args, 1, "search requires a symbol query or relationship command");
            Path classRoot = classRoot(workspace, args, 2);
            AtlasInvestigationIndex index = AtlasInvestigationIndex.load(workspace, classRoot);
            AtlasRelationshipQueryEngine relationships = new AtlasRelationshipQueryEngine(index);
            System.out.println("Investigation index: " + index.getSymbolCount() + " symbols / "
                    + index.getRelationshipCount() + " relationships");
            System.out.println("Index load time: " + formatMillis(index.getLoadNanos()) + " ms");
            if (relationships.isRelationshipCommand(args[1])) {
                RelationshipQueryResult result = relationships.query(args[1]);
                System.out.println(result.toDisplayText());
            } else {
                SearchResult result = new AtlasSearchEngine(index).search(args[1]);
                System.out.println(result.toDisplayText());
            }
            return;
        }

        if ("assistant-json".equals(command)) {
            requireArgument(args, 1, "assistant-json requires a symbol query or relationship command");
            Path classRoot = classRoot(workspace, args, 2);
            AtlasInvestigationIndex index = AtlasInvestigationIndex.load(workspace, classRoot);
            ExportResult result = new AtlasAssistantExportEngine(index).build(args[1]);
            System.out.println(result.toJson());
            return;
        }

        if ("assistant-export".equals(command)) {
            requireArgument(args, 1, "assistant-export requires a symbol query or relationship command");
            requireArgument(args, 2, "assistant-export requires an output file");
            Path classRoot = classRoot(workspace, args, 3);
            AtlasInvestigationIndex index = AtlasInvestigationIndex.load(workspace, classRoot);
            AtlasAssistantExportEngine engine = new AtlasAssistantExportEngine(index);
            ExportResult result = engine.build(args[1]);
            Path output = engine.writeExport(result, Paths.get(args[2]));
            System.out.println("Client Atlas assistant export written: " + output);
            System.out.println("Resolved: " + result.isResolved());
            System.out.println("Relevant symbols: " + result.getRelevantSymbolCount()
                    + (result.isSymbolsTruncated() ? " (output capped)" : ""));
            System.out.println("Relationships: " + result.getTotalRelationshipMatches()
                    + (result.isRelationshipsTruncated() ? " (output capped)" : ""));
            return;
        }

        if ("query".equals(command)) {
            requireArgument(args, 1, "query requires an exact Atlas symbol id");
            Path classRoot = classRoot(workspace, args, 2);
            QueryResult result = new AtlasQueryEngine(workspace).queryExact(args[1], classRoot);
            System.out.println(result.toJson());
            return;
        }

        if ("export".equals(command)) {
            requireArgument(args, 1, "export requires an exact Atlas symbol id");
            requireArgument(args, 2, "export requires an output file");
            Path classRoot = classRoot(workspace, args, 3);
            AtlasQueryEngine engine = new AtlasQueryEngine(workspace);
            QueryResult result = engine.queryExact(args[1], classRoot);
            Path output = engine.writeExport(result, Paths.get(args[2]));
            System.out.println("Client Atlas export written: " + output);
            System.out.println("Immediate relationships: " + result.getRelationshipCount()
                    + (result.isRelationshipsTruncated() ? " (output capped)" : ""));
            return;
        }

        throw new IllegalArgumentException("Unknown Client Atlas command: " + command);
    }

    private static Path classRoot(AtlasWorkspace workspace, String[] args, int argumentIndex) {
        return args.length > argumentIndex
                ? Paths.get(args[argumentIndex]).toAbsolutePath().normalize()
                : workspace.defaultClassRoot();
    }

    private static void requireArgument(String[] args, int index, String message) {
        if (args.length <= index || args[index].trim().length() == 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void printMetadata(AtlasWorkspace workspace, Metadata metadata) {
        System.out.println("Workspace: " + workspace.getWorkspaceRoot());
        System.out.println("Schema version: " + metadata.getSchemaVersion());
        System.out.println("Scan root: " + metadata.getScanRoot());
        System.out.println("Client fingerprint: " + metadata.getClientFingerprint());
        System.out.println("Generated UTC: " + metadata.getGeneratedAtUtc());
        System.out.println("Symbols: " + metadata.getSymbolCount());
        System.out.println("Relationships: " + metadata.getRelationshipCount());
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", Double.valueOf(nanos / 1000000.0D));
    }

    private static void printUsage() {
        System.out.println("Client Atlas offline tool");
        System.out.println("  (no args)                                      Open the standalone Client Atlas Control UI");
        System.out.println("  scan [classes-dir]                              Rebuild the generated Atlas index");
        System.out.println("  verify-structural [classes-dir]                 Rebuild + run structural checks/metrics");
        System.out.println("  verify-search [classes-dir]                     Run investigation/search/export checks without rescanning");
        System.out.println("  status [classes-dir]                            Show persisted metadata and stale/current fingerprint state");
        System.out.println("  init [classes-dir]                              Create/reset metadata for the compiled client class directory");
        System.out.println("  search \"<query-or-command>\" [classes-dir]       Friendly symbol search or relationship investigation");
        System.out.println("     examples: Class387 | Class387.method4844 | calls Class387.method4844");
        System.out.println("               called-by <symbol> | reads <symbol/field> | written-by <field>");
        System.out.println("               references <type/class> | constant 762 | neighbors <symbol> depth=2");
        System.out.println("  assistant-json \"<query-or-command>\" [classes-dir]");
        System.out.println("                                                  Print bounded machine-readable investigation JSON");
        System.out.println("  assistant-export \"<query-or-command>\" <file> [classes-dir]");
        System.out.println("                                                  Write the same assistant package atomically");
        System.out.println("  query \"<atlas-symbol-id>\" [classes-dir]        Print one exact symbol and immediate relationships as compact JSON");
        System.out.println("  export \"<atlas-symbol-id>\" <file> [classes-dir] Write the same compact JSON result to a file");
        System.out.println("  help                                            Show this help");
    }
}
