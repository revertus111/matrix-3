package game.atlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import game.atlas.AtlasInvestigationIndex.RelationshipEntry;
import game.atlas.AtlasInvestigationIndex.SymbolEntry;
import game.atlas.AtlasSchema.RelationshipType;
import game.atlas.AtlasSchema.SymbolKind;
import game.atlas.AtlasSearchEngine.SearchResult;

/**
 * Relationship investigation queries over the immutable in-memory Atlas index.
 *
 * This layer filters and traverses already-recorded bytecode facts. It does not
 * scan classes or assign semantic meaning to generic constants/relationships.
 */
public final class AtlasRelationshipQueryEngine {

    public static final int MAX_EDGES = 500;
    public static final int MAX_NODES = 100;
    public static final int MAX_DEPTH = 2;

    private final AtlasInvestigationIndex index;
    private final AtlasSearchEngine search;

    public AtlasRelationshipQueryEngine(AtlasInvestigationIndex index) {
        if (index == null) {
            throw new IllegalArgumentException("index cannot be null");
        }
        this.index = index;
        this.search = new AtlasSearchEngine(index);
    }

    public boolean isRelationshipCommand(String input) {
        if (input == null) {
            return false;
        }
        String command = firstToken(input.trim()).toLowerCase(Locale.ROOT);
        return "calls".equals(command)
                || "called-by".equals(command)
                || "reads".equals(command)
                || "written-by".equals(command)
                || "references".equals(command)
                || "constant".equals(command)
                || "neighbors".equals(command)
                || "neighbours".equals(command)
                || "near".equals(command);
    }

    public RelationshipQueryResult query(String input) {
        String value = requireInput(input);
        String command = firstToken(value).toLowerCase(Locale.ROOT);
        String operand = remainder(value);
        if (operand.length() == 0) {
            throw new IllegalArgumentException(command + " requires a symbol, type, or literal operand");
        }

        if ("calls".equals(command)) {
            return relationshipForSymbol(command, operand, RelationshipType.CALLS, QueryDirection.OUTGOING, true);
        }
        if ("called-by".equals(command)) {
            return relationshipForSymbol(command, operand, RelationshipType.CALLS, QueryDirection.INCOMING, true);
        }
        if ("reads".equals(command)) {
            return reads(operand);
        }
        if ("written-by".equals(command)) {
            return writtenBy(operand);
        }
        if ("references".equals(command)) {
            return references(operand);
        }
        if ("constant".equals(command)) {
            return constant(operand);
        }
        if ("neighbors".equals(command) || "neighbours".equals(command) || "near".equals(command)) {
            return neighborhood(command, operand);
        }
        throw new IllegalArgumentException("Unknown Client Atlas relationship command: " + command);
    }

    private RelationshipQueryResult reads(String operand) {
        SearchResult resolution = search.search(operand);
        if (!resolution.isResolved()) {
            return unresolved("reads", operand, resolution);
        }
        SymbolEntry symbol = resolution.getResolvedSymbol();
        QueryDirection direction = symbol.getKind() == SymbolKind.FIELD
                ? QueryDirection.INCOMING : QueryDirection.OUTGOING;
        return collectForResolved("reads", operand, symbol, RelationshipType.READS_FIELD,
                direction, true, resolution);
    }

    private RelationshipQueryResult writtenBy(String operand) {
        SearchResult resolution = search.search(operand);
        if (!resolution.isResolved()) {
            return unresolved("written-by", operand, resolution);
        }
        SymbolEntry symbol = resolution.getResolvedSymbol();
        if (symbol.getKind() != SymbolKind.FIELD) {
            return message("written-by", operand, resolution,
                    "`written-by` expects a FIELD. Resolved operand was " + symbol.getKind().name()
                            + ": " + symbol.getId());
        }
        return collectForResolved("written-by", operand, symbol, RelationshipType.WRITES_FIELD,
                QueryDirection.INCOMING, false, resolution);
    }

    private RelationshipQueryResult references(String operand) {
        long started = System.nanoTime();
        if (operand.startsWith("TYPE:")) {
            EdgeSelection selection = select(index.incoming(operand), RelationshipType.REFERENCES_TYPE);
            return result("references", operand, operand, null, selection,
                    System.nanoTime() - started, null);
        }

        SearchResult resolution = search.search(operand);
        if (!resolution.isResolved()) {
            return unresolved("references", operand, resolution);
        }
        SymbolEntry symbol = resolution.getResolvedSymbol();
        if (!isClassLike(symbol.getKind())) {
            return message("references", operand, resolution,
                    "`references` expects a class/interface/enum/annotation or TYPE:<internal-name>. "
                            + "Resolved operand was " + symbol.getKind().name() + ": " + symbol.getId());
        }

        String target = "TYPE:" + symbol.getOwner();
        EdgeSelection selection = select(index.incoming(target), RelationshipType.REFERENCES_TYPE);
        return result("references", operand, target, symbol, selection,
                System.nanoTime() - started, resolution);
    }

