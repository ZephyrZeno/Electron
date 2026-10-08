package top.lacrus.zephyrzeno.electron.power;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.Industrial;
import top.lacrus.zephyrzeno.electron.multiblock.Assembly;

/** A recessed terminal cabinet. Shutters retract within its cell; closing never disconnects a wire. */
public final class WiringCabinet {
    public record Part(Box box,String texture,int pin){}
    /** Face patches share the real carrier plane; printed areas replace, never cover, metal. */
    public record Surface(Box box,Direction side,String texture){}
    private static final Map<String,List<Part>> PARTS=new HashMap<>();
    private static final Map<String,List<Surface>> SURFACES=new HashMap<>();
    private static final Map<String,VoxelShape> SHAPES=new HashMap<>();
    public static boolean present(BlockState s){
        return s.isOf(Industrial.WIND_BASE)||Assembly.formed(s)&&s.getBlock() instanceof ComponentBlock b
            &&b.kind!=ComponentBlock.Kind.WIND&&GridDevices.handles(b.kind);
    }
    public static boolean accessible(net.minecraft.world.World w,BlockPos p){
        return !present(w.getBlockState(p))||w.getBlockEntity(p) instanceof ComponentEntity e&&e.cabinetOpen;
    }
    public static void toggle(ServerWorld w,ComponentEntity e){
        e.cabinetOpen=!e.cabinetOpen;e.markDirty();
        w.updateListeners(e.getPos(),e.getCachedState(),e.getCachedState(),Block.NOTIFY_LISTENERS);
        ElectricalEffects.play(w,Vec3d.of(e.getPos()).add(ComponentGeometry.local(e.getCachedState().get(ComponentBlock.FACING),e.getCachedState().get(ComponentBlock.TURN),8,8,.6)),
            e.cabinetOpen?ElectricalEffects.CABINET_OPEN:ElectricalEffects.CABINET_CLOSE,.45f,1);
    }
    public static synchronized List<Part> parts(ComponentBlock.Kind kind,boolean windBase,boolean open){
        return PARTS.computeIfAbsent(kind+":"+windBase+":"+open,key->{
            var parts=new ArrayList<Part>();
            // Canonical coordinates: terminal plane is y=15/16, matching all existing saved cable endpoints.
            add(parts,0,10,0,16,11,16,"cabinet_inside");
            add(parts,0,11,0,1,16,16,"cabinet_skin");add(parts,15,11,0,16,16,16,"cabinet_skin");
            add(parts,1,11,0,15,16,1,"cabinet_skin");add(parts,1,11,15,15,16,16,"cabinet_skin");
            for(int pin=windBase?2:0;pin<ComponentPins.count(kind);pin++){
                double x=ComponentPins.u(kind,pin),z=ComponentPins.v(kind,pin);
                // The printed end extends away from the cable gland, never underneath it.
                double front=z<8?z-1.1:z-2.6,back=z<8?z+2.6:z+1.1;
                parts.add(new Part(new Box((x-1.2)/16,11d/16,front/16,(x+1.2)/16,14.4/16,back/16),"cabinet_inside",pin));
                add(parts,x-.65,14.4,z-.65,x+.65,15.35,z+.65,"cabinet_contact");
                add(parts,x-.35,15.35,z-.10,x+.35,15.4,z+.10,"cast_base");
                // A cable gland reaches the edge at the same height as the routed conductor.
                add(parts,x-.48,14.2,z<8?1:z+.8,x+.48,15.1,z<8?z-.8:15,"cast_base");
            }
            for(double x:new double[]{1.4,14.2})for(double z:new double[]{1.4,14.2})
                add(parts,x,15.4,z,x+.4,15.7,z+.4,"galvanized");
            if(open){
                add(parts,1.05,15.7,1,1.55,15.97,15,"cabinet_door");
                add(parts,14.45,15.7,1,14.95,15.97,15,"cabinet_door");
            }else{
                add(parts,1,15.7,1,7.94,15.97,15,"cabinet_door");
                add(parts,8.06,15.7,1,15,15.97,15,"cabinet_door");
                add(parts,6.65,15.98,6,7.35,16,10,"galvanized");
                add(parts,8.65,15.98,6,9.35,16,10,"galvanized");
            }
            return List.copyOf(parts);
        });
    }
    private static void add(List<Part> parts,double x,double y,double z,double X,double Y,double Z,String t){
        parts.add(new Part(new Box(x/16,y/16,z/16,X/16,Y/16,Z/16),t,-1));
    }
    public static synchronized List<Surface> surfaces(ComponentBlock.Kind kind,boolean windBase,boolean open){
        return SURFACES.computeIfAbsent(kind+":"+windBase+":"+open,key->{
            var result=new ArrayList<Surface>();
            for(var part:parts(kind,windBase,open)){
                var b=part.box();String material="assembled/"+part.texture();
                for(var side:Direction.values())if(side!=Direction.UP||part.pin()<0)result.add(new Surface(b,side,material));
                if(part.pin()<0)continue;
                double pinZ=ComponentPins.v(kind,part.pin());
                double x0=b.minX+.1/16,x1=b.maxX-.1/16;
                double z0=pinZ<8?(pinZ+.73)/16:b.minZ+.1/16,z1=pinZ<8?b.maxZ-.1/16:(pinZ-.73)/16;
                result.add(new Surface(new Box(b.minX,b.minY,b.minZ,x0,b.maxY,b.maxZ),Direction.UP,material));
                result.add(new Surface(new Box(x1,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ),Direction.UP,material));
                result.add(new Surface(new Box(x0,b.minY,b.minZ,x1,b.maxY,z0),Direction.UP,material));
                result.add(new Surface(new Box(x0,b.minY,z1,x1,b.maxY,b.maxZ),Direction.UP,material));
                boolean four=ComponentPins.count(kind)==4;
                String label=(four?"mains_inlet":"hand_crank")+"_pin_"+part.pin();
                result.add(new Surface(new Box(x0,b.minY,z0,x1,b.maxY,z1),Direction.UP,label));
            }
            return List.copyOf(result);
        });
    }
    public static Box orient(Box b,BlockState s){
        var a=ComponentGeometry.local(s.get(ComponentBlock.FACING),s.get(ComponentBlock.TURN),b.minX*16,b.minZ*16,(1-b.minY)*16);
        var z=ComponentGeometry.local(s.get(ComponentBlock.FACING),s.get(ComponentBlock.TURN),b.maxX*16,b.maxZ*16,(1-b.maxY)*16);
        return new Box(a,z);
    }
    public static synchronized VoxelShape shape(BlockState s,boolean open){
        return SHAPES.computeIfAbsent(s.get(ComponentBlock.FACING)+":"+s.get(ComponentBlock.TURN)+":"+open,key->{
            if(!open)return VoxelShapes.cuboid(orient(new Box(0,.625,0,1,1,1),s));
            var shape=VoxelShapes.cuboid(orient(new Box(0,.625,0,1,.875,1),s));
            for(var box:List.of(new Box(0,.875,0,.125,1,1),new Box(.875,.875,0,1,1,1),new Box(.125,.875,0,.875,1,.0625),new Box(.125,.875,.9375,.875,1,1)))
                shape=VoxelShapes.union(shape,VoxelShapes.cuboid(orient(box,s)));
            return shape;
        });
    }
    private WiringCabinet(){}
}
