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
 * renderer Model from the canonical 46298 / 49717+49718 source geometry.
 *
 * Connected-component role classification is a HYPOTHESIS until runtime
 * inspection confirms the exact authored belt/support/detail submeshes.
 */
public final class ConveyorRunPreview {

    private static final int SOURCE_OBJECT_ID = 46298;
    private static final int[] SOURCE_MODEL_IDS = { 49717, 49718 };

    private static final int TILE_UNITS = 512;
    private static final double MAX_SUPPORT_SPAN_TILES = 3.0;
    private static final double STRETCH_MIN_LONG_FRACTION = 0.60;
    private static final double SUPPORT_MAX_LONG_FRACTION = 0.45;
    private static final double SUPPORT_MIN_HEIGHT_FRACTION = 0.30;

    private static final int BASE_MODEL_FLAGS = 2048;
    private static final int TRANSFORM_FLAGS = 0x0f;
    private static final int MODEL_FLAGS = BASE_MODEL_FLAGS | TRANSFORM_FLAGS;
    private static final int RAW_BUILD_FLAGS = MODEL_FLAGS | 0x1f01f | 0x80000;

    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 RENDER_BOUNDS = new Class90();

    private static volatile boolean active;
    private static volatile ConveyorRun[] runs = new ConveyorRun[0];
    private static volatile int revision;
    private static volatile int lastRenderedCycle = Integer.MIN_VALUE;
    private static volatile String status = "HIDDEN";
    private static volatile String roleSummary = "roles not generated";

    private static Class106 cachedRenderer;
    private static int cachedRevision = Integer.MIN_VALUE;
    private static Model[] cachedModels = new Model[0];

    private ConveyorRunPreview() {
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

        runs = new ConveyorRun[] {
                eastWestRun("SHORT", worldX, worldY + 2, plane, 2),
                eastWestRun("MEDIUM", worldX, worldY + 4, plane, 5),
                eastWestRun("LONG", worldX, worldY + 6, plane, 9)
        };
        active = true;
        revision++;
        invalidateModels();
        status = "READY ConveyorRun V0: short=2t medium=5t long=9t; "
                + "source=46298 models=49717/49718";
        return status;
    }

    public static String hide() {
        active = false;
        runs = new ConveyorRun[0];
        revision++;
        invalidateModels();
        lastRenderedCycle = Integer.MIN_VALUE;
        status = "HIDDEN";
        return status;
    }

    public static String getStatus() {
        return status + " | " + roleSummary;
    }

