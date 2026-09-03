package com.rs.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.rs.cache.Cache;
import com.rs.cache.loaders.AnimationDefinitions;
import com.rs.cache.loaders.NPCDefinitions;
import com.rs.cache.loaders.RenderAnimDefinitions;
import com.rs.utils.Utils;

/**
 * Read-only revision-830 cache scanner for Rise of the Six research.
 *
 * The first goal is to identify every Barrows-brother NPC variant, dump its
 * model fingerprint, and correlate other NPC definitions that share those
 * models. This is especially useful for distinguishing seasonal/Santa-hat
 * variants from the normal RoTS definitions.
 *
 * Findings from this tool are verified-static cache evidence only until they
 * are confirmed in runtime.
 */
public final class RotsCacheScanner {

    private static final String[] DEFAULT_NAMES = {
            "dharok", "ahrim", "karil", "torag", "guthan", "verac"
    };

    private static final int RELATED_LIMIT = 20;

    private RotsCacheScanner() {
    }

    public static void main(String[] args) throws IOException {
        Cache.init();

        String[] names = args != null && args.length > 0 ? args : DEFAULT_NAMES;
        List<NpcCandidate> candidates = findCandidates(names);

        System.out.println("=== RISE OF THE SIX CACHE DISCOVERY ===");
        System.out.println("NPC definitions: " + Utils.getNPCDefinitionsSize());
        System.out.println("Animation definitions: " + Utils.getAnimationDefinitionsSize());
        System.out.println("Search terms: " + Arrays.toString(names));
        System.out.println("Matches: " + candidates.size());
        System.out.println();

        for (NpcCandidate candidate : candidates) {
            dumpCandidate(candidate);
            dumpRelatedNpcVariants(candidate);
            System.out.println();
        }
    }

    private static List<NpcCandidate> findCandidates(String[] names) {
        List<NpcCandidate> candidates = new ArrayList<NpcCandidate>();
        int size = Utils.getNPCDefinitionsSize();

        for (int id = 0; id < size; id++) {
            NPCDefinitions def = NPCDefinitions.getNPCDefinitions(id);
            if (def == null || def.name == null)
                continue;

            String lowerName = def.name.toLowerCase();
            for (String search : names) {
                if (search != null && lowerName.contains(search.toLowerCase())) {
                    candidates.add(new NpcCandidate(id, def));
                    break;
                }
            }
        }
        return candidates;
    }

    private static void dumpCandidate(NpcCandidate candidate) {
        NPCDefinitions def = candidate.def;

        System.out.println("------------------------------------------------------------");
        System.out.println("NPC " + candidate.id + " - " + def.name);
        System.out.println("combatLevel=" + def.combatLevel + ", size=" + def.size);
        System.out.println("models=" + Arrays.toString(def.modelIds));
        System.out.println("renderEmote=" + def.renderEmote);
        System.out.println("transforms=" + Arrays.toString(def.transformTo));
        System.out.println("options=" + Arrays.toString(def.options));
        System.out.println("clientScriptData=" + def.clientScriptData);

        dumpRenderAnimations(def.renderEmote);
    }

    private static void dumpRenderAnimations(int renderEmote) {
        RenderAnimDefinitions render = RenderAnimDefinitions.getRenderAnimDefinitions(renderEmote);
        if (render == null) {
            System.out.println("renderAnimations=<none>");
            return;
        }

        System.out.println("renderAnimations:");
        dumpAnimation("stand", render.defaultStandAnimation);
        dumpAnimation("walk", render.walkAnimation);
        dumpAnimation("run", render.runAnimation);
        dumpAnimation("walkBackwards", render.walkBackwardsAnimation);
        dumpAnimation("walkLeft", render.walkLeftAnimation);
        dumpAnimation("walkRight", render.walkRightAnimation);
        dumpAnimation("walkUpwards", render.walkUpwardsAnimation);
    }

