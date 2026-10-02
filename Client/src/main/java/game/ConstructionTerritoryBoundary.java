package game;

/**
 * Client-only visualization of the real settlement gameplay frontier.
 *
 * The server remains authoritative for movement/build/worker/logistics access.
 * This class only renders sparse world-space frontier markers around the
 * centered 4x4-chunk (32x32 tile) foundation area.
 */
public final class ConstructionTerritoryBoundary {

    private static final int FRONTIER_GFX_ID = 4171;
    private static final int MODEL_FLAGS = 2048 | 0x80000 | 0x8000 | 0x100 | 0x5;
    private static final int BASE_MODEL_SCALE = 128;
    private static final int FRONTIER_SCALE_PERCENT = 38;
    private static final int FRONTIER_SPACING_TILES = 2;
    private static final int FOUNDATION_TILES = 32;
    private static final int FOUNDATION_HALF_TILES = FOUNDATION_TILES / 2;

    private static final int LOCKED_FRONTIER_RGB = 0xE2A126;
    private static final int BYPASS_FRONTIER_RGB = 0x39B8FF;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static boolean settlementActive;
    private static boolean anchorReady;
    private static boolean fullPlotTesting;
    private static int centerWorldX;
    private static int centerWorldY;
    private static int plane;
    private static int lastRenderedCycle = Integer.MIN_VALUE;
    private static String status = "Frontier waiting for settlement.";

    private ConstructionTerritoryBoundary() {
    }

    public static synchronized void onSettlementEnter() {
        settlementActive = true;
        fullPlotTesting = false;
        anchorReady = captureEntryAnchor();
        lastRenderedCycle = Integer.MIN_VALUE;
        status = anchorReady
                ? "Frontier centered at " + centerWorldX + "," + centerWorldY
                        + " | 4x4 chunks / 32x32 tiles."
                : "Frontier waiting for settlement entry anchor.";
    }

    public static synchronized void resetForSettlementBoundary() {
        settlementActive = false;
        anchorReady = false;
        fullPlotTesting = false;
        centerWorldX = 0;
        centerWorldY = 0;
        plane = 0;
        lastRenderedCycle = Integer.MIN_VALUE;
        status = "Frontier waiting for settlement.";
    }

    public static synchronized void setFullPlotTesting(boolean enabled) {
        fullPlotTesting = enabled;
        status = "Frontier "
                + (enabled ? "DEV BYPASS" : "LOCKED")
                + " | 4x4 gameplay boundary remains visible.";
    }

    public static synchronized String getStatus() {
        return status;
    }

    static void render(Class523 scene, Class106 renderer) {
        if (!settlementActive || !ConstructionBuildCamera.isSettlementAutoMode()
                || scene == null || renderer == null || client.aClass613_8605 == null) {
            return;
        }

        if (!anchorReady && !captureEntryAnchor()) {
            return;
        }

        if (lastRenderedCycle == client.cycles) {
            return;
        }
        lastRenderedCycle = client.cycles;

        Class613 region = client.aClass613_8605;
        if (region.method7285(0) != scene) {
            return;
        }
        Class497 sceneBase = region.method7280((byte) -102);
        if (sceneBase == null || plane < 0 || plane >= scene.aClass174Array5838.length) {
            return;
        }
        Class174 ground = scene.aClass174Array5838[plane];
        if (ground == null) {
            return;
        }

        GraphicsDefinition definition = (GraphicsDefinition) Class667.aClass639_Sub10_8509
                .getDefinition(FRONTIER_GFX_ID, 235749166);
        if (definition == null) {
            return;
        }
        Model marker = createFrontierModel(definition, renderer);
        if (marker == null) {
            return;
        }

        int minPlayableX = centerWorldX - FOUNDATION_HALF_TILES;
        int minPlayableY = centerWorldY - FOUNDATION_HALF_TILES;
        int maxPlayableX = centerWorldX + FOUNDATION_HALF_TILES - 1;
        int maxPlayableY = centerWorldY + FOUNDATION_HALF_TILES - 1;

        int west = minPlayableX - 1;
        int east = maxPlayableX + 1;
        int south = minPlayableY - 1;
        int north = maxPlayableY + 1;

        int rendered = 0;
        for (int worldY = minPlayableY; worldY <= maxPlayableY;
                worldY += FRONTIER_SPACING_TILES) {
            rendered += renderMarker(scene, sceneBase, ground, marker, west, worldY);
            rendered += renderMarker(scene, sceneBase, ground, marker, east, worldY);
        }
        for (int worldX = minPlayableX; worldX <= maxPlayableX;
                worldX += FRONTIER_SPACING_TILES) {
            rendered += renderMarker(scene, sceneBase, ground, marker, worldX, south);
            rendered += renderMarker(scene, sceneBase, ground, marker, worldX, north);
        }
        rendered += renderMarker(scene, sceneBase, ground, marker, west, south);
        rendered += renderMarker(scene, sceneBase, ground, marker, west, north);
        rendered += renderMarker(scene, sceneBase, ground, marker, east, south);
        rendered += renderMarker(scene, sceneBase, ground, marker, east, north);

        status = "Frontier " + (fullPlotTesting ? "DEV BYPASS" : "LOCKED")
                + " | markers=" + rendered
                + " | center=" + centerWorldX + "," + centerWorldY;
    }

