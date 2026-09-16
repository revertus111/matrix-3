package game.atlas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import game.atlas.AtlasInvestigationIndex.RelationshipEntry;
import game.atlas.AtlasInvestigationIndex.SymbolEntry;
import game.atlas.AtlasSchema.EvidenceStatus;

/**
 * Safe domain-aware candidate correlation over already-recorded constants.
 *
 * A requested domain is a search hint only. Generic constants remain generic
 * structural facts and are never promoted to LITERAL_ID or verified semantics
 * by this engine.
 */
public final class AtlasDomainCorrelationEngine {

    public static final int MAX_CANDIDATES = 50;
    public static final int MAX_RELATIONSHIPS = 200;

    private static final Set<String> DOMAINS;

    static {
        Set<String> domains = new LinkedHashSet<String>();
        domains.add("interface");
        domains.add("component");
        domains.add("packet");
        domains.add("opcode");
        domains.add("npc");
        domains.add("item");
        domains.add("object");
        domains.add("player");
        domains.add("cache");
        domains.add("model");
        domains.add("animation");
        domains.add("gfx");
        domains.add("sprite");
        domains.add("projectile");
        domains.add("particle");
        domains.add("varp");
        domains.add("varbit");
        domains.add("container");
        domains.add("inventory");
        domains.add("equipment");
        domains.add("menu");
        domains.add("camera");
        domains.add("input");
        domains.add("rendering");
        DOMAINS = Collections.unmodifiableSet(domains);
    }

    private final AtlasInvestigationIndex index;

    public AtlasDomainCorrelationEngine(AtlasInvestigationIndex index) {
        if (index == null) {
            throw new IllegalArgumentException("index cannot be null");
        }
        this.index = index;
    }

    public boolean isDomainQuery(String input) {
        if (input == null) {
            return false;
        }
        String value = input.trim();
        if (value.length() == 0) {
            return false;
        }
        if (bareIntegerPair(value) != null) {
            return true;
        }
        int space = value.indexOf(' ');
        if (space <= 0 || space >= value.length() - 1) {
            return false;
        }
        return canonicalDomain(value.substring(0, space)) != null;
    }

    public DomainCorrelationResult query(String input) {
        DomainRequest request = DomainRequest.parse(input);
        long started = System.nanoTime();
        Selection selection = request.secondaryValue == null
                ? selectSingle(request.value)
                : selectPair(request.value, request.secondaryValue);
        return new DomainCorrelationResult(request.original, request.domain,
                request.value, request.secondaryValue,
                request.secondaryValue == null ? "constant-occurrence" : "same-symbol-constant-cooccurrence",
                constantTargets(request.value),
                request.secondaryValue == null
                        ? Collections.<String>emptyList()
                        : constantTargets(request.secondaryValue),
                selection.candidates, selection.totalCandidates,
                selection.candidatesTruncated, selection.relationships,
                selection.totalRelationships, selection.relationshipsTruncated,
                System.nanoTime() - started);
    }

    private Selection selectSingle(String value) {
        List<RelationshipEntry> all = collectConstantRelationships(constantTargets(value));
        Map<String, List<RelationshipEntry>> bySource = groupBySource(all);
        return buildSelection(bySource, 500, "constant-occurrence");
    }

    private Selection selectPair(String firstValue, String secondValue) {
        Map<String, List<RelationshipEntry>> first = groupBySource(
                collectConstantRelationships(constantTargets(firstValue)));
        Map<String, List<RelationshipEntry>> second = groupBySource(
                collectConstantRelationships(constantTargets(secondValue)));

        Map<String, List<RelationshipEntry>> matched = new LinkedHashMap<String, List<RelationshipEntry>>();
        for (Map.Entry<String, List<RelationshipEntry>> entry : first.entrySet()) {
            List<RelationshipEntry> secondEdges = second.get(entry.getKey());
            if (secondEdges == null || secondEdges.isEmpty()) {
                continue;
            }
            Map<String, RelationshipEntry> unique = new LinkedHashMap<String, RelationshipEntry>();
            addUnique(unique, entry.getValue());
            addUnique(unique, secondEdges);
            matched.put(entry.getKey(), new ArrayList<RelationshipEntry>(unique.values()));
        }
        return buildSelection(matched, 900, "same-symbol-constant-cooccurrence");
    }

