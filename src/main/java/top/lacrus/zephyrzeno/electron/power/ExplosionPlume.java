package top.lacrus.zephyrzeno.electron.power;

import java.util.*;
import net.minecraft.util.math.Vec3d;

/** Deterministic expanding smoke volumes, sampled independently of frame rate. */
public final class ExplosionPlume {
    public record Puff(Vec3d offset,double radius,float alpha,float heat,int tile,boolean ground){}
    public static int lifetime(int kind){return kind==PlantAccident.NUCLEAR?400:kind==PlantAccident.THERMAL?180:100;}
    /** Artistic buoyant plume: analytic stem + advected torus + ellipsoidal cap, not a weapon simulation. */
    public static List<Puff> sample(int kind,double age,long seed){
        if(age<0||age>=lifetime(kind))return List.of();if(kind!=PlantAccident.NUCLEAR)return pressureCloud(kind,age,seed);
        var out=new ArrayList<Puff>();var rng=new Random(seed);double fade=Math.min(1,(lifetime(kind)-age)/80d),growth=-Math.expm1(-age/90),height=4+40*growth,radius=2+21*growth;
        double phase=(seed&65535)*.0001;
        // Narrow neck, broad entrainment foot; a smooth helical drift avoids disconnected random beads.
        for(int i=0;i<32;i++){double z=i/31d,theta=i*2.399963229728653+age*.007,neck=(1.2+radius*.075)*(1+1.6*Math.pow(1-z,3));double drift=.3*radius*Math.pow(z,3)*Math.sin(age*.006+phase);double swell=1+.09*Math.sin(7*z+age*.018+phase);out.add(new Puff(new Vec3d(drift+Math.cos(theta)*neck*.35,height*z,Math.sin(theta)*neck*.35),neck*swell,(float)(fade*.62),(float)(Math.max(0,1-age/70)*(1-z)),rng.nextInt(4),false));}
        // Toroidal vortex surface: r=R+a*cos(phi), y=H+b*sin(phi), phi advects with time.
        // Coherent Fourier lobes vary the rim without frame-dependent noise.
        for(int ring=0;ring<3;ring++)for(int i=0;i<32;i++){double theta=2*Math.PI*i/32,phi=2*Math.PI*ring/3+age*.018+2*theta+.24*Math.sin(3*theta+phase);double lobes=1+.06*Math.sin(5*theta+phase)+.035*Math.sin(9*theta-age*.011);double major=radius*.66,minor=radius*.26;double radial=(major+minor*Math.cos(phi))*lobes,y=height+minor*.72*Math.sin(phi);double billow=radius*(.23+.018*Math.sin(3*theta+phi));out.add(new Puff(new Vec3d(radial*Math.cos(theta),y,radial*Math.sin(theta)),billow,(float)(fade*.64),(float)(Math.max(0,1-age/65)*.8),rng.nextInt(4),false));}
        // Fill the crown using equal-area polar samples of an oblate ellipsoid.
        for(int i=0;i<16;i++){double r=Math.sqrt((i+.5)/16d)*radius*.7,theta=i*2.399963229728653;double y=height+radius*.24*Math.sqrt(Math.max(0,1-r*r/(radius*radius)));out.add(new Puff(new Vec3d(r*Math.cos(theta),y,r*Math.sin(theta)),radius*.22,(float)(fade*.58),(float)(Math.max(0,1-age/65)*.6),rng.nextInt(4),false));}
        double dustFade=Math.max(0,1-age/110)*fade;for(int i=0;i<24;i++){double theta=2*Math.PI*i/24,r=32*(-Math.expm1(-age/70));out.add(new Puff(new Vec3d(Math.cos(theta)*r,.25,Math.sin(theta)*r),1.1+age*.05,(float)(dustFade*.55),0,rng.nextInt(4),true));}
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
