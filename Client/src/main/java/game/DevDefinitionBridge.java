package game;

/**
 * Narrow Dev Mode bridge for client definition lookup.
 *
 * Matrix3's existing Class639 loaders remain authoritative. This class only
 * remembers the NPC/object loaders after their real definitions pass through the
 * normal decode path so developer UI can search the same cache data by id/name.
 */
public final class DevDefinitionBridge {

    private static volatile Interface18 npcDefinitions;
    private static volatile Interface18 objectDefinitions;

    private DevDefinitionBridge() {
    }

    static void observeDefinitionLoader(Interface18 loader, Interface17 definition) {
        if (loader == null || definition == null) {
            return;
        }
        AtlasRuntimeBridge.observeDefinitionLoader(loader, definition);
        if (definition instanceof NPCDefintion) {
            npcDefinitions = loader;
        } else if (definition instanceof ObjectDefinitions) {
            objectDefinitions = loader;
        }
    }

    public static boolean isNpcDefinitionsReady() {
        return npcDefinitions != null;
    }

    public static boolean isObjectDefinitionsReady() {
        return objectDefinitions != null;
    }

    public static int getNpcCount() {
        return count(npcDefinitions);
    }

    public static int getObjectCount() {
        return count(objectDefinitions);
    }

    public static DefinitionInfo getNpcInfo(int id) {
        Interface18 definitions = npcDefinitions;
        if (definitions == null || id < 0 || id >= definitions.method45()) {
            return null;
        }
        try {
            Interface17 value = definitions.getDefinition(id, 0);
            if (!(value instanceof NPCDefintion)) {
                return null;
            }
            String name = cleanName(((NPCDefintion) value).aString4791);
            return name == null ? null : new DefinitionInfo(id, name);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static DefinitionInfo getObjectInfo(int id) {
        Interface18 definitions = objectDefinitions;
        if (definitions == null || id < 0 || id >= definitions.method45()) {
            return null;
        }
        try {
            Interface17 value = definitions.getDefinition(id, 0);
            if (!(value instanceof ObjectDefinitions)) {
                return null;
            }
            ObjectDefinitions object = (ObjectDefinitions) value;
            String name = cleanName(object.name);
            return name == null ? null : new DefinitionInfo(id, name, object.method6053((byte) 0));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Object Explorer lookup that preserves unnamed cache definitions.
     *
     * The normal Dev Spawn search intentionally filters null/blank names.
     * Object Explorer must still be able to browse those definitions by ID
     * because many rail primitives have no useful cache name.
     */
    public static DefinitionInfo getObjectInfoAny(int id) {
        Interface18 definitions = objectDefinitions;
        if (definitions == null || id < 0 || id >= definitions.method45()) {
            return null;
        }
        try {
            Interface17 value = definitions.getDefinition(id, 0);
            if (!(value instanceof ObjectDefinitions)) {
                return null;
            }
            ObjectDefinitions object = (ObjectDefinitions) value;
            String name = cleanName(object.name);
            if (name == null) {
                name = "id-" + id;
            }
            return new DefinitionInfo(id, name, object.method6053((byte) 0));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static int[] getObjectModelIds(int id) {
        Interface18 definitions = objectDefinitions;
        if (definitions == null || id < 0 || id >= definitions.method45()) return new int[0];
        try {
            Interface17 value = definitions.getDefinition(id, 0);
            if (!(value instanceof ObjectDefinitions)) return new int[0];
            ObjectDefinitions object = (ObjectDefinitions) value;
            if (object.anIntArrayArray5611 == null) return new int[0];
            int count = 0;
            for (int[] group : object.anIntArrayArray5611) if (group != null) count += group.length;
            int[] models = new int[count];
            int offset = 0;
            for (int[] group : object.anIntArrayArray5611) if (group != null) for (int model : group) models[offset++] = model;
            return models;
        } catch (RuntimeException ex) {
            return new int[0];
        }
    }

    private static int count(Interface18 definitions) {
        if (definitions == null) {
            return 0;
        }
        try {
            return Math.max(0, definitions.method45());
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static String cleanName(String value) {
        if (value == null) {
            return null;
        }
        String name = value.trim();
        if (name.length() == 0 || "null".equalsIgnoreCase(name)) {
            return null;
        }
        return name;
    }

    public static final class DefinitionInfo {
        private final int id;
        private final String name;
        private final int[] animationIds;

        private DefinitionInfo(int id, String name) {
            this(id, name, null);
        }

        private DefinitionInfo(int id, String name, int[] animationIds) {
            this.id = id;
            this.name = name;
            this.animationIds = animationIds == null ? null : animationIds.clone();
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        /**
         * Object-definition sequence ids decoded by ObjectDefinitions opcode 24/106.
         * NPC/name-only lookups return an empty array.
         */
        public int[] getAnimationIds() {
            return animationIds == null ? new int[0] : animationIds.clone();
        }
    }
}
