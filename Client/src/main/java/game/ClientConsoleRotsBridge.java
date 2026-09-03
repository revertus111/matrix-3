package game;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Read-only Client Console bridge for focused Rise of the Six cache research.
 * Matrix3's live client definition loaders remain authoritative.
 */
public final class ClientConsoleRotsBridge {

    private static final int[] ROTS_NPC_IDS = {
            18538, 18539, 18540, 18541, 18542, 18543, 18544, 18545,
            18546, 18547, 18548, 18549, 18550, 18551
    };

    private static final int[] ROTS_NPC_MODELS = {
            91490, 91521, 91497, 91500, 91494, 91531, 91491, 91511,
            91492, 91513, 91496, 91506
    };

    private static final int[] ROTS_RENDER_SET_IDS = {
            2687, 2689, 2690, 2982, 2987
    };

    private static final int[] ROTS_CANDIDATE_ANIMATIONS = {
            18025, 18026, 21903, 21931,
            21922, 21923, 21924, 21925, 21926, 21927, 21928, 21929, 21930,
            21932, 21933, 21934, 21935, 21936, 21937, 21938, 21939, 21940,
            21941, 21942, 21943, 21944, 21945, 21946, 21947
    };

    private static final int[] NPC_PARAM_KEYS = {
            3, 4, 14, 26, 29, 641, 643, 965, 1346,
            2838, 2848, 2849, 2850, 2851, 2852, 2864, 2865,
            2890, 2892, 2918, 2955
    };

    private static volatile Interface18 npcDefinitions;
    private static volatile Interface18 animationDefinitions;
    private static volatile Interface18 graphicsDefinitions;

    private ClientConsoleRotsBridge() {
    }

    static void registerNpcDefinitions(Interface18 definitions) {
        if (definitions != null) {
            npcDefinitions = definitions;
        }
    }

    static void registerAnimationDefinitions(Interface18 definitions) {
        if (definitions != null) {
            animationDefinitions = definitions;
        }
    }

    static void registerGraphicsDefinitions(Interface18 definitions) {
        if (definitions != null) {
            graphicsDefinitions = definitions;
        }
    }

    public static boolean isReady() {
        return npcDefinitions != null && animationDefinitions != null;
    }

    public static boolean isDeepReady() {
        return animationDefinitions != null
                && graphicsDefinitions != null
                && Class197.aClass639_Sub11_2359 != null;
    }

    public static String getReadinessLabel() {
        boolean npcReady = npcDefinitions != null;
        boolean animationReady = animationDefinitions != null;
        if (npcReady && animationReady) {
            return "Ready";
        }
        if (!npcReady && !animationReady) {
            return "Waiting for NPC and animation definitions";
        }
        return npcReady ? "Waiting for animation definitions" : "Waiting for NPC definitions";
    }

    public static String getDeepReadinessLabel() {
        if (isDeepReady()) {
            return "Deep Scan ready";
        }
        StringBuilder missing = new StringBuilder("Deep Scan waiting for ");
        boolean first = true;
        if (animationDefinitions == null) {
            missing.append("animation definitions");
            first = false;
        }
        if (graphicsDefinitions == null) {
            if (!first) {
                missing.append(", ");
            }
            missing.append("GFX definitions");
            first = false;
        }
        if (Class197.aClass639_Sub11_2359 == null) {
            if (!first) {
                missing.append(", ");
            }
            missing.append("render-set definitions");
        }
        return missing.toString();
    }

    public static String buildResearchDump() {
        Interface18 npcLoader = npcDefinitions;
        Interface18 animationLoader = animationDefinitions;
        StringBuilder out = new StringBuilder(32768);

        out.append("=== RISE OF THE SIX CLIENT CACHE RESEARCH ===\n");
        out.append("Evidence from this tool is verified-static unless a state is explicitly marked VERIFIED.\n");
        out.append("NPC loader: ").append(npcLoader == null ? "NOT READY" : "ready").append('\n');
        out.append("Animation loader: ").append(animationLoader == null ? "NOT READY" : "ready").append('\n');

        if (npcLoader == null || animationLoader == null) {
            out.append("\nOpen/login far enough for Matrix3 to load both definition systems, then scan again.\n");
            return out.toString();
        }

        out.append("NPC definition count: ").append(npcLoader.method45()).append('\n');
        out.append("Animation definition count: ").append(animationLoader.method45()).append('\n');

        out.append("\n=== KNOWN ROTS NPC FAMILY 18538-18551 ===\n");
        for (int npcId : ROTS_NPC_IDS) {
            appendNpc(out, npcLoader, npcId);
        }

        out.append("\n=== ROTS ANIMATION NEIGHBORHOOD 17960-18040 ===\n");
        appendAnimationRange(out, animationLoader, 17960, 18040);

        out.append("\n=== ROTS ALTERNATE ANIMATION NEIGHBORHOOD 21920-21947 ===\n");
        appendAnimationRange(out, animationLoader, 21920, 21947);

        return out.toString();
    }