    private RelationshipQueryResult constant(String operand) {
        long started = System.nanoTime();
        List<String> targets = constantTargets(operand);
        List<RelationshipEntry> matches = new ArrayList<RelationshipEntry>();
        long total = 0L;
        boolean truncated = false;
        Set<String> seen = new LinkedHashSet<String>();

        for (String target : targets) {
            for (RelationshipEntry entry : index.constantReferrers(target)) {
                total++;
                String key = edgeKey(entry);
                if (matches.size() < MAX_EDGES && seen.add(key)) {
                    matches.add(entry);
                } else if (matches.size() >= MAX_EDGES) {
                    truncated = true;
                }
            }
        }

        EdgeSelection selection = new EdgeSelection(matches, total,
                truncated || total > matches.size());
        String targetLabel = join(targets, ", ");
        return result("constant", operand, targetLabel, null, selection,
                System.nanoTime() - started, null);
    }

    private RelationshipQueryResult neighborhood(String command, String rawOperand) {
        NeighborhoodRequest request = NeighborhoodRequest.parse(rawOperand);
        SearchResult resolution = search.search(request.operand);
        if (!resolution.isResolved()) {
            return unresolved(command, request.operand, resolution);
        }

        long started = System.nanoTime();
        SymbolEntry root = resolution.getResolvedSymbol();
        Set<String> nodes = new LinkedHashSet<String>();
        Set<String> frontier = new LinkedHashSet<String>();
        Map<String, RelationshipEntry> edges = new LinkedHashMap<String, RelationshipEntry>();
        nodes.add(root.getId());
        frontier.add(root.getId());
        boolean truncated = false;

        for (int depth = 0; depth < request.depth && !frontier.isEmpty(); depth++) {
            Set<String> next = new LinkedHashSet<String>();
            for (String node : frontier) {
                List<RelationshipEntry> around = new ArrayList<RelationshipEntry>();
                around.addAll(index.outgoing(node));
                around.addAll(index.incoming(node));

                for (RelationshipEntry edge : around) {
                    String key = edgeKey(edge);
                    if (!edges.containsKey(key)) {
                        if (edges.size() >= MAX_EDGES) {
                            truncated = true;
                            continue;
                        }
                        edges.put(key, edge);
                    }

                    String other = node.equals(edge.getFromId()) ? edge.getTarget() : edge.getFromId();
                    if (!nodes.contains(other)) {
                        if (nodes.size() >= MAX_NODES) {
                            truncated = true;
                            continue;
                        }
                        nodes.add(other);
                        if (index.getSymbol(other) != null) {
                            next.add(other);
                        }
                    }
                }
            }
            frontier = next;
        }

        List<RelationshipEntry> edgeList = new ArrayList<RelationshipEntry>(edges.values());
        EdgeSelection selection = new EdgeSelection(edgeList, edgeList.size(), truncated);
        return new RelationshipQueryResult(command, request.operand, root.getId(), root,
                selection.records, selection.totalCount, selection.truncated,
                request.depth, nodes.size(), resolution, null, System.nanoTime() - started);
    }

    private RelationshipQueryResult relationshipForSymbol(String command, String operand,
            RelationshipType type, QueryDirection direction, boolean expandClasses) {
        SearchResult resolution = search.search(operand);
        if (!resolution.isResolved()) {
            return unresolved(command, operand, resolution);
        }
        return collectForResolved(command, operand, resolution.getResolvedSymbol(), type,
                direction, expandClasses, resolution);
    }

    private RelationshipQueryResult collectForResolved(String command, String operand,
            SymbolEntry symbol, RelationshipType type, QueryDirection direction,
            boolean expandClasses, SearchResult resolution) {
        long started = System.nanoTime();
        List<String> subjects = new ArrayList<String>();
        subjects.add(symbol.getId());

        if (expandClasses && isClassLike(symbol.getKind())) {
            subjects.clear();
            for (RelationshipEntry relation : index.outgoing(symbol.getId())) {
                if (relation.getType() == RelationshipType.DECLARES
                        && index.getSymbol(relation.getTarget()) != null) {
                    SymbolEntry declared = index.getSymbol(relation.getTarget());
                    if (declared.getKind() == SymbolKind.METHOD
                            || declared.getKind() == SymbolKind.CONSTRUCTOR) {
                        subjects.add(declared.getId());
                    }
                }
            }
        }

        EdgeSelection selection = selectAcross(subjects, type, direction);
        return result(command, operand, symbol.getId(), symbol, selection,
                System.nanoTime() - started, resolution);
    }

