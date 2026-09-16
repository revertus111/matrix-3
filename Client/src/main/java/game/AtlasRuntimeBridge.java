package game;

import game.atlas.AtlasTraceRecorder;

/**
 * One-way runtime observation seam from obfuscated client ownership into Client
 * Atlas. Only compact neutral facts cross this boundary.
 */
public final class AtlasRuntimeBridge {

    private static final String KEYBOARD_SOURCE = "METHOD:game/AtlasKeyboardObserver#dispatch(Ljava/awt/AWTEvent;)V";
    private static final String KEYBOARD_OWNER = "METHOD:game/Class549_Sub1#method8081(ICII)V";
    private static final String MENU_ACTION_SOURCE = "METHOD:game/DevModeBridge#handleMenuAction(III)Z";
    private static final String MENU_ACTION_OWNER = "METHOD:game/Class319#method4094(Lgame/Class572_Sub12_Sub10;IIB)V";
    private static final String OUTGOING_PACKET_SOURCE = "METHOD:game/Class195#method2929(Lgame/Class572_Sub25;B)V";
    private static final String INCOMING_PACKET_SOURCE = "METHOD:game/MaterialInformation#method1605(Lgame/Class195;I)Z";
    private static final String INCOMING_PACKET_OWNER = "METHOD:game/PacketsDecoder#method3031(Lgame/Class195;B)Z";
    private static final String DEFINITION_SOURCE = "METHOD:game/Class639#method7568(II)Lgame/Interface17;";

    private AtlasRuntimeBridge() {
    }

    static void observeDefinitionLoader(Interface18 loader, Interface17 definition) {
        if (loader == null || definition == null) {
            return;
        }
        AtlasTraceRecorder.ensureRuntimeControl();
        AtlasKeyboardObserver.ensureInstalled();
    }

    public static void observeDefinitionLoad(int definitionId, Object loader, Object definition) {
        if (!ready()) {
            return;
        }
        String loaderClass = safeClassName(loader);
        String definitionClass = safeClassName(definition);
        String uniqueKey = definitionId + "|" + loaderClass + "|" + definitionClass;
        AtlasTraceRecorder.recordOnce("definition", "cache-miss-load", DEFINITION_SOURCE,
                uniqueKey, AtlasTraceRecorder.MAX_DEFINITION_EVENTS,
                "definitionId", Integer.toString(definitionId),
                "loaderClass", loaderClass,
                "definitionClass", definitionClass);
    }

    public static void observeKeyboardEvent(int action, char typedCharacter, int normalizedKeyCode) {
        if (!ready()) {
            return;
        }
        AtlasTraceRecorder.record("input", "keyboard", KEYBOARD_SOURCE,
                "ownerSymbol", KEYBOARD_OWNER,
                "action", Integer.toString(action),
                "keyCode", Integer.toString(normalizedKeyCode),
                "charCode", Integer.toString((int) typedCharacter));
    }

    public static void observeMenuAction(int action, int rawArg1, int rawArg2) {
        if (!ready()) {
            return;
        }
        AtlasTraceRecorder.record("input", "menu-action", MENU_ACTION_SOURCE,
                "ownerSymbol", MENU_ACTION_OWNER,
                "action", Integer.toString(action),
                "rawArg1", Integer.toString(rawArg1),
                "rawArg2", Integer.toString(rawArg2));
    }

    public static void observeOutgoingPacket(Class572_Sub25 node) {
        if (!ready() || node == null) {
            return;
        }
        OutgoingPacket packet = node.aClass312_9253;
        int packetId = packet == null ? -1 : packet.anInt3763 * 1414223481;
        int declaredLength = packet == null ? -1 : packet.anInt3792 * 1116005703;
        int encodedLength = node.anInt9254 * -423333573;
        AtlasTraceRecorder.record("network", "outgoing-packet", OUTGOING_PACKET_SOURCE,
                "packetId", Integer.toString(packetId),
                "declaredLength", Integer.toString(declaredLength),
                "encodedLength", Integer.toString(encodedLength));
    }

    public static void observeIncomingPacket(IncomingPacket packet, int actualLength) {
        if (!ready() || packet == null) {
            return;
        }
        int packetId = packet.id * 1839801621;
        int declaredLength = packet.length * 551393061;
        String interfaceName = interfacePacketName(packet);
        String category = interfaceName == null ? "network" : "interface";
        AtlasTraceRecorder.record(category, "incoming-packet", INCOMING_PACKET_SOURCE,
                "ownerSymbol", INCOMING_PACKET_OWNER,
                "packetId", Integer.toString(packetId),
                "declaredLength", Integer.toString(declaredLength),
                "actualLength", Integer.toString(actualLength),
                "packetName", interfaceName == null ? "UNKNOWN" : interfaceName);
    }

    private static boolean ready() {
        AtlasTraceRecorder.ensureRuntimeControl();
        AtlasKeyboardObserver.ensureInstalled();
        return AtlasTraceRecorder.isActive();
    }

    private static String safeClassName(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static String interfacePacketName(IncomingPacket packet) {
        if (packet == IncomingPacket.ROOT_INTERFACE)
            return "ROOT_INTERFACE";
        if (packet == IncomingPacket.SET_INTERFACE)
            return "SET_INTERFACE";
        if (packet == IncomingPacket.CLOSE_INTERFACE)
            return "CLOSE_INTERFACE";
        if (packet == IncomingPacket.MOVE_INTERFACE)
            return "MOVE_INTERFACE";
        if (packet == IncomingPacket.HIDE_INTERFACE_COMPONENT)
            return "HIDE_INTERFACE_COMPONENT";
        if (packet == IncomingPacket.INTERFACE_SETTINGS)
            return "INTERFACE_SETTINGS";
        if (packet == IncomingPacket.ANIMATION_ON_INTERFACE)
            return "ANIMATION_ON_INTERFACE";
        if (packet == IncomingPacket.SET_NPC_INTERFACE)
            return "SET_NPC_INTERFACE";
        if (packet == IncomingPacket.SET_PLAYER_INTERFACE)
            return "SET_PLAYER_INTERFACE";
        if (packet == IncomingPacket.SET_OBJECT_INTERFACE)
            return "SET_OBJECT_INTERFACE";
        if (packet == IncomingPacket.INTERFACE_COMPONENT_TEXT)
            return "INTERFACE_COMPONENT_TEXT";
        if (packet == IncomingPacket.MODEL_ON_ICOMPONENT)
            return "MODEL_ON_ICOMPONENT";
        if (packet == IncomingPacket.ITEM_ON_ICOMPONENT)
            return "ITEM_ON_ICOMPONENT";
        if (packet == IncomingPacket.NPC_ON_ICOMPONENT)
            return "NPC_ON_ICOMPONENT";
        if (packet == IncomingPacket.PLAYER_ON_ICOMPONENT)
            return "PLAYER_ON_ICOMPONENT";
        if (packet == IncomingPacket.PLAYER_HEAD_ON_ICOMPONENT)
            return "PLAYER_HEAD_ON_ICOMPONENT";
        if (packet == IncomingPacket.OTHER_PLAYER_ON_COMPONENT)
            return "OTHER_PLAYER_ON_COMPONENT";
        if (packet == IncomingPacket.OTHER_PLAYER_ON_ICOMPONENT)
            return "OTHER_PLAYER_ON_ICOMPONENT";
        if (packet == IncomingPacket.SET_SPRITE)
            return "SET_SPRITE";
        return null;
    }
}