    public static String buildDeepResearchDump() {
        Interface18 animationLoader = animationDefinitions;
        Interface18 graphicsLoader = graphicsDefinitions;
        Class639_Sub11 renderLoader = Class197.aClass639_Sub11_2359;
        StringBuilder out = new StringBuilder(65536);

        out.append("=== RISE OF THE SIX DEEP CACHE RESEARCH ===\n");
        out.append("This dump reports cache relationships only. It does not assign mechanic names without runtime evidence.\n");
        out.append("Animation loader: ").append(animationLoader == null ? "NOT READY" : "ready").append('\n');
        out.append("GFX loader: ").append(graphicsLoader == null ? "NOT READY" : "ready").append('\n');
        out.append("Render-set loader: ").append(renderLoader == null ? "NOT READY" : "ready").append('\n');

        if (animationLoader == null || graphicsLoader == null || renderLoader == null) {
            out.append("\n").append(getDeepReadinessLabel()).append('\n');
            out.append("Load/login far enough for these live Matrix3 definition systems, then run Deep Scan again.\n");
            return out.toString();
        }

        out.append("Animation definition count: ").append(animationLoader.method45()).append('\n');
        out.append("GFX definition count: ").append(graphicsLoader.method45()).append('\n');
        out.append("Render-set definition count: ").append(renderLoader.method45()).append('\n');

        Set<Integer> renderAnimations = new LinkedHashSet<Integer>();
        out.append("\n=== ROTS RENDER / BAS SETS ===\n");
        for (int renderSetId : ROTS_RENDER_SET_IDS) {
            appendRenderSet(out, renderLoader, renderSetId, renderAnimations);
        }

        Set<Integer> interestingAnimations = new LinkedHashSet<Integer>(renderAnimations);
        for (int animationId : ROTS_CANDIDATE_ANIMATIONS) {
            interestingAnimations.add(Integer.valueOf(animationId));
        }

        out.append("\n=== TARGETED ANIMATION CLASSIFICATION ===\n");
        for (int animationId : ROTS_CANDIDATE_ANIMATIONS) {
            out.append("\nanimation=").append(animationId)
                    .append(" renderSetReference=")
                    .append(renderAnimations.contains(Integer.valueOf(animationId)) ? "YES" : "NO")
                    .append('\n');
            appendAnimationById(out, animationLoader, animationId);
        }

        out.append("\n=== GFX CORRELATION ===\n");
        appendGraphicsCorrelations(out, graphicsLoader, interestingAnimations);

        return out.toString();
    }

    private static void appendNpc(StringBuilder out, Interface18 loader, int npcId) {
        out.append("\n------------------------------------------------------------\n");
        out.append("NPC ").append(npcId).append(" - ");
        try {
            NPCDefintion def = (NPCDefintion) loader.getDefinition(npcId, 0);
            if (def == null) {
                out.append("<definition unavailable>\n");
                return;
            }

            out.append(def.aString4791).append('\n');
            out.append("state=").append(stateLabel(npcId)).append('\n');
            out.append("combatLevel=").append(def.anInt4812 * -1340892491)
                    .append(", size=").append(def.anInt4792 * 358769667).append('\n');
            out.append("models=").append(Arrays.toString(def.anIntArray4793)).append('\n');
            out.append("renderEmote=").append(def.anInt4805 * 1071699547).append('\n');
            out.append("transforms=").append(Arrays.toString(def.anIntArray4795)).append('\n');
            out.append("options=").append(Arrays.toString(def.aStringArray4808)).append('\n');
            appendNpcParams(out, def);
        } catch (RuntimeException ex) {
            out.append("<failed to read definition: ").append(ex.getClass().getSimpleName()).append(">\n");
        }
    }

    private static void appendNpcParams(StringBuilder out, NPCDefintion def) {
        StringBuilder params = new StringBuilder();
        params.append('{');
        boolean first = true;
        for (int key : NPC_PARAM_KEYS) {
            int value = def.method4952(key, Integer.MIN_VALUE, 0);
            if (value == Integer.MIN_VALUE) {
                continue;
            }
            if (!first) {
                params.append(", ");
            }
            params.append(key).append('=').append(value);
            first = false;
        }
        params.append('}');
        out.append("knownClientScriptData=").append(params).append('\n');
    }

