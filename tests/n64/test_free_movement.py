#!/usr/bin/env python3
"""Compile/run production movement drivers with engine stubs; no ROM required."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "Client/src/main/java/game"

STUBS = r'''
package game;
class Class240 { float aFloat2653, aFloat2656, aFloat2657; }
class Class238 { Class240 aClass240_2647 = new Class240(); }
class Player {
    int[] screenX={0}, screenY={0}; Class238 p=new Class238();
    Class238 method5394(){return p;}
    void method5395(float x,float y,float z){p.aClass240_2647.aFloat2653=x;p.aClass240_2647.aFloat2656=y;p.aClass240_2647.aFloat2657=z;}
    int method10556(short x){return 1;}
}
class Class611 { static Player aClass456_Sub1_Sub2_Sub3_Sub2_7976; }
class Class195 { int sent; void method2929(Class572_Sub25 p,byte b){sent++;} }
class Class572_Sub25 {}
class Class613 { int method7347(int x){return 100;} int method7278(int x){return 100;} }
class client { static int cycles,anInt8665; static float aFloat8678; static Class195 aClass195_8589=new Class195(); static Class613 aClass613_8605=new Class613(); }
class IncomingPacket {
    static boolean method4113(byte b){return false;}
    static Class572_Sub25 method4108(int x,int y,int a,int b){return new Class572_Sub25();}
}
class PlayerControllerMode {
    enum Mode {RUNESCAPE,MARIO,LINK} static Mode mode=Mode.RUNESCAPE;
    static boolean isMarioMode(){return mode==Mode.MARIO;} static boolean isLinkMode(){return mode==Mode.LINK;}
    static void setMode(Mode m){mode=m;} static void tick(){} static void resetForPlayerLifecycle(){}
}
class AlternateCharacterInputKeyboard {
    static boolean[] keys=new boolean[128]; static void install(){} static void uninstall(){}
    static boolean rawKeyDown(int k){return keys[k];}
}
class MarioHelmetCalibrationController { static boolean isActive(){return false;} }
class AlternateCharacterCombatBridge { static void reset(){} static void requestPrimaryMeleeAttack(){} }
class MarioWeaponCombat { static void reset(){} static boolean updateInput(boolean b){return false;} static int getRequest(){return 0;} }
class Mario64Diagnostics {
    static void observeRuntime(Player p){} static void noteFallback(String s){}
    static void observeControls(AlternateCharacterController.ControlState c,boolean a,boolean b,boolean z){}
}
class Sm64BridgeSession {
    static final class NativePosition { float x,y,z; }
    static NativePosition p=new NativePosition();
    static void start(){} static void stop(){} static boolean hasFailed(){return false;} static String getFailureReason(){return null;}
    static NativePosition getLatestPosition(){return p;} static NativePosition getInterpolatedPosition(){return p;}
    static Float getLatestY(){return p.y;} static Float getInterpolatedY(){return p.y;}
    static void setInput(float a,float b,float c,float d,boolean e,boolean f,boolean g){}
    static void setCombatInput(float a,float b,float c,float d,boolean e,boolean f,boolean g,int h,int i){}
}
class OotBridgeSession {
    static final class LinkFrame {}
    static final class NativePosition {float x,z;}
    static NativePosition p=new NativePosition(); static LinkFrame frame=new LinkFrame();
    static void start(){} static void stop(){} static boolean hasFailed(){return false;} static String getFailureReason(){return null;}
    static LinkFrame getLatestFrame(){return frame;} static NativePosition getInterpolatedPosition(){return p;}
    static void setInput(float a,float b,float c,float d,boolean e,boolean f,boolean g){}
}
class LinkCharacterFit { static final class Profile {float scale=3;} static Profile resolve(Player p,OotBridgeSession.LinkFrame f){return new Profile();} }
class ConstructionBuildCamera { static float[] getMovementForward(){return new float[]{0,1};} static boolean isRequested(){return false;} }
class Class423_Sub2 { Class240 method5159(byte b){return new Class240();} }
class Class658_Sub2 { Class240 method7736(int n){return new Class240();} }
class Class411_Sub1 { Object method4990(byte b){return new Class423_Sub2();} Object method4991(int n){return new Class658_Sub2();} }
class Class24 { static Class411_Sub1 aClass411_Sub1_158; }
class Class133_Sub1 { static Class411_Sub1 aClass411_Sub1_9827; }
class Class18 { static int anInt143; }
class Entity { static int anInt11674; }
class Class165 { static int anInt2050; }
class Class36 { static int anInt387; }
class Class49 { static int anInt490; }
'''

TEST = r'''
package game;
public class FreeMovementTest {
    static int checks;
    static void near(float a,float b){checks++;if(!Float.isFinite(b)||Math.abs(a-b)>0.002F)throw new AssertionError(a+" != "+b);}
    static void tick(){client.cycles++;AlternateCharacterController.tick();}
    static float x(Player p){return p.method5394().aClass240_2647.aFloat2653;}
    static float z(Player p){return p.method5394().aClass240_2647.aFloat2657;}
    public static void main(String[] args){
        if(AlternateCharacterController.isRuneScapeClippingEnabled())throw new AssertionError("clipping must default OFF");
        Player p=new Player();p.method5395(256,0,256);Class611.aClass456_Sub1_Sub2_Sub3_Sub2_7976=p;
        PlayerControllerMode.mode=PlayerControllerMode.Mode.MARIO;tick();
        AlternateCharacterInputKeyboard.keys[50]=true;
        for(int i=1;i<=2000;i++){Sm64BridgeSession.p.x=i*0.75F;tick();near(256+i*2.25F,x(p));}
        near(0,client.aClass195_8589.sent); // crosses >8 tiles with zero walk requests
        tick();near(4756,x(p)); // no native change means no drift
        AlternateCharacterController.setRuneScapeClippingEnabled(true);tick();near(256,x(p));
        Sm64BridgeSession.p.x+=100;tick();
        if(client.aClass195_8589.sent==0)throw new AssertionError("clipping must use stock walk requests");
        if(x(p)>512)throw new AssertionError("clipping boundary bypass");
        AlternateCharacterController.setRuneScapeClippingEnabled(false);tick();
        int packets=client.aClass195_8589.sent;
        float start=x(p);Sm64BridgeSession.p.x+=0.125F;tick();near(start+0.375F,x(p));
        near(packets,client.aClass195_8589.sent);
        PlayerControllerMode.mode=PlayerControllerMode.Mode.RUNESCAPE;tick();near(256,x(p));
        PlayerControllerMode.mode=PlayerControllerMode.Mode.LINK;tick();
        for(int i=1;i<=2000;i++){OotBridgeSession.p.x=i*0.625F;OotBridgeSession.p.z=-i*0.25F;tick();near(256+i*1.875F,x(p));near(256-i*0.75F,z(p));}
        near(packets,client.aClass195_8589.sent);
        AlternateCharacterController.setRuneScapeClippingEnabled(true);tick();near(256,x(p));near(256,z(p));
        if(client.aClass195_8589.sent<=packets)throw new AssertionError("Link clipping routing");
        AlternateCharacterController.setRuneScapeClippingEnabled(false);tick();
        OotBridgeSession.p.x+=0.125F;tick();near(256.375F,x(p));
        PlayerControllerMode.mode=PlayerControllerMode.Mode.RUNESCAPE;tick();near(256,x(p));
        AlternateCharacterFreeMovement f=new AlternateCharacterFreeMovement();
        f.apply(p,0,0,3);f.apply(p,100,100,3);near(556,x(p));
        p.method5395(1000,20,2000);f.apply(p,101,101,3);near(1003,x(p));near(2003,z(p));
        f.restore(p);near(1000,x(p));near(2000,z(p));
        f.apply(p,0,0,3);f.apply(p,100,0,3);
        Player replacement=new Player();replacement.method5395(11,12,13);f.restore(replacement);near(11,x(replacement));
        System.out.println("PASS "+checks+" production-driver checks: free travel, zero walk packets, toggle, clipping, restore, corrections");
    }
}
'''

BRIDGE_TEST = r'''
package game;
import java.lang.reflect.*;
public class BridgePositionTest {
    static Object frame(float x,float z,long nanos) throws Exception {
        Constructor<?> c=OotBridgeSession.LinkFrame.class.getDeclaredConstructors()[0];c.setAccessible(true);
        Class<?>[] types=c.getParameterTypes();Object[] a=new Object[types.length];
        for(int i=0;i<a.length;i++) {
            if(types[i]==long.class)a[i]=Long.valueOf(0);
            else if(types[i]==int.class)a[i]=Integer.valueOf(0);
            else if(types[i]==float.class)a[i]=Float.valueOf(0);
            else if(types[i]==boolean.class)a[i]=Boolean.FALSE;
        }
        a[2]=x;a[4]=z;a[a.length-1]=nanos;return c.newInstance(a);
    }
    public static void main(String[] args) throws Exception {
        Class<?> pair=Class.forName("game.OotBridgeSession$PositionFrames");
        Constructor<?> c=pair.getDeclaredConstructors()[0];c.setAccessible(true);
        Method sample=pair.getDeclaredMethod("sample",long.class);sample.setAccessible(true);
        long now=1000000000L;
        Object p=c.newInstance(frame(10,20,now-50000000),frame(30,60,now));
        for(int ms=-10;ms<=100;ms++) {
            OotBridgeSession.NativePosition v=(OotBridgeSession.NativePosition)sample.invoke(p,now+ms*1000000L);
            float t=Math.max(0,Math.min(1,ms/50.0F));
            if(Math.abs(v.x-(10+20*t))>0.0001F||Math.abs(v.z-(20+40*t))>0.0001F)throw new AssertionError("interpolation "+ms);
        }
        Object first=c.newInstance(null,frame(123,456,now));
        OotBridgeSession.NativePosition v=(OotBridgeSession.NativePosition)sample.invoke(first,now);
        if(v.x!=123||v.z!=456)throw new AssertionError("first frame baseline");
        System.out.println("PASS 112 native-position interpolation samples (production bridge)");
    }
}
'''


def main():
    java = os.environ.get("JAVA") or shutil.which("java")
    javac = os.environ.get("JAVAC") or shutil.which("javac")
    if not java:
        raise SystemExit("Set JAVA to a JDK java executable.")
    compiler = [javac, "-source", "8", "-target", "8"] if javac else [java, "-m", "jdk.compiler/com.sun.tools.javac.Main", "--release", "8"]
    with tempfile.TemporaryDirectory(prefix="matrix3-free-") as tmp:
        path=Path(tmp); game=path/"game";game.mkdir()
        for name in ["AlternateCharacterController","AlternateCharacterFreeMovement","MarioJumpController","LinkController"]:
            shutil.copyfile(SRC/(name+".java"),game/(name+".java"))
        (game/"Stubs.java").write_text(STUBS)
        (game/"FreeMovementTest.java").write_text(TEST)
        subprocess.run(compiler+["-d",tmp]+[str(p) for p in game.glob("*.java")],check=True)
        subprocess.run([java,"-cp",tmp,"game.FreeMovementTest"],check=True)
        # The bridge is standalone: compile its complete production source too.
        bridge_test=path/"BridgePositionTest.java"
        bridge_test.write_text(BRIDGE_TEST)
        subprocess.run(compiler+["-d",str(path/"bridge"),str(SRC/"OotBridgeSession.java"),str(bridge_test)],check=True)
        subprocess.run([java,"-cp",str(path/"bridge"),"game.BridgePositionTest"],check=True)


if __name__ == "__main__":
    main()
