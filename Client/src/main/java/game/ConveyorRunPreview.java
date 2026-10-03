package game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Client-only procedural conveyor presentation.
 *
 * Gameplay/persistence remains server-owned. One logical ConveyorRun is rendered
 * from an authored one-tile visual module repeated at exact 512-unit intervals,
 * then merged into one cached renderer Model for that straight run.
 */
public final class ConveyorRunPreview {

    private static final int SOURCE_OBJECT_ID = 46298;
    private static final int[] SOURCE_MODEL_IDS = { 49717, 49718 };
    private static final int TILE_UNITS = 512;

    public static final int ROUTE_AUTO = 0;
    public static final int ROUTE_X_FIRST = 1;
    public static final int ROUTE_Y_FIRST = 2;

    private static final int DEFAULT_PAYLOAD_ITEM_ID = 1511;
    private static volatile int payloadItemId = DEFAULT_PAYLOAD_ITEM_ID;

    /*
     * Canonical fallback authored conveyor assembly. Live Model Editor recipes
     * override this when applied, but persistent/build-palette rendering must
     * remain useful after a fresh client launch.
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

    public static final int SETTLEMENT_SYNC_CS_VAR = 65534;

    private static volatile boolean demoActive;
    private static volatile ConveyorRun[] demoRuns = new ConveyorRun[0];
    private static volatile ConveyorRun[] settlementRuns = new ConveyorRun[0];
    private static volatile ConveyorRun placementRun;
    private static final List<ConveyorRun> pendingSettlementRuns =
            new ArrayList<ConveyorRun>();
    private static boolean settlementSyncOpen;
    private static volatile int revision;
    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile String status = "HIDDEN";
    private static volatile String roleSummary = "module not generated";
    private static volatile String lastLoggedRoleSummary = "";
    private static volatile LiveModelEditorParts.ConveyorRecipePart[] authoringRecipe =
            new LiveModelEditorParts.ConveyorRecipePart[0];

    private static Class106 cachedRenderer;
    private static int cachedRevision = Integer.MIN_VALUE;
    private static Model[][] cachedModels = new Model[0][];

    private static final int MAX_PAYLOAD_MODEL_CACHE = 64;
    private static final double MAX_PAYLOAD_EXTRAPOLATION_SECONDS = 1.2;

    private static volatile ConveyorPayload[] settlementPayloads =
            new ConveyorPayload[0];
    private static volatile PayloadRunState[] payloadRunStates =
            new PayloadRunState[0];
    private static final List<ConveyorPayload> pendingSettlementPayloads =
            new ArrayList<ConveyorPayload>();
    private static final List<PayloadRunState> pendingPayloadRunStates =
            new ArrayList<PayloadRunState>();
    private static boolean payloadSyncOpen;
    private static volatile long payloadSnapshotNanos = System.nanoTime();

    private static Class106 cachedPayloadRenderer;
    private static final LinkedHashMap<Long, Model> cachedPayloadModels =
            new LinkedHashMap<Long, Model>();
    private static volatile boolean payloadEditorActive;

    public static final class PayloadEditorReference {
        public final long runId;
        public final int startX;
        public final int startY;
        public final int endX;
        public final int endY;
        public final int plane;
        public final int headingYaw;

        PayloadEditorReference(ConveyorRun run) {
            ConveyorRun[] segments = run.segmentRuns();
            ConveyorRun segment = segments.length == 0 ? run : segments[0];
            this.runId = run.runId;
            this.startX = segment.startX;
            this.startY = segment.startY;
            this.endX = segment.endX;
            this.endY = segment.endY;
            this.plane = run.plane;
            this.headingYaw = segment.headingYaw();
        }

        public double lengthTiles() {
            double dx = endX - startX;
            double dy = endY - startY;
            return Math.sqrt(dx * dx + dy * dy);
        }
    }

    private ConveyorRunPreview() {
    }

    public static int getPayloadTestItemId() {
        return payloadItemId;
    }

    public static synchronized PayloadEditorReference getPayloadEditorReference() {
        ConveyorRun[] runs = settlementRuns;
        for (ConveyorRun run : runs) {
            if (run != null && run.runId > 0L) {
                return new PayloadEditorReference(run);
            }
        }
        return null;
    }

    public static void setPayloadEditorActive(boolean active) {
        payloadEditorActive = active;
    }

    public static boolean isPayloadEditorActive() {
        return payloadEditorActive;
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

    public static synchronized String setAuthoringRecipe(
            LiveModelEditorParts.ConveyorRecipePart[] recipe) {
        if (recipe == null || recipe.length == 0) {
            status = "CONVEYOR 1T MODULE: include at least one authored part";
            return status;
        }

        int included = 0;
        LinkedHashSet<Integer> sourceParts = new LinkedHashSet<Integer>();
        for (LiveModelEditorParts.ConveyorRecipePart part : recipe) {
            if (part == null
                    || part.role == LiveModelEditorParts.ConveyorRole.UNASSIGNED) {
                continue;
            }
            if (!sourceParts.add(Integer.valueOf(part.sourcePart))) {
                status = "CONVEYOR 1T MODULE: duplicate source part "
                        + part.sourcePart + " is not supported";
                return status;
            }
            if (part.role != LiveModelEditorParts.ConveyorRole.IGNORE) {
                included++;
            }
        }

        if (included == 0) {
            status = "CONVEYOR 1T MODULE: recipe has no visible parts";
            return status;
        }

        authoringRecipe = recipe.clone();
        revision++;
        invalidateModels();
        status = "CONVEYOR 1T MODULE READY included=" + included
                + " tagged=" + sourceParts.size();
        return status;
    }

    public static synchronized String showDemoNearPlayer() {
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
        status = "READY Conveyor 1T module demo: short=2t medium=5t long=9t";
        return status;
    }

    public static synchronized String hide() {
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
        if (id != SETTLEMENT_SYNC_CS_VAR) return false;
        if (payload == null) return true;

        if ("BEGIN".equals(payload)) {
            pendingSettlementRuns.clear();
            settlementSyncOpen = true;
            return true;
        }
        if ("END".equals(payload)) {
            if (!settlementSyncOpen) return true;
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
            pendingSettlementPayloads.clear();
            pendingPayloadRunStates.clear();
            payloadSyncOpen = false;
            settlementPayloads = new ConveyorPayload[0];
            payloadRunStates = new PayloadRunState[0];
            payloadSnapshotNanos = System.nanoTime();
            revision++;
            invalidateModels();
            lastRenderedCycle = Integer.MIN_VALUE;
            status = demoActive ? "PERSISTENT CLEARED; demo active" : "HIDDEN";
            return true;
        }
        if ("PBEGIN".equals(payload)) {
            pendingSettlementPayloads.clear();
            pendingPayloadRunStates.clear();
            payloadSyncOpen = true;
            return true;
        }
        if ("PEND".equals(payload)) {
            if (!payloadSyncOpen) return true;
            Collections.sort(pendingSettlementPayloads,
                    new Comparator<ConveyorPayload>() {
                        @Override
                        public int compare(ConveyorPayload a, ConveyorPayload b) {
                            int run = Long.compare(a.runId, b.runId);
                            if (run != 0) return run;
                            int distance = Double.compare(
                                    b.distanceTiles, a.distanceTiles);
                            if (distance != 0) return distance;
                            return Long.compare(a.payloadId, b.payloadId);
                        }
                    });
            settlementPayloads = pendingSettlementPayloads.toArray(
                    new ConveyorPayload[pendingSettlementPayloads.size()]);
            payloadRunStates = pendingPayloadRunStates.toArray(
                    new PayloadRunState[pendingPayloadRunStates.size()]);
            pendingSettlementPayloads.clear();
            pendingPayloadRunStates.clear();
            payloadSyncOpen = false;
            payloadSnapshotNanos = System.nanoTime();
            return true;
        }
        if (payload.startsWith("PSTATE,")) {
            if (!payloadSyncOpen) return true;
            String[] values = payload.split(",");
            if (values.length != 5) {
                status = "PAYLOAD SYNC rejected malformed PSTATE";
                return true;
            }
            try {
                long runId = Long.parseLong(values[1]);
                double speed = Long.parseLong(values[2]) / 1000.0;
                double spacing = Long.parseLong(values[3]) / 1000.0;
                boolean blocked = Integer.parseInt(values[4]) != 0;
                if (runId > 0L && speed >= 0.0 && spacing > 0.0) {
                    pendingPayloadRunStates.add(new PayloadRunState(
                            runId, speed, spacing, blocked));
                }
            } catch (NumberFormatException ex) {
                status = "PAYLOAD SYNC rejected malformed PSTATE numbers";
            }
            return true;
        }
        if (payload.startsWith("PAYLOAD,")) {
            if (!payloadSyncOpen) return true;
            String[] values = payload.split(",");
            if (values.length != 6) {
                status = "PAYLOAD SYNC rejected malformed PAYLOAD";
                return true;
            }
            try {
                long runId = Long.parseLong(values[1]);
                long payloadId = Long.parseLong(values[2]);
                int itemId = Integer.parseInt(values[3]);
                int amount = Integer.parseInt(values[4]);
                double distanceTiles = Long.parseLong(values[5]) / 1000.0;
                if (runId > 0L && payloadId > 0L
                        && itemId >= 0 && amount > 0 && distanceTiles >= 0.0) {
                    pendingSettlementPayloads.add(new ConveyorPayload(
                            runId, payloadId, itemId, amount, distanceTiles));
                }
            } catch (NumberFormatException ex) {
                status = "PAYLOAD SYNC rejected malformed PAYLOAD numbers";
            }
            return true;
        }
        if (payload.startsWith("RUN,")) {
            if (!settlementSyncOpen) return true;
            String[] values = payload.split(",");
            if (values.length != 7 && values.length != 8
                    && values.length != 10) {
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
                int routeAxis = values.length >= 8
                        ? Integer.parseInt(values[7]) : ROUTE_AUTO;
                long outputRunId = values.length == 10
                        ? Long.parseLong(values[8]) : 0L;
                double outputInsertDistance = values.length == 10
                        ? Long.parseLong(values[9]) / 1000.0 : -1.0;
                if (runId > 0L && (startX != endX || startY != endY)) {
                    pendingSettlementRuns.add(new ConveyorRun(
                            runId, "RUN#" + runId,
                            startX, startY, endX, endY, plane, routeAxis,
                            outputRunId, outputInsertDistance));
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
        pendingSettlementPayloads.clear();
        pendingPayloadRunStates.clear();
        payloadSyncOpen = false;
        settlementPayloads = new ConveyorPayload[0];
        payloadRunStates = new PayloadRunState[0];
        payloadSnapshotNanos = System.nanoTime();
        revision++;
        invalidateModels();
        lastRenderedCycle = Integer.MIN_VALUE;
        status = demoActive ? "PERSISTENT CLEARED; demo active" : "HIDDEN";
    }

    public static String getStatus() {
        return status + " | persistent=" + settlementRuns.length
                + " payloads=" + settlementPayloads.length
                + " placement=" + (placementRun == null ? 0 : 1)
                + " demo=" + (demoActive ? demoRuns.length : 0)
                + " | " + roleSummary;
    }

    public static synchronized void setPlacementPreview(
            int startX, int startY, int endX, int endY,
            int plane, int routeAxis) {
        if (startX == endX && startY == endY) {
            clearPlacementPreview();
            return;
        }
        int resolvedRouteAxis = ConveyorRun.resolveRouteAxis(
                routeAxis, startX, startY, endX, endY);
        ConveyorRun current = placementRun;
        if (current != null
                && current.startX == startX && current.startY == startY
                && current.endX == endX && current.endY == endY
                && current.plane == plane
                && current.routeAxis == resolvedRouteAxis) {
            return;
        }
        placementRun = new ConveyorRun(
                -2L, "PLACEMENT", startX, startY, endX, endY,
                plane, resolvedRouteAxis);
        revision++;
        invalidateModels();
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    public static synchronized void clearPlacementPreview() {
        if (placementRun == null) return;
        placementRun = null;
        revision++;
        invalidateModels();
        lastRenderedCycle = Integer.MIN_VALUE;
    }

    static void render(Class523 scene, Class106 renderer) {
        ConveyorRun[] current = visibleRuns();
        if (current.length == 0 || scene == null || renderer == null) return;

        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) return;
        lastRenderedCycle = cycle;

        Class613 region = client.aClass613_8605;
        if (region == null || region.method7285(0) != scene) return;
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
        int renderedSegments = 0;
        int payloads = 0;
        for (int i = 0; i < current.length; i++) {
            ConveyorRun[] segments = current[i].segmentRuns();
            Model[] models = i < cachedModels.length ? cachedModels[i] : null;
            boolean complete = models != null && models.length == segments.length;
            for (int segmentIndex = 0;
                    complete && segmentIndex < segments.length;
                    segmentIndex++) {
                Model model = models[segmentIndex];
                if (model != null && renderOne(
                        segments[segmentIndex], model, definition,
                        scene, renderer, sceneBase)) {
                    renderedSegments++;
                } else {
                    complete = false;
                }
            }
            if (complete) rendered++;
            else failed++;

            if (current[i].runId > 0L) {
                payloads += renderPayloads(
                        current[i], scene, renderer, sceneBase);
            }
        }

        status = "DRAW ConveyorRun Transport " + rendered + "/" + current.length
                + " segments=" + renderedSegments
                + " payloads=" + payloads
                + " testItem=" + payloadItemId
                + (failed == 0 ? "" : " failed=" + failed);
    }

    private static ConveyorRun[] visibleRuns() {
        ConveyorRun[] persistent = settlementRuns;
        ConveyorRun[] demo = demoActive ? demoRuns : new ConveyorRun[0];
        ConveyorRun placement = placementRun;
        int count = persistent.length + demo.length + (placement == null ? 0 : 1);
        if (count == 0) return new ConveyorRun[0];
        ConveyorRun[] combined = new ConveyorRun[count];
        int index = 0;
        System.arraycopy(persistent, 0, combined, index, persistent.length);
        index += persistent.length;
        System.arraycopy(demo, 0, combined, index, demo.length);
        index += demo.length;
        if (placement != null) combined[index] = placement;
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
        Model[][] built = new Model[current.length][];
        String summary = "module unavailable";

        for (int i = 0; i < current.length; i++) {
            ConveyorRun[] segments = current[i].segmentRuns();
            built[i] = new Model[segments.length];
            for (int segmentIndex = 0;
                    segmentIndex < segments.length;
                    segmentIndex++) {
                ConveyorRun segment = segments[segmentIndex];
                Generation generation = generateRaw(definition, segment);
                if (generation == null || generation.raw == null) {
                    built[i][segmentIndex] = null;
                    continue;
                }
                summary = generation.summary;
                built[i][segmentIndex] = buildModel(
                        renderer, definition, generation.raw,
                        generation.sourceAxisX, segment.headingYaw());
            }
        }

        cachedModels = built;
        cachedRenderer = renderer;
        cachedRevision = revision;
        roleSummary = summary + " | one-tile repeated visual";
        if (!roleSummary.equals(lastLoggedRoleSummary)) {
            lastLoggedRoleSummary = roleSummary;
            System.out.println("[ConveyorRunPreview] " + roleSummary);
        }
    }

    private static Generation generateRaw(
            ObjectDefinitions definition, ConveyorRun run) {
        LiveModelEditorParts.ConveyorRecipePart[] recipe = authoringRecipe;
        if (recipe != null && recipe.length > 0) {
            return generateRoleRaw(definition, run, recipe);
        }
        return generateLegacyRaw(definition, run);
    }

    private static Generation generateRoleRaw(ObjectDefinitions definition,
            ConveyorRun run,
            LiveModelEditorParts.ConveyorRecipePart[] recipe) {
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
        for (LiveModelEditorParts.ConveyorRecipePart part : recipe) {
            if (part != null
                    && (part.sourcePart < 0 || part.sourcePart >= components.length)) {
                status = "CONVEYOR MODULE source part " + part.sourcePart
                        + " unavailable; detected=" + components.length;
                return null;
            }
        }

        ensureFaceAlpha(working);
        ArrayList<Component> moduleParts = new ArrayList<Component>();
        for (int i = 0; i < components.length; i++) {
            LiveModelEditorParts.ConveyorRecipePart part = recipePart(recipe, i);
            if (part == null
                    || part.role == LiveModelEditorParts.ConveyorRole.UNASSIGNED
                    || part.role == LiveModelEditorParts.ConveyorRole.IGNORE) {
                hideFaces(working, components[i]);
                continue;
            }
            applyRecipeTransform(working, components[i], part);
            moduleParts.add(components[i]);
        }

        populateComponentBounds(working, components);
        Bounds all = boundsForRecipeParts(working, components, recipe);
        if (all == null || moduleParts.isEmpty()) {
            status = "CONVEYOR 1T MODULE recipe has no visible geometry";
            return null;
        }
        boolean axisX = all.sizeX >= all.sizeZ;
        double sourceCenter = axisX
                ? (all.minX + all.maxX) * 0.5
                : (all.minZ + all.maxZ) * 0.5;
        double sourceLength = Math.max(1.0, axisX ? all.sizeX : all.sizeZ);
        return repeatOneTileModule(
                working, moduleParts, axisX,
                sourceCenter, sourceLength, run,
                "EDITOR");
    }

    private static Generation generateLegacyRaw(
            ObjectDefinitions definition, ConveyorRun run) {
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

        ensureFaceAlpha(working);
        ArrayList<Component> moduleParts = new ArrayList<Component>();
        for (int i = 0; i < components.length; i++) {
            AuthoredPart authored = authoredPart(i);
            if (authored == null) {
                hideFaces(working, components[i]);
            } else {
                applyAuthoredTransform(working, components[i], authored);
                moduleParts.add(components[i]);
            }
        }

        populateComponentBounds(working, components);
        Bounds all = boundsForAuthoredParts(working, components);
        if (all == null || moduleParts.isEmpty()) {
            status = "SOURCE authored conveyor bounds unavailable";
            return null;
        }
        boolean axisX = all.sizeX >= all.sizeZ;
        double sourceCenter = axisX
                ? (all.minX + all.maxX) * 0.5
                : (all.minZ + all.maxZ) * 0.5;
        double sourceLength = Math.max(1.0, axisX ? all.sizeX : all.sizeZ);
        return repeatOneTileModule(
                working, moduleParts, axisX,
                sourceCenter, sourceLength, run,
                "FALLBACK");
    }

    /**
     * Normalizes the complete authored assembly to exactly one RuneScape tile
     * along its long axis, recenters that axis at local zero, repeats the whole
     * intact raw module at 512-unit intervals, then merges the copies into one
     * Class159. Cloning through Matrix3's Class159 merge constructor preserves
     * the source face/texture/UV tables instead of rebuilding partial faces.
     */
    private static Generation repeatOneTileModule(
            Class159 working, List<Component> moduleParts,
            boolean axisX, double sourceCenter, double sourceLength,
            ConveyorRun run, String sourceLabel) {
        if (working == null || moduleParts == null || moduleParts.isEmpty()) {
            return null;
        }

        double moduleScale = TILE_UNITS / Math.max(1.0, sourceLength);
        int recenter = (int) Math.round(-sourceCenter);
        for (Component component : moduleParts) {
            scaleComponentAxis(
                    working, component, axisX, sourceCenter, moduleScale);
            translateComponentAxis(working, component, axisX, recenter);
        }

        int modules = Math.max(1, (int) Math.round(run.lengthTiles()));
        Class159[] copies = new Class159[modules];
        for (int station = 0; station < modules; station++) {
            double offset = ((station + 0.5) - modules * 0.5) * TILE_UNITS;
            Class159 copy = new Class159(new Class159[] { working }, 1);
            translateRawAxis(copy, axisX, (int) Math.round(offset));
            copies[station] = copy;
        }

        Class159 raw = copies.length == 1
                ? copies[0]
                : new Class159(copies, copies.length);
        String summary = "MODULE_1T source=" + sourceLabel
                + " axis=" + (axisX ? "X" : "Z")
                + " parts=" + moduleParts.size()
                + " modules=" + modules
                + " sourceSpan=" + Math.round(sourceLength)
                + " normalizedSpan=" + TILE_UNITS;
        return new Generation(raw, axisX, summary);
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

    private static AuthoredPart authoredPart(int componentIndex) {
        for (AuthoredPart authored : AUTHORED_PARTS) {
            if (authored.index == componentIndex) return authored;
        }
        return null;
    }

    private static void applyAuthoredTransform(Class159 raw, Component component,
            AuthoredPart state) {
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

    private static Bounds boundsForAuthoredParts(
            Class159 raw, Component[] components) {
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
        model.method1450(MODEL_FLAGS);
        return model;
    }

    private static int renderPayloads(ConveyorRun run,
            Class523 scene, Class106 renderer, Class497 sceneBase) {
        if (payloadEditorActive || run == null || run.runId <= 0L
                || renderer == null || scene == null || sceneBase == null) {
            return 0;
        }
        PayloadRunState runState = payloadRunState(run.runId);
        if (runState == null) return 0;
        ConveyorPayload[] payloads = settlementPayloads;
        if (payloads.length == 0) return 0;

        double elapsedSeconds =
                (System.nanoTime() - payloadSnapshotNanos) / 1000000000.0;
        elapsedSeconds = Math.max(0.0,
                Math.min(MAX_PAYLOAD_EXTRAPOLATION_SECONDS, elapsedSeconds));
        double advance = runState.speedTilesPerSecond * elapsedSeconds;
        double lengthTiles = Math.max(0.001, run.lengthTiles());
        double leaderDistance = Double.POSITIVE_INFINITY;
        int rendered = 0;

        for (ConveyorPayload payload : payloads) {
            if (payload == null || payload.runId != run.runId) continue;
            double distance = Math.min(
                    lengthTiles, payload.distanceTiles + advance);
            if (!Double.isInfinite(leaderDistance)) {
                distance = Math.min(
                        distance,
                        Math.max(0.0,
                                leaderDistance - runState.spacingTiles));
            }
            leaderDistance = distance;
            if (renderPayloadAtDistance(
                    run, payload, distance, scene, renderer, sceneBase)) {
                rendered++;
            }
        }
        return rendered;
    }

    private static boolean renderPayloadAtDistance(
            ConveyorRun run, ConveyorPayload payload, double distanceTiles,
            Class523 scene, Class106 renderer, Class497 sceneBase) {
        ConveyorPayloadVisualProfiles.Resolution payloadResolution =
                ConveyorPayloadVisualProfiles.resolveForRender(payload.itemId);
        ConveyorPayloadVisualProfiles.Profile payloadProfile =
                payloadResolution.profile;
        ConveyorPayloadVisualProfiles.Anchor payloadAnchor =
                ConveyorPayloadVisualProfiles.getGlobalAnchor();
        Model payloadModel = getPayloadModel(
                renderer, payload.itemId, payloadProfile.scalePercent);
        if (payloadModel == null) return false;

        int plane = run.plane;
        if (plane < 0 || plane >= scene.aClass174Array5838.length) return false;
        Class174 ground = scene.aClass174Array5838[plane];
        if (ground == null) return false;

        int baseWorldX = sceneBase.localX * -2109597897;
        int baseWorldY = sceneBase.localY * 417324155;
        PathPoint point = run.sampleAtDistance(distanceTiles);
        double localX = point.worldX - baseWorldX;
        double localY = point.worldY - baseWorldY;

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (localX < 0.0 || localY < 0.0
                || localX >= sceneWidth || localY >= sceneHeight) return false;

        int tileSize = ground.anInt2087 * 2129890771;
        double directionX = point.directionX;
        double directionY = point.directionY;
        double alongTiles = (payloadAnchor.alongOffset
                + payloadProfile.alongOffset) / (double) TILE_UNITS;
        double sideTiles = (payloadAnchor.sideOffset
                + payloadProfile.sideOffset) / (double) TILE_UNITS;

        localX += directionX * alongTiles - directionY * sideTiles;
        localY += directionY * alongTiles + directionX * sideTiles;
        int sceneX = (int) Math.round(localX * tileSize + tileSize * 0.5);
        int sceneZ = (int) Math.round(localY * tileSize + tileSize * 0.5);
        int sceneY = ground.method2718(sceneX, sceneZ, 0)
                + payloadAnchor.heightOffset + payloadProfile.heightOffset;

        PAYLOAD_TRANSFORM.method3594();
        int pitch = degreesToAngle(payloadProfile.pitchDegrees);
        int yaw = (point.headingYaw
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
        PAYLOAD_TRANSFORM.method3580(
                (float) sceneX, (float) sceneY, (float) sceneZ);
        payloadModel.method1375(PAYLOAD_TRANSFORM, null, 0);
        return true;
    }

    private static PayloadRunState payloadRunState(long runId) {
        PayloadRunState[] states = payloadRunStates;
        for (PayloadRunState state : states) {
            if (state != null && state.runId == runId) return state;
        }
        return null;
    }

    private static Model getPayloadModel(
            Class106 renderer, int itemId, int scalePercent) {
        if (renderer == null) return null;
        if (cachedPayloadRenderer != renderer) {
            cachedPayloadRenderer = renderer;
            cachedPayloadModels.clear();
        }

        long cacheKey = ((long) itemId << 32)
                ^ (scalePercent & 0xffffffffL);
        Model cached = cachedPayloadModels.get(Long.valueOf(cacheKey));
        if (cached != null) return cached;

        Class639_Sub5 itemDefinitions =
                ClientConsoleItemBridge.getRegisteredItemDefinitions();
        if (itemDefinitions == null
                || itemId < 0
                || itemId >= itemDefinitions.method45()) return null;
        try {
            ItemDefinitions definition = (ItemDefinitions)
                    itemDefinitions.getDefinition(itemId, 0);
            if (definition == null) return null;
            Model model = definition.method7526(
                    renderer, MODEL_FLAGS, 1,
                    null, null, 0, 0, 0, 0, 0);
            if (model == null) return null;

            int modelScale = Math.max(1,
                    (int) Math.round(128.0 * scalePercent / 100.0));
            if (modelScale != 128) {
                model.method1464(modelScale, modelScale, modelScale);
            }
            model.method1450(MODEL_FLAGS);
            if (cachedPayloadModels.size() >= MAX_PAYLOAD_MODEL_CACHE) {
                cachedPayloadModels.clear();
            }
            cachedPayloadModels.put(Long.valueOf(cacheKey), model);
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
            int value = axisX
                    ? raw.anIntArray1782[vertex]
                    : raw.anIntArray1797[vertex];
            int scaled = (int) Math.round(
                    center + (value - center) * factor);
            if (axisX) raw.anIntArray1782[vertex] = scaled;
            else raw.anIntArray1797[vertex] = scaled;
        }
    }

    private static void translateComponentAxis(Class159 raw, Component component,
            boolean axisX, int shift) {
        if (shift == 0) return;
        for (int vertex : component.vertices) {
            if (axisX) raw.anIntArray1782[vertex] += shift;
            else raw.anIntArray1797[vertex] += shift;
        }
    }

    private static void translateRawAxis(
            Class159 raw, boolean axisX, int shift) {
        if (raw == null || shift == 0) return;
        for (int vertex = 0; vertex < raw.anInt1791; vertex++) {
            if (axisX) raw.anIntArray1782[vertex] += shift;
            else raw.anIntArray1797[vertex] += shift;
        }
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

    private static void populateComponentBounds(
            Class159 raw, Component[] components) {
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
            byte[] replacement = new byte[raw.anInt1778];
            if (raw.faceAlpha != null) {
                System.arraycopy(raw.faceAlpha, 0, replacement, 0,
                        Math.min(raw.faceAlpha.length, replacement.length));
            }
            raw.faceAlpha = replacement;
        }
    }

    private static void hideFaces(Class159 raw, Component component) {
        for (int face : component.faces) {
            if (face >= 0 && face < raw.anInt1778
                    && raw.faceAlpha != null
                    && face < raw.faceAlpha.length) {
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
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i).intValue();
        }
        return out;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : value > max ? max : value;
    }

    private static void invalidateModels() {
        cachedRenderer = null;
        cachedRevision = Integer.MIN_VALUE;
        cachedModels = new Model[0][];
    }

    private static final class ConveyorPayload {
        final long runId;
        final long payloadId;
        final int itemId;
        final int amount;
        final double distanceTiles;

        ConveyorPayload(long runId, long payloadId, int itemId,
                int amount, double distanceTiles) {
            this.runId = runId;
            this.payloadId = payloadId;
            this.itemId = itemId;
            this.amount = amount;
            this.distanceTiles = distanceTiles;
        }
    }

    private static final class PayloadRunState {
        final long runId;
        final double speedTilesPerSecond;
        final double spacingTiles;
        final boolean outputBlocked;

        PayloadRunState(long runId, double speedTilesPerSecond,
                double spacingTiles, boolean outputBlocked) {
            this.runId = runId;
            this.speedTilesPerSecond = speedTilesPerSecond;
            this.spacingTiles = spacingTiles;
            this.outputBlocked = outputBlocked;
        }
    }

    private static final class ConveyorRun {
        final long runId;
        final String name;
        final int startX;
        final int startY;
        final int endX;
        final int endY;
        final int plane;
        final int routeAxis;
        final long outputRunId;
        final double outputInsertDistanceTiles;

        ConveyorRun(long runId, String name, int startX, int startY,
                int endX, int endY, int plane) {
            this(runId, name, startX, startY, endX, endY, plane,
                    ROUTE_AUTO, 0L, -1.0);
        }

        ConveyorRun(long runId, String name, int startX, int startY,
                int endX, int endY, int plane, int routeAxis) {
            this(runId, name, startX, startY, endX, endY, plane,
                    routeAxis, 0L, -1.0);
        }

        ConveyorRun(long runId, String name, int startX, int startY,
                int endX, int endY, int plane, int routeAxis,
                long outputRunId, double outputInsertDistanceTiles) {
            this.runId = runId;
            this.name = name;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.plane = plane;
            this.routeAxis = resolveRouteAxis(
                    routeAxis, startX, startY, endX, endY);
            this.outputRunId = outputRunId > 0L ? outputRunId : 0L;
            this.outputInsertDistanceTiles = outputRunId > 0L
                    && outputInsertDistanceTiles >= 0.0
                    ? outputInsertDistanceTiles : -1.0;
        }

        static int resolveRouteAxis(
                int requested, int startX, int startY, int endX, int endY) {
            if (requested == ROUTE_X_FIRST || requested == ROUTE_Y_FIRST) {
                return requested;
            }
            int dx = Math.abs(endX - startX);
            int dy = Math.abs(endY - startY);
            return dx >= dy ? ROUTE_X_FIRST : ROUTE_Y_FIRST;
        }

        double lengthTiles() {
            return Math.abs(endX - startX) + Math.abs(endY - startY);
        }

        int headingYaw() {
            ConveyorRun[] segments = segmentRuns();
            return segments.length == 0 ? 0 : segments[0].straightHeadingYaw();
        }

        private int straightHeadingYaw() {
            double angle = Math.atan2(endY - startY, endX - startX);
            return ((int) Math.round(
                    angle * 16384.0 / (Math.PI * 2.0))) & 0x3fff;
        }

        ConveyorRun[] segmentRuns() {
            if (startX == endX || startY == endY) {
                return new ConveyorRun[] {
                        new ConveyorRun(runId, name + "-S0",
                                startX, startY, endX, endY, plane,
                                routeAxis)
                };
            }

            int bendX = routeAxis == ROUTE_X_FIRST ? endX : startX;
            int bendY = routeAxis == ROUTE_X_FIRST ? startY : endY;
            return new ConveyorRun[] {
                    new ConveyorRun(runId, name + "-S0",
                            startX, startY, bendX, bendY, plane,
                            routeAxis),
                    new ConveyorRun(runId, name + "-S1",
                            bendX, bendY, endX, endY, plane,
                            routeAxis)
            };
        }

        PathPoint sampleAtDistance(double distanceTiles) {
            ConveyorRun[] segments = segmentRuns();
            if (segments.length == 0) {
                return new PathPoint(startX, startY, 1.0, 0.0, 0);
            }
            double remaining = Math.max(0.0,
                    Math.min(lengthTiles(), distanceTiles));
            for (int i = 0; i < segments.length; i++) {
                ConveyorRun segment = segments[i];
                double length = Math.max(0.001, segment.lengthTiles());
                if (remaining <= length || i == segments.length - 1) {
                    double progress = Math.max(0.0,
                            Math.min(1.0, remaining / length));
                    double dx = segment.endX - segment.startX;
                    double dy = segment.endY - segment.startY;
                    double directionX = dx / length;
                    double directionY = dy / length;
                    return new PathPoint(
                            segment.startX + dx * progress,
                            segment.startY + dy * progress,
                            directionX, directionY,
                            segment.straightHeadingYaw());
                }
                remaining -= length;
            }
            ConveyorRun last = segments[segments.length - 1];
            double length = Math.max(0.001, last.lengthTiles());
            return new PathPoint(
                    last.endX, last.endY,
                    (last.endX - last.startX) / length,
                    (last.endY - last.startY) / length,
                    last.straightHeadingYaw());
        }
    }

    private static final class PathPoint {
        final double worldX;
        final double worldY;
        final double directionX;
        final double directionY;
        final int headingYaw;

        PathPoint(double worldX, double worldY,
                double directionX, double directionY, int headingYaw) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.directionX = directionX;
            this.directionY = directionY;
            this.headingYaw = headingYaw;
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
