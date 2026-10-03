package top.lacrus.zephyrzeno.electron.appliance;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.power.FaultEffects;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.node;

/** Two insulated conductors share one jacket. Geometry never merges their electrical nodes. */
public final class CordNetwork {
    public record Lead(UUID id,BlockPos source,BlockPos load,int socket,String sourceBlock,String loadBlock,List<Vec3d> path){
        public Lead{source=source.toImmutable();load=load.toImmutable();path=List.copyOf(path);}
        public double length(){double n=0;for(int i=1;i<path.size();i++)n+=path.get(i-1).distanceTo(path.get(i));return n;}
    }
    public static Lead connect(ServerWorld w,CircuitState s,BlockPos source,BlockPos load){
        var a=w.getBlockState(source);var b=w.getBlockState(load);
        if(source.equals(load)||Appliances.outlets(a)==0||!Appliances.load(b))throw new CircuitError("error.electron.cord_target");
        if(s.cords.values().stream().anyMatch(c->c.load.equals(load)))throw new CircuitError("error.electron.cord_occupied");
        int socket=-1;
        for(int i=0;i<Appliances.outlets(a);i++){int n=i;if(s.cords.values().stream().noneMatch(c->c.source.equals(source)&&c.socket==n)){socket=i;break;}}
        if(socket<0)throw new CircuitError("error.electron.cord_full");
        var path=route(w,source,a,load,b,socket);var lead=new Lead(UUID.randomUUID(),source,load,socket,Registries.BLOCK.getId(a.getBlock()).toString(),Registries.BLOCK.getId(b.getBlock()).toString(),path);
        if(lead.length()>12)throw new CircuitError("error.electron.cord_length");
        return lead;
    }
    private static Vec3d leadOut(BlockPos p,BlockState s,boolean output,int socket){
        var at=Appliances.plug(p,s,output,socket);
        var rear=ComponentGeometry.local(s.get(ComponentBlock.FACING),s.get(ComponentBlock.TURN),8,-3,s.getBlock() instanceof ApplianceBlock?.2:9);
        return Vec3d.of(p).add(rear);
    }
    private static BlockPos floor(ServerWorld w,Vec3d at){
        var q=BlockPos.ofFloored(at);for(int i=0;i<5;i++){
            var p=q.down(i);if(!w.isChunkLoaded(p))throw new CircuitError("error.electron.cord_route");
            if(Block.isShapeFullCube(w.getBlockState(p).getCollisionShape(w,p)))return p;
        }throw new CircuitError("error.electron.cord_route");
    }
    private static boolean clear(ServerWorld w,BlockPos p){
        return w.isChunkLoaded(p)&&w.isChunkLoaded(p.up())&&!(w.getBlockState(p).getBlock() instanceof ComponentBlock)&&Block.isShapeFullCube(w.getBlockState(p).getCollisionShape(w,p))
            &&w.getBlockState(p.up()).getCollisionShape(w,p.up()).isEmpty()&&w.getFluidState(p.up()).isEmpty();
    }
    public static List<Vec3d> route(ServerWorld w,BlockPos src,BlockState a,BlockPos dst,BlockState b,int socket){
        var start=leadOut(src,a,true,socket);var end=leadOut(dst,b,false,0);
        var floorA=floor(w,start);var floorB=floor(w,end);
        if(floorA.getManhattanDistance(floorB)>12)throw new CircuitError("error.electron.cord_length");
        var parents=new HashMap<BlockPos,BlockPos>();var queue=new ArrayDeque<BlockPos>();parents.put(floorA,floorA);queue.add(floorA);
        while(!queue.isEmpty()&&parents.size()<1024){
            var p=queue.remove();if(p.equals(floorB))break;
            for(var d:Direction.Type.HORIZONTAL)for(int dy:new int[]{0,1,-1}){
                var q=p.offset(d).up(dy);
                if(q.getManhattanDistance(floorA)>12||parents.containsKey(q)||!clear(w,q))continue;
                parents.put(q,p);queue.add(q);break;
            }
        }
        if(!parents.containsKey(floorB))throw new CircuitError("error.electron.cord_route");
        var cells=new ArrayList<BlockPos>();for(var p=floorB;!p.equals(floorA);p=parents.get(p))cells.add(p);cells.add(floorA);Collections.reverse(cells);
        var path=new ArrayList<Vec3d>();path.add(Appliances.plug(src,a,true,socket));path.add(start);
        path.add(new Vec3d(start.x,floorA.getY()+1.035,start.z));
        for(var p:cells)path.add(new Vec3d(p.getX()+.5,p.getY()+1.035,p.getZ()+.5));
        path.add(new Vec3d(end.x,floorB.getY()+1.035,end.z));path.add(end);path.add(Appliances.plug(dst,b,false,0));
        // End stubs are permitted inside their own chassis. The exposed floor run cannot pass through walls.
        for(int i=2;i<path.size()-2;i++){
            var aa=path.get(i-1);var bb=path.get(i);
            int count=Math.max(1,(int)Math.ceil(aa.distanceTo(bb)*16));
            for(int j=0;j<=count;j++){var at=aa.lerp(bb,j/(double)count);var cell=BlockPos.ofFloored(at);
                if(cell.equals(src)||cell.equals(dst))continue;
                if(!w.isChunkLoaded(cell)||w.getBlockState(cell).getCollisionShape(w,cell).getBoundingBoxes().stream().anyMatch(box->box.offset(cell).expand(.012).contains(at)))throw new CircuitError("error.electron.cord_route");
            }
        }
        return List.copyOf(path);
    }
    public static void stamp(ServerWorld w,CircuitState s,List<DcSolver.Branch> branches,Set<String> unavailable){
        for(var c:s.cords.values()){
            double r=Math.max(.002,c.length()*.006);
            for(int pole=0;pole<2;pole++){
                String a=node(c.source,2+pole),b=node(c.load,pole);
                branches.add(DcSolver.Branch.resistor("cord:"+c.id+":"+pole,a,b,r));
                if(c.path.stream().anyMatch(p->!w.isChunkLoaded(BlockPos.ofFloored(p)))){unavailable.add(a);unavailable.add(b);}
            }
        }
    }
    public static void maintain(ServerWorld w,CircuitState s){
        boolean changed=false;
        for(var it=s.cords.values().iterator();it.hasNext();){var c=it.next();
            if(!w.isChunkLoaded(c.source)||!w.isChunkLoaded(c.load))continue;
            boolean inspect=Math.floorMod(w.getTime()+c.id.hashCode(),20)==0&&c.path.stream().allMatch(p->w.isChunkLoaded(BlockPos.ofFloored(p)));
            if(!Registries.BLOCK.getId(w.getBlockState(c.source).getBlock()).toString().equals(c.sourceBlock)||!Registries.BLOCK.getId(w.getBlockState(c.load).getBlock()).toString().equals(c.loadBlock)||inspect&&!clearPath(w,c)){
                it.remove();s.cordHeat.remove(c.id);net.minecraft.util.ItemScatterer.spawn(w,c.load.getX()+.5,c.load.getY()+.5,c.load.getZ()+.5,new ItemStack(Appliances.CORD));changed=true;
            }
        }if(changed)s.changed();
    }
    private static boolean clearPath(ServerWorld w,Lead c){
        for(int i=2;i<c.path.size()-2;i++){
            var a=c.path.get(i-1);var b=c.path.get(i);int steps=Math.max(1,(int)Math.ceil(a.distanceTo(b)*8));
            for(int j=0;j<=steps;j++){var v=a.lerp(b,j/(double)steps);var p=BlockPos.ofFloored(v);
                if(p.equals(c.source)||p.equals(c.load))continue;
                if(!w.getFluidState(p).isEmpty()||w.getBlockState(p).getCollisionShape(w,p).getBoundingBoxes().stream().anyMatch(box->box.offset(p).expand(.012).contains(v)))return false;
                if(Math.abs(v.y-Math.floor(v.y)-.035)<.001&&!Block.isShapeFullCube(w.getBlockState(p.down()).getCollisionShape(w,p.down())))return false;
            }
        }return true;
    }
    public static void update(ServerWorld w,CircuitState s){
        for(var it=s.cords.values().iterator();it.hasNext();){var c=it.next();double amps=Math.max(Math.abs(s.readings.current("cord:"+c.id+":0")),Math.abs(s.readings.current("cord:"+c.id+":1")));
            if(s.paused.containsKey(node(c.source,2)))continue;
            double oldHeat=s.cordHeat.getOrDefault(c.id,0d);double heat=Math.max(0,oldHeat+(amps>2.5?(amps/2.5-1)*.05:-.05));if(heat!=oldHeat){s.cordHeat.put(c.id,heat);s.markDirty();}
            if(heat>=3){var at=c.path.get(c.path.size()/2);FaultEffects.emit(w,at,at.add(.08,0,0),FaultEffects.MELT,amps*24);it.remove();s.cordHeat.remove(c.id);s.changed();net.minecraft.util.ItemScatterer.spawn(w,at.x,at.y,at.z,new ItemStack(Foundry.SCRAP));}
        }
    }
    public static void write(CircuitState s,NbtCompound n){
        var list=new NbtList();for(var c:s.cords.values()){var tag=new NbtCompound();tag.putUuid("id",c.id);tag.putLong("source",c.source.asLong());tag.putLong("load",c.load.asLong());tag.putInt("socket",c.socket);tag.putString("source_block",c.sourceBlock);tag.putString("load_block",c.loadBlock);tag.putDouble("heat",s.cordHeat.getOrDefault(c.id,0d));
            var points=new NbtList();for(var p:c.path){var point=new NbtCompound();point.putDouble("x",p.x);point.putDouble("y",p.y);point.putDouble("z",p.z);points.add(point);}tag.put("path",points);list.add(tag);}
        n.put("appliance_cords",list);
    }
    public static void read(CircuitState s,NbtCompound n){
        for(var entry:n.getList("appliance_cords",NbtElement.COMPOUND_TYPE)){var t=(NbtCompound)entry;
            var path=new ArrayList<Vec3d>();for(var v:t.getList("path",NbtElement.COMPOUND_TYPE)){var p=(NbtCompound)v;path.add(new Vec3d(p.getDouble("x"),p.getDouble("y"),p.getDouble("z")));}
            if(!t.containsUuid("id")||path.size()<2||path.size()>128||path.stream().anyMatch(p->!Double.isFinite(p.lengthSquared())))continue;
            var c=new Lead(t.getUuid("id"),BlockPos.fromLong(t.getLong("source")),BlockPos.fromLong(t.getLong("load")),Math.clamp(t.getInt("socket"),0,2),t.getString("source_block"),t.getString("load_block"),path);
            if(c.length()>12.01)continue;s.cords.put(c.id,c);s.cordHeat.put(c.id,Math.clamp(t.getDouble("heat"),0,3));
        }
    }
    private CordNetwork(){}
}
