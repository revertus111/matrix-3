package game;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Developer-only runtime facade for the N64 Mario equipment workbench.
 *
 * Equipment/render ownership remains in the established Mario owners. This class
 * exposes session calibration, presentation freeze and semantic equipment coverage.
 * Protocol-v2 semantic coverage is preferred; the old geometric cutter is kept
 * only as an explicit legacy-v1 debug fallback.
 */
public final class MarioEquipmentWorkbench {

    public static final int COVERAGE_KEEP_ALL = 0;
    public static final int COVERAGE_FULL_HELM_SAFE = 1;
    public static final int COVERAGE_HEAD_SHELL_CLOSED = 2;
    public static final int COVERAGE_HEAD_SHELL_FACE = 3;

    private static final int EQUIPMENT_SLOT_HAT = 0;
    private static final int EQUIP_SLOT_DECODE_MULTIPLIER = -917104297;
    private static final float DEFAULT_FACE_FRONT_FRACTION = 0.55F;

    private static volatile int coverageProfile = COVERAGE_KEEP_ALL;
    private static volatile boolean coverageOnlyWithHelmet = true;
    private static volatile float faceFrontFraction = DEFAULT_FACE_FRONT_FRACTION;

    /* Legacy protocol-v1 debug fallback. Never used when semantic metadata exists. */
    private static volatile boolean headMaskEnabled;
    private static volatile boolean maskOnlyWithHelmet = true;
    private static volatile float maskStartFraction = 0.72F;
    private static volatile float maskRadiusFraction = 0.40F;
    private static volatile long maskRevision;

    private static volatile long preparedMaskSequence = Long.MIN_VALUE;
    private static volatile long preparedMaskRevision = Long.MIN_VALUE;
    private static volatile boolean preparedHelmetPresent;
    private static volatile boolean preparedSemanticAvailable;
    private static volatile float preparedFaceCutLocalY;
    private static volatile boolean preparedFaceCutValid;
    private static volatile float maskCenterX;
    private static volatile float maskCenterZ;
    private static volatile float maskStartY;
    private static volatile float maskRadiusSquared;
    private static volatile int lastMaskedTriangles;
    private static volatile int lastFaceInsertTriangles;
    private static volatile int lastFaceTriangles;
    private static volatile int lastEyesTriangles;
    private static volatile int lastMustacheTriangles;
    private static volatile int lastCapTriangles;
    private static volatile int lastSideburnTriangles;
    private static volatile int lastBackHairTriangles;
    private static volatile int lastUnknownTriangles;

    private MarioEquipmentWorkbench() {
    }

    public static Snapshot getSnapshot() {
        HelmetInfo helmet = findVisibleHelmet();
        int itemId = helmet == null ? -1 : helmet.itemId;
        MarioHelmetCalibrationController.Snapshot calibration =
                itemId < 0
                        ? MarioHelmetCalibrationController.Snapshot.DEFAULT
                        : MarioHelmetCalibrationController.getSnapshot(itemId);

        Sm64BridgeSession.GeometryFrame frame = Sm64BridgeSession.getLatestGeometryFrame();
        float faceAngle = frame == null || frame.state == null
                ? Float.NaN : frame.state.faceAngle;
        boolean semanticAvailable = MarioSemanticGeometry.isAvailable(frame);
        MarioSemanticGeometry.Reference reference = MarioSemanticGeometry.getReference(frame);

        return new Snapshot(
                PlayerControllerMode.isMarioMode(),
                itemId,
                helmet == null ? "-" : helmet.name,
                calibration.scaleMultiplier,
                calibration.offsetX,
                calibration.offsetY,
                calibration.offsetZ,
                calibration.yawDegrees,
                Sm64BridgeSession.isPresentationFrozen(),
                MarioHeadOrientationTracker.hasReference(),
                faceAngle,
                Sm64BridgeSession.getBinaryProtocolVersion(),
                semanticAvailable,
                coverageProfile,
                coverageName(coverageProfile),
                coverageOnlyWithHelmet,
                faceFrontFraction * 100.0F,
                lastFaceInsertTriangles,
                reference == null ? Float.NaN : reference.width,
                reference == null ? Float.NaN : reference.height,
                reference == null ? Float.NaN : reference.depth,
                headMaskEnabled,
                maskOnlyWithHelmet,
                maskStartFraction * 100.0F,
                maskRadiusFraction * 100.0F,
                lastMaskedTriangles,
                lastFaceTriangles,
                lastEyesTriangles,
                lastMustacheTriangles,
                lastCapTriangles,
                lastSideburnTriangles,
                lastBackHairTriangles,
                lastUnknownTriangles);
    }