    private static boolean captureEntryAnchor() {
        if (client.aClass613_8605 == null
                || Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976 == null) {
            return false;
        }
        Class497 sceneBase = client.aClass613_8605.method7280((byte) -102);
        Player localPlayer = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (sceneBase == null || localPlayer.screenX == null || localPlayer.screenY == null
                || localPlayer.screenX.length == 0 || localPlayer.screenY.length == 0) {
            return false;
        }
        int sceneBaseWorldX = sceneBase.localX * -2109597897;
        int sceneBaseWorldY = sceneBase.localY * 417324155;
        centerWorldX = sceneBaseWorldX + localPlayer.screenX[0];
        centerWorldY = sceneBaseWorldY + localPlayer.screenY[0];
        plane = localPlayer.aByte9009 & 0xff;
        anchorReady = true;
        return true;
    }

    private static Model createFrontierModel(
            GraphicsDefinition definition, Class106 renderer) {
        Model marker = definition.method7764(
                renderer, MODEL_FLAGS, 0, 0, 0, 0, null, (byte) 2, 1913622280);
        if (marker == null) {
            return null;
        }

        int minX = marker.method1380();
        int maxX = marker.method1381();
        int minZ = marker.method1384();
        int maxZ = marker.method1508();
        int markerCenterX = (minX + maxX) / 2;
        int markerCenterZ = (minZ + maxZ) / 2;
        if (markerCenterX != 0 || markerCenterZ != 0) {
            marker.method1358(-markerCenterX, 0, -markerCenterZ);
        }

        int runtimeScale = Math.max(1,
                Math.round(BASE_MODEL_SCALE * (FRONTIER_SCALE_PERCENT / 100.0F)));
        marker.method1464(runtimeScale, BASE_MODEL_SCALE, runtimeScale);
        applyTint(marker, fullPlotTesting ? BYPASS_FRONTIER_RGB : LOCKED_FRONTIER_RGB);
        return marker;
    }

    private static int renderMarker(
            Class523 scene, Class497 sceneBase, Class174 ground,
            Model marker, int worldX, int worldY) {
        int sceneBaseWorldX = sceneBase.localX * -2109597897;
        int sceneBaseWorldY = sceneBase.localY * 417324155;
        int localX = worldX - sceneBaseWorldX;
        int localY = worldY - sceneBaseWorldY;
        int sceneWidth = scene.anInt5833 * -1396185127;
        int sceneHeight = scene.anInt5834 * -1519623925;
        if (localX < 0 || localY < 0 || localX >= sceneWidth || localY >= sceneHeight) {
            return 0;
        }

        int tileSize = ground.anInt2087 * 2129890771;
        int sceneX = Math.round((localX + 0.5F) * tileSize);
        int sceneZ = Math.round((localY + 0.5F) * tileSize);
        int sceneY = ground.method2718(sceneX, sceneZ, 0);

        TRANSFORM.method3588(sceneX, sceneY, sceneZ);
        marker.method1375(TRANSFORM, RENDER_BOUNDS, 0);
        return 1;
    }

    private static void applyTint(Model model, int rgb) {
        java.util.Set<Short> textureIds = new java.util.HashSet<Short>();
        if (model instanceof AbstractModel) {
            AbstractModel m = (AbstractModel) model;
            if (m.aShortArray10821 != null) {
                int count = Math.min(m.anInt10833, m.aShortArray10821.length);
                for (int i = 0; i < count; i++) {
                    if (m.aShortArray10821[i] != (short) -1) {
                        textureIds.add(Short.valueOf(m.aShortArray10821[i]));
                    }
                }
            }
        } else if (model instanceof Class89_Sub2) {
            Class89_Sub2 m = (Class89_Sub2) model;
            if (m.aShortArray10591 != null) {
                int count = Math.min(m.anInt10573, m.aShortArray10591.length);
                for (int i = 0; i < count; i++) {
                    if (m.aShortArray10591[i] != (short) -1) {
                        textureIds.add(Short.valueOf(m.aShortArray10591[i]));
                    }
                }
            }
        } else if (model instanceof OpenGLModel) {
            OpenGLModel m = (OpenGLModel) model;
            if (m.aShortArray10306 != null) {
                int count = Math.min(m.anInt10299, m.aShortArray10306.length);
                for (int i = 0; i < count; i++) {
                    if (m.aShortArray10306[i] != (short) -1) {
                        textureIds.add(Short.valueOf(m.aShortArray10306[i]));
                    }
                }
            }
        }
        for (Short textureId : textureIds) {
            model.method1475(textureId.shortValue(), (short) -1);
        }
        int[] hsl = rgbToModelHsl(rgb);
        model.method1396(hsl[0], hsl[1], hsl[2], 128);
    }

    private static int[] rgbToModelHsl(int rgb) {
        float r = ((rgb >> 16) & 0xff) / 255.0F;
        float g = ((rgb >> 8) & 0xff) / 255.0F;
        float b = (rgb & 0xff) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float hue = 0.0F;
        float saturation = 0.0F;
        float lightness = (max + min) * 0.5F;

        if (max != min) {
            float delta = max - min;
            saturation = lightness > 0.5F
                    ? delta / (2.0F - max - min)
                    : delta / (max + min);
            if (max == r) {
                hue = (g - b) / delta + (g < b ? 6.0F : 0.0F);
            } else if (max == g) {
                hue = (b - r) / delta + 2.0F;
            } else {
                hue = (r - g) / delta + 4.0F;
            }
            hue /= 6.0F;
        }

        return new int[] {
                Math.max(0, Math.min(63, Math.round(hue * 63.0F))),
                Math.max(0, Math.min(7, Math.round(saturation * 7.0F))),
                Math.max(0, Math.min(127, Math.round(lightness * 127.0F)))
        };
    }
}
