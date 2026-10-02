package top.lacrus.zephyrzeno.electron.circuit;

import java.util.*;
import java.util.function.ToIntFunction;
import net.minecraft.util.math.Vec3d;

/** Shared centerline for preview, rendering and material accounting, including exterior corners. */
public final class WireGeometry {
    public static Vec3d position(SurfacePoint p,ToIntFunction<SurfacePoint> inset){
        return p.position().subtract(Vec3d.of(p.face().getVector()).multiply(inset.applyAsInt(p)/16d));
    }
    public static List<Vec3d> segment(SurfacePoint from,SurfacePoint to,ToIntFunction<SurfacePoint> inset){
        Vec3d a=position(from,inset),b=position(to,inset),normal=Vec3d.of(from.face().getVector());
        List<Vec3d> points=new ArrayList<>();points.add(a);
        if(from.face()!=to.face()){
            // The intersection of the two offset surface planes lies outside a convex block edge.
            points.add(b.add(normal.multiply(a.subtract(b).dotProduct(normal))));
        }else{
            double rise=b.subtract(a).dotProduct(normal);
            if(Math.abs(rise)>1e-8){
                Vec3d along=b.subtract(a).subtract(normal.multiply(rise));
                Vec3d edge=a.add(along.multiply(.5));
                if(!from.host().equals(to.host())&&along.lengthSquared()>1e-8)
                    edge=edge.subtract(along.normalize().multiply(Math.signum(rise)*.03125));
                points.add(edge);points.add(edge.add(normal.multiply(rise)));
            }
        }
        points.add(b);return points;
    }
    public static int segmentUnits(SurfacePoint a,SurfacePoint b,ToIntFunction<SurfacePoint> inset){
        var vertices=segment(a,b,inset);double length=0;
        for(int i=1;i<vertices.size();i++)length+=vertices.get(i).distanceTo(vertices.get(i-1));
        return Math.max(1,(int)Math.round(length*16));
    }
    public static List<Vec3d> vertices(List<SurfacePoint> path,ToIntFunction<SurfacePoint> inset){
        if(path.isEmpty())return List.of();
        List<Vec3d> result=new ArrayList<>();result.add(position(path.getFirst(),inset));
        for(int i=1;i<path.size();i++)for(var p:segment(path.get(i-1),path.get(i),inset))
            if(result.getLast().squaredDistanceTo(p)>1e-16)result.add(p);
        return result;
    }
    public static int lengthUnits(List<SurfacePoint> path,ToIntFunction<SurfacePoint> inset){
        int units=path.getFirst().layer()+path.getLast().layer();
        for(int i=1;i<path.size();i++)units+=segmentUnits(path.get(i-1),path.get(i),inset);
        return units;
    }
    private WireGeometry(){}
}
