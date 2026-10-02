package top.lacrus.zephyrzeno.electron.circuit;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import top.lacrus.zephyrzeno.electron.foundry.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

/** Finite-length suspended cable. Gravity sag is a static parabolic game approximation. */
public final class OverheadCable {
    public static final int MAX_SPAN=64;
    public static boolean anchor(CircuitState state,Endpoint endpoint){
        var device=state.devices.get(endpoint.point().host());
        return device!=null&&device.block().equals("electron:tower_terminal")&&device.face()==Direction.UP;
    }
    public static boolean connection(CircuitState state,Endpoint a,Endpoint b,boolean cable){
        return cable&&!a.point().host().equals(b.point().host())&&anchor(state,a)&&anchor(state,b);
    }
    public static boolean tower(World world,BlockPos head){
        if(!world.getBlockState(head).isOf(Foundry.TOWER_TERMINAL)||world.getBlockState(head).get(ComponentBlock.FACING)!=Direction.UP)return false;
        var pos=head.down();int height=0;
        while(height<64&&world.getBlockState(pos).isOf(Foundry.TOWER_SECTION)){height++;pos=pos.down();}
        return height>=3&&height<64&&Block.isShapeFullCube(world.getBlockState(pos).getCollisionShape(world,pos));
    }
    public static List<Vec3d> curve(Vec3d a,Vec3d b){
        double horizontal=Math.hypot(b.x-a.x,b.z-a.z),sag=Math.min(3,Math.max(.15,horizontal*.06));
        int count=Math.max(8,(int)Math.ceil(a.distanceTo(b)*8));List<Vec3d> points=new ArrayList<>(count+1);
        for(int i=0;i<=count;i++){double t=i/(double)count;points.add(a.lerp(b,t).add(0,-4*sag*t*(1-t),0));}
        return List.copyOf(points);
    }
    public static List<Vec3d> vertices(CircuitState state,List<SurfacePoint> path){return curve(state.position(path.getFirst()),state.position(path.getLast()));}
    public static int units(CircuitState state,List<SurfacePoint> path){return units(vertices(state,path));}
    public static int units(List<Vec3d> points){double length=0;for(int i=1;i<points.size();i++)length+=points.get(i).distanceTo(points.get(i-1));return (int)Math.ceil(length*16-1e-8);}
    public static Set<BlockPos> cells(List<Vec3d> points){
        Set<BlockPos> cells=new HashSet<>();for(var p:points)for(var pos:BlockPos.iterate(BlockPos.ofFloored(p.add(-.03,-.03,-.03)),BlockPos.ofFloored(p.add(.03,.03,.03))))cells.add(pos.toImmutable());return cells;
    }
    public static boolean loaded(World world,List<Vec3d> points){return cells(points).stream().allMatch(world::isChunkLoaded);}
    public static boolean clear(World world,List<Vec3d> points,BlockPos a,BlockPos b){
        for(int i=1;i<points.size();i++){
            Vec3d start=points.get(i-1),end=points.get(i);
            for(var pos:BlockPos.iterate(BlockPos.ofFloored(new Vec3d(Math.min(start.x,end.x)-.03,Math.min(start.y,end.y)-.03,Math.min(start.z,end.z)-.03)),BlockPos.ofFloored(new Vec3d(Math.max(start.x,end.x)+.03,Math.max(start.y,end.y)+.03,Math.max(start.z,end.z)+.03)))){
                if(pos.equals(a)||pos.equals(b))continue;
                if(!world.isChunkLoaded(pos))return false;
                for(var box:world.getBlockState(pos).getCollisionShape(world,pos).getBoundingBoxes()){
                    var bounds=box.offset(pos).expand(.025);
                    if(bounds.contains(start)||bounds.contains(end)||bounds.raycast(start,end).isPresent())return false;
                }
            }
        }
        return true;
    }
    public static List<SurfacePoint> route(World world,CircuitState state,Endpoint a,Endpoint b,int layer){
        if(layer!=0)throw new CircuitError("error.electron.overhead_layer");
        if(!tower(world,a.point().host())||!tower(world,b.point().host()))throw new CircuitError("error.electron.tower_required");
        double span=state.position(a.point()).distanceTo(state.position(b.point()));
        if(span>MAX_SPAN||Math.hypot(a.point().host().getX()-b.point().host().getX(),a.point().host().getZ()-b.point().host().getZ())<2)throw new CircuitError("error.electron.overhead_span");
        var path=List.of(a.point(),b.point());var vertices=vertices(state,path);
        if(!loaded(world,vertices)||!clear(world,vertices,a.point().host(),b.point().host()))throw new CircuitError("error.electron.overhead_blocked");
        return path;
    }
    private OverheadCable(){}
}
