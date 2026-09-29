package game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Client-only procedural conveyor visual proof.
 *
 * V0 deliberately owns no settlement state, collision, scene registration,
 * inventories, or transport logic. One ConveyorRun produces one generated
 * renderer Model from the canonical custom conveyor assembly authored from
 * object 46298 / models 49717+49718. The untouched sawmill source components
 * are excluded before procedural run generation.
 */
public final class ConveyorRunPreview {

    private static final int SOURCE_OBJECT_ID = 46298;
    private static final int[] SOURCE_MODEL_IDS = { 49717, 49718 };

    private static final int TILE_UNITS = 512;

    private static final int DEFAULT_PAYLOAD_ITEM_ID = 1511;

    /*
     * Temporary belt-owned movement speed for the visual proof. Payload visual
     * profiles deliberately do not own speed; future conveyor tiers/runtime
     * state replace this constant without touching item presentation profiles.
     */
    private static final double CONVEYOR_TEST_SPEED_TILES_PER_SECOND = 1.25;
    private static final long PAYLOAD_EPOCH_NANOS = System.nanoTime();
    private static volatile int payloadItemId = DEFAULT_PAYLOAD_ITEM_ID;

    private static final double STRETCH_MIN_LONG_FRACTION = 0.60;
    private static final double REPEAT_DETAIL_SPACING_TILES = 1.0;
    private static final double MAX_SUPPORT_SPAN_TILES = 3.0;

    /*
     * Canonical authored conveyor assembly from the saved Matrix3 Live Model
     * project object_46298_model_49717(1).json.
     *
     * The source object contains the entire sawmill. Only these non-default
     * edited connected components were moved/scaled into the custom conveyor.
     * Runtime must never render the untouched sawmill components.
     */
    private static final AuthoredPart[] AUTHORED_PARTS = {
            new AuthoredPart(4, 45, 100, 100, -744, 0, -1140, 0),
            new AuthoredPart(5, 45, 100, 100, -744, 0, -1140, 0),
            new AuthoredPart(8, 100, 100, 100, -36, 0, -1140, 0),
            new AuthoredPart(16, 100, 100, 100, -36, 0, -1140, 0),
            new AuthoredPart(17, 100, 100, 100, -36, 0, -1140, 0),
            new AuthoredPart(18, 100, 100, 100, -36, 0, -1140, 0),
            new AuthoredPart(19, 100, 100, 100, -36, 0, -1140, 0),
            new AuthoredPart(21, 100, 100, 100, -36, 0, -1140, 0)
    };

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int TRANSFORM_FLAGS = 0x0f;
    private static final int MODEL_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS;
    private static final int RAW_BUILD_FLAGS = MODEL_FLAGS | 0x1f01f | 0x80000;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class261 PAYLOAD_TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    // Reserved Construction-only CSVar-string transport. PacketsDecoder
    // intercepts this id before Matrix3's normal CSVar store.
    public static final int SETTLEMENT_SYNC_CS_VAR = 65534;

    private static volatile boolean demoActive;
    private static volatile ConveyorRun[] demoRuns = new ConveyorRun[0];
    private static volatile ConveyorRun[] settlementRuns = new ConveyorRun[0];
    private static final List<ConveyorRun> pendingSettlementRuns =
            new ArrayList<ConveyorRun>();
    private static boolean settlementSyncOpen;
    private static volatile int revision;
    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile String status = "HIDDEN";
    private static volatile String roleSummary = "roles not generated";
    private static volatile LiveModelEditorParts.ConveyorRecipePart[] authoringRecipe =
            new LiveModelEditorParts.ConveyorRecipePart[0];

    private static Class106 cachedRenderer;
    private static int cachedRevision = Integer.MIN_VALUE;
    private static Model[] cachedModels = new Model[0];

    private static Class106 cachedPayloadRenderer;
    private static Model cachedPayloadModel;
    private static int cachedPayloadItemId = -1;
    private static int cachedPayloadScalePercent = -1;

    private ConveyorRunPreview() {
    }

    public static int getPayloadTestItemId() {
        return payloadItemId;
    }

    public static synchronized String setPayloadTestItemId(int itemId) {
        int nextItemId = Math.max(0, itemId);
        if (nextItemId != payloadItemId) {
            payloadItemId = nextItemId;
            ConveyorPayloadVisualProfiles.clearPreview();
        }
        return getPayloadProfileStatus();
    }

    public static synchronized String setPayloadVisualPreview(
            int itemId, int alongOffset, int sideOffset, int heightOffset,
            int scalePercent, int pitchDegrees, int yawDegrees, int rollDegrees) {
        payloadItemId = Math.max(0, itemId);
        ConveyorPayloadVisualProfiles.setPreview(
                payloadItemId,
                new ConveyorPayloadVisualProfiles.Profile(
                        alongOffset, sideOffset, heightOffset, scalePercent,
                        pitchDegrees, yawDegrees, rollDegrees));
        return getPayloadProfileStatus();
    }

    public static ConveyorPayloadVisualProfiles.Resolution getPayloadResolution() {
        return ConveyorPayloadVisualProfiles.resolve(payloadItemId);
    }

    public static ConveyorPayloadVisualProfiles.Resolution getPayloadRenderResolution() {
        return ConveyorPayloadVisualProfiles.resolveForRender(payloadItemId);
    }

    public static synchronized String resetPayloadVisualPreview() {
        ConveyorPayloadVisualProfiles.clearPreview();
        return getPayloadProfileStatus();
    }

    public static String getPayloadProfileStatus() {
        ConveyorPayloadVisualProfiles.Resolution render =
                ConveyorPayloadVisualProfiles.resolveForRender(payloadItemId);
        return ConveyorPayloadVisualProfiles.describe(payloadItemId)
                + ("LIVE PREVIEW".equals(render.source)
                        ? " | active=LIVE PREVIEW" : "");
    }

    private static int normalizeDegrees(int value) {
        int normalized = value % 360;
        if (normalized > 180) normalized -= 360;
        if (normalized < -180) normalized += 360;
        return normalized;
    }

    private static int degreesToAngle(int degrees) {
        return ((int) Math.round(normalizeDegrees(degrees)
                * 16384.0 / 360.0)) & 0x3fff;
    }

