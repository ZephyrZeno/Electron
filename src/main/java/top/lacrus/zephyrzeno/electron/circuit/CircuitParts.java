package top.lacrus.zephyrzeno.electron.circuit;

import java.util.*;
import net.minecraft.util.math.*;
import net.minecraft.text.Text;

public final class CircuitParts {
    public record Endpoint(String id,SurfacePoint point,Text name) {}
    public record Junction(UUID id,SurfacePoint point,String hostBlock,boolean wireNode,boolean cable) {
        public Junction(UUID id,SurfacePoint point,String hostBlock){this(id,point,hostBlock,false,false);}
        public Junction(UUID id,SurfacePoint point,String hostBlock,boolean wireNode){this(id,point,hostBlock,wireNode,false);}
        public Endpoint endpoint(){return new Endpoint("j:"+id,point,Text.translatable(wireNode?"pin.electron.node":"pin.electron.terminal"));}
    }
    public record Bridge(UUID id,BlockPos host,Direction face,String hostBlock) {}
    public record WallCore(UUID id,BlockPos host,Direction face,int slot,String hostBlock) {
        public int u(){return (slot&1)==0?3:11;}
        public int v(){return (slot&2)==0?3:11;}
        public Endpoint endpoint(int side,int channel){Direction f=side==0?face:face.getOpposite();return new Endpoint("p:"+id+":"+side+":"+channel,new SurfacePoint(host,f,u()+(channel==0?-2:2),v(),0),Text.translatable("pin.electron.wall_core",channel));}
        public List<Endpoint> endpoints(){return List.of(endpoint(0,0),endpoint(0,1),endpoint(1,0),endpoint(1,1));}
        public Box volume() {
            Vec3d a=endpoint(0,0).point.position().subtract(Vec3d.of(face.getVector()).multiply(.03125));
            Vec3d b=endpoint(1,1).point.position().add(Vec3d.of(face.getVector()).multiply(.03125));
            return new Box(a,b).expand(.0624);
        }
    }
    public record Wire(UUID id,String a,String b,int color,List<SurfacePoint> path,int units,boolean cable,boolean overhead) {
        public Wire(UUID id,String a,String b,int color,List<SurfacePoint> path,int units,boolean cable){this(id,a,b,color,path,units,cable,false);}
        public Wire(UUID id,String a,String b,int color,List<SurfacePoint> path,int units){this(id,a,b,color,path,units,false);}
        public Wire { path=List.copyOf(path); }
        public double resistance(){return Math.max(.0001,units/16d*(cable?.002:.02));}
        public double rating(){return cable?5:.25;}
        public Set<BlockPos> hosts(){Set<BlockPos> result=new HashSet<>();path.forEach(p->result.add(p.host()));return result;}
    }
    public static String node(BlockPos pos,int pin){return "c:"+pos.asLong()+":"+pin;}
    public static Endpoint devicePin(BlockPos pos,Direction face,int pin){return new Endpoint(node(pos,pin),new SurfacePoint(pos,face,pin==0?3:11,7,0),Text.translatable(pin==0?"pin.electron.plus":"pin.electron.minus"));}
    public static Endpoint gatePin(BlockPos pos,Direction face,int pin){int[] u={3,11,3,11,7},v={13,13,3,3,7};return new Endpoint(node(pos,pin),new SurfacePoint(pos,face,u[pin],v[pin],0),Text.translatable("pin.electron."+new String[]{"vcc","gnd","a","b","y"}[pin]));}
}
