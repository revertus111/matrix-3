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

import game.atlas.AtlasInvestigationIndex.SymbolEntry;
import game.atlas.AtlasSchema.SymbolKind;

/**
 * Ranked/friendly symbol resolver over {@link AtlasInvestigationIndex}.
 *
 * Exact obfuscated Atlas IDs remain authoritative. Friendly search may rank and
 * surface candidates, but it never silently chooses between ambiguous symbols.
 */
public final class AtlasSearchEngine {

    public static final int DEFAULT_LIMIT = 50;

    private final AtlasInvestigationIndex index;

    public AtlasSearchEngine(AtlasInvestigationIndex index) {
        if (index == null) {
            throw new IllegalArgumentException("index cannot be null");
        }
        this.index = index;
    }

    public SearchResult search(String query) {
        return search(query, DEFAULT_LIMIT);
    }

    public SearchResult search(String query, int limit) {
        String value = requireQuery(query);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        long started = System.nanoTime();

        SearchResult canonical = exactCanonical(value, limit, started);
        if (canonical != null) {
            return canonical;
        }

        SearchResult exactClassPath = exactClassPath(value, limit, started);
        if (exactClassPath != null) {
            return exactClassPath;
        }

        List<SymbolEntry> exactName = unique(index.findByName(value));
        if (!exactName.isEmpty()) {
            return exactResult(value, exactName, 900, "exact-name", limit, started);
        }

        MemberQuery memberQuery = MemberQuery.parse(value);
        if (memberQuery != null) {
            List<SymbolEntry> memberMatches = resolveMember(memberQuery);
            if (!memberMatches.isEmpty()) {
                return exactResult(value, memberMatches, 950, "exact-owner-member", limit, started);
            }
        }

        return fuzzy(value, limit, started);
    }

    private SearchResult exactCanonical(String query, int limit, long started) {
        if (!looksCanonical(query)) {
            return null;
        }
        SymbolEntry symbol = index.getSymbol(query);
        if (symbol == null) {
            return new SearchResult(query, Collections.<SearchMatch>emptyList(), 0L,
                    false, null, System.nanoTime() - started);
        }
        SearchMatch match = new SearchMatch(symbol, 1000, "exact-id");
        return new SearchResult(query, Collections.singletonList(match), 1L,
                false, symbol, System.nanoTime() - started);
    }

    private SearchResult exactClassPath(String query, int limit, long started) {
        String owner = normalizeExplicitOwnerPath(query);
        if (owner == null) {
            return null;
        }
        List<SymbolEntry> matches = classSymbols(index.findByOwner(owner));
        if (matches.isEmpty()) {
            return null;
        }
        return exactResult(query, matches, 925, "exact-class-owner", limit, started);
    }

    private List<SymbolEntry> resolveMember(MemberQuery query) {
        Set<String> owners = new LinkedHashSet<String>();
        String explicitOwner = normalizeExplicitOwnerPath(query.owner);
        if (explicitOwner != null) {
            owners.add(explicitOwner);
        } else {
            List<SymbolEntry> ownerCandidates = classSymbols(index.findByName(query.owner));
            for (SymbolEntry candidate : ownerCandidates) {
                owners.add(candidate.getOwner());
            }
        }

        Map<String, SymbolEntry> matches = new LinkedHashMap<String, SymbolEntry>();
        for (String owner : owners) {
            for (SymbolEntry entry : index.findByOwnerAndName(owner, query.memberName)) {
                if (query.descriptor != null && !query.descriptor.equals(entry.getDescriptor())) {
                    continue;
                }
                matches.put(entry.getId(), entry);
            }
        }
        return new ArrayList<SymbolEntry>(matches.values());
    }

    private SearchResult exactResult(String query, List<SymbolEntry> symbols, int score,
            String reason, int limit, long started) {
        List<SearchMatch> matches = new ArrayList<SearchMatch>(symbols.size());
        for (SymbolEntry symbol : symbols) {
            matches.add(new SearchMatch(symbol, score, reason));
        }
        sort(matches);
        long total = matches.size();
        boolean truncated = matches.size() > limit;
        if (truncated) {
            matches = new ArrayList<SearchMatch>(matches.subList(0, limit));
        }
        SymbolEntry resolved = total == 1L ? matches.get(0).getSymbol() : null;
        return new SearchResult(query, matches, total, truncated, resolved,
                System.nanoTime() - started);
    }