    static void render(Class523 scene, Class106 renderer) {
        ConveyorRun[] current = runs;
        if (!active || current.length == 0 || scene == null || renderer == null) {
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
        for (int i = 0; i < current.length; i++) {
            Model model = i < cachedModels.length ? cachedModels[i] : null;
            if (model != null && renderOne(current[i], model, scene, renderer, sceneBase)) {
                rendered++;
            } else {
                failed++;
            }
        }

        status = "DRAW ConveyorRun V0 " + rendered + "/" + current.length
                + (failed == 0 ? "" : " failed=" + failed);
    }

    private static ConveyorRun eastWestRun(String name, int centerWorldX,
            int worldY, int plane, int lengthTiles) {
        int left = lengthTiles / 2;
        int startX = centerWorldX - left;
        int endX = startX + lengthTiles;
        return new ConveyorRun(name, startX, worldY, endX, worldY, plane);
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
        roleSummary = summary;
        System.out.println("[ConveyorRunPreview] " + summary);
    }

    private static Generation generateRaw(ObjectDefinitions definition, ConveyorRun run) {
        Class159 pristine = decodeSource(definition);
        Class159 working = decodeSource(definition);
        if (pristine == null || working == null
                || pristine.anInt1791 <= 0 || pristine.anInt1778 <= 0) {
            status = "SOURCE decode failed for 49717/49718";
            return null;
        }

        Bounds all = bounds(pristine);
        Component[] components = detectComponents(pristine);
        populateComponentBounds(pristine, components);
        if (all == null || components.length == 0) {
            status = "SOURCE connected-component analysis failed";
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
        ArrayList<Component> support = new ArrayList<Component>();
        ArrayList<Component> positioned = new ArrayList<Component>();

        double fullHeight = Math.max(1.0, all.sizeY);
        for (Component component : components) {
            double longSize = axisX ? component.sizeX : component.sizeZ;
            double longFraction = longSize / sourceLength;
            if (longFraction >= STRETCH_MIN_LONG_FRACTION) {
                stretch.add(component);
                continue;
            }

            boolean supportCandidate = component.sizeY >= fullHeight * SUPPORT_MIN_HEIGHT_FRACTION
                    && longFraction <= SUPPORT_MAX_LONG_FRACTION;
            if (supportCandidate) {
                support.add(component);
            } else {
                positioned.add(component);
            }
        }

        boolean supportFallback = false;
        if (support.isEmpty() && !positioned.isEmpty()) {
            Component best = positioned.get(0);
            for (Component candidate : positioned) {
                if (candidate.sizeY > best.sizeY) best = candidate;
            }
            positioned.remove(best);
            support.add(best);
            supportFallback = true;
        }

        ensureFaceAlpha(working);
        for (Component component : stretch) {
            scaleComponentAxis(working, component, axisX, sourceCenter, stretchFactor);
        }
        for (Component component : support) {
            hideFaces(working, component);
        }
        for (Component component : positioned) {
            remapComponentPosition(working, component, axisX,
                    sourceMin, sourceLength, sourceCenter, targetLength);
        }

        ArrayList<Class159> generated = new ArrayList<Class159>();
        generated.add(working);

        int interiorSupports = Math.max(0,
                (int) Math.ceil(run.lengthTiles() / MAX_SUPPORT_SPAN_TILES) - 1);
        int supportStations = support.isEmpty() ? 0 : interiorSupports + 2;
        if (!support.isEmpty()) {
            double groupCenter = 0.0;
            for (Component component : support) {
                groupCenter += axisX ? component.centerX : component.centerZ;
            }
            groupCenter /= support.size();

            for (int station = 0; station < supportStations; station++) {
                double fraction = supportStations == 1
                        ? 0.5 : station / (double) (supportStations - 1);
                double targetCenter = sourceCenter + (fraction - 0.5) * targetLength;
                int shift = (int) Math.round(targetCenter - groupCenter);
                for (Component component : support) {
                    Class159 copy = componentOnlyTranslatedRaw(
                            pristine, component, axisX, shift);
                    if (copy != null) generated.add(copy);
                }
            }
        }

        Class159 raw;
        if (generated.size() == 1) {
            raw = generated.get(0);
        } else {
            Class159[] raws = generated.toArray(new Class159[generated.size()]);
            raw = new Class159(raws, raws.length);
        }

        String summary = "HYPOTHESIS axis=" + (axisX ? "X" : "Z")
                + " components=" + components.length
                + " stretch=" + stretch.size()
                + " supportParts=" + support.size()
                + " fixed=" + positioned.size()
                + " supportStations(short/med/long vary)"
                + (supportFallback ? " supportFallback=YES" : "")
                + " sourceSpan=" + Math.round(sourceLength);
        return new Generation(raw, axisX, summary);
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

    private static boolean renderOne(ConveyorRun run, Model model,
            Class523 scene, Class106 renderer, Class497 sceneBase) {
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

            // V0 intentionally strips support-copy texture mappings. The primary
            // stretched source keeps its original texture data; UV-repeat work is
            // a later, separately verified renderer slice.
            raw.faceTextures[i] = -1;
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
        final String name;
        final int startX;
        final int startY;
        final int endX;
        final int endY;
        final int plane;

        ConveyorRun(String name, int startX, int startY,
                int endX, int endY, int plane) {
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
