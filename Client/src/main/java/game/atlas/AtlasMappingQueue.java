package game.atlas;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import game.atlas.AtlasEvidenceStore.EvidenceView;
import game.atlas.AtlasInvestigationIndex.RelationshipEntry;
import game.atlas.AtlasInvestigationIndex.SymbolEntry;
import game.atlas.AtlasSchema.EvidenceRecord;
import game.atlas.AtlasSchema.EvidenceStatus;

/**
 * Deterministic whole-client semantic mapping queue over the current Atlas
 * structure + curated evidence.
 *
 * The queue does not invent semantics. It partitions the mapped client into
 * bounded structural owner clusters, measures current evidence coverage, and
 * exports the next assistant investigation bundle using exact Atlas IDs.
 */
public final class AtlasMappingQueue {

    public static final int MAX_OWNERS_PER_BUNDLE = 8;
    public static final int MAX_PRIORITY_SYMBOLS = 80;
    public static final int MAX_BOUNDARY_OWNERS = 12;
    public static final int MAX_EVIDENCE_SUMMARIES = 40;
    public static final String NEXT_BUNDLE_FILE = "mapping-next.md";

    private final AtlasInvestigationIndex index;
    private final AtlasEvidenceStore evidenceStore;

    public AtlasMappingQueue(AtlasInvestigationIndex index, AtlasEvidenceStore evidenceStore) {
        if (index == null) {
            throw new IllegalArgumentException("index cannot be null");
        }
        if (evidenceStore == null) {
            throw new IllegalArgumentException("evidenceStore cannot be null");
        }
        this.index = index;
        this.evidenceStore = evidenceStore;
    }