    private SearchResult fuzzy(String query, int limit, long started) {
        String normalized = query.toLowerCase(Locale.ROOT);
        Map<String, SearchMatch> byId = new LinkedHashMap<String, SearchMatch>();

        for (SymbolEntry symbol : index.getSymbols()) {
            SearchMatch match = fuzzyMatch(symbol, normalized);
            if (match != null) {
                SearchMatch previous = byId.get(symbol.getId());
                if (previous == null || match.getScore() > previous.getScore()) {
                    byId.put(symbol.getId(), match);
                }
            }
        }

        List<SearchMatch> matches = new ArrayList<SearchMatch>(byId.values());
        sort(matches);
        long total = matches.size();
        boolean truncated = matches.size() > limit;
        if (truncated) {
            matches = new ArrayList<SearchMatch>(matches.subList(0, limit));
        }

        // Fuzzy/partial matching never auto-resolves, even if only one candidate
        // remains. The caller must choose the exact ID explicitly.
        return new SearchResult(query, matches, total, truncated, null,
                System.nanoTime() - started);
    }

    private static SearchMatch fuzzyMatch(SymbolEntry symbol, String query) {
        String name = lower(symbol.getName());
        String owner = lower(symbol.getOwner());
        String simpleOwner = lower(simpleName(symbol.getOwner()));
        String display = lower(displayName(symbol));
        String id = lower(symbol.getId());

        int score = 0;
        String reason = null;

        if (name.startsWith(query)) {
            score = 720;
            reason = "name-prefix";
        } else if (simpleOwner.startsWith(query)) {
            score = 700;
            reason = "owner-prefix";
        } else if (display.startsWith(query)) {
            score = 680;
            reason = "display-prefix";
        } else if (name.contains(query)) {
            score = 560;
            reason = "name-contains";
        } else if (simpleOwner.contains(query)) {
            score = 540;
            reason = "owner-contains";
        } else if (display.contains(query)) {
            score = 520;
            reason = "display-contains";
        } else if (owner.contains(query) || id.contains(query)) {
            score = 480;
            reason = "id-contains";
        }

        if (score == 0) {
            return null;
        }
        if (isClassLike(symbol.getKind())) {
            score += 5;
        }
        return new SearchMatch(symbol, score, reason);
    }

    private static void sort(List<SearchMatch> matches) {
        Collections.sort(matches, new Comparator<SearchMatch>() {
            @Override
            public int compare(SearchMatch left, SearchMatch right) {
                int score = Integer.compare(right.getScore(), left.getScore());
                if (score != 0) {
                    return score;
                }
                return left.getSymbol().getId().compareTo(right.getSymbol().getId());
            }
        });
    }

    private static List<SymbolEntry> classSymbols(List<SymbolEntry> entries) {
        List<SymbolEntry> result = new ArrayList<SymbolEntry>();
        for (SymbolEntry entry : entries) {
            if (isClassLike(entry.getKind())) {
                result.add(entry);
            }
        }
        return unique(result);
    }

    private static List<SymbolEntry> unique(List<SymbolEntry> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, SymbolEntry> unique = new LinkedHashMap<String, SymbolEntry>();
        for (SymbolEntry entry : entries) {
            unique.put(entry.getId(), entry);
        }
        return new ArrayList<SymbolEntry>(unique.values());
    }

    private static boolean isClassLike(SymbolKind kind) {
        return kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE
                || kind == SymbolKind.ENUM || kind == SymbolKind.ANNOTATION;
    }

