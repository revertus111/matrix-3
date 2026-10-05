package game;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native upper-body proof controller and Matrix-owned 830 weapon presentation. */
public final class MarioWeaponCombat {
    private static final int FLAGS = 2048 | 0x0f;
    private static final Class261 TRANSFORM = new Class261();
    private static final Class90 BOUNDS = new Class90();
    private static final AtomicBoolean PLAY = new AtomicBoolean();
    private static volatile boolean enabled = true;
    private static volatile boolean forceSlash;
    // scale, hand-local XYZ, yaw/pitch/roll degrees. Immutable published snapshot.
    private static volatile float[] calibration = {1, 0, 0, 0, 0, 0, 0};
    private static volatile String status = "Enter Mario mode with a sword; native v3 required";
    private static Appearance appearance;
    private static Class106 cachedRenderer;
    private static Appearance cachedAppearance;
    private static Model cachedModel;
    private static float fitScale;
    private static long lastRenderNanos;
    private static long retryAfterNanos;
    private static int request;

    private MarioWeaponCombat() { }

    public static void setEnabled(boolean value) { enabled = value; }
    public static void setForceSlash(boolean value) { forceSlash = value; }
    public static void playSlash() { PLAY.set(true); }
    public static String getStatus() { return status; }
    public static synchronized void setCalibration(int index, float value) {
        if (index < 0 || index >= 7 || Float.isNaN(value) || Float.isInfinite(value)) return;
        float[] next = calibration.clone();
        next[index] = index == 0 ? Math.max(.05F, Math.min(5, value))
                : Math.max(-720, Math.min(720, value));
        calibration = next;
    }

    static int getRequest() { return request; }

    static void reset() {
        PLAY.set(false);
        appearance = cachedAppearance = null;
        cachedRenderer = null;
        cachedModel = null;
        lastRenderNanos = retryAfterNanos = 0;
        request = 0;
        status = "Mario inactive / weapon cleared";
    }

    /** Client-thread intent only. Existing combat bridge retains all damage authority. */
    static boolean updateInput(boolean attackEdge) {
        boolean preview = PLAY.getAndSet(false);
        try {
            appearance = enabled ? findWeapon(Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976) : null;
        } catch (RuntimeException unavailable) {
            appearance = null;
        }
        Sm64BridgeSession.GeometryFrame frame = Sm64BridgeSession.getLatestGeometryFrame();
        boolean active = appearance != null && Sm64BridgeSession.getBinaryProtocolVersion() >= 3
                && frame != null && frame.rightHand != null && cachedModel != null
                && sameAppearance(appearance, cachedAppearance)
                && System.nanoTime() - lastRenderNanos < 250000000L
                && !Sm64BridgeSession.isPresentationFrozen();
        if (active && (attackEdge || preview)) request++;
        if (!active) status = !enabled ? "Custom combat OFF"
                : appearance == null ? "No supported sword (or use explicit equipped-weapon override)"
                : Sm64BridgeSession.getBinaryProtocolVersion() < 3 ? "Native v3 required: make bootstrap"
                : Sm64BridgeSession.isPresentationFrozen() ? "Presentation frozen; unfreeze to attack"
                : "Waiting for hand socket / weapon model";
        return active;
    }

    /** Same appearance/item customization path as MarioEquipmentAdapter's helmet. */
    private static Appearance findWeapon(Player player) {
        if (player == null || player.aClass474_11831 == null) return null;
        Class474 a = player.aClass474_11831;
        if (a.anIntArray5317 == null) return null;
        for (int i = 0; i < a.anIntArray5317.length; i++) {
            int encoded = a.anIntArray5317[i];
            if ((encoded & 0x40000000) == 0) continue;
            int id = encoded & 0x3fffffff;
            ItemDefinitions def = (ItemDefinitions) Class672.aClass639_Sub5_8533.getDefinition(id, -1597715602);
            if (def == null || def.equipSlot * -917104297 != 3) continue;
            String name = def.aString8180 == null ? "" : def.aString8180.toLowerCase(Locale.ROOT);
            // Conservative V1 name policy, NOT a claimed cache combat-family mapping.
            boolean sword = (name.contains("longsword") || name.contains("scimitar") || name.endsWith(" sword"))
                    && !name.contains("2h") && !name.contains("two-handed")
                    && !name.contains("godsword") && !name.contains("off-hand");
            if (!forceSlash && !sword) return null;
            Class634 custom = a.aClass634Array5318 != null && i < a.aClass634Array5318.length
                    ? a.aClass634Array5318[i] : null;
            return new Appearance(id, def, custom, a.aBool5314);
        }
        return null;
    }