    public static String setAuthoringRecipe(
            LiveModelEditorParts.ConveyorRecipePart[] recipe) {
        if (recipe == null || recipe.length == 0) {
            status = "CONVEYOR ROLES: tag at least one BELT_SURFACE part first";
            return status;
        }

        int belts = 0;
        int included = 0;
        LinkedHashSet<Integer> sourceParts = new LinkedHashSet<Integer>();
        for (LiveModelEditorParts.ConveyorRecipePart part : recipe) {
            if (part == null
                    || part.role == LiveModelEditorParts.ConveyorRole.UNASSIGNED) {
                continue;
            }
            if (!sourceParts.add(Integer.valueOf(part.sourcePart))) {
                status = "CONVEYOR ROLES: duplicate source part "
                        + part.sourcePart + " is not supported in V1";
                return status;
            }
            if (part.role != LiveModelEditorParts.ConveyorRole.IGNORE) included++;
            if (part.role == LiveModelEditorParts.ConveyorRole.BELT_SURFACE) belts++;
        }

        if (belts == 0) {
            status = "CONVEYOR ROLES: at least one BELT_SURFACE is required";
            return status;
        }

        authoringRecipe = recipe.clone();
        revision++;
        invalidateModels();
        status = "CONVEYOR ROLE RECIPE READY included=" + included
                + " belt=" + belts + " tagged=" + sourceParts.size();
        return status;
    }

    /**
     * Builds three parallel A->B visual proofs around the local player.
     * No persistent Construction/world objects are created.
     */
    public static String showDemoNearPlayer() {
        Class613 region = client.aClass613_8605;
        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (region == null || player == null) {
            status = "WAIT active region/player";
            return status;
        }

        Class497 sceneBase = region.method7280((byte) -102);
        Class240 position = player.method5394().aClass240_2647;
        if (sceneBase == null || position == null) {
            status = "WAIT player world position";
            return status;
        }

        int localX = Math.round(position.aFloat2653) >> 9;
        int localY = Math.round(position.aFloat2657) >> 9;
        int worldX = sceneBase.localX * -2109597897 + localX;
        int worldY = sceneBase.localY * 417324155 + localY;
        int plane = player.aByte9009 & 0xff;

        demoRuns = new ConveyorRun[] {
                eastWestRun("SHORT", worldX, worldY + 2, plane, 2),
                eastWestRun("MEDIUM", worldX, worldY + 4, plane, 5),
                eastWestRun("LONG", worldX, worldY + 6, plane, 9)
        };
        demoActive = true;
        revision++;
        invalidateModels();
        status = "READY ConveyorRun V0: short=2t medium=5t long=9t; "
                + "source=authored conveyor assembly (46298 / 49717+49718)";
        return status;
    }

    public static String hide() {
        demoActive = false;
        demoRuns = new ConveyorRun[0];
        revision++;
        invalidateModels();
        lastRenderedCycle = Integer.MIN_VALUE;
        status = settlementRuns.length == 0
                ? "HIDDEN"
                : "DEMO HIDDEN; persistent=" + settlementRuns.length;
        return status;
    }

    public static String createPersistentTestNearPlayer() {
        Class613 region = client.aClass613_8605;
        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (region == null || player == null) {
            return "Persistent conveyor test is waiting for the active settlement.";
        }
        Class497 sceneBase = region.method7280((byte) -102);
        Class240 position = player.method5394().aClass240_2647;
        if (sceneBase == null || position == null) {
            return "Persistent conveyor test is waiting for player world position.";
        }

        int localX = Math.round(position.aFloat2653) >> 9;
        int localY = Math.round(position.aFloat2657) >> 9;
        int worldX = sceneBase.localX * -2109597897 + localX;
        int worldY = sceneBase.localY * 417324155 + localY;
        int plane = player.aByte9009 & 0xff;
        String command = "settlementconveyorcreate "
                + (worldX - 2) + " " + (worldY + 2) + " "
                + (worldX + 3) + " " + (worldY + 2) + " " + plane;
        String error = ClientConsoleBridge.queueConsoleCommands(
                new String[] { command });
        return error == null
                ? "Persistent 5-tile ConveyorRun create queued."
                : "Persistent ConveyorRun create failed to queue: " + error;
    }

    public static String clearPersistentRuns() {
        String error = ClientConsoleBridge.queueConsoleCommands(
                new String[] { "settlementconveyorclear" });
        return error == null
                ? "Persistent ConveyorRun clear queued."
                : "Persistent ConveyorRun clear failed to queue: " + error;
    }

    public static synchronized boolean handleSettlementSyncSignal(
            int id, String payload) {
        if (id != SETTLEMENT_SYNC_CS_VAR) {
            return false;
        }
        if (payload == null) {
            return true;
        }

        if ("BEGIN".equals(payload)) {
            pendingSettlementRuns.clear();
            settlementSyncOpen = true;
            return true;
        }
        if ("END".equals(payload)) {
            if (!settlementSyncOpen) {
                return true;
            }
            settlementRuns = pendingSettlementRuns.toArray(
                    new ConveyorRun[pendingSettlementRuns.size()]);
            pendingSettlementRuns.clear();
            settlementSyncOpen = false;
            revision++;
            invalidateModels();
            lastRenderedCycle = Integer.MIN_VALUE;
            status = "PERSISTENT SYNC runs=" + settlementRuns.length;
            return true;
        }
        if ("CLEAR".equals(payload)) {
            pendingSettlementRuns.clear();
            settlementSyncOpen = false;
            settlementRuns = new ConveyorRun[0];
            revision++;
            invalidateModels();
            lastRenderedCycle = Integer.MIN_VALUE;
            status = demoActive ? "PERSISTENT CLEARED; demo active" : "HIDDEN";
            return true;
        }
        if (payload.startsWith("RUN,")) {
            if (!settlementSyncOpen) {
                return true;
            }
            String[] values = payload.split(",");
            if (values.length != 7) {
                status = "PERSISTENT SYNC rejected malformed RUN";
                return true;
            }
            try {
                long runId = Long.parseLong(values[1]);
                int startX = Integer.parseInt(values[2]);
                int startY = Integer.parseInt(values[3]);
                int endX = Integer.parseInt(values[4]);
                int endY = Integer.parseInt(values[5]);
                int plane = Integer.parseInt(values[6]);
                if (runId > 0L && (startX != endX || startY != endY)) {
                    pendingSettlementRuns.add(new ConveyorRun(
                            runId, "RUN#" + runId,
                            startX, startY, endX, endY, plane));
                }
            } catch (NumberFormatException ex) {
                status = "PERSISTENT SYNC rejected malformed numbers";
            }
            return true;
        }

        return true;
    }

    public static synchronized void clearSettlementRuns() {
        pendingSettlementRuns.clear();
        settlementSyncOpen = false;
        settlementRuns = new ConveyorRun[0];
        revision++;
        invalidateModels();
        lastRenderedCycle = Integer.MIN_VALUE;
        status = demoActive ? "PERSISTENT CLEARED; demo active" : "HIDDEN";
    }

    public static String getStatus() {
        return status + " | persistent=" + settlementRuns.length
                + " demo=" + (demoActive ? demoRuns.length : 0)
                + " | " + roleSummary;
    }