    private Selection buildSelection(Map<String, List<RelationshipEntry>> bySource,
            int score, String reason) {
        List<DomainCandidate> allCandidates = new ArrayList<DomainCandidate>();
        long totalRelationships = 0L;
        for (Map.Entry<String, List<RelationshipEntry>> entry : bySource.entrySet()) {
            List<RelationshipEntry> edges = entry.getValue();
            totalRelationships += edges.size();
            SymbolEntry symbol = index.getSymbol(entry.getKey());
            String sourcePath = symbol == null ? firstSourcePath(edges) : symbol.getSourcePath();
            allCandidates.add(new DomainCandidate(entry.getKey(), score, reason,
                    edges.size(), sourcePath, edges));
        }

        Collections.sort(allCandidates, new Comparator<DomainCandidate>() {
            @Override
            public int compare(DomainCandidate left, DomainCandidate right) {
                int relationshipCount = Integer.compare(right.getRelationshipCount(), left.getRelationshipCount());
                if (relationshipCount != 0) {
                    return relationshipCount;
                }
                return left.getSubjectId().compareTo(right.getSubjectId());
            }
        });

        long totalCandidates = allCandidates.size();
        boolean candidatesTruncated = allCandidates.size() > MAX_CANDIDATES;
        List<DomainCandidate> shownCandidates = candidatesTruncated
                ? new ArrayList<DomainCandidate>(allCandidates.subList(0, MAX_CANDIDATES))
                : new ArrayList<DomainCandidate>(allCandidates);

        Map<String, RelationshipEntry> shownRelationships = new LinkedHashMap<String, RelationshipEntry>();
        boolean relationshipsTruncated = false;
        for (DomainCandidate candidate : shownCandidates) {
            for (RelationshipEntry edge : candidate.getRelationships()) {
                String key = edgeKey(edge);
                if (shownRelationships.containsKey(key)) {
                    continue;
                }
                if (shownRelationships.size() >= MAX_RELATIONSHIPS) {
                    relationshipsTruncated = true;
                    continue;
                }
                shownRelationships.put(key, edge);
            }
        }
        relationshipsTruncated |= totalRelationships > shownRelationships.size();

        return new Selection(shownCandidates, totalCandidates, candidatesTruncated,
                new ArrayList<RelationshipEntry>(shownRelationships.values()),
                totalRelationships, relationshipsTruncated);
    }

    private List<RelationshipEntry> collectConstantRelationships(List<String> targets) {
        Map<String, RelationshipEntry> unique = new LinkedHashMap<String, RelationshipEntry>();
        for (String target : targets) {
            addUnique(unique, index.constantReferrers(target));
        }
        return new ArrayList<RelationshipEntry>(unique.values());
    }

    private static Map<String, List<RelationshipEntry>> groupBySource(List<RelationshipEntry> relationships) {
        Map<String, List<RelationshipEntry>> grouped = new LinkedHashMap<String, List<RelationshipEntry>>();
        for (RelationshipEntry edge : relationships) {
            List<RelationshipEntry> edges = grouped.get(edge.getFromId());
            if (edges == null) {
                edges = new ArrayList<RelationshipEntry>();
                grouped.put(edge.getFromId(), edges);
            }
            edges.add(edge);
        }
        return grouped;
    }

    private static void addUnique(Map<String, RelationshipEntry> unique,
            List<RelationshipEntry> relationships) {
        for (RelationshipEntry edge : relationships) {
            unique.put(edgeKey(edge), edge);
        }
    }

    private static String firstSourcePath(List<RelationshipEntry> edges) {
        for (RelationshipEntry edge : edges) {
            if (edge.getSourcePath() != null) {
                return edge.getSourcePath();
            }
        }
        return null;
    }

    private static String edgeKey(RelationshipEntry edge) {
        return edge.getFromId() + '\u0000' + edge.getType().name() + '\u0000' + edge.getTarget();
    }

    private static String canonicalDomain(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if ("anim".equals(value)) {
            return "animation";
        }
        if ("interface-component".equals(value)) {
            return value;
        }
        return DOMAINS.contains(value) ? value : null;
    }

    private static String[] bareIntegerPair(String value) {
        int colon = value.indexOf(':');
        if (colon <= 0 || colon != value.lastIndexOf(':') || colon >= value.length() - 1) {
            return null;
        }
        String left = value.substring(0, colon).trim();
        String right = value.substring(colon + 1).trim();
        if (parseLong(left) == null || parseLong(right) == null) {
            return null;
        }
        return new String[] { left, right };
    }