    static void render(Class106 renderer, Player player, Sm64BridgeSession.GeometryFrame frame, float modelScale) {
        Appearance weapon = appearance;
        if (!enabled || weapon == null || frame.rightHand == null || frame.protocolVersion < 3) return;
        try {
            if (!ensureModel(renderer, weapon)) return;
            Class238 transform = player.method5394();
            if (transform == null || transform.aClass240_2647 == null) return;
            float[] m = frame.rightHand;
            float[] c = calibration;
            float[] r = calibratedBasis(m, c[4], c[5], c[6]);
            float size = fitScale * c[0] * modelScale;
            // Class261.method3572 accepts columns (verified-static via method3582).
            TRANSFORM.method3572(r[0], r[3], r[6], r[1], r[4], r[7], r[2], r[5], r[8]);
            TRANSFORM.method3578(size, size, size);
            Class240 p = transform.aClass240_2647;
            // Same S*(native position - native Mario origin)*modelScale as body.
            float x = m[12] + m[0]*c[1] + m[4]*c[2] + m[8]*c[3];
            float y = m[13] + m[1]*c[1] + m[5]*c[2] + m[9]*c[3];
            float z = m[14] + m[2]*c[1] + m[6]*c[2] + m[10]*c[3];
            TRANSFORM.method3580(p.aFloat2653 + (x-frame.state.x)*modelScale,
                    p.aFloat2656 - (y-frame.state.y)*modelScale,
                    p.aFloat2657 + (z-frame.state.z)*modelScale);
            cachedModel.method1375(TRANSFORM, BOUNDS, 0);
            lastRenderNanos = System.nanoTime();
            status = String.format(Locale.ROOT, "%s | t %.2f | weight %.2f | RIGHT_HAND AVAILABLE | %d %s",
                    frame.combatAnimation == 1 ? "1H_SLASH" : "NONE", frame.combatTime,
                    frame.combatWeight, weapon.id, weapon.definition.aString8180);
        } catch (RuntimeException ex) {
            cachedModel = null;
            lastRenderNanos = 0;
            retryAfterNanos = System.nanoTime() + 1000000000L;
            status = "Weapon unavailable: " + ex.getClass().getSimpleName();
            // Fail open to native combat; weapon failures must never hide Mario.
        }
    }

    private static boolean ensureModel(Class106 renderer, Appearance weapon) {
        if (renderer == cachedRenderer && sameAppearance(weapon, cachedAppearance)) {
            if (cachedModel != null) return true;
            if (System.nanoTime() < retryAfterNanos) return false;
        }
        cachedRenderer = renderer;
        cachedAppearance = weapon;
        cachedModel = null;
        retryAfterNanos = System.nanoTime() + 1000000000L;
        Class159 raw = weapon.definition.method7531(weapon.female, weapon.customization, (byte)114);
        if (raw == null || raw.anInt1791 <= 0 || raw.anIntArray1782 == null
                || raw.anIntArray1777 == null || raw.anIntArray1797 == null) return false;
        int minX=Integer.MAX_VALUE, minY=minX, minZ=minX;
        int maxX=Integer.MIN_VALUE, maxY=maxX, maxZ=maxX;
        for(int i=0;i<raw.anInt1791;i++) {
            minX=Math.min(minX,raw.anIntArray1782[i]); maxX=Math.max(maxX,raw.anIntArray1782[i]);
            minY=Math.min(minY,raw.anIntArray1777[i]); maxY=Math.max(maxY,raw.anIntArray1777[i]);
            minZ=Math.min(minZ,raw.anIntArray1797[i]); maxZ=Math.max(maxZ,raw.anIntArray1797[i]);
        }
        float span=Math.max(maxY-minY,Math.max(maxX-minX,maxZ-minZ));
        if (span <= 0) return false;
        // HYPOTHESIS / calibration seed: hilt near lower end of a vertical worn sword.
        // Real item meshes have no shared grip metadata. XYZ/angles are adjustable.
        raw.method2564(-Math.round((minX+maxX)*.5F),
                -Math.round(minY+(maxY-minY)*.85F),-Math.round((minZ+maxZ)*.5F));
        fitScale=320.0F/span; // local native length; Mario geo root applies 0.25.
        cachedModel=renderer.method1755(raw, FLAGS | 0x1f01f | 0x80000, 0, 64, 850);
        if(cachedModel == null) return false;
        cachedModel.method1450(FLAGS);
        return true;
    }

    /* Column-vector convention: S * hand * (Rz * Ry * Rx) * S, S=(1,-1,1).
     * Both Y mirrors preserve orientation/winding. Hand basis includes root scale.
     */
    static float[] calibratedBasis(float[] m, float yaw, float pitch, float roll) {
        double x=Math.toRadians(pitch), y=Math.toRadians(yaw), z=Math.toRadians(roll);
        float sx=(float)Math.sin(x),cx=(float)Math.cos(x),sy=(float)Math.sin(y),cy=(float)Math.cos(y);
        float sz=(float)Math.sin(z),cz=(float)Math.cos(z);
        float[] c={cy*cz,sx*sy*cz-cx*sz,cx*sy*cz+sx*sz,
                cy*sz,sx*sy*sz+cx*cz,cx*sy*sz-sx*cz,-sy,sx*cy,cx*cy};
        float[] result=new float[9];
        for(int row=0;row<3;row++) for(int col=0;col<3;col++) {
            float v=0;
            for(int k=0;k<3;k++) v+=m[k*4+row]*c[k*3+col];
            result[row*3+col]=v*(row==1?-1:1)*(col==1?-1:1);
        }
        return result;
    }

    private static boolean sameAppearance(Appearance a, Appearance b) {
        return a != null && b != null && a.id==b.id && a.female==b.female && a.customization==b.customization;
    }

    private static final class Appearance {
        final int id;
        final ItemDefinitions definition;
        final Class634 customization;
        final boolean female;
        Appearance(int id, ItemDefinitions definition, Class634 customization, boolean female) {
            this.id=id; this.definition=definition; this.customization=customization; this.female=female;
        }
    }
}
