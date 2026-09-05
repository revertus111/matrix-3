package game.atlas;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import game.atlas.AtlasSchema.Metadata;

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
        String command = args.length == 0 ? "status" : args[0].toLowerCase();
        if ("help".equals(command) || "--help".equals(command) || "-h".equals(command)) {
            printUsage();
            return;
        }

        Path clientRoot = AtlasWorkspace.findClientRoot(Paths.get("."));
        AtlasWorkspace workspace = new AtlasWorkspace(clientRoot);
        Path classRoot = args.length >= 2
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : workspace.defaultClassRoot();

        if ("init".equals(command)) {
            Metadata metadata = workspace.initialize(classRoot);
            System.out.println("Client Atlas workspace initialized.");
            printMetadata(workspace, metadata);
            return;
        }

        if ("status".equals(command)) {
            Metadata metadata = workspace.readMetadata();
            printMetadata(workspace, metadata);
            System.out.println("Current fingerprint: " + workspace.isCurrent(classRoot));
            return;
        }

        throw new IllegalArgumentException("Unknown Client Atlas command: " + command);
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

    private static void printUsage() {
        System.out.println("Client Atlas offline tool");
        System.out.println("  status [classes-dir]  Show persisted metadata and stale/current fingerprint state");
        System.out.println("  init [classes-dir]    Create/reset metadata for the compiled client class directory");
        System.out.println("  help                  Show this help");
    }
}