    public static void setScale(float value) {
        updateCalibration(value, null, null, null, null);
    }

    public static void setOffsetX(float value) {
        updateCalibration(null, value, null, null, null);
    }

    public static void setOffsetY(float value) {
        updateCalibration(null, null, value, null, null);
    }

    public static void setOffsetZ(float value) {
        updateCalibration(null, null, null, value, null);
    }

    public static void setYawDegrees(float value) {
        updateCalibration(null, null, null, null, value);
    }

    public static void flipHelmetYaw180() {
        Snapshot snapshot = getSnapshot();
        if (snapshot.itemId >= 0) {
            setYawDegrees(normalizeDegrees(snapshot.yawDegrees + 180.0F));
        }
    }

    public static void resetHelmetTransform() {
        Snapshot snapshot = getSnapshot();
        if (snapshot.itemId >= 0) {
            MarioHelmetCalibrationController.resetItem(snapshot.itemId);
        }
    }

    public static void setPresentationFrozen(boolean frozen) {
        Sm64BridgeSession.setPresentationFrozen(frozen);
    }

    public static void useKeepAllCoverage() {
        setCoverageProfile(COVERAGE_KEEP_ALL);
    }

    public static void useFullHelmSafeCoverage() {
        setCoverageProfile(COVERAGE_FULL_HELM_SAFE);
    }

    public static void useHeadShellClosedCoverage() {
        setCoverageProfile(COVERAGE_HEAD_SHELL_CLOSED);
    }

    public static void useHeadShellFaceCoverage() {
        setCoverageProfile(COVERAGE_HEAD_SHELL_FACE);
    }

    public static boolean isReplacementShellCoverage() {
        return coverageProfile == COVERAGE_HEAD_SHELL_CLOSED
                || coverageProfile == COVERAGE_HEAD_SHELL_FACE;
    }

    public static void setCoverageProfile(int profile) {
        int next;
        switch (profile) {
            case COVERAGE_FULL_HELM_SAFE:
            case COVERAGE_HEAD_SHELL_CLOSED:
            case COVERAGE_HEAD_SHELL_FACE:
                next = profile;
                break;
            default:
                next = COVERAGE_KEEP_ALL;
                break;
        }
        if (coverageProfile != next) {
            boolean enteringReplacementShell = isReplacementShell(next)
                    && !isReplacementShell(coverageProfile);
            coverageProfile = next;
            maskRevision++;
            if (enteringReplacementShell) {
                HelmetInfo helmet = findVisibleHelmet();
                if (helmet != null) {
                    /* Re-resolve auto-fit without the old cavity-containment multiplier. */
                    MarioHelmetCalibrationController.resetItem(helmet.itemId);
                }
            }
        }
    }

    public static void setCoverageOnlyWithHelmet(boolean enabled) {
        if (coverageOnlyWithHelmet != enabled) {
            coverageOnlyWithHelmet = enabled;
            maskRevision++;
        }
    }

    public static void setFaceFrontPercent(float percent) {
        float next = clamp(percent / 100.0F, 0.25F, 0.90F);
        if (Math.abs(next - faceFrontFraction) > 0.0001F) {
            faceFrontFraction = next;
            maskRevision++;
        }
    }

    public static void resetFaceInsert() {
        if (Math.abs(faceFrontFraction - DEFAULT_FACE_FRONT_FRACTION) > 0.0001F) {
            faceFrontFraction = DEFAULT_FACE_FRONT_FRACTION;
            maskRevision++;
        }
    }

    /** Legacy protocol-v1 geometric cutter. Ignored whenever semantic v2 exists. */
    public static void setHeadMaskEnabled(boolean enabled) {
        if (headMaskEnabled != enabled) {
            headMaskEnabled = enabled;
            maskRevision++;
        }
    }

    public static void setMaskOnlyWithHelmet(boolean enabled) {
        if (maskOnlyWithHelmet != enabled) {
            maskOnlyWithHelmet = enabled;
            maskRevision++;
        }
    }

    public static void setMaskStartPercent(float percent) {
        float next = clamp(percent / 100.0F, 0.50F, 0.95F);
        if (Math.abs(next - maskStartFraction) > 0.0001F) {
            maskStartFraction = next;
            maskRevision++;
        }
    }

