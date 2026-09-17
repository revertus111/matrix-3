package game;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * Client-only Construction placement preview.
 *
 * The preview is rendered directly through Matrix3's normal object-model path
 * and is never registered with Class523. It therefore owns no collision,
 * persistence, server state, or scene-object lifecycle.
 */
public final class ConstructionGhostPreview {

    private static final int MODEL_FLAGS = 2048 | 0x80000;
    private static final int GHOST_TINT_HUE = 0;
    private static final int GHOST_TINT_SATURATION = 0;
    private static final int GHOST_TINT_LIGHTNESS = 127;
    private static final int GHOST_TINT_WEIGHT = 160;

    private static final int DEBUG_CLASS578_ENTRY = 1 << 0;
    private static final int DEBUG_CLASS578_IMMEDIATE = 1 << 1;
    private static final int DEBUG_CLASS578_RENDER_CALL = 1 << 2;
    private static final int DEBUG_RENDER_ENTRY = 1 << 3;
    private static final int DEBUG_STATE_READY = 1 << 4;
    private static final int DEBUG_MODEL_BUILD_START = 1 << 5;
    private static final int DEBUG_MODEL_READY = 1 << 6;
    private static final int DEBUG_TINT_START = 1 << 7;
    private static final int DEBUG_TINT_DONE = 1 << 8;
    private static final int DEBUG_DRAW_START = 1 << 9;
    private static final int DEBUG_DRAW_DONE = 1 << 10;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();
    private static volatile String lastDiagnosticKey = "";
    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile int debugMilestones;
    private static volatile boolean debugSessionActive;
    private static volatile String latestDebugState = "IDLE";

    private static JWindow debugWindow;
    private static JLabel debugLabel;
    private static Window debugOwner;

    private ConstructionGhostPreview() {
    }

    static void beginDebugSession() {
        debugSessionActive = true;
        debugMilestones = 0;
        lastDiagnosticKey = "";
        lastRenderedCycle = Integer.MIN_VALUE;
        publishDebug("PALETTE_OPENED - waiting for render hook");
    }

