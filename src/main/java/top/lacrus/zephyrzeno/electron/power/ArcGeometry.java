package top.lacrus.zephyrzeno.electron.power;

import java.util.*;
import net.minecraft.util.math.Vec3d;

/** Short-lived dielectric filaments. Endpoints never jitter off the real contacts. */
public final class ArcGeometry {
    public record Filament(List<Vec3d> points,double width){}
    public static List<Filament> create(Vec3d start,Vec3d end,long seed){
        var random=new Random(seed);var delta=end.subtract(start);double length=delta.length();if(length<1e-6)return List.of();
        var axis=delta.normalize();var u=axis.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<.001)u=axis.crossProduct(new Vec3d(1,0,0));u=u.normalize();var v=axis.crossProduct(u);
        int count=Math.clamp((int)Math.ceil(length*18),6,96);var points=new ArrayList<Vec3d>();
        double width=Math.clamp(length*.08,.008,.14);
        for(int i=0;i<=count;i++){double t=i/(double)count,envelope=Math.sin(Math.PI*t);points.add(start.lerp(end,t).add(u.multiply((random.nextDouble()-.5)*width*envelope)).add(v.multiply((random.nextDouble()-.5)*width*envelope)));}
        points.set(0,start);points.set(count,end);var result=new ArrayList<Filament>();result.add(new Filament(List.copyOf(points),1));
        for(int i=count/4;i<count-2;i+=Math.max(3,count/4)){
            var a=points.get(i);var side=u.multiply((random.nextDouble()-.5)*width*2).add(v.multiply((random.nextDouble()-.5)*width*2));
            result.add(new Filament(List.of(a,a.add(delta.multiply(.025)).add(side.multiply(.55)),a.add(delta.multiply(.05)).add(side)),.35));
        }return result;
    }
    public static Vec3d closest(Vec3d p,Vec3d a,Vec3d b){var d=b.subtract(a);return a.add(d.multiply(d.lengthSquared()<1e-12?0:Math.clamp(p.subtract(a).dotProduct(d)/d.lengthSquared(),0,1)));}
    private ArcGeometry(){}
}