    public static void main(String[] args) {
        try {
            Path clientRoot = AtlasWorkspace.findClientRoot(Paths.get("."));
            AtlasWorkspace workspace = new AtlasWorkspace(clientRoot);
            Path classRoot = args.length > 0
                    ? Paths.get(args[0]).toAbsolutePath().normalize()
                    : workspace.defaultClassRoot();
            AtlasInvestigationIndex index = loadCurrentIndex(workspace, classRoot, true);
            AtlasMappingQueue queue = new AtlasMappingQueue(index, new AtlasEvidenceStore(workspace));
            MappingPlan plan = queue.build();
            Path output = workspace.getWorkspaceRoot().resolve(NEXT_BUNDLE_FILE);
            queue.writeNextBundle(plan, output);
            System.out.println(plan.toDisplayText());
            System.out.println("Next mapping bundle: " + output);
        } catch (Exception ex) {
            System.err.println("Client Atlas mapping queue failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static AtlasInvestigationIndex loadCurrentIndex(AtlasWorkspace workspace, Path classRoot,
            boolean rebuildIfNeeded) throws IOException {
        boolean current = false;
        if (Files.isDirectory(classRoot) && Files.isRegularFile(workspace.metadataFile())) {
            try {
                current = workspace.isCurrent(classRoot);
            } catch (IOException ex) {
                current = false;
            }
        }
        if (!current && rebuildIfNeeded) {
            new AtlasScanner(workspace).scan(classRoot);
        }
        return AtlasInvestigationIndex.load(workspace, classRoot);
    }

    public MappingPlan build() throws IOException {
        List<EvidenceView> evidenceViews = evidenceStore.inspect(index);
        Map<String, EvidenceView> evidenceBySubject = new HashMap<String, EvidenceView>();
        int orphanEvidence = 0;
        for (EvidenceView view : evidenceViews) {
            evidenceBySubject.put(view.getRecord().getSubjectId(), view);
            if (!view.isSubjectPresent()) {
                orphanEvidence++;
            }
        }

        Map<String, OwnerBuilder> owners = new LinkedHashMap<String, OwnerBuilder>();
        Map<String, MappingState> stateBySymbol = new HashMap<String, MappingState>();
        int scopedSymbols = 0;
        int runtimeVerified = 0;
        int staticVerified = 0;
        int hypotheses = 0;
        int unknown = 0;
        int stale = 0;

        for (SymbolEntry symbol : index.getSymbols()) {
            if (!isMappingScope(symbol)) {
                continue;
            }
            scopedSymbols++;
            OwnerBuilder owner = owners.get(symbol.getOwner());
            if (owner == null) {
                owner = new OwnerBuilder(symbol.getOwner());
                owners.put(symbol.getOwner(), owner);
            }
            owner.symbols.add(symbol);

            MappingState state = stateFor(symbol.getId(), evidenceBySubject);
            stateBySymbol.put(symbol.getId(), state);
            owner.increment(state);
            switch (state) {
            case VERIFIED_RUNTIME:
                runtimeVerified++;
                break;
            case VERIFIED_STATIC:
                staticVerified++;
                break;
            case HYPOTHESIS:
                hypotheses++;
                break;
            case STALE:
                stale++;
                break;
            case UNKNOWN:
            default:
                unknown++;
                break;
            }
        }

        for (SymbolEntry sourceSymbol : index.getSymbols()) {
            if (!isMappingScope(sourceSymbol)) {
                continue;
            }
            OwnerBuilder source = owners.get(sourceSymbol.getOwner());
            if (source == null) {
                continue;
            }
            for (RelationshipEntry relationship : index.outgoing(sourceSymbol.getId())) {
                SymbolEntry targetSymbol = index.getSymbol(relationship.getTarget());
                if (targetSymbol == null || !isMappingScope(targetSymbol)) {
                    continue;
                }
                if (sourceSymbol.getOwner().equals(targetSymbol.getOwner())) {
                    continue;
                }
                OwnerBuilder target = owners.get(targetSymbol.getOwner());
                if (target == null) {
                    continue;
                }
                int weight = Math.max(1, relationship.getOccurrenceCount());
                source.externalConnections += weight;
                target.externalConnections += weight;
                addWeight(source.adjacency, target.owner, weight);
                addWeight(target.adjacency, source.owner, weight);
            }
        }

        List<OwnerBuilder> queueOrder = new ArrayList<OwnerBuilder>(owners.values());
        for (OwnerBuilder owner : queueOrder) {
            owner.priorityScore = priority(owner);
        }
        Collections.sort(queueOrder, new Comparator<OwnerBuilder>() {
            @Override
            public int compare(OwnerBuilder left, OwnerBuilder right) {
                int score = Long.compare(right.priorityScore, left.priorityScore);
                return score != 0 ? score : left.owner.compareTo(right.owner);
            }
        });

        List<MappingBundle> bundles = new ArrayList<MappingBundle>();
        Set<String> assigned = new HashSet<String>();
        int bundleNumber = 1;
        for (OwnerBuilder seed : queueOrder) {
            if (assigned.contains(seed.owner)) {
                continue;
            }
            List<OwnerBuilder> members = new ArrayList<OwnerBuilder>();
            members.add(seed);
            assigned.add(seed.owner);

            while (members.size() < MAX_OWNERS_PER_BUNDLE) {
                OwnerBuilder next = bestConnectedUnassigned(members, queueOrder, assigned);
                if (next == null) {
                    break;
                }
                members.add(next);
                assigned.add(next.owner);
            }

            bundles.add(buildBundle(String.format("MAP-%04d", Integer.valueOf(bundleNumber++)),
                    seed, members, evidenceBySubject, stateBySymbol));
        }

        return new MappingPlan(index.getMetadata().getClientFingerprint(), scopedSymbols,
                runtimeVerified, staticVerified, hypotheses, unknown, stale,
                orphanEvidence, owners.size(), bundles);
    }

    public Path writeNextBundle(MappingPlan plan, Path output) throws IOException {
        if (plan == null) {
            throw new IllegalArgumentException("plan cannot be null");
        }
        if (output == null) {
            throw new IllegalArgumentException("output cannot be null");
        }
        Path target = output.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.write(temp, plan.toNextBundleMarkdown().getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private MappingBundle buildBundle(String id, OwnerBuilder seed, List<OwnerBuilder> members,
            Map<String, EvidenceView> evidenceBySubject,
            Map<String, MappingState> stateBySymbol) {
        int symbolCount = 0;
        int runtimeVerified = 0;
        int staticVerified = 0;
        int hypotheses = 0;
        int unknown = 0;
        int stale = 0;
        long priorityScore = 0L;
        Set<String> memberOwners = new HashSet<String>();
        List<String> ownerNames = new ArrayList<String>();
        List<PrioritySymbol> prioritySymbols = new ArrayList<PrioritySymbol>();
        List<EvidenceSummary> evidenceSummaries = new ArrayList<EvidenceSummary>();

        for (OwnerBuilder member : members) {
            memberOwners.add(member.owner);
            ownerNames.add(member.owner);
            symbolCount += member.symbols.size();
            runtimeVerified += member.runtimeVerified;
            staticVerified += member.staticVerified;
            hypotheses += member.hypotheses;
            unknown += member.unknown;
            stale += member.stale;
            priorityScore += member.priorityScore;

            for (SymbolEntry symbol : member.symbols) {
                MappingState state = stateBySymbol.get(symbol.getId());
                if (state == null) {
                    state = MappingState.UNKNOWN;
                }
                if (state != MappingState.VERIFIED_RUNTIME
                        && state != MappingState.VERIFIED_STATIC) {
                    int degree = index.outgoing(symbol.getId()).size()
                            + index.incoming(symbol.getId()).size();
                    prioritySymbols.add(new PrioritySymbol(symbol.getId(), shortDisplay(symbol),
                            symbol.getKind().name(), symbol.getSourcePath(), state, degree));
                }
                EvidenceView evidence = evidenceBySubject.get(symbol.getId());
                if (evidence != null) {
                    evidenceSummaries.add(new EvidenceSummary(symbol.getId(), state,
                            evidence.getRecord().getAlias(), evidence.getRecord().getClaim(),
                            evidence.isCurrent()));
                }
            }
        }

        Collections.sort(prioritySymbols, new Comparator<PrioritySymbol>() {
            @Override
            public int compare(PrioritySymbol left, PrioritySymbol right) {
                int state = Integer.compare(statePriority(right.state), statePriority(left.state));
                if (state != 0) {
                    return state;
                }
                int degree = Integer.compare(right.degree, left.degree);
                return degree != 0 ? degree : left.symbolId.compareTo(right.symbolId);
            }
        });
        if (prioritySymbols.size() > MAX_PRIORITY_SYMBOLS) {
            prioritySymbols = new ArrayList<PrioritySymbol>(
                    prioritySymbols.subList(0, MAX_PRIORITY_SYMBOLS));
        }

        Collections.sort(evidenceSummaries, new Comparator<EvidenceSummary>() {
            @Override
            public int compare(EvidenceSummary left, EvidenceSummary right) {
                int state = Integer.compare(statePriority(right.state), statePriority(left.state));
                return state != 0 ? state : left.symbolId.compareTo(right.symbolId);
            }
        });
        if (evidenceSummaries.size() > MAX_EVIDENCE_SUMMARIES) {
            evidenceSummaries = new ArrayList<EvidenceSummary>(
                    evidenceSummaries.subList(0, MAX_EVIDENCE_SUMMARIES));
        }

        Map<String, Integer> boundaryWeights = new HashMap<String, Integer>();
        for (OwnerBuilder member : members) {
            for (Map.Entry<String, Integer> entry : member.adjacency.entrySet()) {
                if (!memberOwners.contains(entry.getKey())) {
                    addWeight(boundaryWeights, entry.getKey(), entry.getValue().intValue());
                }
            }
        }
        List<BoundaryOwner> boundaryOwners = new ArrayList<BoundaryOwner>();
        for (Map.Entry<String, Integer> entry : boundaryWeights.entrySet()) {
            boundaryOwners.add(new BoundaryOwner(entry.getKey(), entry.getValue().intValue()));
        }
        Collections.sort(boundaryOwners, new Comparator<BoundaryOwner>() {
            @Override
            public int compare(BoundaryOwner left, BoundaryOwner right) {
                int weight = Integer.compare(right.weight, left.weight);
                return weight != 0 ? weight : left.owner.compareTo(right.owner);
            }
        });
        if (boundaryOwners.size() > MAX_BOUNDARY_OWNERS) {
            boundaryOwners = new ArrayList<BoundaryOwner>(
                    boundaryOwners.subList(0, MAX_BOUNDARY_OWNERS));
        }

        return new MappingBundle(id, seed.owner, ownerNames, symbolCount,
                runtimeVerified, staticVerified, hypotheses, unknown, stale,
                priorityScore, actionFor(hypotheses, unknown, stale),
                prioritySymbols, evidenceSummaries, boundaryOwners);
    }

    private static OwnerBuilder bestConnectedUnassigned(List<OwnerBuilder> members,
            List<OwnerBuilder> queueOrder, Set<String> assigned) {
        OwnerBuilder best = null;
        int bestWeight = 0;
        for (OwnerBuilder candidate : queueOrder) {
            if (assigned.contains(candidate.owner)) {
                continue;
            }
            int weight = 0;
            for (OwnerBuilder member : members) {
                Integer edge = member.adjacency.get(candidate.owner);
                if (edge != null) {
                    weight += edge.intValue();
                }
            }
            if (weight <= 0) {
                continue;
            }
            if (best == null || weight > bestWeight
                    || (weight == bestWeight && candidate.priorityScore > best.priorityScore)
                    || (weight == bestWeight && candidate.priorityScore == best.priorityScore
                            && candidate.owner.compareTo(best.owner) < 0)) {
                best = candidate;
                bestWeight = weight;
            }
        }
        return best;
    }

    private static long priority(OwnerBuilder owner) {
        int pending = owner.hypotheses + owner.unknown + owner.stale;
        if (pending == 0) {
            return 0L;
        }
        long score = 0L;
        score += (long) owner.stale * 1000L;
        score += (long) owner.hypotheses * 600L;
        score += (long) owner.unknown * 100L;
        score += (long) Math.min(owner.adjacency.size(), 40) * 50L;
        score += Math.min(owner.externalConnections, 5000L);
        return score;
    }

    private static MappingAction actionFor(int hypotheses, int unknown, int stale) {
        if (stale > 0) {
            return MappingAction.REVIEW_STALE;
        }
        if (hypotheses > 0) {
            return MappingAction.VERIFY_HYPOTHESES;
        }
        if (unknown > 0) {
            return MappingAction.STATIC_INVESTIGATION;
        }
        return MappingAction.COMPLETE;
    }

    private static MappingState stateFor(String subjectId,
            Map<String, EvidenceView> evidenceBySubject) {
        EvidenceView view = evidenceBySubject.get(subjectId);
        if (view == null) {
            return MappingState.UNKNOWN;
        }
        if (!view.isCurrent()) {
            return MappingState.STALE;
        }
        EvidenceStatus status = view.getRecord().getStatus();
        if (status == EvidenceStatus.VERIFIED) {
            return MappingState.VERIFIED_RUNTIME;
        }
        if (status == EvidenceStatus.VERIFIED_STATIC) {
            return MappingState.VERIFIED_STATIC;
        }
        if (status == EvidenceStatus.HYPOTHESIS) {
            return MappingState.HYPOTHESIS;
        }
        return MappingState.UNKNOWN;
    }

    private static boolean isMappingScope(SymbolEntry symbol) {
        if (symbol == null || symbol.getOwner() == null) {
            return false;
        }
        String owner = symbol.getOwner();
        if (owner.startsWith("game/atlas/") || owner.startsWith("game/console/")) {
            return false;
        }
        return !"game/AtlasRuntimeBridge".equals(owner)
                && !"game/AtlasKeyboardObserver".equals(owner);
    }

    static boolean isMappingScopeOwner(String owner) {
        if (owner == null) {
            return false;
        }
        return !(owner.startsWith("game/atlas/")
                || owner.startsWith("game/console/")
                || "game/AtlasRuntimeBridge".equals(owner)
                || "game/AtlasKeyboardObserver".equals(owner));
    }

    private static int statePriority(MappingState state) {
        switch (state) {
        case STALE:
            return 5;
        case HYPOTHESIS:
            return 4;
        case UNKNOWN:
            return 3;
        case VERIFIED_STATIC:
            return 2;
        case VERIFIED_RUNTIME:
        default:
            return 1;
        }
    }

    private static void addWeight(Map<String, Integer> map, String key, int amount) {
        Integer current = map.get(key);
        long total = (current == null ? 0L : current.longValue()) + Math.max(1, amount);
        map.put(key, Integer.valueOf((int) Math.min(Integer.MAX_VALUE, total)));
    }

    private static String shortDisplay(SymbolEntry symbol) {
        String owner = simpleOwner(symbol.getOwner());
        String kind = symbol.getKind().name();
        if ("CLASS".equals(kind) || "INTERFACE".equals(kind)
                || "ENUM".equals(kind) || "ANNOTATION".equals(kind)) {
            return owner;
        }
        return owner + "." + symbol.getName();
    }

    private static String simpleOwner(String owner) {
        if (owner == null) {
            return "";
        }
        int slash = owner.lastIndexOf('/');
        return slash >= 0 ? owner.substring(slash + 1) : owner;
    }

    private static String compact(String value, int limit) {
        if (value == null) {
            return "";
        }
        String flat = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (flat.length() <= limit) {
            return flat;
        }
        return flat.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private static final class OwnerBuilder {
        private final String owner;
        private final List<SymbolEntry> symbols = new ArrayList<SymbolEntry>();
        private final Map<String, Integer> adjacency = new HashMap<String, Integer>();
        private int runtimeVerified;
        private int staticVerified;
        private int hypotheses;
        private int unknown;
        private int stale;
        private long externalConnections;
        private long priorityScore;

        private OwnerBuilder(String owner) {
            this.owner = owner;
        }

        private void increment(MappingState state) {
            switch (state) {
            case VERIFIED_RUNTIME:
                runtimeVerified++;
                break;
            case VERIFIED_STATIC:
                staticVerified++;
                break;
            case HYPOTHESIS:
                hypotheses++;
                break;
            case STALE:
                stale++;
                break;
            case UNKNOWN:
            default:
                unknown++;
                break;
            }
        }
    }

    public enum MappingState {
        VERIFIED_RUNTIME,
        VERIFIED_STATIC,
        HYPOTHESIS,
        UNKNOWN,
        STALE
    }

    public enum MappingAction {
        REVIEW_STALE("Review stale evidence first"),
        VERIFY_HYPOTHESES("Verify existing hypotheses, then continue mapping"),
        STATIC_INVESTIGATION("Static investigation first; request runtime evidence only when needed"),
        COMPLETE("Mapped with current proven evidence");

        private final String display;

        MappingAction(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }

    public static final class MappingPlan {
        private final String fingerprint;
        private final int scopedSymbolCount;
        private final int runtimeVerifiedCount;
        private final int staticVerifiedCount;
        private final int hypothesisCount;
        private final int unknownCount;
        private final int staleCount;
        private final int orphanEvidenceCount;
        private final int ownerCount;
        private final List<MappingBundle> bundles;

        private MappingPlan(String fingerprint, int scopedSymbolCount,
                int runtimeVerifiedCount, int staticVerifiedCount, int hypothesisCount,
                int unknownCount, int staleCount, int orphanEvidenceCount, int ownerCount,
                List<MappingBundle> bundles) {
            this.fingerprint = fingerprint;
            this.scopedSymbolCount = scopedSymbolCount;
            this.runtimeVerifiedCount = runtimeVerifiedCount;
            this.staticVerifiedCount = staticVerifiedCount;
            this.hypothesisCount = hypothesisCount;
            this.unknownCount = unknownCount;
            this.staleCount = staleCount;
            this.orphanEvidenceCount = orphanEvidenceCount;
            this.ownerCount = ownerCount;
            this.bundles = Collections.unmodifiableList(new ArrayList<MappingBundle>(bundles));
        }

        public String getFingerprint() { return fingerprint; }
        public int getScopedSymbolCount() { return scopedSymbolCount; }
        public int getRuntimeVerifiedCount() { return runtimeVerifiedCount; }
        public int getStaticVerifiedCount() { return staticVerifiedCount; }
        public int getHypothesisCount() { return hypothesisCount; }
        public int getUnknownCount() { return unknownCount; }
        public int getStaleCount() { return staleCount; }
        public int getOrphanEvidenceCount() { return orphanEvidenceCount; }
        public int getOwnerCount() { return ownerCount; }
        public List<MappingBundle> getBundles() { return bundles; }

        public int getPendingSymbolCount() {
            return hypothesisCount + unknownCount + staleCount;
        }

        public MappingBundle getNextBundle() {
            for (MappingBundle bundle : bundles) {
                if (bundle.getAction() != MappingAction.COMPLETE) {
                    return bundle;
                }
            }
            return null;
        }

        public String toDisplayText() {
            StringBuilder out = new StringBuilder(2048);
            out.append("Client Atlas semantic mapping coverage\n");
            out.append("Fingerprint: ").append(fingerprint).append('\n');
            out.append("Scoped symbols: ").append(scopedSymbolCount).append('\n');
            out.append("Verified at runtime: ").append(runtimeVerifiedCount).append('\n');
            out.append("Confirmed from code/data: ").append(staticVerifiedCount).append('\n');
            out.append("Hypotheses: ").append(hypothesisCount).append('\n');
            out.append("Stale mappings: ").append(staleCount).append('\n');
            out.append("Unknown: ").append(unknownCount).append('\n');
            out.append("Orphan evidence records: ").append(orphanEvidenceCount).append('\n');
            out.append("Mapped owners: ").append(ownerCount).append('\n');
            out.append("Structural bundles: ").append(bundles.size()).append('\n');

            MappingBundle next = getNextBundle();
            if (next == null) {
                out.append("Next bundle: none - current scoped map is fully proven.\n");
            } else {
                out.append("Next bundle: ").append(next.getId())
                        .append(" | seed ").append(next.getSeedOwner())
                        .append(" | owners ").append(next.getOwners().size())
                        .append(" | symbols ").append(next.getSymbolCount()).append('\n');
                out.append("Action: ").append(next.getAction().getDisplay()).append('\n');
                out.append("Bundle coverage: runtime=").append(next.getRuntimeVerifiedCount())
                        .append(" static=").append(next.getStaticVerifiedCount())
                        .append(" hypothesis=").append(next.getHypothesisCount())
                        .append(" stale=").append(next.getStaleCount())
                        .append(" unknown=").append(next.getUnknownCount()).append('\n');
            }
            return out.toString();
        }

        public String toNextBundleMarkdown() {
            MappingBundle next = getNextBundle();
            if (next == null) {
                return "# Client Atlas Mapping Queue\n\n"
                        + "Current scoped semantic map is fully proven for fingerprint `"
                        + fingerprint + "`.\n";
            }
            return next.toAssistantMarkdown(this);
        }

        String deterministicKey() {
            StringBuilder key = new StringBuilder(8192);
            key.append(fingerprint).append('|').append(scopedSymbolCount).append('|')
                    .append(runtimeVerifiedCount).append('|').append(staticVerifiedCount).append('|')
                    .append(hypothesisCount).append('|').append(unknownCount).append('|')
                    .append(staleCount).append('|').append(ownerCount).append('\n');
            for (MappingBundle bundle : bundles) {
                key.append(bundle.id).append('|').append(bundle.action.name()).append('|');
                for (String owner : bundle.owners) {
                    key.append(owner).append(',');
                }
                key.append('\n');
            }
            return key.toString();
        }
    }

    public static final class MappingBundle {
        private final String id;
        private final String seedOwner;
        private final List<String> owners;
        private final int symbolCount;
        private final int runtimeVerifiedCount;
        private final int staticVerifiedCount;
        private final int hypothesisCount;
        private final int unknownCount;
        private final int staleCount;
        private final long priorityScore;
        private final MappingAction action;
        private final List<PrioritySymbol> prioritySymbols;
        private final List<EvidenceSummary> evidenceSummaries;
        private final List<BoundaryOwner> boundaryOwners;

        private MappingBundle(String id, String seedOwner, List<String> owners,
                int symbolCount, int runtimeVerifiedCount, int staticVerifiedCount,
                int hypothesisCount, int unknownCount, int staleCount, long priorityScore,
                MappingAction action, List<PrioritySymbol> prioritySymbols,
                List<EvidenceSummary> evidenceSummaries, List<BoundaryOwner> boundaryOwners) {
            this.id = id;
            this.seedOwner = seedOwner;
            this.owners = Collections.unmodifiableList(new ArrayList<String>(owners));
            this.symbolCount = symbolCount;
            this.runtimeVerifiedCount = runtimeVerifiedCount;
            this.staticVerifiedCount = staticVerifiedCount;
            this.hypothesisCount = hypothesisCount;
            this.unknownCount = unknownCount;
            this.staleCount = staleCount;
            this.priorityScore = priorityScore;
            this.action = action;
            this.prioritySymbols = Collections.unmodifiableList(new ArrayList<PrioritySymbol>(prioritySymbols));
            this.evidenceSummaries = Collections.unmodifiableList(new ArrayList<EvidenceSummary>(evidenceSummaries));
            this.boundaryOwners = Collections.unmodifiableList(new ArrayList<BoundaryOwner>(boundaryOwners));
        }

        public String getId() { return id; }
        public String getSeedOwner() { return seedOwner; }
        public List<String> getOwners() { return owners; }
        public int getSymbolCount() { return symbolCount; }
        public int getRuntimeVerifiedCount() { return runtimeVerifiedCount; }
        public int getStaticVerifiedCount() { return staticVerifiedCount; }
        public int getHypothesisCount() { return hypothesisCount; }
        public int getUnknownCount() { return unknownCount; }
        public int getStaleCount() { return staleCount; }
        public long getPriorityScore() { return priorityScore; }
        public MappingAction getAction() { return action; }
        public List<PrioritySymbol> getPrioritySymbols() { return prioritySymbols; }
        public List<EvidenceSummary> getEvidenceSummaries() { return evidenceSummaries; }
        public List<BoundaryOwner> getBoundaryOwners() { return boundaryOwners; }

        private String toAssistantMarkdown(MappingPlan plan) {
            StringBuilder out = new StringBuilder(16384);
            out.append("# Client Atlas Mapping Bundle ").append(id).append("\n\n");
            out.append("Client fingerprint: `").append(plan.getFingerprint()).append("`\n\n");
            out.append("## Goal\n\n");
            out.append("Semantically map this bounded structural cluster without renaming obfuscated source or inventing meaning. ")
                    .append("Use exact Atlas IDs as authority, preserve `VERIFIED`, `verified-static`, `HYPOTHESIS`, and `UNKNOWN`, ")
                    .append("and request runtime evidence only when static evidence cannot prove the claim.\n\n");
            out.append("## Bundle status\n\n");
            out.append("- Action: **").append(action.getDisplay()).append("**\n");
            out.append("- Seed owner: `").append(seedOwner).append("`\n");
            out.append("- Owners: ").append(owners.size()).append("\n");
            out.append("- Symbols: ").append(symbolCount).append("\n");
            out.append("- Verified runtime: ").append(runtimeVerifiedCount).append("\n");
            out.append("- Verified static: ").append(staticVerifiedCount).append("\n");
            out.append("- Hypotheses: ").append(hypothesisCount).append("\n");
            out.append("- Stale: ").append(staleCount).append("\n");
            out.append("- Unknown: ").append(unknownCount).append("\n\n");

            out.append("## Structural owners\n\n");
            for (String owner : owners) {
                out.append("- `").append(owner).append("`\n");
            }

            if (!boundaryOwners.isEmpty()) {
                out.append("\n## Strong boundary connections\n\n");
                for (BoundaryOwner boundary : boundaryOwners) {
                    out.append("- `").append(boundary.owner).append("` - structural weight ")
                            .append(boundary.weight).append('\n');
                }
            }

            out.append("\n## Priority exact symbols\n\n");
            if (prioritySymbols.isEmpty()) {
                out.append("No unresolved symbols remain in this bundle.\n");
            } else {
                for (PrioritySymbol symbol : prioritySymbols) {
                    out.append("- [").append(symbol.state.name()).append("] `")
                            .append(symbol.symbolId).append("` - ")
                            .append(symbol.displayName)
                            .append(" | kind ").append(symbol.kind)
                            .append(" | degree ").append(symbol.degree);
                    if (symbol.sourcePath != null) {
                        out.append(" | source `").append(symbol.sourcePath).append('`');
                    }
                    out.append('\n');
                }
            }

            if (!evidenceSummaries.isEmpty()) {
                out.append("\n## Existing curated evidence\n\n");
                for (EvidenceSummary evidence : evidenceSummaries) {
                    out.append("- [").append(evidence.state.name()).append("] `")
                            .append(evidence.symbolId).append("`");
                    if (evidence.alias != null && evidence.alias.length() > 0) {
                        out.append(" - **").append(evidence.alias).append("**");
                    }
                    out.append(" - ").append(compact(evidence.claim, 240));
                    if (!evidence.current) {
                        out.append(" - **STALE: review before reuse**");
                    }
                    out.append('\n');
                }
            }

            out.append("\n## Investigation rules\n\n");
            out.append("1. Start with the seed owner and highest-degree unresolved exact symbols.\n");
            out.append("2. Follow only relationships needed to establish semantics; do not broad-rescan unrelated client code.\n");
            out.append("3. Reuse existing curated evidence when current; stale evidence must be re-verified.\n");
            out.append("4. `VERIFIED` requires runtime confirmation; `verified-static` requires direct source/data proof; guesses stay `HYPOTHESIS` or `UNKNOWN`.\n");
            out.append("5. Save every established semantic claim back to Atlas evidence so queue coverage advances instead of repeating work.\n");
            return out.toString();
        }
    }

    public static final class PrioritySymbol {
        private final String symbolId;
        private final String displayName;
        private final String kind;
        private final String sourcePath;
        private final MappingState state;
        private final int degree;

        private PrioritySymbol(String symbolId, String displayName, String kind,
                String sourcePath, MappingState state, int degree) {
            this.symbolId = symbolId;
            this.displayName = displayName;
            this.kind = kind;
            this.sourcePath = sourcePath;
            this.state = state;
            this.degree = degree;
        }

        public String getSymbolId() { return symbolId; }
        public String getDisplayName() { return displayName; }
        public String getKind() { return kind; }
        public String getSourcePath() { return sourcePath; }
        public MappingState getState() { return state; }
        public int getDegree() { return degree; }
    }

    public static final class EvidenceSummary {
        private final String symbolId;
        private final MappingState state;
        private final String alias;
        private final String claim;
        private final boolean current;

        private EvidenceSummary(String symbolId, MappingState state, String alias,
                String claim, boolean current) {
            this.symbolId = symbolId;
            this.state = state;
            this.alias = alias;
            this.claim = claim;
            this.current = current;
        }

        public String getSymbolId() { return symbolId; }
        public MappingState getState() { return state; }
        public String getAlias() { return alias; }
        public String getClaim() { return claim; }
        public boolean isCurrent() { return current; }
    }

    public static final class BoundaryOwner {
        private final String owner;
        private final int weight;

        private BoundaryOwner(String owner, int weight) {
            this.owner = owner;
            this.weight = weight;
        }

        public String getOwner() { return owner; }
        public int getWeight() { return weight; }
    }
}