    static void render(Class523 scene, Class106 renderer) {
        ConveyorRun[] current = visibleRuns();
        if (current.length == 0 || scene == null || renderer == null) {
            return;
        }

        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) {
            return;
        }
        lastRenderedCycle = cycle;

        Class613 region = client.aClass613_8605;
        if (region == null || region.method7285(0) != scene) {
            return;
        }

        Class497 sceneBase = region.method7280((byte) -102);
        Class639_Sub16 definitions = region.method7288(0);
        if (sceneBase == null || definitions == null) {
            status = "WAIT scene base/object definitions";
            return;
        }

        ObjectDefinitions definition = (ObjectDefinitions) definitions.getDefinition(
                SOURCE_OBJECT_ID, -1356282071);
        if (definition == null) {
            status = "SOURCE object 46298 definition unavailable";
            return;
        }

        if (cachedRenderer != renderer || cachedRevision != revision
                || cachedModels.length != current.length) {
            rebuildModels(renderer, definition, current);
        }

        int rendered = 0;
        int failed = 0;
        int payloads = 0;
        for (int i = 0; i < current.length; i++) {
            Model model = i < cachedModels.length ? cachedModels[i] : null;
            if (model != null && renderOne(current[i], model, definition,
                    scene, renderer, sceneBase)) {
                rendered++;
            } else {
                failed++;
            }

            if (current[i].runId > 0L
                    && renderPayload(current[i], scene, renderer, sceneBase)) {
                payloads++;
            }
        }