    private static boolean looksCanonical(String value) {
        for (SymbolKind kind : SymbolKind.values()) {
            if (value.startsWith(kind.name() + ":")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeExplicitOwnerPath(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("game.")) {
            return trimmed.replace('.', '/');
        }
        if (trimmed.indexOf('/') >= 0) {
            return trimmed;
        }
        return null;
    }

    private static String displayName(SymbolEntry symbol) {
        if (isClassLike(symbol.getKind())) {
            return simpleName(symbol.getOwner());
        }
        return simpleName(symbol.getOwner()) + "." + symbol.getName();
    }

    private static String simpleName(String value) {
        if (value == null) {
            return "";
        }
        int slash = value.lastIndexOf('/');
        return slash >= 0 && slash + 1 < value.length() ? value.substring(slash + 1) : value;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String requireQuery(String query) {
        if (query == null || query.trim().length() == 0) {
            throw new IllegalArgumentException("search query cannot be empty");
        }
        return query.trim();
    }

    public static final class SearchResult {
        private final String query;
        private final List<SearchMatch> matches;
        private final long totalMatches;
        private final boolean truncated;
        private final SymbolEntry resolvedSymbol;
        private final long searchNanos;

        private SearchResult(String query, List<SearchMatch> matches, long totalMatches,
                boolean truncated, SymbolEntry resolvedSymbol, long searchNanos) {
            this.query = query;
            this.matches = Collections.unmodifiableList(new ArrayList<SearchMatch>(matches));
            this.totalMatches = totalMatches;
            this.truncated = truncated;
            this.resolvedSymbol = resolvedSymbol;
            this.searchNanos = searchNanos;
        }

        public String getQuery() {
            return query;
        }

        public List<SearchMatch> getMatches() {
            return matches;
        }

        public long getTotalMatches() {
            return totalMatches;
        }

        public boolean isTruncated() {
            return truncated;
        }

        public SymbolEntry getResolvedSymbol() {
            return resolvedSymbol;
        }

        public boolean isResolved() {
            return resolvedSymbol != null;
        }

        public long getSearchNanos() {
            return searchNanos;
        }

        public String toDisplayText() {
            StringBuilder out = new StringBuilder(1024);
            out.append("Search: ").append(query).append('\n');
            out.append("Matches: ").append(totalMatches);
            if (truncated) {
                out.append(" (showing ").append(matches.size()).append(')');
            }
            out.append('\n');
            out.append("Search time: ").append(formatMillis(searchNanos)).append(" ms\n");

            if (resolvedSymbol != null) {
                out.append("Resolved exact symbol: ").append(resolvedSymbol.getId()).append("\n\n");
            } else if (totalMatches > 1L) {
                out.append("Ambiguous: choose an exact Atlas ID from the candidates below.\n\n");
            } else if (totalMatches == 1L) {
                out.append("Partial/fuzzy match only: use the exact Atlas ID below to resolve it.\n\n");
            } else {
                out.append("No matching symbols found.\n");
                return out.toString();
            }

            for (int i = 0; i < matches.size(); i++) {
                SearchMatch match = matches.get(i);
                SymbolEntry symbol = match.getSymbol();
                out.append(i + 1).append(". [").append(match.getScore()).append("] ")
                        .append(symbol.getKind().name()).append(' ')
                        .append(symbol.getId()).append('\n');
                out.append("   match: ").append(match.getReason()).append('\n');
                if (symbol.getSourcePath() != null) {
                    out.append("   source: ").append(symbol.getSourcePath()).append('\n');
                }
            }
            return out.toString();
        }
    }

    public static final class SearchMatch {
        private final SymbolEntry symbol;
        private final int score;
        private final String reason;

        private SearchMatch(SymbolEntry symbol, int score, String reason) {
            this.symbol = symbol;
            this.score = score;
            this.reason = reason;
        }

        public SymbolEntry getSymbol() {
            return symbol;
        }

        public int getScore() {
            return score;
        }

        public String getReason() {
            return reason;
        }
    }

    private static final class MemberQuery {
        private final String owner;
        private final String memberName;
        private final String descriptor;

        private MemberQuery(String owner, String memberName, String descriptor) {
            this.owner = owner;
            this.memberName = memberName;
            this.descriptor = descriptor;
        }

        private static MemberQuery parse(String value) {
            int separator = value.indexOf('#');
            if (separator < 0) {
                separator = value.lastIndexOf('.');
            }
            if (separator <= 0 || separator >= value.length() - 1) {
                return null;
            }

            String owner = value.substring(0, separator).trim();
            String member = value.substring(separator + 1).trim();
            if (owner.length() == 0 || member.length() == 0) {
                return null;
            }

            String descriptor = null;
            int descriptorStart = member.indexOf('(');
            if (descriptorStart > 0) {
                descriptor = member.substring(descriptorStart);
                member = member.substring(0, descriptorStart);
            }
            if (member.length() == 0) {
                return null;
            }
            return new MemberQuery(owner, member, descriptor);
        }
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", Double.valueOf(nanos / 1000000.0D));
    }
}
