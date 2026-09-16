package game.atlas;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Bounded metadata-only catalog of saved Client Atlas runtime traces.
 *
 * This class does not parse event payloads or perform correlation. It only
 * exposes saved trace files for developer-tool browsing while the existing
 * trace recorder/correlation engine remain authoritative for trace semantics.
 */
public final class AtlasTraceCatalog {

    public static final int MAX_VISIBLE_TRACES = 100;

    private AtlasTraceCatalog() {
    }

    public static List<TraceEntry> list(AtlasWorkspace workspace) throws IOException {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace cannot be null");
        }

        workspace.ensureLayout();
        Path directory = workspace.tracesDirectory();
        List<TraceEntry> entries = new ArrayList<TraceEntry>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.trace.jsonl")) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                entries.add(new TraceEntry(
                        path.toAbsolutePath().normalize(),
                        Files.getLastModifiedTime(path).toMillis(),
                        Files.size(path)));
            }
        }

        Collections.sort(entries, new Comparator<TraceEntry>() {
            @Override
            public int compare(TraceEntry left, TraceEntry right) {
                int modified = Long.compare(right.getLastModifiedMillis(), left.getLastModifiedMillis());
                if (modified != 0) {
                    return modified;
                }
                return right.getFileName().compareTo(left.getFileName());
            }
        });

        if (entries.size() > MAX_VISIBLE_TRACES) {
            entries = new ArrayList<TraceEntry>(entries.subList(0, MAX_VISIBLE_TRACES));
        }
        return Collections.unmodifiableList(entries);
    }

    public static final class TraceEntry {
        private final Path path;
        private final long lastModifiedMillis;
        private final long sizeBytes;

        private TraceEntry(Path path, long lastModifiedMillis, long sizeBytes) {
            this.path = path;
            this.lastModifiedMillis = lastModifiedMillis;
            this.sizeBytes = sizeBytes;
        }

        public Path getPath() {
            return path;
        }

        public String getFileName() {
            return path.getFileName().toString();
        }

        public long getLastModifiedMillis() {
            return lastModifiedMillis;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }
    }
}
