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
            String name = cleanName(((ObjectDefinitions) value).name);
            return name == null ? null : new DefinitionInfo(id, name);
        } catch (RuntimeException ex) {
            return null;
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

        private DefinitionInfo(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
