package game;

import java.util.Arrays;

/**
 * Read-only Client Console bridge for focused Rise of the Six cache research.
 * Matrix3's live client definition loaders remain authoritative.
 */
public final class ClientConsoleRotsBridge {

    private static final int[] ROTS_NPC_IDS = {
            18538, 18539, 18540, 18541, 18542, 18543, 18544, 18545,
            18546, 18547, 18548, 18549, 18550, 18551
    };

    private static final int[] NPC_PARAM_KEYS = {
            3, 4, 14, 26, 29, 641, 643, 965, 1346,
            2838, 2848, 2849, 2850, 2851, 2852, 2864, 2865,
            2890, 2892, 2918, 2955
    };

    private static volatile Interface18 npcDefinitions;
    private static volatile Interface18 animationDefinitions;

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

    public static boolean isReady() {
        return npcDefinitions != null && animationDefinitions != null;
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

        out.append("\n=== ROTS ALTERNATE ANIMATION NEIGHBORHOOD 21920-21940 ===\n");
        appendAnimationRange(out, animationLoader, 21920, 21940);

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
            return "HYPOTHESIS - subdued/downed brother state";
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

    private static void appendAnimation(StringBuilder out, int animationId, AnimationDefinition def) {
        long totalMs = 0L;
        for (int duration : def.anIntArray1546) {
            totalMs += duration * 10L;
        }

        int rightHandItem = normalizeItemOverride(def.anInt1553 * 196196667);
        int leftHandItem = normalizeItemOverride(def.anInt1554 * 224298189);
        int priority = def.priority * 1882694951;

        out.append("\nanimation=").append(animationId)
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

    private static int normalizeItemOverride(int itemId) {
        return itemId == 65535 ? -1 : itemId;
    }
}