        status = "DRAW ConveyorRun V1 " + rendered + "/" + current.length
                + " payloadItem=" + payloadItemId
                + " payloads=" + payloads
                + (failed == 0 ? "" : " failed=" + failed);
    }

    private static ConveyorRun[] visibleRuns() {
        ConveyorRun[] persistent = settlementRuns;
        ConveyorRun[] demo = demoActive ? demoRuns : new ConveyorRun[0];
        if (demo.length == 0) {
            return persistent;
        }
        if (persistent.length == 0) {
            return demo;
        }
        ConveyorRun[] combined = new ConveyorRun[persistent.length + demo.length];
        System.arraycopy(persistent, 0, combined, 0, persistent.length);
        System.arraycopy(demo, 0, combined, persistent.length, demo.length);
        return combined;
    }

    private static ConveyorRun eastWestRun(String name, int centerWorldX,
            int worldY, int plane, int lengthTiles) {
        int left = lengthTiles / 2;
        int startX = centerWorldX - left;
        int endX = startX + lengthTiles;
        return new ConveyorRun(-1L, name, startX, worldY, endX, worldY, plane);
    }

    private static void rebuildModels(Class106 renderer, ObjectDefinitions definition,
            ConveyorRun[] current) {
        Model[] built = new Model[current.length];
        String summary = "roles unavailable";

        for (int i = 0; i < current.length; i++) {
            Generation generation = generateRaw(definition, current[i]);
            if (generation == null || generation.raw == null) {
                built[i] = null;
                continue;
            }
            summary = generation.summary;
            built[i] = buildModel(renderer, definition, generation.raw,
                    generation.sourceAxisX, current[i].headingYaw());
        }

        cachedModels = built;
        cachedRenderer = renderer;
        cachedRevision = revision;
        roleSummary = summary + " | static-belt visual";
        System.out.println("[ConveyorRunPreview] " + roleSummary);
    }

    private static Generation generateRaw(ObjectDefinitions definition, ConveyorRun run) {
        LiveModelEditorParts.ConveyorRecipePart[] recipe = authoringRecipe;
        if (recipe != null && recipe.length > 0) {
            return generateRoleRaw(definition, run, recipe);
        }
        return generateLegacyRaw(definition, run);
    }

    private static Generation generateRoleRaw(ObjectDefinitions definition,
            ConveyorRun run, LiveModelEditorParts.ConveyorRecipePart[] recipe) {
        Class159 authored = decodeSource(definition);
        Class159 working = decodeSource(definition);
        if (authored == null || working == null
                || working.anInt1791 <= 0 || working.anInt1778 <= 0) {
            status = "SOURCE decode failed for 49717/49718";
            return null;
        }

        Component[] components = detectComponents(working);
        if (components.length == 0) {
            status = "SOURCE connected-component analysis failed";
            return null;
        }

        for (LiveModelEditorParts.ConveyorRecipePart part : recipe) {
            if (part == null) continue;
            if (part.sourcePart < 0 || part.sourcePart >= components.length) {
                status = "CONVEYOR ROLE source part " + part.sourcePart
                        + " unavailable; detected=" + components.length;
                return null;
            }
        }

        ensureFaceAlpha(working);
        ensureFaceAlpha(authored);
        for (int i = 0; i < components.length; i++) {
            LiveModelEditorParts.ConveyorRecipePart part = recipePart(recipe, i);
            if (part == null
                    || part.role == LiveModelEditorParts.ConveyorRole.UNASSIGNED
                    || part.role == LiveModelEditorParts.ConveyorRole.IGNORE) {
                hideFaces(working, components[i]);
                continue;
            }
            applyRecipeTransform(working, components[i], part);
            applyRecipeTransform(authored, components[i], part);
        }

        populateComponentBounds(working, components);
        Bounds all = boundsForRecipeParts(working, components, recipe);
        if (all == null) {
            status = "CONVEYOR ROLE recipe has no visible authored geometry";
            return null;
        }

        boolean axisX = all.sizeX >= all.sizeZ;
        double sourceMin = axisX ? all.minX : all.minZ;
        double sourceMax = axisX ? all.maxX : all.maxZ;
        double sourceCenter = (sourceMin + sourceMax) * 0.5;
        double sourceLength = Math.max(1.0, sourceMax - sourceMin);
        double targetLength = Math.max(TILE_UNITS, run.lengthTiles() * TILE_UNITS);
        double stretchFactor = targetLength / sourceLength;

        ArrayList<Component> repeat = new ArrayList<Component>();
        ArrayList<Component> supports = new ArrayList<Component>();
        int beltCount = 0;
        int startCount = 0;
        int endCount = 0;
        int fixedCount = 0;

        int startShift = (int) Math.round(
                (sourceCenter - targetLength * 0.5) - sourceMin);
        int endShift = (int) Math.round(
                (sourceCenter + targetLength * 0.5) - sourceMax);

        for (LiveModelEditorParts.ConveyorRecipePart part : recipe) {
            if (part == null
                    || part.role == LiveModelEditorParts.ConveyorRole.UNASSIGNED
                    || part.role == LiveModelEditorParts.ConveyorRole.IGNORE) {
                continue;
            }
            Component component = components[part.sourcePart];
            switch (part.role) {
            case BELT_SURFACE:
                scaleComponentAxis(working, component, axisX,
                        sourceCenter, stretchFactor);
                beltCount++;
                break;
            case START_CAP:
                translateComponentAxis(working, component, axisX, startShift);
                startCount++;
                break;
            case END_CAP:
                translateComponentAxis(working, component, axisX, endShift);
                endCount++;
                break;
            case REPEAT_DETAIL:
                hideFaces(working, component);
                repeat.add(component);
                break;
            case SUPPORT:
                hideFaces(working, component);
                supports.add(component);
                break;
            case FIXED_DETAIL:
            case SCALE_POSITION:
                remapComponentPosition(working, component, axisX,
                        sourceMin, sourceLength, sourceCenter, targetLength);
                fixedCount++;
                break;
            default:
                break;
            }
        }

        if (beltCount == 0) {
            status = "CONVEYOR ROLE recipe lost BELT_SURFACE";
            return null;
        }

        ArrayList<Class159> generated = new ArrayList<Class159>();
        generated.add(working);

        int repeatStations = 0;
        if (!repeat.isEmpty()) {
            repeatStations = Math.max(1, (int) Math.floor(
                    run.lengthTiles() / REPEAT_DETAIL_SPACING_TILES));
            double groupCenter = roleGroupCenter(repeat, axisX);
            for (int station = 0; station < repeatStations; station++) {
                double fraction = (station + 0.5) / repeatStations;
                double targetCenter = sourceCenter + (fraction - 0.5) * targetLength;
                int shift = (int) Math.round(targetCenter - groupCenter);
                for (Component component : repeat) {
                    Class159 copy = componentOnlyTranslatedRaw(
                            authored, component, axisX, shift);
                    if (copy != null) generated.add(copy);
                }
            }
        }

        int supportStations = 0;
        if (!supports.isEmpty()) {
            supportStations = Math.max(0,
                    (int) Math.ceil(run.lengthTiles() / MAX_SUPPORT_SPAN_TILES) - 1);
            double groupCenter = roleGroupCenter(supports, axisX);
            for (int station = 0; station < supportStations; station++) {
                double fraction = (station + 1.0) / (supportStations + 1.0);
                double targetCenter = sourceCenter + (fraction - 0.5) * targetLength;
                int shift = (int) Math.round(targetCenter - groupCenter);
                for (Component component : supports) {
                    Class159 copy = componentOnlyTranslatedRaw(
                            authored, component, axisX, shift);
                    if (copy != null) generated.add(copy);
                }
            }
        }

        Class159 raw = generated.size() == 1
                ? generated.get(0)
                : new Class159(generated.toArray(new Class159[generated.size()]),
                        generated.size());

        String summary = "ROLES axis=" + (axisX ? "X" : "Z")
                + " belt=" + beltCount
                + " startCap=" + startCount
                + " endCap=" + endCount
                + " fixed=" + fixedCount
                + " repeatParts=" + repeat.size()
                + " repeatStations=" + repeatStations
                + " supportParts=" + supports.size()
                + " supportStations=" + supportStations;
        return new Generation(raw, axisX, summary);
    }

    private static Generation generateLegacyRaw(ObjectDefinitions definition, ConveyorRun run) {
        Class159 working = decodeSource(definition);
        if (working == null || working.anInt1791 <= 0 || working.anInt1778 <= 0) {
            status = "SOURCE decode failed for 49717/49718";
            return null;
        }

        Component[] components = detectComponents(working);
        if (components.length == 0) {
            status = "SOURCE connected-component analysis failed";
            return null;
        }

        for (AuthoredPart authored : AUTHORED_PARTS) {
            if (authored.index < 0 || authored.index >= components.length) {
                status = "SOURCE authored component " + authored.index
                        + " unavailable; detected=" + components.length;
                return null;
            }
        }

        /*
         * Reproduce the user's saved Live Model Editor assembly first.
         * Everything not in AUTHORED_PARTS is hidden before any procedural
         * run-length work, so the original sawmill can never appear.
         */
        ensureFaceAlpha(working);
        for (int i = 0; i < components.length; i++) {
            AuthoredPart authored = authoredPart(i);
            if (authored == null) {
                hideFaces(working, components[i]);
            } else {
                applyAuthoredTransform(working, components[i], authored);
            }
        }

        populateComponentBounds(working, components);
        Bounds all = boundsForAuthoredParts(working, components);
        if (all == null) {
            status = "SOURCE authored conveyor bounds unavailable";
            return null;
        }

        boolean axisX = all.sizeX >= all.sizeZ;
        double sourceMin = axisX ? all.minX : all.minZ;
        double sourceMax = axisX ? all.maxX : all.maxZ;
        double sourceCenter = (sourceMin + sourceMax) * 0.5;
        double sourceLength = Math.max(1.0, sourceMax - sourceMin);
        double targetLength = Math.max(TILE_UNITS, run.lengthTiles() * TILE_UNITS);
        double stretchFactor = targetLength / sourceLength;

        ArrayList<Component> stretch = new ArrayList<Component>();
        ArrayList<Component> positioned = new ArrayList<Component>();

        for (AuthoredPart authored : AUTHORED_PARTS) {
            Component component = components[authored.index];
            double longSize = axisX ? component.sizeX : component.sizeZ;
            double longFraction = longSize / sourceLength;
            if (longFraction >= STRETCH_MIN_LONG_FRACTION) {
                stretch.add(component);
            } else {
                positioned.add(component);
            }
        }

        /*
         * If the authored source does not expose an obvious long-span mesh,
         * fail visibly rather than stretching every detail as a fallback.
         */
        if (stretch.isEmpty()) {
            status = "SOURCE authored conveyor has no stretch candidate; "
                    + "inspect authored component bounds";
            return null;
        }

        for (Component component : stretch) {
            scaleComponentAxis(working, component, axisX, sourceCenter, stretchFactor);
        }
        for (Component component : positioned) {
            remapComponentPosition(working, component, axisX,
                    sourceMin, sourceLength, sourceCenter, targetLength);
        }

        String summary = "AUTHORED sourceParts=4,5,8,16,17,18,19,21"
                + " axis=" + (axisX ? "X" : "Z")
                + " stretch=" + stretch.size()
                + " fixed=" + positioned.size()
                + " support=DEFERRED"
                + " sourceSpan=" + Math.round(sourceLength);
        return new Generation(working, axisX, summary);
    }

    private static LiveModelEditorParts.ConveyorRecipePart recipePart(
            LiveModelEditorParts.ConveyorRecipePart[] recipe, int sourcePart) {
        for (LiveModelEditorParts.ConveyorRecipePart part : recipe) {
            if (part != null && part.sourcePart == sourcePart) return part;
        }
        return null;
    }

    private static void applyRecipeTransform(Class159 raw, Component component,
            LiveModelEditorParts.ConveyorRecipePart state) {
        if (raw == null || component == null || component.vertices.length == 0) return;

        long cx = 0L, cy = 0L, cz = 0L;
        for (int vertex : component.vertices) {
            cx += raw.anIntArray1782[vertex];
            cy += raw.anIntArray1777[vertex];
            cz += raw.anIntArray1797[vertex];
        }
        cx /= component.vertices.length;
        cy /= component.vertices.length;
        cz /= component.vertices.length;

        double radians = Math.toRadians(state.yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        for (int vertex : component.vertices) {
            double x = (raw.anIntArray1782[vertex] - cx) * state.scaleX / 100.0;
            double y = (raw.anIntArray1777[vertex] - cy) * state.scaleY / 100.0;
            double z = (raw.anIntArray1797[vertex] - cz) * state.scaleZ / 100.0;
            double rx = x * cos + z * sin;
            double rz = z * cos - x * sin;
            raw.anIntArray1782[vertex] = (int) Math.round(cx + rx + state.moveX);
            raw.anIntArray1777[vertex] = (int) Math.round(cy + y + state.moveY);
            raw.anIntArray1797[vertex] = (int) Math.round(cz + rz + state.moveZ);
        }
    }

    private static Bounds boundsForRecipeParts(Class159 raw, Component[] components,
            LiveModelEditorParts.ConveyorRecipePart[] recipe) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean found = false;

        for (LiveModelEditorParts.ConveyorRecipePart part : recipe) {
            if (part == null
                    || part.role == LiveModelEditorParts.ConveyorRole.UNASSIGNED
                    || part.role == LiveModelEditorParts.ConveyorRole.IGNORE) {
                continue;
            }
            Component component = components[part.sourcePart];
            for (int vertex : component.vertices) {
                int x = raw.anIntArray1782[vertex];
                int y = raw.anIntArray1777[vertex];
                int z = raw.anIntArray1797[vertex];
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
                found = true;
            }
        }
        return found ? new Bounds(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }

    private static double roleGroupCenter(List<Component> components, boolean axisX) {
        if (components == null || components.isEmpty()) return 0.0;
        double total = 0.0;
        for (Component component : components) {
            total += axisX ? component.centerX : component.centerZ;
        }
        return total / components.size();
    }

    private static AuthoredPart authoredPart(int componentIndex) {
        for (AuthoredPart authored : AUTHORED_PARTS) {
            if (authored.index == componentIndex) return authored;
        }
        return null;
    }

    private static void applyAuthoredTransform(Class159 raw, Component component,
            AuthoredPart state) {
        if (raw == null || component == null || component.vertices.length == 0) return;

        long cx = 0L;
        long cy = 0L;
        long cz = 0L;
        for (int vertex : component.vertices) {
            cx += raw.anIntArray1782[vertex];
            cy += raw.anIntArray1777[vertex];
            cz += raw.anIntArray1797[vertex];
        }
        cx /= component.vertices.length;
        cy /= component.vertices.length;
        cz /= component.vertices.length;

        double radians = Math.toRadians(state.yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        for (int vertex : component.vertices) {
            double x = (raw.anIntArray1782[vertex] - cx) * state.scaleX / 100.0;
            double y = (raw.anIntArray1777[vertex] - cy) * state.scaleY / 100.0;
            double z = (raw.anIntArray1797[vertex] - cz) * state.scaleZ / 100.0;
            double rx = x * cos + z * sin;
            double rz = z * cos - x * sin;
            raw.anIntArray1782[vertex] = (int) Math.round(cx + rx + state.moveX);
            raw.anIntArray1777[vertex] = (int) Math.round(cy + y + state.moveY);
            raw.anIntArray1797[vertex] = (int) Math.round(cz + rz + state.moveZ);
        }
    }

    private static Bounds boundsForAuthoredParts(Class159 raw, Component[] components) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean found = false;

        for (AuthoredPart authored : AUTHORED_PARTS) {
            Component component = components[authored.index];
            for (int vertex : component.vertices) {
                int x = raw.anIntArray1782[vertex];
                int y = raw.anIntArray1777[vertex];
                int z = raw.anIntArray1797[vertex];
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
                found = true;
            }
        }

        return found ? new Bounds(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }

    private static Class159 decodeSource(ObjectDefinitions definition) {
        if (definition == null || definition.aClass518_5608 == null) return null;
        Class159[] raws = new Class159[SOURCE_MODEL_IDS.length];
        for (int i = 0; i < SOURCE_MODEL_IDS.length; i++) {
            byte[] bytes = definition.aClass518_5608.method6136(
                    SOURCE_MODEL_IDS[i], 49248435);
            if (bytes == null) return null;
            raws[i] = new Class159(bytes);
            if (raws[i].anInt1773 < 13) raws[i].method2567(2);
        }
        return raws.length == 1 ? raws[0] : new Class159(raws, raws.length);
    }

    private static Model buildModel(Class106 renderer, ObjectDefinitions definition,
            Class159 raw, boolean sourceAxisX, int runYaw) {
        if (renderer == null || definition == null || raw == null) return null;

        int ambient = definition.anInt5638 * 1878786655 + 64;
        int contrast = -69277109 * definition.anInt5639 + 850;
        Model model;
        try {
            model = renderer.method1755(raw, RAW_BUILD_FLAGS,
                    definition.aClass518_5608.anInt5751 * 1583875953,
                    ambient, contrast);
        } catch (RuntimeException ex) {
            System.err.println("[ConveyorRunPreview] model build failed: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }
        if (model == null) return null;

        if (definition.aBool5647) model.method1359();

        if (definition.aShortArray5613 != null && definition.aShortArray5621 != null) {
            int count = Math.min(definition.aShortArray5613.length,
                    definition.aShortArray5621.length);
            for (int i = 0; i < count; i++) {
                short replacement = definition.aShortArray5621[i];
                if (definition.aByteArray5615 != null
                        && i < definition.aByteArray5615.length) {
                    replacement = ObjectDefinitions.aShortArray5606[
                            definition.aByteArray5615[i] & 0xff];
                }
                model.method1393(definition.aShortArray5613[i], replacement);
            }
        }

        if (definition.aShortArray5618 != null && definition.aShortArray5617 != null) {
            int count = Math.min(definition.aShortArray5618.length,
                    definition.aShortArray5617.length);
            for (int i = 0; i < count; i++) {
                model.method1494(definition.aShortArray5618[i],
                        definition.aShortArray5617[i]);
            }
        }

        if (definition.aByte5666 != 0) {
            model.method1396(definition.aByte5616, definition.aByte5681,
                    definition.aByte5622, definition.aByte5666 & 0xff);
        }

        int dsx = definition.anInt5646 * 898312795;
        int dsy = definition.anInt5634 * 1899990883;
        int dsz = definition.anInt5641 * 1427207859;
        if (dsx != 128 || dsy != 128 || dsz != 128) {
            model.method1464(dsx, dsy, dsz);
        }

        int dmx = definition.anInt5652 * -865773249;
        int dmy = definition.anInt5653 * -955267449;
        int dmz = definition.anInt5654 * -504975083;
        if (dmx != 0 || dmy != 0 || dmz != 0) {
            model.method1358(dmx, dmy, dmz);
        }

        int yaw = runYaw;
        if (!sourceAxisX) yaw = (yaw + 4096) & 0x3fff;
        if (yaw != 0) model.method1412(yaw);

        // Native source animation is intentionally non-blocking/carryover.
        // Runtime conveyor proof stays static while payload motion becomes the
        // gameplay movement layer.
        model.method1450(MODEL_FLAGS);
        return model;
    }

    private static boolean renderPayload(ConveyorRun run,
            Class523 scene, Class106 renderer, Class497 sceneBase) {
        if (run == null || run.runId <= 0L || renderer == null
                || scene == null || sceneBase == null) {
            return false;
        }

        ConveyorPayloadVisualProfiles.Resolution payloadResolution =
                ConveyorPayloadVisualProfiles.resolveForRender(payloadItemId);
        ConveyorPayloadVisualProfiles.Profile payloadProfile =
                payloadResolution.profile;
        Model payloadModel = getPayloadModel(
                renderer, payloadItemId, payloadProfile.scalePercent);
        if (payloadModel == null) {
            return false;
        }

        int plane = run.plane;
        if (plane < 0 || plane >= scene.aClass174Array5838.length) {
            return false;
        }
        Class174 ground = scene.aClass174Array5838[plane];
        if (ground == null) {
            return false;
        }

        int baseWorldX = sceneBase.localX * -2109597897;
        int baseWorldY = sceneBase.localY * 417324155;
        int startLocalX = run.startX - baseWorldX;
        int startLocalY = run.startY - baseWorldY;
        int endLocalX = run.endX - baseWorldX;
        int endLocalY = run.endY - baseWorldY;

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (startLocalX < 0 || startLocalY < 0 || endLocalX < 0 || endLocalY < 0
                || startLocalX >= sceneWidth || endLocalX >= sceneWidth
                || startLocalY >= sceneHeight || endLocalY >= sceneHeight) {
            return false;
        }

        double lengthTiles = Math.max(0.001, run.lengthTiles());
        double elapsedSeconds =
                (System.nanoTime() - PAYLOAD_EPOCH_NANOS) / 1000000000.0;
        double runOffsetTiles = (run.runId % 7L) * 0.37;
        double distanceTiles =
                (elapsedSeconds * CONVEYOR_TEST_SPEED_TILES_PER_SECOND
                        + runOffsetTiles)
                % lengthTiles;
        double progress = distanceTiles / lengthTiles;

        int tileSize = ground.anInt2087 * 2129890771;
        double deltaX = endLocalX - startLocalX;
        double deltaY = endLocalY - startLocalY;
        double directionX = deltaX / lengthTiles;
        double directionY = deltaY / lengthTiles;
        double alongTiles = payloadProfile.alongOffset / (double) TILE_UNITS;
        double sideTiles = payloadProfile.sideOffset / (double) TILE_UNITS;

        double localX = startLocalX + deltaX * progress
                + directionX * alongTiles - directionY * sideTiles;
        double localY = startLocalY + deltaY * progress
                + directionY * alongTiles + directionX * sideTiles;
        int sceneX = (int) Math.round(localX * tileSize + tileSize * 0.5);
        int sceneZ = (int) Math.round(localY * tileSize + tileSize * 0.5);

        // ConveyorRun itself is a straight generated deck positioned from the
        // run midpoint, so keep the payload on that same horizontal deck rather
        // than making it bob with every terrain sample under the span.
        double midLocalX = (startLocalX + endLocalX) * 0.5;
        double midLocalY = (startLocalY + endLocalY) * 0.5;
        int midSceneX = (int) Math.round(midLocalX * tileSize + tileSize * 0.5);
        int midSceneZ = (int) Math.round(midLocalY * tileSize + tileSize * 0.5);
        int sceneY = ground.method2718(midSceneX, midSceneZ, 0)
                + payloadProfile.heightOffset;

        PAYLOAD_TRANSFORM.method3594();

        int pitch = degreesToAngle(payloadProfile.pitchDegrees);
        int yaw = (run.headingYaw()
                + degreesToAngle(payloadProfile.yawDegrees)) & 0x3fff;
        int roll = degreesToAngle(payloadProfile.rollDegrees);

        if (pitch != 0) {
            PAYLOAD_TRANSFORM.method3576(
                    1.0F, 0.0F, 0.0F, Class325.method4146(pitch));
        }
        if (yaw != 0) {
            PAYLOAD_TRANSFORM.method3576(
                    0.0F, 1.0F, 0.0F, Class325.method4146(yaw));
        }
        if (roll != 0) {
            PAYLOAD_TRANSFORM.method3576(
                    0.0F, 0.0F, 1.0F, Class325.method4146(roll));
        }
        PAYLOAD_TRANSFORM.method3580((float) sceneX, (float) sceneY, (float) sceneZ);

        payloadModel.method1375(PAYLOAD_TRANSFORM, null, 0);
        return true;
    }

    private static Model getPayloadModel(
            Class106 renderer, int itemId, int scalePercent) {
        if (renderer == null) {
            return null;
        }
        if (cachedPayloadRenderer == renderer && cachedPayloadModel != null
                && cachedPayloadItemId == itemId
                && cachedPayloadScalePercent == scalePercent) {
            return cachedPayloadModel;
        }

        Class639_Sub5 itemDefinitions =
                ClientConsoleItemBridge.getRegisteredItemDefinitions();
        if (itemDefinitions == null
                || itemId < 0
                || itemId >= itemDefinitions.method45()) {
            return null;
        }

        try {
            ItemDefinitions definition = (ItemDefinitions)
                    itemDefinitions.getDefinition(itemId, 0);
            if (definition == null) {
                return null;
            }
            Model model = definition.method7526(
                    renderer,
                    MODEL_FLAGS,
                    1,
                    null,
                    null,
                    0, 0, 0, 0,
                    0);
            if (model == null) {
                return null;
            }

            int modelScale = Math.max(1,
                    (int) Math.round(128.0 * scalePercent / 100.0));
            if (modelScale != 128) {
                model.method1464(modelScale, modelScale, modelScale);
            }

            model.method1450(MODEL_FLAGS);
            cachedPayloadRenderer = renderer;
            cachedPayloadModel = model;
            cachedPayloadItemId = itemId;
            cachedPayloadScalePercent = scalePercent;
            return model;
        } catch (RuntimeException ex) {
            System.err.println("[ConveyorRunPreview] Payload item model build failed: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return null;
        }
    }

    private static boolean renderOne(ConveyorRun run, Model model,
            ObjectDefinitions definition, Class523 scene,
            Class106 renderer, Class497 sceneBase) {
        int plane = run.plane;
        if (plane < 0 || plane >= scene.aClass174Array5838.length) return false;
        Class174 ground = scene.aClass174Array5838[plane];
        if (ground == null) return false;

        int baseWorldX = sceneBase.localX * -2109597897;
        int baseWorldY = sceneBase.localY * 417324155;
        int startLocalX = run.startX - baseWorldX;
        int startLocalY = run.startY - baseWorldY;
        int endLocalX = run.endX - baseWorldX;
        int endLocalY = run.endY - baseWorldY;

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (startLocalX < 0 || startLocalY < 0 || endLocalX < 0 || endLocalY < 0
                || startLocalX >= sceneWidth || endLocalX >= sceneWidth
                || startLocalY >= sceneHeight || endLocalY >= sceneHeight) {
            return false;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        double midLocalX = (startLocalX + endLocalX) * 0.5;
        double midLocalY = (startLocalY + endLocalY) * 0.5;
        int sceneX = (int) Math.round(midLocalX * tileSize + tileSize * 0.5);
        int sceneZ = (int) Math.round(midLocalY * tileSize + tileSize * 0.5);
        int sceneY = ground.method2718(sceneX, sceneZ, 0);

        int environmentX = clamp((int) Math.floor(midLocalX), 0, sceneWidth - 1);
        int environmentY = clamp((int) Math.floor(midLocalY), 0, sceneHeight - 1);
        if (scene.aClass174Array5840 == scene.aClass174Array5875
                && scene.aClass174Array5838[0] != null) {
            Class86 environment = new Class86();
            environment.anInt1193 =
                    scene.method6231(environmentX, environmentY, 1258315415) * 1368828903;
            environment.anInt1190 =
                    scene.method6230(environmentX, environmentY, -981999643) * 1765263439;
            environment.anInt1191 =
                    scene.method6283(environmentX, environmentY, 775342000) * 628738217;
            environment.anInt1189 =
                    scene.method6233(environmentX, environmentY, -1042067865) * -233369847;
            environment.anInt1194 =
                    scene.method6234(environmentX, environmentY, (byte) 16) * -223776263;
            environment.anInt1195 =
                    scene.method6235(environmentX, environmentY, (byte) 95) * -963547665;
            renderer.method1790(scene.aClass174Array5838[0]
                    .method2726(sceneX, sceneZ, 358769667), environment);
        }

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        model.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        return true;
    }

    private static void scaleComponentAxis(Class159 raw, Component component,
            boolean axisX, double center, double factor) {
        for (int vertex : component.vertices) {
            int value = axisX ? raw.anIntArray1782[vertex] : raw.anIntArray1797[vertex];
            int scaled = (int) Math.round(center + (value - center) * factor);
            if (axisX) raw.anIntArray1782[vertex] = scaled;
            else raw.anIntArray1797[vertex] = scaled;
        }
    }

    private static void remapComponentPosition(Class159 raw, Component component,
            boolean axisX, double sourceMin, double sourceLength,
            double sourceCenter, double targetLength) {
        double componentCenter = axisX ? component.centerX : component.centerZ;
        double fraction = (componentCenter - sourceMin) / sourceLength;
        double targetCenter = sourceCenter + (fraction - 0.5) * targetLength;
        int shift = (int) Math.round(targetCenter - componentCenter);
        translateComponentAxis(raw, component, axisX, shift);
    }

    private static void translateComponentAxis(Class159 raw, Component component,
            boolean axisX, int shift) {
        if (shift == 0) return;
        for (int vertex : component.vertices) {
            if (axisX) raw.anIntArray1782[vertex] += shift;
            else raw.anIntArray1797[vertex] += shift;
        }
    }

    private static Class159 componentOnlyTranslatedRaw(Class159 sourceRaw,
            Component component, boolean axisX, int shift) {
        if (sourceRaw == null || component == null) return null;

        int[] map = new int[sourceRaw.anInt1791];
        Arrays.fill(map, -1);
        Class159 raw = new Class159(component.vertices.length,
                component.faces.length, 0);
        raw.anInt1773 = sourceRaw.anInt1773;
        raw.anInt1791 = component.vertices.length;
        raw.anInt1775 = component.vertices.length;
        raw.anInt1778 = component.faces.length;

        for (int i = 0; i < component.vertices.length; i++) {
            int old = component.vertices[i];
            map[old] = i;
            raw.anIntArray1782[i] = sourceRaw.anIntArray1782[old]
                    + (axisX ? shift : 0);
            raw.anIntArray1777[i] = sourceRaw.anIntArray1777[old];
            raw.anIntArray1797[i] = sourceRaw.anIntArray1797[old]
                    + (axisX ? 0 : shift);
            if (sourceRaw.anIntArray1813 != null
                    && old < sourceRaw.anIntArray1813.length) {
                raw.anIntArray1813[i] = sourceRaw.anIntArray1813[old];
            }
        }

        for (int i = 0; i < component.faces.length; i++) {
            int face = component.faces[i];
            int a = map[sourceRaw.aShortArray1786[face] & 0xffff];
            int b = map[sourceRaw.aShortArray1787[face] & 0xffff];
            int c = map[sourceRaw.aShortArray1789[face] & 0xffff];
            if (a < 0 || b < 0 || c < 0) return null;

            raw.aShortArray1786[i] = (short) a;
            raw.aShortArray1787[i] = (short) b;
            raw.aShortArray1789[i] = (short) c;
            raw.faceColours[i] = sourceRaw.faceColours == null
                    ? 0 : sourceRaw.faceColours[face];
            raw.faceAlpha[i] = sourceRaw.faceAlpha == null
                    ? 0 : sourceRaw.faceAlpha[face];

            // Compact role copies keep the face texture id but drop explicit
            // texture-triangle mapping because that mapping references source
            // model texture triangles not copied into this compact raw.
            raw.faceTextures[i] = sourceRaw.faceTextures == null
                    ? (short) -1 : sourceRaw.faceTextures[face];
            raw.faceTextureIndexes[i] = -1;
            raw.aByteArray1792[i] = sourceRaw.aByteArray1792 == null
                    ? 0 : sourceRaw.aByteArray1792[face];
            raw.aByteArray1799[i] = sourceRaw.aByteArray1799 == null
                    ? 0 : sourceRaw.aByteArray1799[face];
            raw.anIntArray1780[i] = sourceRaw.anIntArray1780 == null
                    ? 0 : sourceRaw.anIntArray1780[face];
        }
        return raw;
    }

    private static Component[] detectComponents(Class159 raw) {
        int vertices = raw.anInt1791;
        int[] parent = new int[vertices];
        for (int i = 0; i < vertices; i++) parent[i] = i;

        for (int face = 0; face < raw.anInt1778; face++) {
            int a = raw.aShortArray1786[face] & 0xffff;
            int b = raw.aShortArray1787[face] & 0xffff;
            int c = raw.aShortArray1789[face] & 0xffff;
            if (a < vertices && b < vertices && c < vertices) {
                union(parent, a, b);
                union(parent, a, c);
            }
        }

        Map<Integer, Builder> byRoot = new LinkedHashMap<Integer, Builder>();
        for (int face = 0; face < raw.anInt1778; face++) {
            int a = raw.aShortArray1786[face] & 0xffff;
            int b = raw.aShortArray1787[face] & 0xffff;
            int c = raw.aShortArray1789[face] & 0xffff;
            if (a >= vertices || b >= vertices || c >= vertices) continue;

            int root = find(parent, a);
            Builder builder = byRoot.get(Integer.valueOf(root));
            if (builder == null) {
                builder = new Builder();
                byRoot.put(Integer.valueOf(root), builder);
            }
            builder.faces.add(Integer.valueOf(face));
            builder.vertices.add(Integer.valueOf(a));
            builder.vertices.add(Integer.valueOf(b));
            builder.vertices.add(Integer.valueOf(c));
        }

        List<Builder> builders = new ArrayList<Builder>(byRoot.values());
        Collections.sort(builders, new Comparator<Builder>() {
            @Override
            public int compare(Builder a, Builder b) {
                int byFaces = Integer.compare(b.faces.size(), a.faces.size());
                if (byFaces != 0) return byFaces;
                return Integer.compare(a.faces.get(0).intValue(),
                        b.faces.get(0).intValue());
            }
        });

        Component[] components = new Component[builders.size()];
        for (int i = 0; i < builders.size(); i++) {
            Builder builder = builders.get(i);
            components[i] = new Component(
                    toInts(builder.faces), toInts(builder.vertices));
        }
        return components;
    }

    private static void populateComponentBounds(Class159 raw, Component[] components) {
        for (Component component : components) {
            Bounds b = bounds(raw, component.vertices);
            if (b == null) continue;
            component.centerX = (b.minX + b.maxX) / 2.0;
            component.centerY = (b.minY + b.maxY) / 2.0;
            component.centerZ = (b.minZ + b.maxZ) / 2.0;
            component.sizeX = b.sizeX;
            component.sizeY = b.sizeY;
            component.sizeZ = b.sizeZ;
        }
    }

    private static Bounds bounds(Class159 raw) {
        if (raw == null || raw.anInt1791 <= 0) return null;
        int[] vertices = new int[raw.anInt1791];
        for (int i = 0; i < vertices.length; i++) vertices[i] = i;
        return bounds(raw, vertices);
    }

    private static Bounds bounds(Class159 raw, int[] vertices) {
        if (raw == null || vertices == null || vertices.length == 0) return null;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int vertex : vertices) {
            int x = raw.anIntArray1782[vertex];
            int y = raw.anIntArray1777[vertex];
            int z = raw.anIntArray1797[vertex];
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void ensureFaceAlpha(Class159 raw) {
        if (raw.faceAlpha == null || raw.faceAlpha.length < raw.anInt1778) {
            raw.faceAlpha = new byte[raw.anInt1778];
        }
    }

    private static void hideFaces(Class159 raw, Component component) {
        for (int face : component.faces) {
            if (face >= 0 && face < raw.anInt1778) {
                raw.faceAlpha[face] = (byte) 0xff;
            }
        }
    }

    private static int find(int[] parent, int value) {
        while (parent[value] != value) {
            parent[value] = parent[parent[value]];
            value = parent[value];
        }
        return value;
    }

    private static void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) parent[rootB] = rootA;
    }

    private static int[] toInts(LinkedHashSet<Integer> values) {
        int[] out = new int[values.size()];
        int index = 0;
        for (Integer value : values) out[index++] = value.intValue();
        return out;
    }

    private static int[] toInts(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) out[i] = values.get(i).intValue();
        return out;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }

    private static void invalidateModels() {
        cachedRenderer = null;
        cachedRevision = Integer.MIN_VALUE;
        cachedModels = new Model[0];
    }

    private static final class ConveyorRun {
        final long runId;
        final String name;
        final int startX;
        final int startY;
        final int endX;
        final int endY;
        final int plane;

        ConveyorRun(long runId, String name, int startX, int startY,
                int endX, int endY, int plane) {
            this.runId = runId;
            this.name = name;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.plane = plane;
        }

        double lengthTiles() {
            double dx = endX - startX;
            double dy = endY - startY;
            return Math.sqrt(dx * dx + dy * dy);
        }

        int headingYaw() {
            double angle = Math.atan2(endY - startY, endX - startX);
            return ((int) Math.round(angle * 16384.0 / (Math.PI * 2.0))) & 0x3fff;
        }
    }

    private static final class AuthoredPart {
        final int index;
        final int scaleX;
        final int scaleY;
        final int scaleZ;
        final int moveX;
        final int moveY;
        final int moveZ;
        final int yaw;

        AuthoredPart(int index, int scaleX, int scaleY, int scaleZ,
                int moveX, int moveY, int moveZ, int yaw) {
            this.index = index;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
            this.moveX = moveX;
            this.moveY = moveY;
            this.moveZ = moveZ;
            this.yaw = yaw;
        }
    }

    private static final class Generation {
        final Class159 raw;
        final boolean sourceAxisX;
        final String summary;

        Generation(Class159 raw, boolean sourceAxisX, String summary) {
            this.raw = raw;
            this.sourceAxisX = sourceAxisX;
            this.summary = summary;
        }
    }

    private static final class Bounds {
        final int minX, minY, minZ;
        final int maxX, maxY, maxZ;
        final int sizeX, sizeY, sizeZ;

        Bounds(int minX, int minY, int minZ,
                int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.sizeX = Math.max(1, maxX - minX);
            this.sizeY = Math.max(1, maxY - minY);
            this.sizeZ = Math.max(1, maxZ - minZ);
        }
    }

    private static final class Component {
        final int[] faces;
        final int[] vertices;
        double centerX;
        double centerY;
        double centerZ;
        int sizeX;
        int sizeY;
        int sizeZ;

        Component(int[] faces, int[] vertices) {
            this.faces = faces;
            this.vertices = vertices;
        }
    }

    private static final class Builder {
        final List<Integer> faces = new ArrayList<Integer>();
        final LinkedHashSet<Integer> vertices = new LinkedHashSet<Integer>();
    }
}