    private static List<String> constantTargets(String operand) {
        String value = operand.trim();
        if (value.startsWith("int:") || value.startsWith("long:")
                || value.startsWith("float:") || value.startsWith("double:")
                || value.startsWith("string:")) {
            return Collections.singletonList(value);
        }

        Long integer = parseLong(value);
        if (integer != null) {
            List<String> targets = new ArrayList<String>();
            long number = integer.longValue();
            if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                targets.add("int:" + Integer.toString((int) number));
            }
            targets.add("long:" + Long.toString(number));
            return targets;
        }

        Double decimal = parseDecimal(value);
        if (decimal != null) {
            List<String> targets = new ArrayList<String>();
            float floatValue = Float.parseFloat(value);
            if (!Float.isInfinite(floatValue) && !Float.isNaN(floatValue)) {
                targets.add("float:" + Float.toString(floatValue));
            }
            targets.add("double:" + Double.toString(decimal.doubleValue()));
            return targets;
        }
        return Collections.singletonList("string:" + value);
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double parseDecimal(String value) {
        if (value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0) {
            return null;
        }
        try {
            double number = Double.parseDouble(value);
            return Double.isInfinite(number) || Double.isNaN(number) ? null : Double.valueOf(number);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value);
        }
        return out.toString();
    }

    private static final class DomainRequest {
        private final String original;
        private final String domain;
        private final String value;
        private final String secondaryValue;

        private DomainRequest(String original, String domain, String value, String secondaryValue) {
            this.original = original;
            this.domain = domain;
            this.value = value;
            this.secondaryValue = secondaryValue;
        }

        private static DomainRequest parse(String input) {
            if (input == null || input.trim().length() == 0) {
                throw new IllegalArgumentException("domain correlation query cannot be empty");
            }
            String original = input.trim();
            String[] barePair = bareIntegerPair(original);
            if (barePair != null) {
                return new DomainRequest(original, "interface-component", barePair[0], barePair[1]);
            }

            int space = original.indexOf(' ');
            if (space <= 0 || space >= original.length() - 1) {
                throw new IllegalArgumentException("domain query must be like `interface 762`, `animation 1234`, or `762:7`");
            }
            String domain = canonicalDomain(original.substring(0, space));
            if (domain == null) {
                throw new IllegalArgumentException("unsupported Client Atlas domain: " + original.substring(0, space));
            }
            String operand = original.substring(space + 1).trim();
            String[] pair = bareIntegerPair(operand);
            if (pair != null) {
                return new DomainRequest(original, domain, pair[0], pair[1]);
            }
            if (operand.length() == 0) {
                throw new IllegalArgumentException("domain query requires a value");
            }
            return new DomainRequest(original, domain, operand, null);
        }
    }

    private static final class Selection {
        private final List<DomainCandidate> candidates;
        private final long totalCandidates;
        private final boolean candidatesTruncated;
        private final List<RelationshipEntry> relationships;
        private final long totalRelationships;
        private final boolean relationshipsTruncated;

        private Selection(List<DomainCandidate> candidates, long totalCandidates,
                boolean candidatesTruncated, List<RelationshipEntry> relationships,
                long totalRelationships, boolean relationshipsTruncated) {
            this.candidates = candidates;
            this.totalCandidates = totalCandidates;
            this.candidatesTruncated = candidatesTruncated;
            this.relationships = relationships;
            this.totalRelationships = totalRelationships;
            this.relationshipsTruncated = relationshipsTruncated;
        }
    }

    public static final class DomainCandidate {
        private final String subjectId;
        private final int score;
        private final String reason;
        private final int relationshipCount;
        private final String sourcePath;
        private final List<RelationshipEntry> relationships;

        private DomainCandidate(String subjectId, int score, String reason,
                int relationshipCount, String sourcePath, List<RelationshipEntry> relationships) {
            this.subjectId = subjectId;
            this.score = score;
            this.reason = reason;
            this.relationshipCount = relationshipCount;
            this.sourcePath = sourcePath;
            this.relationships = Collections.unmodifiableList(new ArrayList<RelationshipEntry>(relationships));
        }

        public String getSubjectId() { return subjectId; }
        public int getScore() { return score; }
        public String getReason() { return reason; }
        public int getRelationshipCount() { return relationshipCount; }
        public String getSourcePath() { return sourcePath; }
        public List<RelationshipEntry> getRelationships() { return relationships; }
    }

    public static final class DomainCorrelationResult {
        private final String query;
        private final String requestedDomain;
        private final String value;
        private final String secondaryValue;
        private final String correlationBasis;
        private final List<String> typedTargets;
        private final List<String> secondaryTypedTargets;
        private final List<DomainCandidate> candidates;
        private final long totalCandidates;
        private final boolean candidatesTruncated;
        private final List<RelationshipEntry> relationships;
        private final long totalRelationships;
        private final boolean relationshipsTruncated;
        private final long queryNanos;

        private DomainCorrelationResult(String query, String requestedDomain,
                String value, String secondaryValue, String correlationBasis,
                List<String> typedTargets, List<String> secondaryTypedTargets,
                List<DomainCandidate> candidates, long totalCandidates,
                boolean candidatesTruncated, List<RelationshipEntry> relationships,
                long totalRelationships, boolean relationshipsTruncated, long queryNanos) {
            this.query = query;
            this.requestedDomain = requestedDomain;
            this.value = value;
            this.secondaryValue = secondaryValue;
            this.correlationBasis = correlationBasis;
            this.typedTargets = Collections.unmodifiableList(new ArrayList<String>(typedTargets));
            this.secondaryTypedTargets = Collections.unmodifiableList(new ArrayList<String>(secondaryTypedTargets));
            this.candidates = Collections.unmodifiableList(new ArrayList<DomainCandidate>(candidates));
            this.totalCandidates = totalCandidates;
            this.candidatesTruncated = candidatesTruncated;
            this.relationships = Collections.unmodifiableList(new ArrayList<RelationshipEntry>(relationships));
            this.totalRelationships = totalRelationships;
            this.relationshipsTruncated = relationshipsTruncated;
            this.queryNanos = queryNanos;
        }

        public String getQuery() { return query; }
        public String getRequestedDomain() { return requestedDomain; }
        public String getValue() { return value; }
        public String getSecondaryValue() { return secondaryValue; }
        public String getCorrelationBasis() { return correlationBasis; }
        public String getSemanticStatus() { return EvidenceStatus.UNKNOWN.getWireValue(); }
        public boolean isLiteralIdPromoted() { return false; }
        public List<String> getTypedTargets() { return typedTargets; }
        public List<String> getSecondaryTypedTargets() { return secondaryTypedTargets; }
        public List<DomainCandidate> getCandidates() { return candidates; }
        public long getTotalCandidates() { return totalCandidates; }
        public boolean isCandidatesTruncated() { return candidatesTruncated; }
        public List<RelationshipEntry> getRelationships() { return relationships; }
        public long getTotalRelationships() { return totalRelationships; }
        public boolean isRelationshipsTruncated() { return relationshipsTruncated; }
        public long getQueryNanos() { return queryNanos; }

        public String toDisplayText() {
            StringBuilder out = new StringBuilder(2048);
            out.append("Domain candidate search: ").append(query).append('\n');
            out.append("Requested domain: ").append(requestedDomain).append('\n');
            out.append("Semantic status: ").append(getSemanticStatus()).append('\n');
            out.append("Basis: ").append(correlationBasis).append('\n');
            out.append("Typed constant target(s): ").append(join(typedTargets)).append('\n');
            if (!secondaryTypedTargets.isEmpty()) {
                out.append("Secondary target(s): ").append(join(secondaryTypedTargets)).append('\n');
            }
            out.append("LITERAL_ID promoted: false\n");
            out.append("Candidates: ").append(totalCandidates);
            if (candidatesTruncated) {
                out.append(" (showing ").append(candidates.size()).append(", cap ").append(MAX_CANDIDATES).append(')');
            }
            out.append('\n');
            out.append("Relationships: ").append(totalRelationships);
            if (relationshipsTruncated) {
                out.append(" (showing ").append(relationships.size()).append(", cap ").append(MAX_RELATIONSHIPS).append(')');
            }
            out.append('\n');
            out.append("Query time: ").append(String.format(Locale.ROOT, "%.3f",
                    Double.valueOf(queryNanos / 1000000.0D))).append(" ms\n\n");
            out.append("NOTE: the requested domain is a search hint only. These are structural constant candidates, not verified domain IDs.\n");

            for (int i = 0; i < candidates.size(); i++) {
                DomainCandidate candidate = candidates.get(i);
                out.append(i + 1).append(". [").append(candidate.getScore()).append("] ")
                        .append(candidate.getSubjectId()).append('\n');
                out.append("   match: ").append(candidate.getReason())
                        .append(" | constant edges: ").append(candidate.getRelationshipCount()).append('\n');
                if (candidate.getSourcePath() != null) {
                    out.append("   source: ").append(candidate.getSourcePath()).append('\n');
                }
            }
            return out.toString();
        }
    }
}
