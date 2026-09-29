package game;

/**
 * Construction hybrid camera controller.
 *
 * RTS owns a movable world pivot plus yaw/pitch/zoom inputs, but feeds those
 * inputs into Matrix3's existing vanilla Class246 camera solver. Free Build
 * retains the proven detached Class24/Class411 developer-camera path.
 */
public final class ConstructionBuildCamera {

    public enum CameraMode {
        FREE_BUILD("Free"),
        RTS("RTS");

        private final String displayName;

        private CameraMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // Speeds preserve the accepted v1 feel while making motion time-based.
    private static final float NORMAL_SPEED = 1250.0F;
    private static final float FAST_SPEED = 3000.0F;
    private static final float PRECISION_SPEED = 400.0F;

    // RTS pan-speed presets are client-session state and intentionally do not
    // reset when the Construction palette closes/reopens.
    private static final float[] RTS_MOVE_SPEED_MULTIPLIERS = {
            0.50F, 0.75F, 1.00F, 1.25F, 1.50F, 2.00F, 2.50F, 3.00F
    };
    // Default to 2.0x; 1.0x proved too slow for normal settlement navigation.
    private static int rtsMoveSpeedIndex = 5;

    // Exponential response rates: higher = more immediate.
    private static final float ACCEL_RESPONSE = 10.0F;
    private static final float DECEL_RESPONSE = 7.0F;

    private static final float DEFAULT_DT = 0.020F;
    private static final float MIN_DT = 0.005F;
    private static final float MAX_DT = 0.050F;
    private static final float VELOCITY_EPSILON = 0.5F;

    // RTS keeps a bounded editor-friendly orbit pitch and pans on the ground plane.
    private static final float RTS_DEFAULT_PITCH_RADIANS = (float) Math.toRadians(52.0);
    private static final float RTS_MIN_PITCH_RADIANS = (float) Math.toRadians(24.0);
    private static final float RTS_MAX_PITCH_RADIANS = (float) Math.toRadians(78.0);
    private static final float RTS_ROTATE_SPEED = (float) Math.toRadians(90.0);
    private static final float RTS_MOUSE_ORBIT_RADIANS_PER_PIXEL = (float) Math.toRadians(0.32);
    private static final int RTS_MAX_QUEUED_ORBIT_PIXELS = 240;
    private static final float RTS_LOOK_DISTANCE = 4096.0F;
    private static final float RTS_INITIAL_BACKOFF = 3600.0F;
    private static final float RTS_ZOOM_STEP = 450.0F;
    private static final float RTS_MIN_ORBIT_DISTANCE = 700.0F;
    private static final float RTS_MAX_ORBIT_DISTANCE = 10000.0F;
    private static final int RTS_MAX_QUEUED_WHEEL_STEPS = 8;
    private static final float RTS_SCENE_EDGE_MARGIN = 768.0F;

    // Matrix3 action 23 uses movement type 1 for minimap-originated walking.
    // Construction RTS consumes that already-resolved destination before a
    // player movement packet is sent.
    private static final int MATRIX3_TILE_ACTION = 23;
    private static final int MATRIX3_MINIMAP_MOVE_TYPE = 1;

    private static volatile boolean active;
    private static volatile boolean ownsFreeCamera;
    private static volatile boolean restoreExternalFreeCamera;
    private static volatile boolean settlementAutoMode;
    private static volatile CameraMode cameraMode = CameraMode.RTS;

    private static int lastTickCycle = Integer.MIN_VALUE;
    private static int lastMouseX;
    private static int lastMouseY;
    private static long lastTickNanos;

    // World-space velocity in Matrix3 camera units/second.
    private static float velocityX;
    private static float velocityY;
    private static float velocityZ;

    // RTS keeps deterministic heading/pitch plus a world-space orbit pivot.
    // Q/E moves the camera around this pivot instead of turning in place.
    private static boolean rtsOrientationInitialized;
    private static float rtsYawRadians;
    private static float rtsPitchRadians = RTS_DEFAULT_PITCH_RADIANS;
    private static float rtsOrbitDistance;
    private static float rtsPivotX;
    private static float rtsPivotY;
    private static float rtsPivotZ;
    private static int pendingRtsZoomSteps;
    private static int pendingRtsOrbitX;
    private static int pendingRtsOrbitY;

    // Preserve the last accepted RTS view for the lifetime of this client.
    private static boolean savedRtsView;
    private static float savedRtsYawRadians;
    private static float savedRtsPitchRadians = RTS_DEFAULT_PITCH_RADIANS;
    private static float savedRtsOrbitDistance;
    private static float savedRtsPivotX;
    private static float savedRtsPivotY;
    private static float savedRtsPivotZ;

    // RTS temporarily owns Matrix3's existing minimap destination marker so the
    // camera focus stays visible without hardcoding any minimap screen layout.
    private static boolean rtsMinimapMarkerSnapshotValid;
    private static int savedMinimapMarkerXRaw;
    private static int savedMinimapMarkerYRaw;
    private static boolean savedMinimapMarkerBool;
    private static int lastRtsMinimapMarkerLocalX = Integer.MIN_VALUE;
    private static int lastRtsMinimapMarkerLocalY = Integer.MIN_VALUE;
    private static boolean pendingRtsMinimapFocus;
    private static int pendingRtsMinimapFocusX;
    private static int pendingRtsMinimapFocusY;

    // A placement click should stop motion even if a key is still physically held.
    // Movement can resume only after all camera movement keys are released once.
    private static boolean clickStopLatched;

