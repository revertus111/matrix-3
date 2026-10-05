#!/usr/bin/env python3
"""ROM-free production controller checks; requires Python 3 and a JDK (8+).

Compiles the complete shared controller and extracts the actual camera/native
input methods into dependency stubs. Decodes the published input with the pinned
native yaw/XZ equations; this is not a full client/native build or runtime proof.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "Client/src/main/java/game"


def method(source, signature):
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


def main():
    java = os.environ.get("JAVA", shutil.which("java"))
    javac = os.environ.get("JAVAC", shutil.which("javac"))
    if not java:
        raise SystemExit("Set JAVA to the JDK java executable or add it to PATH.")
    shared = (SRC / "AlternateCharacterController.java").read_text()
    camera = (SRC / "ConstructionBuildCamera.java").read_text()
    mario = (SRC / "MarioJumpController.java").read_text()
    link = (SRC / "LinkController.java").read_text()
    adapters = """
class ConstructionBuildCamera {
    static boolean active = true;
    static boolean isRequested() { return active; }
""" + method(camera, "    static float[] getMovementForward()") + "\n}\n"
    adapters += """
class MarioJumpController {
    static final float LIBSM64_NEUTRAL_CAMERA_X = 0, LIBSM64_NEUTRAL_CAMERA_Z = 1;
    static boolean spaceReleaseRequired, attackReleaseRequired, crouchReleaseRequired, combatAttackWasDown;
    static float requestedWorldMoveX, requestedWorldMoveZ;
    static void tickMarioDriver() { publishControls(); }
""" + method(mario, "    private static void publishControls()") + "\n}\n"
    adapters += """
class LinkController {
    static boolean spaceReleaseRequired, attackReleaseRequired, targetReleaseRequired;
    static void tickLinkDriver() { publishControls(new Player(), AlternateCharacterController.sampleControls()); }
""" + method(link, "    private static void publishControls(") + "\n}\n"
    stubs = r"""
package game;
class Class240 { float aFloat2653, aFloat2657; }
class Class423_Sub2 { Class240 p = new Class240(); Class240 method5159(byte b) { return p; } }
class Class658_Sub2 { Class240 p = new Class240(); Class240 method7736(int n) { return p; } }
class Class411_Sub1 {
    Class423_Sub2 position = new Class423_Sub2(); Class658_Sub2 look = new Class658_Sub2();
    Object method4990(byte b) { return position; } Object method4991(int n) { return look; }
}
class Class24 { static Class411_Sub1 aClass411_Sub1_158 = new Class411_Sub1(); }
class Class133_Sub1 { static Class411_Sub1 aClass411_Sub1_9827; }
class Class18 { static int anInt143; }
class Entity { static int anInt11674; }
class Class165 { static int anInt2050; }
class Class36 { static int anInt387; }
class Class49 { static int anInt490; }
class client { static float aFloat8678; static int anInt8665; }
class IncomingPacket { static boolean method4113(byte b) { return false; } }
class PlayerControllerMode {
    static boolean isMarioMode() { return true; } static boolean isLinkMode() { return false; }
}
class MarioHelmetCalibrationController { static boolean active; static boolean isActive() { return active; } }
class AlternateCharacterInputKeyboard {
    static boolean[] keys = new boolean[128];
    static boolean rawKeyDown(int k) { return keys[k]; }
}
class Player {}
class LinkCombatController {
    static AlternateCharacterController.PlanarDirection target;
    static boolean returnNull;
    static AlternateCharacterController.PlanarDirection update(Player p, boolean b, boolean z,
            AlternateCharacterController.PlanarDirection camera) {
        if (returnNull) return null;
        return z && target != null ? target : camera;
    }
}
class MarioWeaponCombat { static boolean updateInput(boolean b) { return false; } static int getRequest() { return 0; } }
class Mario64Diagnostics {
    static void observeControls(AlternateCharacterController.ControlState s, boolean a, boolean b, boolean z) {}
}
class AlternateCharacterCombatBridge { static void requestPrimaryMeleeAttack() {} }
class Sm64BridgeSession {
    static float cx, cz, sx, sy;
    static void setCombatInput(float x, float z, float a, float b, boolean c, boolean d, boolean e, int f, int g) {
        cx=x; cz=z; sx=a; sy=b;
    }
}
class OotBridgeSession {
    static float cx, cz, sx, sy;
    static boolean zDown;
    static void setInput(float x, float z, float a, float b, boolean c, boolean d, boolean e) {
        cx=x; cz=z; sx=a; sy=b; zDown=e;
    }
}
"""
    test = r"""
package game;
public class SharedMovementTest {
    static int checks;
    static String context = "";
    static void near(double expected, double actual) {
        checks++;
        if (!Double.isFinite(actual) || Math.abs(expected-actual) > 0.0001)
            throw new AssertionError(context + ": expected " + expected + ", got " + actual);
    }
    // libsm64 fd118132: cameraYaw=atan2s(camZ,camX), stick=(-64*sx,64*sy),
    // intendedYaw=atan2s(-stickY,stickX)+cameraYaw; X=sin(yaw), Z=cos(yaw).
    // SM64 atan2s(y,x) uses yaw from +Z: its quadrant code is C atan2(x,y).
    static double[] marioVector() {
        double yaw = Math.atan2(Sm64BridgeSession.cx, Sm64BridgeSession.cz)
                + Math.atan2(-Sm64BridgeSession.sx, -Sm64BridgeSession.sy);
        return new double[] {Math.sin(yaw), Math.cos(yaw)};
    }
    // liboot 25208734 + OoT 269d0301: hostYaw=atan2(camX,camZ),
    // Lib_GetControlStickData -> Math_Atan2S(relY,-relX) = C atan2(-relX,relY).
    // Actor_UpdateVelocityXZGravity advances X=sin(yaw), Z=cos(yaw).
    static double[] linkVector() {
        double yaw = Math.atan2(OotBridgeSession.cx, OotBridgeSession.cz)
                + Math.atan2(-OotBridgeSession.sx, OotBridgeSession.sy);
        return new double[] {Math.sin(yaw), Math.cos(yaw)};
    }
    public static void main(String[] args) {
        Class411_Sub1 camera = Class24.aClass411_Sub1_158;
        camera.position.p.aFloat2653 = 1200;
        camera.position.p.aFloat2657 = -800;
        // All key combinations at every integer heading, including wraparound.
        for (int degree = 0; degree <= 360; degree++) {
            double angle = Math.toRadians(degree), fx = Math.sin(angle), fz = Math.cos(angle);
            camera.look.p.aFloat2653 = 1200 + (float)(fx * 1000);
            camera.look.p.aFloat2657 = -800 + (float)(fz * 1000);
            // Deliberately unrelated fallback yaw: active rendered camera must win.
            client.aFloat8678 = 4567;
            for (int bits = 0; bits < 16; bits++) {
                context = "camera=" + degree + " keys=" + bits;
                int[] keys = {33, 49, 48, 50}; // W, S, A, D
                for (int i=0; i<4; i++) AlternateCharacterInputKeyboard.keys[keys[i]] = (bits & (1<<i)) != 0;
                double x = ((bits & 8)!=0 ? 1:0) - ((bits & 4)!=0 ? 1:0);
                double y = ((bits & 1)!=0 ? 1:0) - ((bits & 2)!=0 ? 1:0);
                double magnitude = Math.hypot(x,y);
                if (magnitude > 1) { x/=magnitude; y/=magnitude; }
                AlternateCharacterController.ControlState c = AlternateCharacterController.sampleControls();
                // Project back into the rendered screen basis; no facing input.
                near(x, c.worldMoveX*fz - c.worldMoveZ*fx);
                near(y, c.worldMoveX*fx + c.worldMoveZ*fz);
                near(Math.hypot(x,y), Math.hypot(c.worldMoveX,c.worldMoveZ));
                MarioJumpController.tickMarioDriver();
                LinkController.tickLinkDriver();
                near(0, Sm64BridgeSession.cx); near(1, Sm64BridgeSession.cz);
                near(fx, OotBridgeSession.cx); near(fz, OotBridgeSession.cz);
                if (magnitude > 0) {
                    double[] sm = marioVector(), oot = linkVector();
                    double length = Math.hypot(c.worldMoveX,c.worldMoveZ);
                    near(c.worldMoveX/length, sm[0]); near(c.worldMoveZ/length, sm[1]);
                    near(c.worldMoveX/length, oot[0]); near(c.worldMoveZ/length, oot[1]);
                    // Explicit screen left/right and up/down after native decoding.
                    near(x/length, sm[0]*fz-sm[1]*fx);
                    near(y/length, sm[0]*fx+sm[1]*fz);
                    near(x/length, oot[0]*fz-oot[1]*fx);
                    near(y/length, oot[0]*fx+oot[1]*fz);
                } else {
                    near(0, Sm64BridgeSession.sx); near(0, Sm64BridgeSession.sy);
                    near(0, OotBridgeSession.sx); near(0, OotBridgeSession.sy);
                }
            }
        }
        // Preserve the current combat-owned Z-target basis, even when unrelated
        // to the visible camera. Exercise all directions and opposing-key pairs.
        AlternateCharacterInputKeyboard.keys[81] = true;
        for (int degree = 0; degree < 360; degree += 30) {
            double angle = Math.toRadians(degree), fx = Math.sin(angle), fz = Math.cos(angle);
            LinkCombatController.target = new AlternateCharacterController.PlanarDirection((float)fx, (float)fz);
            for (int bits = 0; bits < 16; bits++) {
                context = "Z target=" + degree + " keys=" + bits;
                int[] keys = {33,49,48,50};
                for (int i=0;i<4;i++) AlternateCharacterInputKeyboard.keys[keys[i]]=(bits&(1<<i))!=0;
                double x=((bits&8)!=0?1:0)-((bits&4)!=0?1:0);
                double y=((bits&1)!=0?1:0)-((bits&2)!=0?1:0);
                LinkController.tickLinkDriver();
                near(fx,OotBridgeSession.cx); near(fz,OotBridgeSession.cz);
                if (!OotBridgeSession.zDown) throw new AssertionError("native Z dropped");
                if (x!=0 || y!=0) {
                    double[] v=linkVector(); double length=Math.hypot(x,y);
                    near(x/length,v[0]*fz-v[1]*fx);
                    near(y/length,v[0]*fx+v[1]*fz);
                } else {near(0,OotBridgeSession.sx);near(0,OotBridgeSession.sy);}
            }
        }
        AlternateCharacterInputKeyboard.keys[81]=false;
        LinkCombatController.returnNull=true;
        context="null combat basis falls back to rendered camera";
        LinkController.tickLinkDriver();
        AlternateCharacterController.PlanarDirection forward=AlternateCharacterController.getCameraForward();
        near(forward.x,OotBridgeSession.cx);near(forward.z,OotBridgeSession.cz);
        LinkCombatController.returnNull=false;
        MarioHelmetCalibrationController.active = true;
        AlternateCharacterInputKeyboard.keys[33] = true;
        AlternateCharacterController.ControlState c = AlternateCharacterController.sampleControls();
        near(0,c.worldMoveX); near(0,c.worldMoveZ);
        MarioHelmetCalibrationController.active = false;
        // Invalid geometry and an inactive Construction camera use vanilla fallback.
        camera.look.p.aFloat2653 = Float.NaN;
        client.aFloat8678 = 0;
        near(0,AlternateCharacterController.getCameraForward().x);
        near(1,AlternateCharacterController.getCameraForward().z);
        ConstructionBuildCamera.active = false;
        if (ConstructionBuildCamera.getMovementForward() != null) throw new AssertionError("inactive camera");
        System.out.println("PASS " + checks + " checks: full orbit, native decoded screen axes, Z-target basis, camera fallback");
    }
}
"""
    with tempfile.TemporaryDirectory(prefix="matrix3-movement-") as tmp:
        folder = Path(tmp) / "game"
        folder.mkdir()
        (folder / "AlternateCharacterController.java").write_text(shared)
        (folder / "Stubs.java").write_text(stubs + adapters)
        (folder / "SharedMovementTest.java").write_text(test)
        compiler = [javac, "-source", "8", "-target", "8"] if javac else [java, "-m", "jdk.compiler/com.sun.tools.javac.Main", "--release", "8"]
        subprocess.run(compiler + ["-d", tmp] + [str(p) for p in folder.glob("*.java")], check=True)
        subprocess.run([java, "-cp", tmp, "game.SharedMovementTest"], check=True)


if __name__ == "__main__":
    main()