    private EdgeSelection selectAcross(List<String> subjects, RelationshipType type,
            QueryDirection direction) {
        List<RelationshipEntry> matches = new ArrayList<RelationshipEntry>();
        Set<String> seen = new LinkedHashSet<String>();
        long total = 0L;
        boolean truncated = false;

        for (String subject : subjects) {
            List<RelationshipEntry> source = direction == QueryDirection.OUTGOING
                    ? index.outgoing(subject) : index.incoming(subject);
            for (RelationshipEntry entry : source) {
                if (entry.getType() != type) {
                    continue;
                }
                total++;
                String key = edgeKey(entry);
                if (matches.size() < MAX_EDGES && seen.add(key)) {
                    matches.add(entry);
                } else if (matches.size() >= MAX_EDGES) {
                    truncated = true;
                }
            }
        }
        return new EdgeSelection(matches, total, truncated || total > matches.size());
    }

    private static EdgeSelection select(List<RelationshipEntry> source, RelationshipType type) {
        List<RelationshipEntry> matches = new ArrayList<RelationshipEntry>();
        long total = 0L;
        for (RelationshipEntry entry : source) {
            if (entry.getType() != type) {
                continue;
            }
            total++;
            if (matches.size() < MAX_EDGES) {
                matches.add(entry);
            }
        }
        return new EdgeSelection(matches, total, total > matches.size());
    }

    private RelationshipQueryResult unresolved(String command, String operand, SearchResult resolution) {
        return new RelationshipQueryResult(command, operand, null, null,
                Collections.<RelationshipEntry>emptyList(), 0L, false,
                0, 0, resolution, null, resolution.getSearchNanos());
    }

    private RelationshipQueryResult message(String command, String operand,
            SearchResult resolution, String message) {
        return new RelationshipQueryResult(command, operand,
                resolution.isResolved() ? resolution.getResolvedSymbol().getId() : null,
                resolution.getResolvedSymbol(), Collections.<RelationshipEntry>emptyList(),
                0L, false, 0, 0, resolution, message, resolution.getSearchNanos());
    }

    private static RelationshipQueryResult result(String command, String operand,
            String resolvedTarget, SymbolEntry symbol, EdgeSelection selection,
            long queryNanos, SearchResult resolution) {
        return new RelationshipQueryResult(command, operand, resolvedTarget, symbol,
                selection.records, selection.totalCount, selection.truncated,
                0, 0, resolution, null, queryNanos);
    }

    private static List<String> constantTargets(String operand) {
        String value = operand.trim();
        if (value.startsWith("int:") || value.startsWith("long:")
                || value.startsWith("float:") || value.startsWith("double:")
                || value.startsWith("string:")) {
            return Collections.singletonList(value);
        }

        if (isInteger(value)) {
            List<String> targets = new ArrayList<String>();
            targets.add("int:" + value);
            targets.add("long:" + value);
            return targets;
        }
        if (isDecimal(value)) {
            List<String> targets = new ArrayList<String>();
            targets.add("float:" + value);
            targets.add("double:" + value);
            return targets;
        }
        return Collections.singletonList("string:" + value);
    }

