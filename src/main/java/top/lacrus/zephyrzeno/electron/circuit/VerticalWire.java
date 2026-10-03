package top.lacrus.zephyrzeno.electron.circuit;

import java.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

/** A taut vertical drop, mechanically anchored at both ends. No suspended free nodes. */
public final class VerticalWire {
    public static boolean connection(CircuitState s,Endpoint a,Endpoint b){
        var x=outer(a.point());var y=outer(b.point());
        return Math.abs(x.x-y.x)<1e-6&&Math.abs(x.z-y.z)<1e-6&&Math.abs(x.y-y.y)>=1
            &&a.point().layer()==0&&b.point().layer()==0&&anchor(s,a)&&anchor(s,b);
    }
    private static boolean anchor(CircuitState s,Endpoint p){
        if(p.id().startsWith("c:")||p.id().startsWith("p:"))return true;
        return s.junctions.values().stream().anyMatch(j->j.endpoint().id().equals(p.id())&&!j.wireNode());
    }
    public static List<SurfacePoint> route(World w,CircuitState s,Endpoint a,Endpoint b,int layer,boolean cable){
        var path=List.of(a.point(),b.point());
        if(layer!=0||!connection(s,a,b)||units(s,path)>1024)throw new CircuitError("error.electron.drop_anchors");
        for(var point:path)if(!CircuitState.supports(w,point.host(),point.face())||!s.assemblyClear(w,point))throw new CircuitError("error.electron.endpoint_blocked");
        if(!clear(w,s,path,cable))throw new CircuitError("error.electron.drop_blocked");
        return path;
    }
    private static Vec3d outer(SurfacePoint p){return p.position().add(Vec3d.of(p.face().getVector()).multiply(.08));}
    public static List<Vec3d> vertices(CircuitState s,List<SurfacePoint> path){return List.of(s.position(path.getFirst()),outer(path.getFirst()),outer(path.getLast()),s.position(path.getLast()));}
    public static int units(CircuitState s,List<SurfacePoint> path){var v=vertices(s,path);double length=0;for(int i=1;i<v.size();i++)length+=v.get(i).distanceTo(v.get(i-1));return (int)Math.ceil(length*16); }
    public static Set<BlockPos> cells(CircuitState s,List<SurfacePoint> path){
        Set<BlockPos> out=new HashSet<>();var a=outer(path.getFirst());var b=outer(path.getLast());
        int n=(int)Math.ceil(a.distanceTo(b)*4);for(int i=0;i<=n;i++)out.add(BlockPos.ofFloored(a.lerp(b,i/(double)Math.max(1,n))));return out;
    }
    public static boolean clear(World w,CircuitState s,List<SurfacePoint> path,boolean cable){
        var a=outer(path.getFirst());var b=outer(path.getLast());double r=cable?.051:.012;
        Set<BlockPos> checked=new HashSet<>();
        for(var cell:cells(s,path))for(var q:BlockPos.iterate(cell.add(-1,0,-1),cell.add(1,0,1)))if(checked.add(q.toImmutable())){
            if(!w.isChunkLoaded(q))return false;
            if(q.equals(path.getFirst().host())||q.equals(path.getLast().host()))continue;
            for(var box:w.getBlockState(q).getCollisionShape(w,q).getBoundingBoxes())if(box.offset(q).expand(r).raycast(a,b).isPresent())return false;
            if(!w.getFluidState(q).isEmpty()&&new Box(q).expand(r).raycast(a,b).isPresent())return false;
        }
        return true;
    }
    private VerticalWire(){}
}