    private static String stateLabel(int npcId) {
        switch (npcId) {
        case 18539:
            return "VERIFIED - Ahrim flying state";
        case 18542:
            return "VERIFIED - Guthan spearless / Impale state";
        case 18546:
        case 18547:
        case 18548:
        case 18549:
        case 18550:
        case 18551:
            return "verified-static - body-only/inactive definition; runtime-invisible as subdued presentation; authentic role HYPOTHESIS";
        default:
            return "verified-static - RoTS combat-family definition; runtime seasonal/Santa model observed";
        }
    }

    private static void appendAnimationRange(StringBuilder out, Interface18 loader, int start, int end) {
        int found = 0;
        int count = loader.method45();
        int boundedStart = Math.max(0, start);
        int boundedEnd = Math.min(end, count - 1);

        for (int animationId = boundedStart; animationId <= boundedEnd; animationId++) {
            try {
                AnimationDefinition def = (AnimationDefinition) loader.getDefinition(animationId, 0);
                if (def == null || def.anIntArray1546 == null || def.anIntArray1546.length == 0) {
                    continue;
                }
                found++;
                appendAnimation(out, animationId, def);
            } catch (RuntimeException ex) {
                out.append("animation ").append(animationId).append("=<read failed: ")
                        .append(ex.getClass().getSimpleName()).append(">\n");
            }
        }
        if (found == 0) {
            out.append("<no populated animation definitions in this range>\n");
        }
    }

    private static void appendAnimationById(StringBuilder out, Interface18 loader, int animationId) {
        try {
            if (animationId < 0 || animationId >= loader.method45()) {
                out.append("definition=<out of range>\n");
                return;
            }
            AnimationDefinition def = (AnimationDefinition) loader.getDefinition(animationId, 0);
            if (def == null || def.anIntArray1546 == null || def.anIntArray1546.length == 0) {
                out.append("definition=<empty>\n");
                return;
            }
            appendAnimation(out, animationId, def);
        } catch (RuntimeException ex) {
            out.append("definition=<read failed: ").append(ex.getClass().getSimpleName()).append(">\n");
        }
    }

    private static void appendAnimation(StringBuilder out, int animationId, AnimationDefinition def) {
        long totalMs = 0L;
        for (int duration : def.anIntArray1546) {
            totalMs += duration * 10L;
        }

        int rightHandItem = normalizeItemOverride(def.anInt1553 * 196196667);
        int leftHandItem = normalizeItemOverride(def.anInt1554 * 224298189);
        int priority = def.priority * 1882694951;

        out.append("animation=").append(animationId)
                .append(" frames=").append(def.anIntArray1546.length)
                .append(" timeMs=").append(totalMs)
                .append(" priority=").append(priority)
                .append(" rightHandItem=").append(rightHandItem)
                .append(" leftHandItem=").append(leftHandItem)
                .append('\n');
        out.append("frameDurations=").append(Arrays.toString(def.anIntArray1546)).append('\n');
        out.append("frameIds=").append(Arrays.toString(def.anIntArray1544)).append('\n');
        out.append("handledSounds=").append(Arrays.deepToString(def.anIntArrayArray1550)).append('\n');
    }