    public static void setMaskRadiusPercent(float percent) {
        float next = clamp(percent / 100.0F, 0.10F, 0.75F);
        if (Math.abs(next - maskRadiusFraction) > 0.0001F) {
            maskRadiusFraction = next;
            maskRevision++;
        }
    }

    public static void resetHeadMask() {
        coverageProfile = COVERAGE_KEEP_ALL;
        coverageOnlyWithHelmet = true;
        faceFrontFraction = DEFAULT_FACE_FRONT_FRACTION;
        headMaskEnabled = false;
        maskOnlyWithHelmet = true;
        maskStartFraction = 0.72F;
        maskRadiusFraction = 0.40F;
        lastMaskedTriangles = 0;
        lastFaceInsertTriangles = 0;
        maskRevision++;
    }

    public static String formatProfileMarkdown() {
        Snapshot value = getSnapshot();
        StringBuilder out = new StringBuilder(1792);
        out.append("# Mario Equipment Runtime Profile\n\n");
        out.append("Generated by the Matrix3 N64 Equipment Workbench.\n\n");
        out.append("## Active helmet\n\n");
        out.append("- Item: ").append(value.itemId < 0 ? "none" : value.itemId).append('\n');
        out.append("- Name: ").append(value.itemName).append('\n');
        out.append("- Scale multiplier: ").append(format(value.scale)).append('\n');
        out.append("- Head-local X: ").append(format(value.x)).append('\n');
        out.append("- Head-local Y: ").append(format(value.y)).append('\n');
        out.append("- Head-local Z: ").append(format(value.z)).append('\n');
        out.append("- Yaw delta degrees: ").append(format(value.yawDegrees)).append('\n');
        out.append("- Presentation frozen: ").append(value.frozen).append('\n');
        out.append("- 3D head basis available: ").append(value.head3d).append('\n');
        out.append("- Native faceAngle radians: ").append(format(value.faceAngle)).append("\n\n");

        out.append("## Semantic head replacement / coverage\n\n");
        out.append("- Bridge protocol: v").append(value.protocolVersion).append('\n');
        out.append("- Semantic metadata: ").append(value.semanticAvailable ? "AVAILABLE" : "UNAVAILABLE").append('\n');
        out.append("- Coverage profile: ").append(value.coverageName).append('\n');
        out.append("- Coverage only with helmet: ").append(value.coverageOnlyWithHelmet).append('\n');
        out.append("- Mario face front slice: ").append(format(value.faceFrontPercent)).append("%\n");
        out.append("- Last face-insert source triangles: ").append(value.faceInsertTriangles).append('\n');
        out.append("- Shared FACE reference W/H/D: ")
                .append(format(value.semanticWidth)).append(" / ")
                .append(format(value.semanticHeight)).append(" / ")
                .append(format(value.semanticDepth)).append('\n');
        out.append("- FULL_HELM_SAFE hides: CAP, HAIR_SIDEBURN, HAIR_BACK\n");
        out.append("- HEAD_SHELL_CLOSED hides every known semantic head part.\n");
        out.append("- HEAD_SHELL_FACE keeps EYES/MOUSTACHE plus the front slice of FACE; the rest of the known head becomes the equipment shell.\n");
        out.append("- Last masked source triangles: ").append(value.maskedTriangles).append("\n\n");

        out.append("## Native part counts\n\n");
        out.append("- FACE: ").append(value.faceTriangles).append('\n');
        out.append("- EYES: ").append(value.eyesTriangles).append('\n');
        out.append("- MOUSTACHE: ").append(value.mustacheTriangles).append('\n');
        out.append("- CAP: ").append(value.capTriangles).append('\n');
        out.append("- HAIR_SIDEBURN: ").append(value.sideburnTriangles).append('\n');
        out.append("- HAIR_BACK: ").append(value.backHairTriangles).append('\n');
        out.append("- UNKNOWN/other: ").append(value.unknownTriangles).append("\n\n");

        out.append("## Legacy geometric fallback\n\n");
        out.append("- Enabled: ").append(value.maskEnabled).append('\n');
        out.append("- Only with helmet: ").append(value.maskOnlyWithHelmet).append('\n');
        out.append("- Start height percent: ").append(format(value.maskStartPercent)).append('\n');
        out.append("- Radius percent: ").append(format(value.maskRadiusPercent)).append('\n');
        out.append("- Note: ignored while protocol-v2 semantic metadata is available.\n\n");

        out.append("## Transform convention\n\n");
        out.append("Authoritative convention notes: `docs/n64/TRANSFORM_CONVENTIONS.md`.\n");
        return out.toString();
    }