    static void endDebugSession() {
        debugSessionActive = false;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (debugWindow != null) {
                    debugWindow.setVisible(false);
                }
            }
        });
    }

    static void debugClass578Entry() {
        debugMilestone(DEBUG_CLASS578_ENTRY,
                "HOOK Class578.method6834 reached while Construction palette is active");
    }

    static void debugClass578ImmediatePath() {
        debugMilestone(DEBUG_CLASS578_IMMEDIATE,
                "HOOK Class578 immediate post-object path reached");
    }

    static void debugClass578RenderCall() {
        debugMilestone(DEBUG_CLASS578_RENDER_CALL,
                "HOOK Class578 calling ghost renderer inside active scene pass");
    }

    /**
     * Draws the currently selected Construction piece at the hovered world tile.
     *
     * verified-static: ObjectDefinitions.method6057(...) is the same model factory
     * used by Matrix3 scene objects, and Model.method1375(...) is their live draw
     * path. Model.method1396(...) is Matrix3's existing whole-model tint override
     * path. This method intentionally never calls Class523 attach/remove methods.
     */
    static void render(Class523 scene, Class106 renderer) {
        debugMilestone(DEBUG_RENDER_ENTRY, "RENDER entered ConstructionGhostPreview.render");

        if (scene == null || renderer == null) {
            diagnostic("WAIT_SCENE", "WAIT scene/renderer unavailable");
            return;
        }
        if (!ConstructionPlacementController.isHoverTracking()) {
            diagnostic("WAIT_TRACKING", "WAIT hover tracking disabled");
            return;
        }
        if (!ConstructionPlacementController.isArmed()) {
            diagnostic("WAIT_ARMED", "WAIT placement not armed");
            return;
        }

        ConstructionPlacementController.BuildPiece piece = ConstructionPlacementController.getSelectedPiece();
        ConstructionPlacementController.HoverTile tile = ConstructionPlacementController.getHoveredTile();
        Class613 region = client.aClass613_8605;
        if (piece == null) {
            diagnostic("WAIT_PIECE", "WAIT no selected Construction piece");
            return;
        }
        if (tile == null) {
            diagnostic("WAIT_HOVER", "WAIT no current hovered world tile");
            return;
        }
        if (region == null) {
            diagnostic("WAIT_REGION", "WAIT active region unavailable");
            return;
        }
        if (region.method7285(0) != scene) {
            diagnostic("WAIT_SCENE_MISMATCH", "WAIT active scene mismatch");
            return;
        }

        debugMilestone(DEBUG_STATE_READY,
                "STATE_READY object=" + piece.getObjectId() + " type=" + piece.getObjectType()
                        + " rot=" + ConstructionPlacementController.getRotation()
                        + " world=" + tile.getWorldX() + "," + tile.getWorldY() + "," + tile.getPlane());

        int cycle = client.cycles;
        if (lastRenderedCycle == cycle) {
            return;
        }
        lastRenderedCycle = cycle;

        Class497 sceneBase = region.method7280((byte) -102);
        Class639_Sub16 definitions = region.method7288(0);
        if (sceneBase == null || definitions == null) {
            diagnostic("WAIT_REGION_DATA", "WAIT scene base/object definitions unavailable");
            return;
        }

        int localX = tile.getWorldX() - sceneBase.localX * -2109597897;
        int localY = tile.getWorldY() - sceneBase.localY * 417324155;
        int plane = tile.getPlane();
        if (plane < 0 || plane >= scene.aClass174Array5838.length) {
            diagnostic("SKIP_PLANE", "SKIP invalid hovered plane " + plane);
            return;
        }

        Class174 ground = scene.aClass174Array5838[plane];
        if (ground == null) {
            diagnostic("WAIT_GROUND", "WAIT terrain unavailable for plane " + plane);
            return;
        }

        ObjectDefinitions definition = (ObjectDefinitions) definitions.getDefinition(piece.getObjectId(), -1356282071);
        if (definition == null) {
            diagnostic("MODEL_DEFINITION_NULL:" + piece.getObjectId(),
                    "MODEL_DEFINITION_NULL object=" + piece.getObjectId());
            return;
        }

        int rotation = ConstructionPlacementController.getRotation() & 0x3;
        int sizeX = definition.sizeX * 1755098015;
        int sizeY = definition.sizeY * -1692133213;
        if ((rotation & 0x1) != 0) {
            int swap = sizeX;
            sizeX = sizeY;
            sizeY = swap;
        }

        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (localX < 0 || localY < 0 || localX + sizeX > sceneWidth || localY + sizeY > sceneHeight) {
            diagnostic("SKIP_BOUNDS", "SKIP hovered tile outside active scene local=" + localX + "," + localY
                    + " size=" + sizeX + "x" + sizeY + " scene=" + sceneWidth + "x" + sceneHeight);
            return;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        int sceneX = localX * tileSize + sizeX * tileSize / 2;
        int sceneZ = localY * tileSize + sizeY * tileSize / 2;
        int sceneY = ground.method2718(sceneX, sceneZ, 0);
        Class174 upperGround = plane + 1 < scene.aClass174Array5838.length
                ? scene.aClass174Array5838[plane + 1]
                : null;

        debugMilestone(DEBUG_MODEL_BUILD_START,
                "MODEL_BUILD_START object=" + piece.getObjectId() + " type=" + piece.getObjectType()
                        + " rot=" + rotation + " scene=" + sceneX + "," + sceneY + "," + sceneZ);

        Class647 built = definition.method6057(renderer, MODEL_FLAGS, piece.getObjectType(), rotation,
                ground, upperGround, sceneX, sceneY, sceneZ, false, null, -272661735);
        if (built == null) {
            diagnostic("MODEL_NULL:" + piece.getObjectId() + ":" + piece.getObjectType() + ":" + rotation,
                    "MODEL_NULL object=" + piece.getObjectId() + " type=" + piece.getObjectType()
                            + " rot=" + rotation + " world=" + tile.getWorldX() + "," + tile.getWorldY() + ","
                            + plane + " local=" + localX + "," + localY);
            return;
        }
        if (!(built.anObject8324 instanceof Model)) {
            String actual = built.anObject8324 == null ? "null" : built.anObject8324.getClass().getName();
            diagnostic("MODEL_NOT_MODEL:" + piece.getObjectId() + ":" + actual,
                    "MODEL_NOT_MODEL object=" + piece.getObjectId() + " result=" + actual);
            return;
        }

        debugMilestone(DEBUG_MODEL_READY,
                "MODEL_READY object=" + piece.getObjectId() + " modelClass=" + built.anObject8324.getClass().getName());

        if (scene.aClass174Array5840 == scene.aClass174Array5875 && scene.aClass174Array5838[0] != null) {
            Class86 environment = new Class86();
            environment.anInt1193 = scene.method6231(localX, localY, 1258315415) * 1368828903;
            environment.anInt1190 = scene.method6230(localX, localY, -981999643) * 1765263439;
            environment.anInt1191 = scene.method6283(localX, localY, 775342000) * 628738217;
            environment.anInt1189 = scene.method6233(localX, localY, -1042067865) * -233369847;
            environment.anInt1194 = scene.method6234(localX, localY, (byte) 16) * -223776263;
            environment.anInt1195 = scene.method6235(localX, localY, (byte) 95) * -963547665;
            renderer.method1790(scene.aClass174Array5838[0].method2726(sceneX, sceneZ, 358769667), environment);
        }

        Model model = (Model) built.anObject8324;
        debugMilestone(DEBUG_TINT_START, "TINT_START Model.method1396 white ghost override");
        model.method1396(GHOST_TINT_HUE, GHOST_TINT_SATURATION, GHOST_TINT_LIGHTNESS, GHOST_TINT_WEIGHT);
        debugMilestone(DEBUG_TINT_DONE, "TINT_DONE Model.method1396 returned normally");

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        Class326 bounds = definition.aClass326_5684;
        debugMilestone(DEBUG_DRAW_START,
                "DRAW_START Model.method1375 specialBounds=" + (bounds != null));
        if (bounds != null) {
            model.method1375(TRANSFORM, null, 0);
            renderer.method1738(TRANSFORM, RENDER_BOUNDS, bounds);
        } else {
            model.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        }
        debugMilestone(DEBUG_DRAW_DONE, "DRAW_DONE model draw call returned normally");

        diagnostic("DRAW_SUBMITTED:" + piece.getObjectId() + ":" + piece.getObjectType() + ":" + rotation
                        + ":" + (bounds != null),
                "DRAW_SUBMITTED object=" + piece.getObjectId() + " type=" + piece.getObjectType()
                        + " rot=" + rotation + " specialBounds=" + (bounds != null)
                        + " ghostTint=white/" + GHOST_TINT_WEIGHT
                        + " world=" + tile.getWorldX() + "," + tile.getWorldY() + "," + plane
                        + " local=" + localX + "," + localY
                        + " scene=" + sceneX + "," + sceneY + "," + sceneZ);
    }

    private static void debugMilestone(int bit, String message) {
        if ((debugMilestones & bit) != 0) {
            return;
        }
        synchronized (ConstructionGhostPreview.class) {
            if ((debugMilestones & bit) != 0) {
                return;
            }
            debugMilestones |= bit;
            System.out.println("[ConstructionGhostDebug] " + message);
            publishDebug(message);
        }
    }

    private static void diagnostic(String key, String message) {
        if (key.equals(lastDiagnosticKey)) {
            return;
        }
        lastDiagnosticKey = key;
        System.out.println("[ConstructionGhostPreview] " + message);
        publishDebug(message);
    }

    private static void publishDebug(String message) {
        latestDebugState = message == null ? "UNKNOWN" : message;
        if (!debugSessionActive) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                refreshDebugWindow();
            }
        });
    }

    private static void refreshDebugWindow() {
        if (!debugSessionActive) {
            if (debugWindow != null) {
                debugWindow.setVisible(false);
            }
            return;
        }

        Canvas canvas = Class584.aCanvas7745;
        if (canvas == null || !canvas.isDisplayable() || !canvas.isVisible()) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(canvas);
        if (owner == null) {
            return;
        }

        if (debugWindow == null || debugOwner != owner) {
            if (debugWindow != null) {
                debugWindow.dispose();
            }
            debugOwner = owner;
            debugLabel = new JLabel();
            debugLabel.setOpaque(true);
            debugLabel.setBackground(new Color(10, 13, 18));
            debugLabel.setForeground(new Color(111, 220, 235));
            debugLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
            debugLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

            debugWindow = new JWindow(owner);
            debugWindow.setFocusableWindowState(false);
            debugWindow.setAutoRequestFocus(false);
            debugWindow.getContentPane().add(debugLabel);
        }

        debugLabel.setText("GHOST DEBUG: " + latestDebugState);
        Point canvasLocation;
        try {
            canvasLocation = canvas.getLocationOnScreen();
        } catch (IllegalComponentStateException ex) {
            return;
        }
        int width = Math.max(220, Math.min(680, canvas.getWidth() - 20));
        debugWindow.setBounds(canvasLocation.x + 10, canvasLocation.y + 18, width, 28);
        if (!debugWindow.isVisible()) {
            debugWindow.setVisible(true);
        }
    }
}