    private static void dumpAnimation(String label, int animationId) {
        if (animationId < 0) {
            System.out.println("  " + label + "=-1");
            return;
        }

        AnimationDefinitions animation = AnimationDefinitions.getAnimationDefinitions(animationId);
        if (animation == null) {
            System.out.println("  " + label + "=" + animationId + " (definition unavailable)");
            return;
        }

        int frameCount = animation.anIntArray2153 == null ? 0 : animation.anIntArray2153.length;
        System.out.println("  " + label + "=" + animationId
                + " frames=" + frameCount
                + " timeMs=" + animation.getEmoteTime()
                + " rightHandItem=" + animation.rightHandItem
                + " leftHandItem=" + animation.leftHandItem);
    }

    private static void dumpRelatedNpcVariants(NpcCandidate candidate) {
        int[] sourceModels = candidate.def.modelIds;
        if (sourceModels == null || sourceModels.length == 0) {
            System.out.println("relatedByModels=<no source models>");
            return;
        }

        List<ModelMatch> matches = new ArrayList<ModelMatch>();
        int size = Utils.getNPCDefinitionsSize();

        for (int id = 0; id < size; id++) {
            if (id == candidate.id)
                continue;

            NPCDefinitions def = NPCDefinitions.getNPCDefinitions(id);
            if (def == null || def.modelIds == null || def.modelIds.length == 0)
                continue;

            int shared = countSharedModels(sourceModels, def.modelIds);
            if (shared == 0)
                continue;

            double sourceCoverage = (double) shared / (double) sourceModels.length;
            double targetCoverage = (double) shared / (double) def.modelIds.length;

            // Keep strong fingerprints while still allowing one seasonal model
            // (for example a Santa hat) to differ between otherwise matching NPCs.
            boolean strongMatch = shared >= 2 || sourceCoverage >= 0.75D || targetCoverage >= 0.75D;
            if (strongMatch)
                matches.add(new ModelMatch(id, def, shared, sourceCoverage, targetCoverage));
        }

        Collections.sort(matches, new Comparator<ModelMatch>() {
            @Override
            public int compare(ModelMatch a, ModelMatch b) {
                int shared = Integer.compare(b.sharedModels, a.sharedModels);
                if (shared != 0)
                    return shared;
                int source = Double.compare(b.sourceCoverage, a.sourceCoverage);
                if (source != 0)
                    return source;
                return Integer.compare(a.id, b.id);
            }
        });

        System.out.println("relatedByModels (top " + RELATED_LIMIT + "):");
        int count = Math.min(RELATED_LIMIT, matches.size());
        for (int i = 0; i < count; i++) {
            ModelMatch match = matches.get(i);
            System.out.println("  NPC " + match.id + " - " + match.def.name
                    + " shared=" + match.sharedModels
                    + "/" + sourceModels.length
                    + " sourceCoverage=" + formatPercent(match.sourceCoverage)
                    + " targetCoverage=" + formatPercent(match.targetCoverage)
                    + " models=" + Arrays.toString(match.def.modelIds)
                    + " renderEmote=" + match.def.renderEmote);
        }
        if (matches.isEmpty())
            System.out.println("  <none>");
    }

    private static int countSharedModels(int[] first, int[] second) {
        int shared = 0;
        for (int firstModel : first) {
            for (int secondModel : second) {
                if (firstModel == secondModel) {
                    shared++;
                    break;
                }
            }
        }
        return shared;
    }

    private static String formatPercent(double value) {
        return String.format("%.0f%%", value * 100.0D);
    }

    private static final class NpcCandidate {
        private final int id;
        private final NPCDefinitions def;

        private NpcCandidate(int id, NPCDefinitions def) {
            this.id = id;
            this.def = def;
        }
    }

    private static final class ModelMatch {
        private final int id;
        private final NPCDefinitions def;
        private final int sharedModels;
        private final double sourceCoverage;
        private final double targetCoverage;

        private ModelMatch(int id, NPCDefinitions def, int sharedModels, double sourceCoverage,
                double targetCoverage) {
            this.id = id;
            this.def = def;
            this.sharedModels = sharedModels;
            this.sourceCoverage = sourceCoverage;
            this.targetCoverage = targetCoverage;
        }
    }
}
