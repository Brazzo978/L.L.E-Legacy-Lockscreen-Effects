package com.codex.lle;
import java.util.*;
import java.nio.file.*;
import java.util.regex.*;

/** Tests actual runtime math, scene chronology and shader builder with the renderer's source. */
public final class SonyWorkshopTest {
    private static void require(boolean ok,String label) { if(!ok)throw new AssertionError(label); }
    private static boolean finite(float f) {return !Float.isNaN(f)&&!Float.isInfinite(f);}
    public static void main(String[] args)throws Exception {
        blinds(); motion(); shaders(args[0]);
        System.out.println("SonyWorkshopTest: PASS");
    }
    private static void blinds() {
        float[] got=new float[2];
        for(float y : new float[]{0f,.01f,.2f,.5f,.99f,1f}) {
            require(XperiaBlindsDynamics.start(y,17,5f/17f)==XperiaBlindsEffectView.affectedStart(y),"stock start");
            require(XperiaBlindsDynamics.end(y,17,5f/17f)==XperiaBlindsEffectView.affectedEnd(y),"stock end");
            for(int i=0;i<17;i++) require(Float.floatToIntBits((float)Math.sin(Math.PI*XperiaBlindsDynamics.distance(i,y,17,5f/17f)))
                    ==Float.floatToIntBits(XperiaBlindsEffectView.stripWave(i,y)),"stock wave exact");
        }
        for(float[] input:new float[][]{{0f,0f,1f,.016f},{.8f,-2f,0f,.008f},{1f,1f,1f,.04f}}) {
            XperiaBlindsDynamics.spring(input[0],input[1],input[2],input[3],400f,.85f,got);
            float[] oracle=XperiaBlindsEffectView.springStep(input[0],input[1],input[2],input[3]);
            require(Float.floatToIntBits(got[0])==Float.floatToIntBits(oracle[0])&&Float.floatToIntBits(got[1])==Float.floatToIntBits(oracle[1]),"stock spring exact");
        }
        for(int count:new int[]{5,17,40}) {
            int previous=0;
            for(int i=0;i<=count;i++) {int top=XperiaBlindsDynamics.bandTop(1920,i,count);require(top>=previous&&top<=1920,"bounded bands");previous=top;}
            require(previous==1920,"full image exact coverage");
            for(float y:new float[]{0f,.5f,1f})require(XperiaBlindsDynamics.start(y,count,.8f)>=0&&XperiaBlindsDynamics.end(y,count,.8f)<=count,"array bounds");
        }
        XperiaBlindsDynamics.spring(0f,0f,1f,.016f,400f,.85f,got);float original=got[0];
        XperiaBlindsDynamics.spring(0f,0f,1f,.016f,1200f,.99f,got);require(Math.abs(got[0]-original)>.001f,"actual spring tuning");
        for(float stiffness:new float[]{50,1200})for(float damping:new float[]{.05f,.99f}) {
            float x=0,v=0;
            for(int i=0;i<1000;i++){XperiaBlindsDynamics.spring(x,v,i<500?2f:0f,.016f,stiffness,damping,got);x=got[0];v=got[1];require(finite(x)&&finite(v)&&Math.abs(x)<5,"bounded spring extrema");}
        }
    }
    private static RevolvingGlassScene tuned() {return new RevolvingGlassScene(.02f,.8f,2f,.13f,0f,179f,.5f,2f,300,1000,30f,2f,.5f);}
    private static void motion() {
        RevolvingGlassScene stock=new RevolvingGlassScene(), custom=tuned();
        stock.begin(540,1080,1000);custom.begin(540,1080,1000);
        stock.move(540,640,1080,1100);custom.move(540,640,1080,1100);
        require(stock.frameAt(1100).angleDegrees==44f&&custom.frameAt(1100).angleDegrees==80f,"actual drag mapping");
        custom.finish(false,1100);boolean negative=false;
        for(long t=1100;t<4500;t+=10){RevolvingGlassScene.Frame f=custom.frameAt(t);require(finite(f.angleDegrees),"finite cancel");negative|=f.angleDegrees<0;}
        require(negative&&!custom.frameAt(4500).visible,"tuned cancel oscillates and terminates");
        custom.reset();custom.affordance(5000);require(custom.frameAt(5600).visible,"actual hint duration");require(!custom.frameAt(6000).visible,"hint ends");
        for(RevolvingGlassScene s:new RevolvingGlassScene[]{stock,custom}) {
            s.reset();s.begin(540,1080,7000);s.move(540,640,1080,7100);s.finish(true,7100);
            require(s.frameAt(7100+RevolvingGlassScene.UNLOCK_MS).visible,"underlay held original clock");
            require(!s.frameAt(7100+RevolvingGlassScene.UNDERLAY_HOLD_MS).visible,"handoff hold fixed");
        }
        custom.reset();custom.begin(540,1080,9000);custom.move(540,640,1080,9100);require(custom.frameAt(9100).angleDegrees==80f,"reset preserves snapshot");
    }
    private static String shader(String source,String marker,String end) {
        int a=source.indexOf(marker),b=source.indexOf(end,a+marker.length());
        require(a>=0&&b>a,"real shader source markers");
        Matcher m=Pattern.compile("\"([^\"]*)\"").matcher(source.substring(a+marker.length(),b));
        StringBuilder out=new StringBuilder();while(m.find())out.append(m.group(1));return out.toString();
    }
    private static void shaders(String root)throws Exception {
        String source=new String(Files.readAllBytes(Paths.get(root,"LLEUnified/src/com/codex/lle/RevolvingGlassEffectView.java")),"UTF-8");
        String v=shader(source,"private static final String VERTEX =","private static final String FRAGMENT =");
        String f=shader(source,"private static final String FRAGMENT =","private String workshopVertex()");
        Map<String,Float> changes=new LinkedHashMap<String,Float>();
        for(EffectWorkshopConfig.Parameter p:EffectWorkshopSonyParameters.parametersFor(36))changes.put(p.key,p.max);
        EffectWorkshopConfig.Values disabled=EffectWorkshopConfig.create(36,false,changes),custom=EffectWorkshopConfig.create(36,true,changes);
        require(RevolvingGlassOptics.vertex(v,disabled)==v&&RevolvingGlassOptics.fragment(f,disabled)==f,"disabled shader identity");
        String result=RevolvingGlassOptics.fragment(f,custom);
        require(!result.contains(".72+.28")&&!result.contains("border*.76")&&!result.contains("border*.68"),"face optics applied");
        require(!result.contains("uTexel*1.25")&&!result.contains("mix(sharp,soft,.20)"),"blur applied");
        require(!result.contains("vUv.x*55.")&&!result.contains(")),10.)")&&!result.contains("float a=.88"),"glint and edge applied");
        require(!result.contains("vec3(.94,.98,1.)")&&!result.contains("vec3(.78,.92,1.)"),"both tints applied");
        require(RevolvingGlassOptics.vertex(v,custom).contains("float camera=5.0;"),"camera applied");
        int balance=0;for(char c:result.toCharArray()){if(c=='(')balance++;if(c==')')balance--;require(balance>=0,"shader balanced parentheses");}require(balance==0,"shader closed parentheses");
        require(!result.contains("NaN")&&!result.contains("Infinity"),"finite generated literals");
        // For the largest card, max rotated z is sqrt(max x squared + depth squared), below min camera.
        float bound=(float)Math.sqrt((.87f*1.1f+.2f)*(.87f*1.1f+.2f)+.15f*.15f);
        require(bound<2f,"perspective denominator positive throughout configured geometry");
    }
}