    private static void appendRenderSet(StringBuilder out, Class639_Sub11 loader, int renderSetId,
            Set<Integer> renderAnimations) {
        out.append("\n------------------------------------------------------------\n");
        out.append("renderSet=").append(renderSetId).append('\n');
        try {
            Class538 def = (Class538) loader.getDefinition(renderSetId, 0);
            if (def == null) {
                out.append("<definition unavailable>\n");
                return;
            }

            appendRenderRef(out, renderAnimations, "opcode1.primary", decodeStored(def.anInt6022, 19022545));
            appendRenderRef(out, renderAnimations, "opcode1.secondary", decodeStored(def.anInt6058, 162484423));
            appendRenderRef(out, renderAnimations, "opcode2", decodeStored(def.anInt6030, 738130903));
            appendRenderRef(out, renderAnimations, "opcode3", decodeStored(def.anInt6031, 1579021739));
            appendRenderRef(out, renderAnimations, "opcode4", decodeStored(def.anInt6032, 1983097175));
            appendRenderRef(out, renderAnimations, "opcode5", decodeStored(def.anInt6033, 438950095));
            appendRenderRef(out, renderAnimations, "opcode6", decodeStored(def.anInt6026, 1226227937));
            appendRenderRef(out, renderAnimations, "opcode7", decodeStored(def.anInt6027, -158750093));
            appendRenderRef(out, renderAnimations, "opcode8", decodeStored(def.anInt6028, -397576193));
            appendRenderRef(out, renderAnimations, "opcode9", decodeStored(def.anInt6019, 1001885913));
            appendRenderRef(out, renderAnimations, "opcode38", decodeStored(def.anInt6020, -1790093695));
            appendRenderRef(out, renderAnimations, "opcode39", decodeStored(def.anInt6037, -30820467));
            appendRenderRef(out, renderAnimations, "opcode40", decodeStored(def.anInt6023, 627352385));
            appendRenderRef(out, renderAnimations, "opcode41", decodeStored(def.anInt6024, 912312451));
            appendRenderRef(out, renderAnimations, "opcode42", decodeStored(def.anInt6021, 1408462263));
            appendRenderRef(out, renderAnimations, "opcode46", decodeStored(def.anInt6034, 167311821));
            appendRenderRef(out, renderAnimations, "opcode47", decodeStored(def.anInt6057, -1831564459));
            appendRenderRef(out, renderAnimations, "opcode48", decodeStored(def.anInt6036, 1898791563));
            appendRenderRef(out, renderAnimations, "opcode49", decodeStored(def.anInt6056, 892449209));
            appendRenderRef(out, renderAnimations, "opcode50", decodeStored(def.anInt6038, 123030213));
            appendRenderRef(out, renderAnimations, "opcode51", decodeStored(def.anInt6039, -2023446939));

            out.append("opcode52.animations=").append(Arrays.toString(def.anIntArray6017)).append('\n');
            out.append("opcode52.weights=").append(Arrays.toString(def.anIntArray6018)).append('\n');
            if (def.anIntArray6017 != null) {
                for (int animationId : def.anIntArray6017) {
                    if (animationId >= 0) {
                        renderAnimations.add(Integer.valueOf(animationId));
                    }
                }
            }
        } catch (RuntimeException ex) {
            out.append("<failed to read render set: ").append(ex.getClass().getSimpleName()).append(">\n");
        }
    }

    private static void appendRenderRef(StringBuilder out, Set<Integer> renderAnimations, String label, int animationId) {
        out.append(label).append('=').append(animationId).append('\n');
        if (animationId >= 0) {
            renderAnimations.add(Integer.valueOf(animationId));
        }
    }

    private static void appendGraphicsCorrelations(StringBuilder out, Interface18 loader,
            Set<Integer> interestingAnimations) {
        int matches = 0;
        int failures = 0;
        StringBuilder failureExamples = new StringBuilder();
        int count = loader.method45();
        for (int gfxId = 0; gfxId < count; gfxId++) {
            try {
                GraphicsDefinition def = (GraphicsDefinition) loader.getDefinition(gfxId, 0);
                if (def == null) {
                    continue;
                }
                int modelId = decodeStored(def.anInt8426, -1162407379);
                int animationId = decodeStored(def.anInt8433, -749724717);
                boolean animationMatch = interestingAnimations.contains(Integer.valueOf(animationId));
                boolean modelMatch = contains(ROTS_NPC_MODELS, modelId);
                if (!animationMatch && !modelMatch) {
                    continue;
                }

                matches++;
                out.append("\nGFX ").append(gfxId)
                        .append(" model=").append(modelId)
                        .append(" animation=").append(animationId)
                        .append(" reason=");
                if (animationMatch && modelMatch) {
                    out.append("animation+RoTS-model match");
                } else if (animationMatch) {
                    out.append("animation match");
                } else {
                    out.append("RoTS-model match");
                }
                out.append('\n');
            } catch (RuntimeException ex) {
                failures++;
                if (failures <= 5) {
                    failureExamples.append("GFX ").append(gfxId).append("=<read failed: ")
                            .append(ex.getClass().getSimpleName()).append(">\n");
                }
            }
        }

        if (matches > 0) {
            out.append("\nGFX matches=").append(matches).append('\n');
        }
        if (failures > 0) {
            out.append("GFX decode failures=").append(failures).append('\n');
            out.append("First decode failures:\n").append(failureExamples);
        }
        if (matches == 0) {
            if (failures == 0) {
                out.append("<no GFX definitions directly reference the targeted/render-set animations or known RoTS NPC models>\n");
            } else {
                out.append("<GFX correlation incomplete because definition decoding failed; do not treat zero matches as evidence>\n");
            }
        }
    }

    private static int decodeStored(int stored, int encodingMultiplier) {
        int inverse = 1;
        for (int i = 0; i < 5; i++) {
            inverse *= 2 - encodingMultiplier * inverse;
        }
        return stored * inverse;
    }

    private static boolean contains(int[] values, int target) {
        for (int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    private static int normalizeItemOverride(int itemId) {
        return itemId == 65535 ? -1 : itemId;
    }
}