    private static boolean tickReported;
    private static boolean inputReported;
    private static boolean stopReported;
    private static boolean failureReported;

    private ConstructionBuildCamera() {
    }

    public static boolean isRequested() {
        return active;
    }

    public static boolean isSettlementAutoMode() {
        return settlementAutoMode;
    }

    public static CameraMode getMode() {
        return cameraMode;
    }

    public static boolean isRtsMode() {
        return cameraMode == CameraMode.RTS;
    }

    public static float getRtsMoveSpeedMultiplier() {
        return RTS_MOVE_SPEED_MULTIPLIERS[rtsMoveSpeedIndex];
    }

    public static String getRtsMoveSpeedLabel() {
        float multiplier = getRtsMoveSpeedMultiplier();
        return multiplier == (int) multiplier
                ? Integer.toString((int) multiplier) + ".0x"
                : Float.toString(multiplier) + "x";
    }


    /**
     * Vanilla scene visibility is normally centered around the player's local
     * scene tile. Settlement RTS keeps the player planted, so expose the
     * detached camera's managed look pivot as the equivalent render-focus tile.
     *
     * This changes only the focus passed into Matrix3's stock scene culling.
     * It does not alter the renderer radius, fog or visibility arrays.
     */
    public static int[] getRtsRenderFocusLocalTile() {
        if (!active || cameraMode != CameraMode.RTS || !rtsOrientationInitialized
                || client.aClass613_8605 == null) {
            return null;
        }
        try {
            Class497 sceneBase =
                    client.aClass613_8605.method7280((byte) -115);
            if (sceneBase == null) {
                return null;
            }
            int baseTileX = sceneBase.localX * -2109597897;
            int baseTileY = sceneBase.localY * 417324155;
            int localX = (int) Math.floor(rtsPivotX / 512.0F) - baseTileX;
            int localY = (int) Math.floor(rtsPivotZ / 512.0F) - baseTileY;
            int sceneWidth = client.aClass613_8605.method7347(-740581830);
            int sceneHeight = client.aClass613_8605.method7278(277214477);
            if (localX < 0 || localY < 0
                    || localX >= sceneWidth || localY >= sceneHeight) {
                return null;
            }
            return new int[] { localX, localY };
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static void adjustRtsMoveSpeed(int delta) {
        if (delta == 0) {
            return;
        }
        int next = clamp(rtsMoveSpeedIndex + (delta > 0 ? 1 : -1),
                0, RTS_MOVE_SPEED_MULTIPLIERS.length - 1);
        if (next == rtsMoveSpeedIndex) {
            return;
        }
        rtsMoveSpeedIndex = next;
        clearVelocity();
        if (active && cameraMode == CameraMode.RTS) {
            reportToServer("RTS_SPEED " + getRtsMoveSpeedLabel());
        }
    }

    public static void setMode(CameraMode nextMode) {
        if (nextMode == null || nextMode == cameraMode) {
            return;
        }

        CameraMode previousMode = cameraMode;
        if (active && previousMode == CameraMode.RTS && nextMode == CameraMode.FREE_BUILD) {
            restoreRtsMinimapMarker();
            if (!enableDetachedFreeBuildCamera()) {
                snapshotRtsMinimapMarker();
                reportToServer("FAIL free-build-camera-activation");
                return;
            }
            lastMouseX = Class26.aClass564_216.method6657((short) -1);
            lastMouseY = Class26.aClass564_216.method6658((byte) -1);
        } else if (active && previousMode == CameraMode.FREE_BUILD && nextMode == CameraMode.RTS) {
            // Keep the detached object available for a later Free Build return,
            // but hand rendering back to Matrix3's ordinary camera branch.
            Class24.aBool157 = false;
        }

        clearVelocity();
        clickStopLatched = false;
        inputReported = false;
        stopReported = false;
        lastTickCycle = Integer.MIN_VALUE;
        lastTickNanos = System.nanoTime();
        resetRtsState();
        cameraMode = nextMode;

        if (active && nextMode == CameraMode.RTS) {
            snapshotRtsMinimapMarker();
        }
        if (active) {
            reportToServer("MODE " + previousMode.name() + "-to-" + nextMode.name());
        }
    }

    /**
     * Called by the Construction palette's world-wheel listener. RTS consumes the
     * wheel for camera zoom; Free Build leaves it available for piece rotation.
     */
    public static synchronized boolean handleWorldWheel(int wheelRotation) {
        if (!active || cameraMode != CameraMode.RTS || wheelRotation == 0) {
            return false;
        }

        pendingRtsZoomSteps = clamp(
                pendingRtsZoomSteps + wheelRotation,
                -RTS_MAX_QUEUED_WHEEL_STEPS,
                RTS_MAX_QUEUED_WHEEL_STEPS);
        return true;
    }

    /**
     * Shared RTS orbit seam for Construction and developer editors.
     * Horizontal MMB drag orbits yaw around the current pivot; vertical drag
     * changes the bounded RTS pitch. The actual camera mutation stays on tick().
     */
    public static synchronized boolean handleRtsMouseOrbitDrag(int deltaX, int deltaY) {
        if (!active || cameraMode != CameraMode.RTS || (deltaX == 0 && deltaY == 0)) {
            return false;
        }
        pendingRtsOrbitX = clamp(
                pendingRtsOrbitX + deltaX,
                -RTS_MAX_QUEUED_ORBIT_PIXELS,
                RTS_MAX_QUEUED_ORBIT_PIXELS);
        pendingRtsOrbitY = clamp(
                pendingRtsOrbitY + deltaY,
                -RTS_MAX_QUEUED_ORBIT_PIXELS,
                RTS_MAX_QUEUED_ORBIT_PIXELS);
        return true;
    }

    /**
     * Convert a screen-space drag into a ground-plane delta. RTS derives the
     * basis directly from its owned yaw, while Free Build keeps using Matrix3's
     * real detached-camera look vector.
     */
    public static int[] mapScreenDragToGround(int deltaX, int deltaY, int unitsPerPixel) {
        if (!active || unitsPerPixel <= 0) {
            return null;
        }

        if (cameraMode == CameraMode.RTS && rtsOrientationInitialized) {
            float forwardX = (float) Math.sin(rtsYawRadians);
            float forwardZ = (float) Math.cos(rtsYawRadians);
            float rightX = forwardZ;
            float rightZ = -forwardX;
            float scale = unitsPerPixel;

            int worldX = Math.round(deltaX * scale * rightX
                    - deltaY * scale * forwardX);
            int worldZ = Math.round(deltaX * scale * rightZ
                    - deltaY * scale * forwardZ);
            return new int[] { worldX, worldZ };
        }

        if (Class24.aClass411_Sub1_158 == null) {
            return null;
        }
        try {
            Class423_Sub2 positionController =
                    (Class423_Sub2) Class24.aClass411_Sub1_158.method4990((byte) -37);
            Class658_Sub2 lookController =
                    (Class658_Sub2) Class24.aClass411_Sub1_158.method4991(-589573040);
            Class240 position = positionController.method5159((byte) -54);
            Class240 viewDirection = getViewDirection(lookController, position);
            if (viewDirection == null) {
                return null;
            }

            float planarLength = (float) Math.sqrt(
                    viewDirection.aFloat2653 * viewDirection.aFloat2653
                            + viewDirection.aFloat2657 * viewDirection.aFloat2657);
            if (planarLength < 0.001F) {
                return null;
            }

            float forwardX = viewDirection.aFloat2653 / planarLength;
            float forwardZ = viewDirection.aFloat2657 / planarLength;
            float rightX = forwardZ;
            float rightZ = -forwardX;
            float scale = unitsPerPixel;

            int worldX = Math.round(deltaX * scale * rightX
                    - deltaY * scale * forwardX);
            int worldZ = Math.round(deltaX * scale * rightZ
                    - deltaY * scale * forwardZ);
            return new int[] { worldX, worldZ };
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * VERIFIED-STATIC: Class319 action 23 movement type 1 is Matrix3's minimap
     * walk variant. Its local X/Y are already resolved before packet creation.
     * RTS consumes only that variant and converts the destination into a camera
     * pivot; normal world Walk Here remains owned by the existing selection path.
     */
    public static boolean handleMinimapWalkAction(int action, int localX, int localY, int movementType) {
        if (!active || cameraMode != CameraMode.RTS
                || action != MATRIX3_TILE_ACTION || movementType != MATRIX3_MINIMAP_MOVE_TYPE) {
            return false;
        }

        focusRtsAtLocalTile(localX, localY);
        return true;
    }

    private static final int SETTLEMENT_LIFECYCLE_CS_VAR = 2835;

    /** VERIFIED-STATIC packet seam: PacketsDecoder forwards server CSVar 2835 here. */
    public static void handleSettlementLifecycleSignal(int id, int value) {
        if (id != SETTLEMENT_LIFECYCLE_CS_VAR) {
            return;
        }
        if (value == 1) {
            /*
             * Settlement RTS is camera-only. The build palette must remain an
             * independent UI: closing it must not tear down the settlement camera.
             * Reuse the proven RTS camera session directly without showing palette.
             */
            setMode(CameraMode.RTS);
            ConstructionPaletteOverlay.installRtsInputListener();
            enter();
            settlementAutoMode = active;
            reportToServer("SETTLEMENT_SIGNAL enter-existing-rts");
            return;
        }
        settlementAutoMode = false;
        if (active) {
            exit();
        }
        reportToServer("SETTLEMENT_SIGNAL exit-existing-rts");
    }

    public static String enter() {
        // Every fresh Construction/settlement camera session starts in RTS.
        // Free Build remains an explicit in-session palette choice.
        cameraMode = CameraMode.RTS;
        if (active) {
            return "Construction " + cameraMode.getDisplayName() + " camera is already active.";
        }
        if (Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return "Construction camera is waiting for the local player.";
        }

        restoreExternalFreeCamera =
                IncomingPacket.method4113((byte) 0) && Class24.aClass411_Sub1_158 != null;
        ownsFreeCamera = false;

        // RTS must render through Matrix3's ordinary Class246 camera branch.
        // Preserve an already-existing detached camera object, but temporarily
        // disable its render-owner flag so it can be restored on exit.
        Class24.aBool157 = false;

        active = true;
        lastTickCycle = Integer.MIN_VALUE;
        lastMouseX = Class26.aClass564_216.method6657((short) -1);
        lastMouseY = Class26.aClass564_216.method6658((byte) -1);
        lastTickNanos = System.nanoTime();
        clearVelocity();
        clickStopLatched = false;
        tickReported = false;
        inputReported = false;
        stopReported = false;
        failureReported = false;
        resetRtsState();
        snapshotRtsMinimapMarker();

        reportToServer("ENTER vanilla-rts external-detached=" + restoreExternalFreeCamera);
        return "Construction " + cameraMode.getDisplayName() + " camera active.";
    }

    public static String exit() {
        if (!active) {
            return "Construction build camera is not active.";
        }

        restoreRtsMinimapMarker();

        if (ownsFreeCamera) {
            RSSocket.method7604(0);
        } else {
            Class24.aBool157 = restoreExternalFreeCamera && Class24.aClass411_Sub1_158 != null;
        }

        active = false;
        ownsFreeCamera = false;
        restoreExternalFreeCamera = false;
        lastTickCycle = Integer.MIN_VALUE;
        lastTickNanos = 0L;
        clearVelocity();
        clickStopLatched = false;
        resetRtsState();

        reportToServer("EXIT detached-active=" + IncomingPacket.method4113((byte) 0));
        return "Construction camera closed.";
    }

    /**
     * RTS hook called immediately after Matrix3 calculates its normal camera and
     * before camera shake/clamp/scene setup. This substitutes only the normal
     * camera inputs by reusing Class246.method3359(...); it does not become a
     * second renderer or directly own final camera globals.
     */
    public static void tickVanillaRtsCamera(int viewportHeight) {
        if (!active || cameraMode != CameraMode.RTS || lastTickCycle == client.cycles) {
            return;
        }
        lastTickCycle = client.cycles;

        // The Class343 render branch is selected by this flag. Keep it off for
        // RTS even when a detached object is retained for Free Build.
        Class24.aBool157 = false;

        try {
            if (!rtsOrientationInitialized) {
                initializeRtsVanillaHeading();
            }

            if (!tickReported) {
                tickReported = true;
                reportToServer("TICK vanilla-rts");
            }

            float dt = consumeDeltaSeconds();
            updateRtsCamera(dt);
            applyRtsVanillaCamera(viewportHeight);
        } catch (RuntimeException ex) {
            if (!failureReported) {
                failureReported = true;
                reportToServer("FAIL vanilla-rts-" + ex.getClass().getSimpleName());
                ex.printStackTrace();
            }
        }
    }

    /**
     * Free Build retains the proven detached Class411 path. This hook remains at
     * the existing late Class343 seam immediately before detached submission.
     */
    public static void tick() {
        if (!active || cameraMode != CameraMode.FREE_BUILD || lastTickCycle == client.cycles) {
            return;
        }
        lastTickCycle = client.cycles;

        if (!IncomingPacket.method4113((byte) 0) || Class24.aClass411_Sub1_158 == null) {
            if (!failureReported) {
                failureReported = true;
                reportToServer("FAIL detached-free-build-not-active");
            }
            return;
        }

        try {
            Class423_Sub2 positionController =
                    (Class423_Sub2) Class24.aClass411_Sub1_158.method4990((byte) -37);
            Class658_Sub2 lookController =
                    (Class658_Sub2) Class24.aClass411_Sub1_158.method4991(-589573040);
            Class240 position = positionController.method5159((byte) -54);

            if (!tickReported) {
                tickReported = true;
                reportToServer("TICK detached-free-build");
            }

            float dt = consumeDeltaSeconds();
            updateFreeBuildCamera(lookController, position, dt);

            Class572_Sub17 target = new Class572_Sub17(
                    0,
                    (int) position.aFloat2653,
                    (int) position.aFloat2656,
                    (int) position.aFloat2657);
            positionController.method9278(target, (byte) 3);

            Class497 sceneBase = client.aClass613_8605.method7280((byte) -115);
            int baseX = sceneBase.localX * -2109597897 << 9;
            int baseY = sceneBase.localY * 417324155 << 9;
            Class24.aClass411_Sub1_158.method5012(
                    0.02F,
                    client.aClass613_8605.method7293(1134705460).anIntArrayArrayArray3141,
                    client.aClass613_8605.method7287((byte) -67),
                    baseX,
                    baseY,
                    (byte) -99);
        } catch (RuntimeException ex) {
            if (!failureReported) {
                failureReported = true;
                reportToServer("FAIL free-build-tick-" + ex.getClass().getSimpleName());
                ex.printStackTrace();
            }
        }
    }

    private static void updateFreeBuildCamera(Class658_Sub2 lookController, Class240 position, float dt) {
        Class230 orientation = lookController.method8929((short) 11906);
        updateMouseLook(lookController, orientation);
        orientation.method3175();

        boolean forward = keyDown(98) || keyDown(33); // Up or W
        boolean backward = keyDown(99) || keyDown(49); // Down or S
        boolean left = keyDown(96) || keyDown(48); // Left or A
        boolean right = keyDown(97) || keyDown(50); // Right or D
        boolean up = keyDown(34); // E
        boolean down = keyDown(32); // Q

        boolean anyMovementKey = forward || backward || left || right || up || down;
        reportInputOnce(anyMovementKey);

        if (clickStopLatched) {
            if (anyMovementKey) {
                forward = false;
                backward = false;
                left = false;
                right = false;
                up = false;
                down = false;
                anyMovementKey = false;
            } else {
                clickStopLatched = false;
            }
        }

        float localX = (right ? 1.0F : 0.0F) - (left ? 1.0F : 0.0F);
        float localZ = (forward ? 1.0F : 0.0F) - (backward ? 1.0F : 0.0F);
        float vertical = (up ? 1.0F : 0.0F) - (down ? 1.0F : 0.0F);

        float magnitude = (float) Math.sqrt(localX * localX + localZ * localZ + vertical * vertical);
        if (magnitude > 1.0F) {
            localX /= magnitude;
            localZ /= magnitude;
            vertical /= magnitude;
        }

        float targetX = 0.0F;
        float targetY = 0.0F;
        float targetZ = 0.0F;

        if (anyMovementKey) {
            float speed = movementSpeed();

            Class240 direction = Class240.method3316(localX, 0.0F, localZ);
            direction.method3288(orientation);
            direction.aFloat2656 *= -1.0F;

            targetX = direction.aFloat2653 * speed;
            targetY = direction.aFloat2656 * speed + vertical * speed;
            targetZ = direction.aFloat2657 * speed;
        }

        updateVelocity(targetX, targetY, targetZ, anyMovementKey, dt);

        position.aFloat2653 += velocityX * dt;
        position.aFloat2656 += velocityY * dt;
        position.aFloat2657 += velocityZ * dt;
    }

    private static void updateRtsCamera(float dt) {
        if (pendingRtsMinimapFocus) {
            int localX = pendingRtsMinimapFocusX;
            int localY = pendingRtsMinimapFocusY;
            pendingRtsMinimapFocus = false;
            focusRtsAtLocalTile(localX, localY);
        }

        boolean forward = keyDown(98) || keyDown(33); // Up or W
        boolean backward = keyDown(99) || keyDown(49); // Down or S
        boolean left = keyDown(96) || keyDown(48); // Left or A
        boolean right = keyDown(97) || keyDown(50); // Right or D
        boolean rotateLeft = keyDown(34); // E
        boolean rotateRight = keyDown(32); // Q

        boolean anyCameraKey = forward || backward || left || right || rotateLeft || rotateRight;
        reportInputOnce(anyCameraKey);

        float rotationInput = (rotateRight ? 1.0F : 0.0F) - (rotateLeft ? 1.0F : 0.0F);
        int[] mouseOrbit = consumeRtsOrbitPixels();
        boolean orbitChanged = false;
        if (rotationInput != 0.0F) {
            rtsYawRadians = normalizeRadians(rtsYawRadians + rotationInput * RTS_ROTATE_SPEED * dt);
            orbitChanged = true;
        }
        if (mouseOrbit[0] != 0) {
            rtsYawRadians = normalizeRadians(rtsYawRadians
                    + mouseOrbit[0] * RTS_MOUSE_ORBIT_RADIANS_PER_PIXEL);
            orbitChanged = true;
        }
        if (mouseOrbit[1] != 0) {
            rtsPitchRadians = clamp(
                    rtsPitchRadians + mouseOrbit[1] * RTS_MOUSE_ORBIT_RADIANS_PER_PIXEL,
                    RTS_MIN_PITCH_RADIANS,
                    RTS_MAX_PITCH_RADIANS);
            orbitChanged = true;
        }

        float localX = (right ? 1.0F : 0.0F) - (left ? 1.0F : 0.0F);
        float localZ = (forward ? 1.0F : 0.0F) - (backward ? 1.0F : 0.0F);
        float magnitude = (float) Math.sqrt(localX * localX + localZ * localZ);
        if (magnitude > 1.0F) {
            localX /= magnitude;
            localZ /= magnitude;
        }

        boolean panning = localX != 0.0F || localZ != 0.0F;
        float targetX = 0.0F;
        float targetZ = 0.0F;

        if (panning) {
            // Keep the already-accepted RTS yaw semantics: yaw 0 faces +Z and
            // positive yaw faces +X. Class246 uses the opposite yaw sign, which
            // is converted only at the vanilla-solver boundary.
            float forwardX = (float) Math.sin(rtsYawRadians);
            float forwardZ = (float) Math.cos(rtsYawRadians);
            float rightX = forwardZ;
            float rightZ = -forwardX;
            float speed = rtsMovementSpeed();

            targetX = (localX * rightX + localZ * forwardX) * speed;
            targetZ = (localX * rightZ + localZ * forwardZ) * speed;
        }

        updateVelocity(targetX, 0.0F, targetZ, panning, dt);

        rtsPivotX += velocityX * dt;
        rtsPivotZ += velocityZ * dt;
        clampRtsPivotToLoadedScene();

        int wheelSteps = consumeRtsZoomSteps();
        if (wheelSteps != 0) {
            rtsOrbitDistance = clamp(
                    rtsOrbitDistance + wheelSteps * RTS_ZOOM_STEP,
                    RTS_MIN_ORBIT_DISTANCE,
                    RTS_MAX_ORBIT_DISTANCE);
        }

        rtsPivotY = resolveRtsPivotHeight();
        if (orbitChanged || panning || wheelSteps != 0) {
            rememberRtsView();
        } else if (!savedRtsView) {
            rememberRtsView();
        }

        syncRtsMinimapMarker();
    }

    private static void initializeRtsVanillaHeading() {
        if (savedRtsView && isSavedRtsPivotInLoadedScene()) {
            rtsYawRadians = savedRtsYawRadians;
            rtsPitchRadians = clamp(savedRtsPitchRadians,
                    RTS_MIN_PITCH_RADIANS, RTS_MAX_PITCH_RADIANS);
            rtsOrbitDistance = clamp(
                    savedRtsOrbitDistance,
                    RTS_MIN_ORBIT_DISTANCE,
                    RTS_MAX_ORBIT_DISTANCE);
            rtsPivotX = savedRtsPivotX;
            rtsPivotZ = savedRtsPivotZ;
            rtsPivotY = resolveRtsPivotHeight();
            rtsOrientationInitialized = true;
            clampRtsPivotToLoadedScene();
            return;
        }

        savedRtsView = false;

        // Reuse Matrix3's already-calculated normal-camera focus as the first RTS
        // pivot. This avoids importing detached-camera coordinate conventions.
        rtsPivotX = Entity.anInt11674 * 1007135537;
        rtsPivotZ = Class165.anInt2050 * -1126693191;
        clampRtsPivotToLoadedScene();
        rtsPivotY = resolveRtsPivotHeight();

        // Preserve the user's current vanilla heading. RTS historically uses the
        // opposite yaw sign from Class246, so invert only at this initialization
        // and again at the solver boundary to keep established Q/E/MMB behavior.
        int vanillaYaw = Class406.anInt4765 * 426389501 & 0x3fff;
        rtsYawRadians = normalizeRadians(-angleUnitsToRadians(vanillaYaw));
        rtsPitchRadians = RTS_DEFAULT_PITCH_RADIANS;
        rtsOrbitDistance = RTS_INITIAL_BACKOFF;
        rtsOrientationInitialized = true;
        rememberRtsView();
    }

    private static void applyRtsVanillaCamera(int viewportHeight) {
        rtsPivotY = resolveRtsPivotHeight();
        int pitch = radiansToAngleUnits(rtsPitchRadians);
        int yaw = radiansToAngleUnits(-rtsYawRadians);

        Class246.method3359(
                Math.round(rtsPivotX),
                Math.round(rtsPivotY),
                Math.round(rtsPivotZ),
                pitch,
                yaw,
                Math.round(rtsOrbitDistance),
                viewportHeight,
                -1798877514);
    }

    private static float resolveRtsPivotHeight() {
        return Class314.method4072(
                (int) rtsPivotX,
                (int) rtsPivotZ,
                Class274.anInt2911 * -374189215,
                -2063621494) - client.anInt8684 * 1915481369;
    }

    /**
     * Lazily activates Matrix3's detached camera only when Free Build actually
     * needs it, then seeds it from the currently rendered camera to avoid a
     * jarring RTS -> Free Build position jump.
     */
    private static boolean enableDetachedFreeBuildCamera() {
        try {
            if (Class24.aClass411_Sub1_158 == null) {
                Class102_Sub5.method9948(
                        new Class572_Sub17(
                                0,
                                Class36.anInt387 * 386814715,
                                Class572_Sub13_Sub2.anInt11451 * -1094666305,
                                Class49.anInt490 * -999214779),
                        0);
                ownsFreeCamera = true;
            }

            if (Class24.aClass411_Sub1_158 == null) {
                return false;
            }

            Class423_Sub2 positionController =
                    (Class423_Sub2) Class24.aClass411_Sub1_158.method4990((byte) -37);
            Class658_Sub2 lookController =
                    (Class658_Sub2) Class24.aClass411_Sub1_158.method4991(-589573040);

            positionController.method9278(
                    new Class572_Sub17(
                            0,
                            Class36.anInt387 * 386814715,
                            Class572_Sub13_Sub2.anInt11451 * -1094666305,
                            Class49.anInt490 * -999214779),
                    (byte) 3);

            if (rtsOrientationInitialized) {
                applyRtsOrientation(lookController);
            }

            Class24.aBool157 = true;
            return true;
        } catch (RuntimeException ex) {
            if (ownsFreeCamera) {
                RSSocket.method7604(0);
                ownsFreeCamera = false;
            }
            return false;
        }
    }

    /**
     * Detached Free Build uses Matrix3's existing Class658_Sub2 look controller.
     * This conversion is retained only for the RTS -> Free Build handoff.
     */
    private static void applyRtsOrientation(Class658_Sub2 lookController) {
        float horizontal = (float) Math.cos(rtsPitchRadians) * RTS_LOOK_DISTANCE;
        int x = Math.round((float) Math.sin(rtsYawRadians) * horizontal);
        int y = -Math.round((float) Math.sin(rtsPitchRadians) * RTS_LOOK_DISTANCE);
        int z = Math.round((float) Math.cos(rtsYawRadians) * horizontal);
        lookController.method8927(x, y, z, 0);
    }

    private static int radiansToAngleUnits(float radians) {
        return Math.round(radians * (16384.0F / (float) (Math.PI * 2.0))) & 0x3fff;
    }

    private static float angleUnitsToRadians(int angle) {
        return (angle & 0x3fff) * ((float) (Math.PI * 2.0) / 16384.0F);
    }

    private static Class240 getViewDirection(Class658_Sub2 lookController, Class240 position) {
        Class240 forwardPoint = lookController.method7736(0);
        float x = forwardPoint.aFloat2653 - position.aFloat2653;
        float y = forwardPoint.aFloat2656 - position.aFloat2656;
        float z = forwardPoint.aFloat2657 - position.aFloat2657;
        float length = (float) Math.sqrt(x * x + y * y + z * z);

        if (Float.isNaN(length) || length < 0.001F) {
            return null;
        }

        return Class240.method3316(x / length, y / length, z / length);
    }

    private static void updateVelocity(float targetX, float targetY, float targetZ,
            boolean accelerating, float dt) {
        float response = accelerating ? ACCEL_RESPONSE : DECEL_RESPONSE;
        float blend = 1.0F - (float) Math.exp(-response * dt);

        velocityX += (targetX - velocityX) * blend;
        velocityY += (targetY - velocityY) * blend;
        velocityZ += (targetZ - velocityZ) * blend;

        if (!accelerating) {
            velocityX = settle(velocityX);
            velocityY = settle(velocityY);
            velocityZ = settle(velocityZ);
        }
    }

    private static void reportInputOnce(boolean anyCameraInput) {
        if (!inputReported && anyCameraInput) {
            inputReported = true;
            reportToServer("INPUT mode=" + cameraMode.name()
                    + " W=" + keyDown(33)
                    + " A=" + keyDown(48)
                    + " S=" + keyDown(49)
                    + " D=" + keyDown(50)
                    + " Q=" + keyDown(32)
                    + " E=" + keyDown(34)
                    + " shift=" + keyDown(81)
                    + " ctrl=" + keyDown(82));
        }
    }

    /**
     * Ground/build confirmation stops the camera immediately. If a camera key is
     * still held, movement remains latched off until all movement keys have been
     * released once, preventing the camera from restarting on the next tick.
     */
    public static void stopMovement() {
        if (!active) {
            return;
        }
        clearVelocity();
        clickStopLatched = true;
        if (!stopReported) {
            stopReported = true;
            reportToServer("STOP click mode=" + cameraMode.name());
        }
    }

    private static void updateMouseLook(Class658_Sub2 lookController, Class230 orientation) {
        int mouseX = Class26.aClass564_216.method6657((short) -1);
        int mouseY = Class26.aClass564_216.method6658((byte) -1);

        if (Class26.aClass564_216.method6654((byte) -102)) {
            Class230 pitch = Class230.method3210();
            pitch.method3172(
                    1.0F,
                    0.0F,
                    0.0F,
                    (float) (mouseY - lastMouseY) / 200.0F);
            orientation.method3189(pitch);

            Class240 upAxis = Class240.method3316(0.0F, 1.0F, 0.0F);
            upAxis.method3288(orientation);

            Class230 yaw = Class230.method3210();
            yaw.method3209(
                    upAxis,
                    (float) (lastMouseX - mouseX) / 200.0F);
            orientation.method3189(yaw);

            lookController.method8940(orientation, (byte) 4);
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    private static float consumeDeltaSeconds() {
        long now = System.nanoTime();
        if (lastTickNanos == 0L) {
            lastTickNanos = now;
            return DEFAULT_DT;
        }

        float dt = (now - lastTickNanos) / 1000000000.0F;
        lastTickNanos = now;

        if (dt < MIN_DT) {
            return MIN_DT;
        }
        if (dt > MAX_DT) {
            return MAX_DT;
        }
        return dt;
    }

    private static synchronized int consumeRtsZoomSteps() {
        int steps = pendingRtsZoomSteps;
        pendingRtsZoomSteps = 0;
        return steps;
    }

    private static synchronized int[] consumeRtsOrbitPixels() {
        int[] delta = { pendingRtsOrbitX, pendingRtsOrbitY };
        pendingRtsOrbitX = 0;
        pendingRtsOrbitY = 0;
        return delta;
    }

    private static float movementSpeed() {
        if (keyDown(82)) {
            return PRECISION_SPEED;
        }
        if (keyDown(81)) {
            return FAST_SPEED;
        }
        return NORMAL_SPEED;
    }

    private static float rtsMovementSpeed() {
        return movementSpeed() * getRtsMoveSpeedMultiplier();
    }

    private static float normalizeRadians(float value) {
        float twoPi = (float) (Math.PI * 2.0);
        while (value > Math.PI) {
            value -= twoPi;
        }
        while (value < -Math.PI) {
            value += twoPi;
        }
        return value;
    }

    private static float settle(float value) {
        return Math.abs(value) < VELOCITY_EPSILON ? 0.0F : value;
    }

    private static void clearVelocity() {
        velocityX = 0.0F;
        velocityY = 0.0F;
        velocityZ = 0.0F;
    }

    private static void focusRtsAtLocalTile(int localX, int localY) {
        clearVelocity();
        clickStopLatched = false;
        if (!rtsOrientationInitialized) {
            pendingRtsMinimapFocus = true;
            pendingRtsMinimapFocusX = localX;
            pendingRtsMinimapFocusY = localY;
            return;
        }
        if (client.aClass613_8605 == null) {
            return;
        }

        Class497 sceneBase = client.aClass613_8605.method7280((byte) -115);
        if (sceneBase == null) {
            return;
        }

        int baseTileX = sceneBase.localX * -2109597897;
        int baseTileY = sceneBase.localY * 417324155;
        int worldTileX = baseTileX + localX;
        int worldTileY = baseTileY + localY;

        // Matrix3 camera horizontal coordinates use 512 units per scene tile.
        // Aim at tile center and preserve the current pivot height/orbit/yaw.
        rtsPivotX = (worldTileX << 9) + 256.0F;
        rtsPivotZ = (worldTileY << 9) + 256.0F;
        clampRtsPivotToLoadedScene();
        rememberRtsView();
        syncRtsMinimapMarker();
        reportToServer("RTS_MINIMAP_FOCUS local=" + localX + "," + localY);
    }

    private static void snapshotRtsMinimapMarker() {
        if (rtsMinimapMarkerSnapshotValid) {
            return;
        }
        savedMinimapMarkerXRaw = Class192.anInt2310;
        savedMinimapMarkerYRaw = Class192.anInt2300;
        savedMinimapMarkerBool = Class192.aBool2307;
        rtsMinimapMarkerSnapshotValid = true;
        lastRtsMinimapMarkerLocalX = Integer.MIN_VALUE;
        lastRtsMinimapMarkerLocalY = Integer.MIN_VALUE;
    }

    private static void restoreRtsMinimapMarker() {
        if (!rtsMinimapMarkerSnapshotValid) {
            return;
        }
        Class192.anInt2310 = savedMinimapMarkerXRaw;
        Class192.anInt2300 = savedMinimapMarkerYRaw;
        Class192.aBool2307 = savedMinimapMarkerBool;
        rtsMinimapMarkerSnapshotValid = false;
        lastRtsMinimapMarkerLocalX = Integer.MIN_VALUE;
        lastRtsMinimapMarkerLocalY = Integer.MIN_VALUE;
        Class10.method544((byte) 1);
    }

    private static void syncRtsMinimapMarker() {
        if (!active || cameraMode != CameraMode.RTS || !rtsOrientationInitialized
                || client.aClass613_8605 == null) {
            return;
        }

        Class497 sceneBase = client.aClass613_8605.method7280((byte) -115);
        if (sceneBase == null) {
            return;
        }

        int baseTileX = sceneBase.localX * -2109597897;
        int baseTileY = sceneBase.localY * 417324155;
        int localX = (int) Math.floor(rtsPivotX / 512.0F) - baseTileX;
        int localY = (int) Math.floor(rtsPivotZ / 512.0F) - baseTileY;
        if (localX < 0 || localY < 0) {
            return;
        }

        if (!rtsMinimapMarkerSnapshotValid) {
            snapshotRtsMinimapMarker();
        }
        if (localX == lastRtsMinimapMarkerLocalX && localY == lastRtsMinimapMarkerLocalY) {
            return;
        }

        // Reuse the exact encoded marker writes from IncomingPacket.method4108.
        Class192.anInt2310 = -3733871 * localX;
        Class192.anInt2300 = -343091919 * localY;
        Class192.aBool2307 = false;
        lastRtsMinimapMarkerLocalX = localX;
        lastRtsMinimapMarkerLocalY = localY;
        Class10.method544((byte) 1);
    }

    private static boolean isSavedRtsPivotInLoadedScene() {
        float[] bounds = getLoadedSceneBounds();
        return bounds != null
                && savedRtsPivotX >= bounds[0] && savedRtsPivotX <= bounds[1]
                && savedRtsPivotZ >= bounds[2] && savedRtsPivotZ <= bounds[3];
    }

    private static void clampRtsPivotToLoadedScene() {
        float[] bounds = getLoadedSceneBounds();
        if (bounds == null) {
            return;
        }
        rtsPivotX = clamp(rtsPivotX, bounds[0], bounds[1]);
        rtsPivotZ = clamp(rtsPivotZ, bounds[2], bounds[3]);
    }

    /**
     * Keep the RTS look pivot inside Matrix3's currently loaded scene. Detached
     * camera position may sit outside this rectangle at long orbit distances; the
     * important invariant is that its look target remains on loaded terrain.
     */
    private static float[] getLoadedSceneBounds() {
        try {
            Class497 sceneBase = client.aClass613_8605.method7280((byte) -115);
            int[][][] heights = client.aClass613_8605.method7293(1134705460).anIntArrayArrayArray3141;
            if (sceneBase == null || heights == null || heights.length == 0
                    || heights[0] == null || heights[0].length < 2
                    || heights[0][0] == null || heights[0][0].length < 2) {
                return null;
            }

            float baseX = (sceneBase.localX * -2109597897) << 9;
            float baseZ = (sceneBase.localY * 417324155) << 9;
            float maxX = baseX + (heights[0].length - 1) * 512.0F;
            float maxZ = baseZ + (heights[0][0].length - 1) * 512.0F;
            float marginX = Math.min(RTS_SCENE_EDGE_MARGIN, Math.max(0.0F, (maxX - baseX) * 0.25F));
            float marginZ = Math.min(RTS_SCENE_EDGE_MARGIN, Math.max(0.0F, (maxZ - baseZ) * 0.25F));
            return new float[] {
                    baseX + marginX, maxX - marginX,
                    baseZ + marginZ, maxZ - marginZ
            };
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static synchronized void rememberRtsView() {
        if (!rtsOrientationInitialized || rtsOrbitDistance <= 0.0F) {
            return;
        }
        savedRtsView = true;
        savedRtsYawRadians = rtsYawRadians;
        savedRtsPitchRadians = rtsPitchRadians;
        savedRtsOrbitDistance = rtsOrbitDistance;
        savedRtsPivotX = rtsPivotX;
        savedRtsPivotY = rtsPivotY;
        savedRtsPivotZ = rtsPivotZ;
    }

    private static synchronized void resetRtsState() {
        rtsOrientationInitialized = false;
        rtsYawRadians = 0.0F;
        rtsPitchRadians = RTS_DEFAULT_PITCH_RADIANS;
        rtsOrbitDistance = 0.0F;
        rtsPivotX = 0.0F;
        rtsPivotY = 0.0F;
        rtsPivotZ = 0.0F;
        pendingRtsZoomSteps = 0;
        pendingRtsOrbitX = 0;
        pendingRtsOrbitY = 0;
        pendingRtsMinimapFocus = false;
        pendingRtsMinimapFocusX = 0;
        pendingRtsMinimapFocusY = 0;
    }

    private static boolean keyDown(int internalKey) {
        return Class108.aClass549_1426 != null
                && Class108.aClass549_1426.method6514(internalKey, (byte) 1);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void reportToServer(String message) {
        String safe = message == null ? "unknown" : message.replace(' ', '_');
        String error = ClientConsoleBridge.queueConsoleCommand(
                "itembrowser constructioncamera debug " + safe);
        if (error != null) {
            System.out.println("[ConstructionBuildCamera] " + message + " (server debug unavailable: " + error + ")");
        }
    }
}