    public static String saveProfileMarkdown() {
        File file = resolveProfileFile();
        Writer writer = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return "Could not create " + parent.getPath();
            }
            writer = new OutputStreamWriter(
                    new FileOutputStream(file, false), StandardCharsets.UTF_8);
            writer.write(formatProfileMarkdown());
            writer.flush();
            return file.getAbsolutePath();
        } catch (Exception ex) {
            return "Save failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                    // Developer save cleanup only.
                }
            }
        }
    }

    /** Called once before a Mario model build so per-triangle masking stays cheap. */
    static void prepareMaskFrame(Sm64BridgeSession.GeometryFrame frame) {
        if (frame == null || frame.state == null || frame.positions == null
                || frame.triangleCount <= 0
                || frame.positions.length < frame.triangleCount * 9) {
            clearPreparedMask();
            return;
        }

        HelmetInfo helmet = findVisibleHelmet();
        boolean helmetPresent = helmet != null;
        boolean semanticAvailable = MarioSemanticGeometry.isAvailable(frame);
        long revision = effectiveMaskRevision(helmetPresent, semanticAvailable);
        if (preparedMaskSequence == frame.sequence && preparedMaskRevision == revision) {
            return;
        }

        preparedHelmetPresent = helmetPresent;
        preparedSemanticAvailable = semanticAvailable;
        preparedMaskSequence = frame.sequence;
        preparedMaskRevision = revision;
        preparedFaceCutValid = false;
        lastMaskedTriangles = 0;
        lastFaceInsertTriangles = 0;
        countSemanticParts(frame);

        if (semanticAvailable) {
            if (coverageProfile == COVERAGE_HEAD_SHELL_FACE
                    && (!coverageOnlyWithHelmet || helmetPresent)) {
                prepareFaceInsert(frame);
            }
            return;
        }

        if (!headMaskEnabled) {
            return;
        }

        int totalVertices = frame.triangleCount * 3;
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < totalVertices; vertex++) {
            int base = vertex * 3;
            float x = frame.positions[base] - frame.state.x;
            float y = frame.positions[base + 1] - frame.state.y;
            float z = frame.positions[base + 2] - frame.state.z;
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        float height = maxY - minY;
        float width = maxX - minX;
        float depth = maxZ - minZ;
        if (!finite(height) || height <= 0.0F) {
            clearPreparedMask();
            return;
        }

        maskCenterX = (minX + maxX) * 0.5F;
        maskCenterZ = (minZ + maxZ) * 0.5F;
        maskStartY = minY + height * maskStartFraction;
        float radius = Math.max(width, depth) * maskRadiusFraction;
        maskRadiusSquared = radius * radius;
    }

    private static void prepareFaceInsert(Sm64BridgeSession.GeometryFrame frame) {
        if (frame.localPositions == null
                || frame.localPositions.length < frame.triangleCount * 9) {
            return;
        }
        float minFaceY = Float.POSITIVE_INFINITY;
        float maxFaceY = Float.NEGATIVE_INFINITY;
        for (int triangle = 0; triangle < frame.triangleCount; triangle++) {
            if ((frame.partIds[triangle] & 0xff) != MarioSemanticGeometry.PART_FACE) {
                continue;
            }
            int base = triangle * 9;
            for (int vertex = 0; vertex < 3; vertex++) {
                float y = frame.localPositions[base + vertex * 3 + 1];
                if (y < minFaceY) minFaceY = y;
                if (y > maxFaceY) maxFaceY = y;
            }
        }
        float depth = maxFaceY - minFaceY;
        if (!finite(depth) || depth <= 0.0F) {
            return;
        }
        preparedFaceCutLocalY = minFaceY + depth * faceFrontFraction;
        preparedFaceCutValid = true;

        int kept = 0;
        for (int triangle = 0; triangle < frame.triangleCount; triangle++) {
            if (isFaceInsertTriangle(frame, triangle)) {
                kept++;
            }
        }
        lastFaceInsertTriangles = kept;
    }

    private static boolean isFaceInsertTriangle(
            Sm64BridgeSession.GeometryFrame frame,
            int sourceTriangle) {
        if (!preparedFaceCutValid || frame == null || frame.partIds == null
                || sourceTriangle < 0 || sourceTriangle >= frame.triangleCount) {
            return false;
        }
        int partId = frame.partIds[sourceTriangle] & 0xff;
        if (partId == MarioSemanticGeometry.PART_EYES
                || partId == MarioSemanticGeometry.PART_MUSTACHE) {
            return true;
        }
        if (partId != MarioSemanticGeometry.PART_FACE
                || frame.localPositions == null
                || frame.localPositions.length < (sourceTriangle + 1) * 9) {
            return false;
        }
        int base = sourceTriangle * 9;
        float localY = (frame.localPositions[base + 1]
                + frame.localPositions[base + 4]
                + frame.localPositions[base + 7]) / 3.0F;
        return finite(localY) && localY >= preparedFaceCutLocalY;
    }

    static boolean shouldMaskTriangle(
            Sm64BridgeSession.GeometryFrame frame,
            int sourceTriangle) {
        if (frame == null || preparedMaskSequence != frame.sequence
                || sourceTriangle < 0 || sourceTriangle >= frame.triangleCount) {
            return false;
        }

        if (preparedSemanticAvailable) {
            if (coverageProfile == COVERAGE_KEEP_ALL) {
                return false;
            }
            if (coverageOnlyWithHelmet && !preparedHelmetPresent) {
                return false;
            }
            int partId = frame.partIds[sourceTriangle] & 0xff;
            switch (coverageProfile) {
                case COVERAGE_FULL_HELM_SAFE:
                    if (MarioSemanticGeometry.isProtectedFacePart(partId)) {
                        return false;
                    }
                    return MarioSemanticGeometry.isFullHelmSafeHiddenPart(partId);
                case COVERAGE_HEAD_SHELL_CLOSED:
                    return isKnownHeadPart(partId);
                case COVERAGE_HEAD_SHELL_FACE:
                    if (partId == MarioSemanticGeometry.PART_FACE
                            || partId == MarioSemanticGeometry.PART_EYES
                            || partId == MarioSemanticGeometry.PART_MUSTACHE) {
                        return !isFaceInsertTriangle(frame, sourceTriangle);
                    }
                    return MarioSemanticGeometry.isFullHelmSafeHiddenPart(partId);
                default:
                    return false;
            }
        }

        if (!headMaskEnabled || (maskOnlyWithHelmet && !preparedHelmetPresent)) {
            return false;
        }
        int base = sourceTriangle * 9;
        float x = ((frame.positions[base] + frame.positions[base + 3] + frame.positions[base + 6]) / 3.0F)
                - frame.state.x;
        float y = ((frame.positions[base + 1] + frame.positions[base + 4] + frame.positions[base + 7]) / 3.0F)
                - frame.state.y;
        float z = ((frame.positions[base + 2] + frame.positions[base + 5] + frame.positions[base + 8]) / 3.0F)
                - frame.state.z;
        float dx = x - maskCenterX;
        float dz = z - maskCenterZ;
        return y >= maskStartY && dx * dx + dz * dz <= maskRadiusSquared;
    }

    static void recordMaskedTriangleCount(int count) {
        lastMaskedTriangles = Math.max(0, count);
    }

    static long getMaskRevision() {
        Sm64BridgeSession.GeometryFrame frame = Sm64BridgeSession.getLatestGeometryFrame();
        boolean semanticAvailable = MarioSemanticGeometry.isAvailable(frame);
        return effectiveMaskRevision(findVisibleHelmet() != null, semanticAvailable);
    }

    private static long effectiveMaskRevision(boolean helmetPresent, boolean semanticAvailable) {
        long revision = maskRevision << 4;
        revision ^= ((long) coverageProfile & 0x3L) << 2;
        if (semanticAvailable) revision |= 0x2L;
        if ((semanticAvailable && coverageOnlyWithHelmet && helmetPresent)
                || (!semanticAvailable && headMaskEnabled && maskOnlyWithHelmet && helmetPresent)) {
            revision |= 0x1L;
        }
        return revision;
    }

    private static void countSemanticParts(Sm64BridgeSession.GeometryFrame frame) {
        lastFaceTriangles = 0;
        lastEyesTriangles = 0;
        lastMustacheTriangles = 0;
        lastCapTriangles = 0;
        lastSideburnTriangles = 0;
        lastBackHairTriangles = 0;
        lastUnknownTriangles = 0;
        if (!MarioSemanticGeometry.isAvailable(frame)) {
            return;
        }
        for (int i = 0; i < frame.triangleCount; i++) {
            switch (frame.partIds[i] & 0xff) {
                case MarioSemanticGeometry.PART_FACE:
                    lastFaceTriangles++;
                    break;
                case MarioSemanticGeometry.PART_EYES:
                    lastEyesTriangles++;
                    break;
                case MarioSemanticGeometry.PART_MUSTACHE:
                    lastMustacheTriangles++;
                    break;
                case MarioSemanticGeometry.PART_CAP:
                    lastCapTriangles++;
                    break;
                case MarioSemanticGeometry.PART_HAIR_SIDEBURN:
                    lastSideburnTriangles++;
                    break;
                case MarioSemanticGeometry.PART_HAIR_BACK:
                    lastBackHairTriangles++;
                    break;
                default:
                    lastUnknownTriangles++;
                    break;
            }
        }
    }

    private static boolean isKnownHeadPart(int partId) {
        return partId >= MarioSemanticGeometry.PART_FACE
                && partId <= MarioSemanticGeometry.PART_HAIR_BACK;
    }

    private static boolean isReplacementShell(int profile) {
        return profile == COVERAGE_HEAD_SHELL_CLOSED
                || profile == COVERAGE_HEAD_SHELL_FACE;
    }

    private static void clearPreparedMask() {
        preparedMaskSequence = Long.MIN_VALUE;
        preparedMaskRevision = Long.MIN_VALUE;
        preparedHelmetPresent = false;
        preparedSemanticAvailable = false;
        preparedFaceCutValid = false;
        lastMaskedTriangles = 0;
        lastFaceInsertTriangles = 0;
        lastFaceTriangles = 0;
        lastEyesTriangles = 0;
        lastMustacheTriangles = 0;
        lastCapTriangles = 0;
        lastSideburnTriangles = 0;
        lastBackHairTriangles = 0;
        lastUnknownTriangles = 0;
    }

    private static void updateCalibration(
            Float scale,
            Float x,
            Float y,
            Float z,
            Float yaw) {
        Snapshot workbench = getSnapshot();
        if (workbench.itemId < 0) {
            return;
        }
        MarioHelmetCalibrationController.Snapshot current =
                MarioHelmetCalibrationController.getSnapshot(workbench.itemId);
        MarioHelmetCalibrationController.setSnapshot(
                workbench.itemId,
                new MarioHelmetCalibrationController.Snapshot(
                        scale == null ? current.scaleMultiplier : scale.floatValue(),
                        x == null ? current.offsetX : x.floatValue(),
                        y == null ? current.offsetY : y.floatValue(),
                        z == null ? current.offsetZ : z.floatValue(),
                        yaw == null ? current.yawDegrees : yaw.floatValue()));
    }

    private static HelmetInfo findVisibleHelmet() {
        Player player = Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976;
        if (player == null || player.aClass474_11831 == null
                || player.aClass474_11831.anIntArray5317 == null) {
            return null;
        }
        int[] parts = player.aClass474_11831.anIntArray5317;
        for (int encoded : parts) {
            if ((encoded & 0x40000000) == 0) {
                continue;
            }
            int itemId = encoded & 0x3fffffff;
            ItemDefinitions definition = (ItemDefinitions) Class672.aClass639_Sub5_8533
                    .getDefinition(itemId, -1597715602);
            if (definition != null
                    && definition.equipSlot * EQUIP_SLOT_DECODE_MULTIPLIER == EQUIPMENT_SLOT_HAT) {
                String name = definition.aString8180;
                return new HelmetInfo(itemId,
                        name == null || name.trim().isEmpty() ? "unknown" : name);
            }
        }
        return null;
    }

    private static String coverageName(int profile) {
        switch (profile) {
            case COVERAGE_FULL_HELM_SAFE:
                return "FULL_HELM_SAFE";
            case COVERAGE_HEAD_SHELL_CLOSED:
                return "HEAD_SHELL_CLOSED";
            case COVERAGE_HEAD_SHELL_FACE:
                return "HEAD_SHELL_FACE";
            default:
                return "KEEP_ALL";
        }
    }

    private static File resolveProfileFile() {
        File direct = new File("docs/n64/MARIO_EQUIPMENT_RUNTIME.md");
        File directParent = direct.getParentFile();
        if (directParent != null && directParent.isDirectory()) {
            return direct;
        }
        File parent = new File("../docs/n64/MARIO_EQUIPMENT_RUNTIME.md");
        File parentDir = parent.getParentFile();
        if (parentDir != null && parentDir.isDirectory()) {
            return parent;
        }
        return direct;
    }

    private static float normalizeDegrees(float value) {
        if (!finite(value)) {
            return 0.0F;
        }
        while (value > 180.0F) value -= 360.0F;
        while (value < -180.0F) value += 360.0F;
        return value;
    }

    private static float clamp(float value, float min, float max) {
        if (!finite(value)) {
            return min;
        }
        return value < min ? min : value > max ? max : value;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static String format(float value) {
        if (!finite(value)) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.4f", Float.valueOf(value));
    }

    private static final class HelmetInfo {
        final int itemId;
        final String name;

        HelmetInfo(int itemId, String name) {
            this.itemId = itemId;
            this.name = name;
        }
    }

    public static final class Snapshot {
        public final boolean marioMode;
        public final int itemId;
        public final String itemName;
        public final float scale;
        public final float x;
        public final float y;
        public final float z;
        public final float yawDegrees;
        public final boolean frozen;
        public final boolean head3d;
        public final float faceAngle;
        public final int protocolVersion;
        public final boolean semanticAvailable;
        public final int coverageProfile;
        public final String coverageName;
        public final boolean coverageOnlyWithHelmet;
        public final float faceFrontPercent;
        public final int faceInsertTriangles;
        public final float semanticWidth;
        public final float semanticHeight;
        public final float semanticDepth;
        public final boolean maskEnabled;
        public final boolean maskOnlyWithHelmet;
        public final float maskStartPercent;
        public final float maskRadiusPercent;
        public final int maskedTriangles;
        public final int faceTriangles;
        public final int eyesTriangles;
        public final int mustacheTriangles;
        public final int capTriangles;
        public final int sideburnTriangles;
        public final int backHairTriangles;
        public final int unknownTriangles;

        Snapshot(
                boolean marioMode,
                int itemId,
                String itemName,
                float scale,
                float x,
                float y,
                float z,
                float yawDegrees,
                boolean frozen,
                boolean head3d,
                float faceAngle,
                int protocolVersion,
                boolean semanticAvailable,
                int coverageProfile,
                String coverageName,
                boolean coverageOnlyWithHelmet,
                float faceFrontPercent,
                int faceInsertTriangles,
                float semanticWidth,
                float semanticHeight,
                float semanticDepth,
                boolean maskEnabled,
                boolean maskOnlyWithHelmet,
                float maskStartPercent,
                float maskRadiusPercent,
                int maskedTriangles,
                int faceTriangles,
                int eyesTriangles,
                int mustacheTriangles,
                int capTriangles,
                int sideburnTriangles,
                int backHairTriangles,
                int unknownTriangles) {
            this.marioMode = marioMode;
            this.itemId = itemId;
            this.itemName = itemName;
            this.scale = scale;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yawDegrees = yawDegrees;
            this.frozen = frozen;
            this.head3d = head3d;
            this.faceAngle = faceAngle;
            this.protocolVersion = protocolVersion;
            this.semanticAvailable = semanticAvailable;
            this.coverageProfile = coverageProfile;
            this.coverageName = coverageName;
            this.coverageOnlyWithHelmet = coverageOnlyWithHelmet;
            this.faceFrontPercent = faceFrontPercent;
            this.faceInsertTriangles = faceInsertTriangles;
            this.semanticWidth = semanticWidth;
            this.semanticHeight = semanticHeight;
            this.semanticDepth = semanticDepth;
            this.maskEnabled = maskEnabled;
            this.maskOnlyWithHelmet = maskOnlyWithHelmet;
            this.maskStartPercent = maskStartPercent;
            this.maskRadiusPercent = maskRadiusPercent;
            this.maskedTriangles = maskedTriangles;
            this.faceTriangles = faceTriangles;
            this.eyesTriangles = eyesTriangles;
            this.mustacheTriangles = mustacheTriangles;
            this.capTriangles = capTriangles;
            this.sideburnTriangles = sideburnTriangles;
            this.backHairTriangles = backHairTriangles;
            this.unknownTriangles = unknownTriangles;
        }
    }
}
