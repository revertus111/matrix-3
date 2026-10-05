package game;

import java.io.*;
import java.lang.reflect.*;

/** ROM-free tests against the production parser and native-written v3 fixture. */
public final class Sm64CombatProtocolTest {
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    private static void le(OutputStream out,int value) throws IOException {
        for(int i=0;i<4;i++) out.write(value >>> (i*8));
    }
    private static byte[] legacy(int version) throws IOException {
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        b.write(new byte[]{'M','6','4','F'}); le(b,17);
        for(int i=0;i<10;i++) le(b,0); // 8 floats, action, animID
        b.write(0);b.write(0); // animation frame
        le(b,0);le(b,0); // flags/particles
        b.write(1);b.write(0); // one triangle
        for(int i=0;i<24;i++) le(b,0);
        if(version==2) {for(int i=0;i<9;i++) le(b,0); b.write(1);}
        return b.toByteArray();
    }
    public static void main(String[] args) throws Exception {
        Class<?> inputType=Class.forName("game.Sm64BridgeSession$InputState");
        Field idle=inputType.getDeclaredField("IDLE"); idle.setAccessible(true);
        Method step=Sm64BridgeSession.class.getDeclaredMethod("step",OutputStream.class,InputStream.class,inputType,int.class);
        step.setAccessible(true);
        for(int v=1;v<=2;v++) {
            ByteArrayInputStream in=new ByteArrayInputStream(legacy(v));
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            Sm64BridgeSession.GeometryFrame f=(Sm64BridgeSession.GeometryFrame)step.invoke(null,out,in,idle.get(null),v);
            check(f.sequence==17 && f.rightHand==null && out.size()==20 && in.available()==0);
            check(f.hasSemanticGeometry()==(v==2));
        }
        try(InputStream in=new FileInputStream(args[0])) {
            for(int sequence=7;sequence<=8;sequence++) {
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                Sm64BridgeSession.GeometryFrame f=(Sm64BridgeSession.GeometryFrame)step.invoke(null,out,in,idle.get(null),3);
                check(out.size()==25 && f.sequence==sequence && f.state.x==123);
                check(f.hasSemanticGeometry() && f.localPositions[0]==42 && f.partIds[0]==1);
                check(f.rightHand!=null && f.rightHand[12]==147 && f.combatAnimation==1 && f.combatTime==.5F);
                Method freeze=Sm64BridgeSession.class.getDeclaredMethod("refreshPresentationTimestamp",Sm64BridgeSession.GeometryFrame.class);
                freeze.setAccessible(true);
                Sm64BridgeSession.GeometryFrame frozen=(Sm64BridgeSession.GeometryFrame)freeze.invoke(null,f);
                check(frozen.rightHand==f.rightHand && frozen.combatTime==f.combatTime);
            }
            check(in.read()==-1);
        }
        try {
            step.invoke(null,new ByteArrayOutputStream(),new ByteArrayInputStream(new byte[]{'M','6','4','F'}),idle.get(null),3);
            throw new AssertionError("truncated frame accepted");
        } catch(InvocationTargetException expected) {check(expected.getCause() instanceof IOException);}
        System.out.println("PASS v1/v2 compatibility, C-written v3 framing, socket metadata, frozen pose and truncation");
    }
}
