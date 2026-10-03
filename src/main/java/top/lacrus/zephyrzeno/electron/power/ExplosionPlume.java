package top.lacrus.zephyrzeno.electron.power;

import java.util.*;
import net.minecraft.util.math.Vec3d;

/** Deterministic expanding smoke volumes, sampled independently of frame rate. */
public final class ExplosionPlume {
    public record Puff(Vec3d offset,double radius,float alpha,float heat,int tile,boolean ground){}
    public static int lifetime(int kind){return kind==PlantAccident.NUCLEAR?400:kind==PlantAccident.THERMAL?180:100;}
    public static List<Puff> sample(int kind,double age,long seed){if(age<0||age>=lifetime(kind))return List.of();if(kind!=PlantAccident.NUCLEAR)return pressureCloud(kind,age,seed);var out=new ArrayList<Puff>();var rng=new Random(seed);double fade=Math.min(1,(lifetime(kind)-age)/80);boolean nuclear=true;double scale=1;
        // Central rising stem, with individual sideways billows rather than a solid cylinder.
        double growth=1-Math.exp(-age/90),height=(4+40*growth)*scale,capRadius=(2+21*growth)*scale;
        int stem=nuclear?32:12;for(int i=0;i<stem;i++){double t=i/(double)stem,phase=rng.nextDouble()*Math.PI*2;double radial=(.8+2.7*(1-t))*scale;double y=height*t;out.add(new Puff(new Vec3d(Math.cos(phase)*radial,y,Math.sin(phase)*radial),(1.5+3.8*(1-t)+age*.009)*scale,(float)(fade*.55), (float)(Math.max(0,1-age/70)*(1-t)),rng.nextInt(4),false));}
        // A rolling toroidal cap and its filled upper centre form the mushroom silhouette.
        int cap=nuclear?72:30;for(int i=0;i<cap;i++){double azimuth=2*Math.PI*i/cap+rng.nextDouble()*.15;double ring=rng.nextDouble();double roll=age*.025+ring*2*Math.PI;double radial=capRadius*(.4+.5*ring)+Math.cos(roll)*capRadius*.1;double y=height+Math.sin(roll)*capRadius*.16+(1-ring)*capRadius*.2;double radius=capRadius*(.20+rng.nextDouble()*.09);out.add(new Puff(new Vec3d(Math.cos(azimuth)*radial,y,Math.sin(azimuth)*radial),radius,(float)(fade*.68),(float)(Math.max(0,1-age/65)*.8),rng.nextInt(4),false));}
        // Dust hugs the ground and expands separately from the buoyant smoke.
        double dustFade=Math.max(0,1-age/110)*fade;for(int i=0;i<24;i++){double a=2*Math.PI*i/24;double r=Math.min(age*.40,32)*scale;out.add(new Puff(new Vec3d(Math.cos(a)*r,.25*scale,Math.sin(a)*r),(1.1+age*.05)*scale,(float)(dustFade*.55),0,rng.nextInt(4),true));}
        return List.copyOf(out);
    }
    private static List<Puff> pressureCloud(int kind,double age,long seed){
        var rng=new Random(seed);var out=new ArrayList<Puff>();boolean boiler=kind==PlantAccident.THERMAL;double fade=Math.min(1,(lifetime(kind)-age)/45d),growth=1-Math.exp(-age/24),spread=(boiler?4.8:1.8)*growth;
        // Housing fragments leave a low radial cloud; a boiler releases an irregular rising jet.
        int count=boiler?48:30;for(int i=0;i<count;i++){double angle=rng.nextDouble()*Math.PI*2,r=rng.nextDouble()*spread,t=rng.nextDouble();double y=boiler?(1+age*.06)*t:age*.013+t*.5;var offset=new Vec3d(Math.cos(angle)*r,y,Math.sin(angle)*r);double radius=(boiler?1:.5)+growth*(boiler?1.8:.8)+rng.nextDouble()*.3;out.add(new Puff(offset,radius,(float)(fade*.65),(float)(Math.max(0,1-age/45)*(1-t*.45)),rng.nextInt(4),false));}
        for(int i=0;i<16;i++){double angle=i*2*Math.PI/16;double r=spread*1.3;out.add(new Puff(new Vec3d(Math.cos(angle)*r,.15,Math.sin(angle)*r),(boiler?1:.45)+age*.012,(float)(Math.max(0,1-age/80)*fade*.4),0,rng.nextInt(4),true));}
        return List.copyOf(out);
    }
    public static Vec3d debris(long seed,int i,double age,int kind){var random=new Random(seed+i*7919);double scale=kind==PlantAccident.NUCLEAR?1.3:kind==PlantAccident.THERMAL?1:.6;var velocity=new Vec3d(random.nextGaussian()*.2,.25+random.nextDouble()*.35,random.nextGaussian()*.2).multiply(scale);return velocity.multiply(age).add(0,-.012*age*age,0);}
    private ExplosionPlume(){}
}
