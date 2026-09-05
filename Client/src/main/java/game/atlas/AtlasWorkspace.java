package game.atlas;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Properties;

import game.atlas.AtlasSchema.Metadata;

/**
 * Local Client Atlas workspace and metadata persistence.
 */
public final class AtlasWorkspace {

    public static final int SCHEMA_VERSION = 2;
    public static final String WORKSPACE_DIRECTORY = ".client-atlas";
    public static final String METADATA_FILE = "metadata.properties";
    public static final String SYMBOLS_FILE = "symbols.jsonl";
    public static final String RELATIONSHIPS_FILE = "relationships.jsonl";
    public static final String EVIDENCE_FILE = "evidence.jsonl";
    public static final String TRACES_DIRECTORY = "traces";

    private final Path clientRoot;
    private final Path workspaceRoot;

    public AtlasWorkspace(Path clientRoot) {
        if (clientRoot == null) {
            throw new IllegalArgumentException("clientRoot cannot be null");
        }
        this.clientRoot = clientRoot.toAbsolutePath().normalize();
        this.workspaceRoot = this.clientRoot.resolve(WORKSPACE_DIRECTORY);
    }

    public static Path findClientRoot(Path start) throws IOException {
        if (start == null) {
            throw new IllegalArgumentException("start cannot be null");
        }

        Path current = start.toAbsolutePath().normalize();
        if (!Files.isDirectory(current)) {
            current = current.getParent();
        }

        while (current != null) {
            if (isClientRoot(current)) {
                return current;
            }

            Path clientChild = current.resolve("Client");
            if (isClientRoot(clientChild)) {
                return clientChild;
            }
            current = current.getParent();
        }

        throw new IOException("Unable to locate the Matrix3 Client project from: " + start);
    }

    public Path defaultClassRoot() {
        return clientRoot.resolve("build/classes/java/main");
    }

    public void ensureLayout() throws IOException {
        Files.createDirectories(workspaceRoot);
        Files.createDirectories(tracesDirectory());
        createIfMissing(evidenceFile());
    }

    public Metadata initialize(Path classRoot) throws IOException {
        Path normalizedClassRoot = requireClassRoot(classRoot);
        ensureLayout();

        resetGeneratedFile(symbolsFile());
        resetGeneratedFile(relationshipsFile());

        Metadata metadata = new Metadata(
                SCHEMA_VERSION,
                AtlasFingerprint.compute(normalizedClassRoot),
                normalizedClassRoot.toString(),
                Instant.now().toString(),
                0L,
                0L);
        writeMetadata(metadata);
        return metadata;
    }

    public Metadata readMetadata() throws IOException {
        Path metadataPath = metadataFile();
        if (!Files.isRegularFile(metadataPath)) {
            throw new IOException("Client Atlas metadata does not exist: " + metadataPath);
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(metadataPath)) {
            properties.load(input);
        }

        int schemaVersion = parsePositiveInt(properties, "schemaVersion");
        String fingerprint = requireProperty(properties, "clientFingerprint");
        String scanRoot = requireProperty(properties, "scanRoot");
        String generatedAtUtc = requireProperty(properties, "generatedAtUtc");
        long symbolCount = parseNonNegativeLong(properties, "symbolCount");
        long relationshipCount = parseNonNegativeLong(properties, "relationshipCount");

        return new Metadata(schemaVersion, fingerprint, scanRoot, generatedAtUtc,
                symbolCount, relationshipCount);
    }

    public boolean isCurrent(Path classRoot) throws IOException {
        Metadata metadata = readMetadata();
        String currentFingerprint = AtlasFingerprint.compute(requireClassRoot(classRoot));
        return metadata.getClientFingerprint().equals(currentFingerprint);
    }

    public void writeMetadata(Metadata metadata) throws IOException {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata cannot be null");
        }
        Files.createDirectories(workspaceRoot);

        Properties properties = new Properties();
        properties.setProperty("schemaVersion", Integer.toString(metadata.getSchemaVersion()));
        properties.setProperty("clientFingerprint", metadata.getClientFingerprint());
        properties.setProperty("scanRoot", metadata.getScanRoot());
        properties.setProperty("generatedAtUtc", metadata.getGeneratedAtUtc());
        properties.setProperty("symbolCount", Long.toString(metadata.getSymbolCount()));
        properties.setProperty("relationshipCount", Long.toString(metadata.getRelationshipCount()));

        Path temp = workspaceRoot.resolve(METADATA_FILE + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) {
            properties.store(output, "Client Atlas metadata - generated; do not edit while Atlas is running");
        }

        try {
            Files.move(temp, metadataFile(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, metadataFile(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path getClientRoot() {
        return clientRoot;
    }

    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    public Path metadataFile() {
        return workspaceRoot.resolve(METADATA_FILE);
    }

    public Path symbolsFile() {
        return workspaceRoot.resolve(SYMBOLS_FILE);
    }

    public Path relationshipsFile() {
        return workspaceRoot.resolve(RELATIONSHIPS_FILE);
    }

    public Path evidenceFile() {
        return workspaceRoot.resolve(EVIDENCE_FILE);
    }

    public Path tracesDirectory() {
        return workspaceRoot.resolve(TRACES_DIRECTORY);
    }

    private static boolean isClientRoot(Path path) {
        return path != null
                && Files.isRegularFile(path.resolve("build.gradle"))
                && Files.isRegularFile(path.resolve("src/main/java/game/RS3Applet.java"));
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

    private static void resetGeneratedFile(Path path) throws IOException {
        Files.write(path, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void createIfMissing(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createFile(path);
        }
    }

    private static String requireProperty(Properties properties, String name) throws IOException {
        String value = properties.getProperty(name);
        if (value == null || value.length() == 0) {
            throw new IOException("Client Atlas metadata is missing property: " + name);
        }
        return value;
    }

    private static int parsePositiveInt(Properties properties, String name) throws IOException {
        String value = requireProperty(properties, name);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException("not positive");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid Client Atlas metadata property " + name + ": " + value, ex);
        }
    }

    private static long parseNonNegativeLong(Properties properties, String name) throws IOException {
        String value = requireProperty(properties, name);
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0L) {
                throw new NumberFormatException("negative");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid Client Atlas metadata property " + name + ": " + value, ex);
        }
    }
}