    private static boolean isInteger(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isDecimal(String value) {
        try {
            Double.parseDouble(value);
            return value.indexOf('.') >= 0 || value.indexOf('e') >= 0 || value.indexOf('E') >= 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isClassLike(SymbolKind kind) {
        return kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE
                || kind == SymbolKind.ENUM || kind == SymbolKind.ANNOTATION;
    }

    private static String edgeKey(RelationshipEntry edge) {
        return edge.getFromId() + '\u0000' + edge.getType().name() + '\u0000' + edge.getTarget();
    }

    private static String firstToken(String value) {
        int space = value.indexOf(' ');
        return space < 0 ? value : value.substring(0, space);
    }

    private static String remainder(String value) {
        int space = value.indexOf(' ');
        return space < 0 ? "" : value.substring(space + 1).trim();
    }

    private static String requireInput(String input) {
        if (input == null || input.trim().length() == 0) {
            throw new IllegalArgumentException("relationship query cannot be empty");
        }
        return input.trim();
    }

    private static String join(List<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append(separator);
            }
            out.append(value);
        }
        return out.toString();
    }

    private enum QueryDirection {
        OUTGOING,
        INCOMING
    }

    private static final class EdgeSelection {
        private final List<RelationshipEntry> records;
        private final long totalCount;
        private final boolean truncated;

        private EdgeSelection(List<RelationshipEntry> records, long totalCount, boolean truncated) {
            this.records = records;
            this.totalCount = totalCount;
            this.truncated = truncated;
        }
    }

    private static final class NeighborhoodRequest {
        private final String operand;
        private final int depth;

        private NeighborhoodRequest(String operand, int depth) {
            this.operand = operand;
            this.depth = depth;
        }

        private static NeighborhoodRequest parse(String raw) {
            String value = raw.trim();
            int depth = 1;
            String marker = " depth=";
            int markerIndex = value.toLowerCase(Locale.ROOT).lastIndexOf(marker);
            if (markerIndex >= 0) {
                String depthText = value.substring(markerIndex + marker.length()).trim();
                value = value.substring(0, markerIndex).trim();
                try {
                    depth = Integer.parseInt(depthText);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("neighbors depth must be 1 or 2");
                }
            }
            if (value.length() == 0) {
                throw new IllegalArgumentException("neighbors requires a symbol operand");
            }
            if (depth < 1 || depth > MAX_DEPTH) {
                throw new IllegalArgumentException("neighbors depth must be between 1 and " + MAX_DEPTH);
            }
            return new NeighborhoodRequest(value, depth);
        }
    }

    public static final class RelationshipQueryResult {
        private final String command;
        private final String operand;
        private final String resolvedTarget;
        private final SymbolEntry resolvedSymbol;
        private final List<RelationshipEntry> relationships;
        private final long totalMatches;
        private final boolean truncated;
        private final int depth;
        private final int nodeCount;
        private final SearchResult resolution;
        private final String message;
        private final long queryNanos;

        private RelationshipQueryResult(String command, String operand, String resolvedTarget,
                SymbolEntry resolvedSymbol, List<RelationshipEntry> relationships,
                long totalMatches, boolean truncated, int depth, int nodeCount,
                SearchResult resolution, String message, long queryNanos) {
            this.command = command;
            this.operand = operand;
            this.resolvedTarget = resolvedTarget;
            this.resolvedSymbol = resolvedSymbol;
            this.relationships = Collections.unmodifiableList(new ArrayList<RelationshipEntry>(relationships));
            this.totalMatches = totalMatches;
            this.truncated = truncated;
            this.depth = depth;
            this.nodeCount = nodeCount;
            this.resolution = resolution;
            this.message = message;
            this.queryNanos = queryNanos;
        }

        public String getCommand() { return command; }
        public String getOperand() { return operand; }
        public String getResolvedTarget() { return resolvedTarget; }
        public SymbolEntry getResolvedSymbol() { return resolvedSymbol; }
        public List<RelationshipEntry> getRelationships() { return relationships; }
        public long getTotalMatches() { return totalMatches; }
        public boolean isTruncated() { return truncated; }
        public int getDepth() { return depth; }
        public int getNodeCount() { return nodeCount; }
        public SearchResult getResolution() { return resolution; }
        public long getQueryNanos() { return queryNanos; }

        public boolean needsExactSelection() {
            return resolution != null && !resolution.isResolved();
        }

        public String toDisplayText() {
            if (message != null) {
                return message + "\n";
            }
            if (needsExactSelection()) {
                return "Relationship command needs an exact symbol before traversal.\n\n"
                        + resolution.toDisplayText();
            }

            StringBuilder out = new StringBuilder(2048);
            out.append("Command: ").append(command).append(' ').append(operand).append('\n');
            if (resolvedTarget != null) {
                out.append("Resolved: ").append(resolvedTarget).append('\n');
            }
            if (depth > 0) {
                out.append("Depth: ").append(depth).append('\n');
                out.append("Nodes: ").append(nodeCount);
                if (nodeCount >= MAX_NODES && truncated) {
                    out.append(" (cap ").append(MAX_NODES).append(')');
                }
                out.append('\n');
            }
            out.append("Relationships: ").append(totalMatches);
            if (truncated) {
                out.append(" (showing ").append(relationships.size())
                        .append(", cap ").append(MAX_EDGES).append(')');
            }
            out.append('\n');
            out.append("Query time: ").append(formatMillis(queryNanos)).append(" ms\n\n");

            if (relationships.isEmpty()) {
                out.append("No matching relationships found.\n");
                return out.toString();
            }

            for (int i = 0; i < relationships.size(); i++) {
                RelationshipEntry edge = relationships.get(i);
                out.append(i + 1).append(". ").append(edge.getType().name()).append(' ')
                        .append(edge.getFromId()).append(" -> ").append(edge.getTarget());
                if (edge.getOccurrenceCount() > 1) {
                    out.append(" x").append(edge.getOccurrenceCount());
                }
                out.append('\n');
                if (edge.getSourcePath() != null) {
                    out.append("   source: ").append(edge.getSourcePath());
                    if (edge.hasSourceLine()) {
                        out.append(':').append(edge.getSourceLine());
                    }
                    out.append('\n');
                }
                if (edge.hasOpcode()) {
                    out.append("   opcode: ").append(edge.getOpcode()).append('\n');
                }
            }
            return out.toString();
        }
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", Double.valueOf(nanos / 1000000.0D));
    }
}
