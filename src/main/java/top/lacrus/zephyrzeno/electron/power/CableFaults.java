package top.lacrus.zephyrzeno.electron.power;

import java.util.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.circuit.CircuitParts.Wire;

/** Passive faults in the actual DC island; ground refers to that island's source return. */
public final class CableFaults {
    public record Fault(String id,UUID wire,String a,String b,Vec3d from,Vec3d to,double resistance,UUID victim){}
    public static final class State {
        public final Map<String,Fault> faults=new LinkedHashMap<>();
        private final Map<UUID,Long> shockUntil=new HashMap<>();
        private long scanned=Long.MIN_VALUE;
    }
    /** Gameplay distances in blocks, not an electrical installation standard. */
    public static double clearance(double voltage){double v=Math.abs(voltage);return v<=48?0:v<=300?.45:v<=6600?1.2:v<=11000?1.6:v<=24000?2.4:3.5;}
    public static List<Vec3d> vertices(CircuitState s,Wire w){return w.overhead()?s.overheadVertices(w):w.vertical()?VerticalWire.vertices(s,w.path()):WireGeometry.vertices(w.path(),s::inset);}
    public static Vec3d middle(CircuitState s,Wire w){var p=vertices(s,w);double length=0;for(int i=1;i<p.size();i++)length+=p.get(i).distanceTo(p.get(i-1));double left=length/2;for(int i=1;i<p.size();i++){double d=p.get(i).distanceTo(p.get(i-1));if(d>=left)return p.get(i-1).lerp(p.get(i),d<1e-9?0:left/d);left-=d;}return p.getFirst();}
    private static List<Vec3d> samples(List<Vec3d> curve){var result=new ArrayList<Vec3d>();for(int i=1;i<curve.size();i++){var a=curve.get(i-1);var b=curve.get(i);int n=Math.max(1,(int)Math.ceil(a.distanceTo(b)/.4));for(int j=0;j<n;j++)result.add(a.lerp(b,j/(double)n));}result.add(curve.getLast());return result;}
    public record Impact(Vec3d wire,Vec3d surface,double gap){}
    public static Optional<Impact> terrain(ServerWorld w,List<Vec3d> curve,double distance){
        Impact best=null;double closest=distance;
        for(var v:samples(curve)){
            if(v.squaredDistanceTo(curve.getFirst())<1||v.squaredDistanceTo(curve.getLast())<1)continue;
            for(var pos:BlockPos.iterate(BlockPos.ofFloored(v.subtract(distance,distance,distance)),BlockPos.ofFloored(v.add(distance,distance,distance)))){
                if(!w.isChunkLoaded(pos))continue;var boxes=w.getBlockState(pos).getCollisionShape(w,pos).getBoundingBoxes();
                if(!w.getFluidState(pos).isEmpty())boxes=List.of(new Box(0,0,0,1,w.getFluidState(pos).getHeight(w,pos),1));
                for(var box:boxes){var b=box.offset(pos);var q=new Vec3d(Math.clamp(v.x,b.minX,b.maxX),Math.clamp(v.y,b.minY,b.maxY),Math.clamp(v.z,b.minZ,b.maxZ));double gap=v.distanceTo(q);if(gap<closest){closest=gap;best=new Impact(v,q,gap);}}
            }
        }return Optional.ofNullable(best);
    }
    private static void add(State s,String id,Wire wire,String other,Vec3d from,Vec3d to,double voltage,UUID victim){s.faults.put(id,new Fault(id,wire.id(),wire.a(),other,from,to,victim==null?Math.max(120,voltage*voltage/120):1000,victim));}
    public static void stamp(ServerWorld w,CircuitState s,List<DcSolver.Branch> branches,State state){
        if(state.scanned==Long.MIN_VALUE||w.getTime()-state.scanned>=5){
            state.scanned=w.getTime();state.faults.clear();var curves=new LinkedHashMap<Wire,List<Vec3d>>();
            for(var wire:s.wires.values()){
                String ref=s.readings.reference().get(wire.a());if(ref==null||s.paused.containsKey(wire.a()))continue;
                var curve=vertices(s,wire);if(curve.size()<2||curve.stream().anyMatch(p->!w.isChunkLoaded(BlockPos.ofFloored(p))))continue;
                double voltage=s.readings.voltage(wire.a())-s.readings.voltage(ref),range=clearance(voltage);curves.put(wire,curve);if(range==0)continue;
                if(wire.overhead()||wire.vertical())terrain(w,curve,range).ifPresent(impact->add(state,"ground:"+wire.id(),wire,ref,impact.wire,impact.surface,voltage,null));
                for(var entity:w.getEntitiesByClass(LivingEntity.class,bounds(curve).expand(range+.4),e->!e.isSpectator()&&!(e instanceof PlayerEntity p&&p.isCreative()))){
                    var body=entity.getBoundingBox();Vec3d contact=null,target=null;double gap=wire.overhead()||wire.vertical()?range:.12;
                    for(var sample:samples(curve)){var q=new Vec3d(Math.clamp(sample.x,body.minX,body.maxX),Math.clamp(sample.y,body.minY,body.maxY),Math.clamp(sample.z,body.minZ,body.maxZ));double d=sample.distanceTo(q);if(d<gap){gap=d;contact=sample;target=q;}}
                    if(contact!=null&&visible(w,contact,target,entity))add(state,"touch:"+wire.id()+":"+entity.getUuid(),wire,ref,contact,target,voltage,entity.getUuid());
                }
            }
            // Spatial indexing scales to arbitrary wire counts; no quadratic scan of distant cables.
            record Probe(Wire wire,Vec3d p){}var buckets=new HashMap<BlockPos,List<Probe>>();var pairs=new HashSet<String>();
            for(var entry:curves.entrySet())if(entry.getKey().overhead()||entry.getKey().vertical())for(var p:samples(entry.getValue())){
                var wire=entry.getKey();var cell=BlockPos.ofFloored(p.multiply(.25));
                for(var q:BlockPos.iterate(cell.add(-1,-1,-1),cell.add(1,1,1)))for(var probe:buckets.getOrDefault(q,List.of())){
                    if(wire.id().equals(probe.wire.id())||!s.readings.commonReference(wire.a(),probe.wire.a()))continue;
                    String key="cross:"+wire.id()+":"+probe.wire.id();if(pairs.contains(key))continue;double volts=s.readings.voltage(wire.a())-s.readings.voltage(probe.wire.a());
                    if(p.distanceTo(probe.p)<clearance(volts)&&visible(w,p,probe.p,null)){pairs.add(key);add(state,key,wire,probe.wire.a(),p,probe.p,volts,null);}
                }buckets.computeIfAbsent(cell.toImmutable(),k->new ArrayList<>()).add(new Probe(wire,p));
            }
        }
        var nodes=new HashSet<String>();for(var b:branches){nodes.add(b.a());nodes.add(b.b());}
        var groups=new HashMap<String,String>();for(var b:branches){groups.putIfAbsent(b.a(),b.a());groups.putIfAbsent(b.b(),b.b());groups.put(root(groups,b.a()),root(groups,b.b()));}
        state.faults.values().removeIf(f->!s.wires.containsKey(f.wire)||!nodes.contains(f.a)||!nodes.contains(f.b)||!root(groups,f.a).equals(root(groups,f.b))||!w.isChunkLoaded(BlockPos.ofFloored(f.from))||!w.isChunkLoaded(BlockPos.ofFloored(f.to))||f.victim!=null&&!contactPresent(w,f));
        for(var f:state.faults.values())branches.add(DcSolver.Branch.resistor(f.id,f.a,f.b,f.resistance));
    }
    private static Box bounds(List<Vec3d> curve){Box b=new Box(curve.getFirst(),curve.getFirst());for(var p:curve)b=b.union(new Box(p,p));return b;}
    private static String root(Map<String,String> groups,String n){var p=groups.get(n);if(!p.equals(n)){p=root(groups,p);groups.put(n,p);}return p;}
    private static boolean contactPresent(ServerWorld w,Fault f){var entity=w.getEntity(f.victim);return entity instanceof LivingEntity l&&l.isAlive()&&l.getBoundingBox().expand(.2).contains(f.to)&&visible(w,f.from,l.getBoundingBox().getCenter(),l);}
    private static boolean visible(ServerWorld w,Vec3d a,Vec3d b,net.minecraft.entity.Entity e){if(a.squaredDistanceTo(b)<1e-8)return true;var hit=w.raycast(new net.minecraft.world.RaycastContext(a,b,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,e==null?net.minecraft.block.ShapeContext.absent():net.minecraft.block.ShapeContext.of(e)));return hit.getType()==net.minecraft.util.hit.HitResult.Type.MISS||hit.getPos().squaredDistanceTo(b)<.02;}
    public static void effects(ServerWorld w,CircuitState s,State state){
        if(w.getTime()%5!=0)return;state.shockUntil.entrySet().removeIf(e->e.getValue()<w.getTime());
        for(var f:state.faults.values()){
            double current=Math.abs(s.readings.current(f.id)),volts=Math.abs(s.readings.voltage(f.a)-s.readings.voltage(f.b)),power=current*volts;
            if(current<.002||power<.1||s.paused.containsKey(f.a))continue;Vec3d end=f.to;LivingEntity victim=null;
            if(f.victim!=null){var entity=w.getEntity(f.victim);if(!(entity instanceof LivingEntity l)||!l.isAlive()||!l.getBoundingBox().expand(.4).contains(f.to))continue;victim=l;var box=l.getBoundingBox();end=new Vec3d(Math.clamp(f.from.x,box.minX,box.maxX),Math.clamp(f.from.y,box.minY,box.maxY),Math.clamp(f.from.z,box.minZ,box.maxZ));}
            FaultEffects.emit(w,f.from,end,victim==null?FaultEffects.ARC:FaultEffects.SHOCK,power,volts);
            if(victim!=null)shock(w,state,victim,power);
            else if(f.id.startsWith("ground:")){double radius=w.getFluidState(BlockPos.ofFloored(f.to)).isEmpty()?1:2;
                for(var e:w.getEntitiesByClass(LivingEntity.class,new Box(end,end).expand(radius),e->!e.isSpectator()))if(e.getPos().squaredDistanceTo(end)<radius*radius&&visible(w,end.add(0,.06,0),e.getPos().add(0,.1,0),e))shock(w,state,e,power*.25);
            }
        }
        if(w.getTime()%10==0){var done=new HashSet<String>();for(var source:s.lastBranches)if(!source.led()&&Math.abs(source.emf())>=3){
            double current=Math.abs(s.readings.current(source.id())),volts=Math.abs(s.readings.voltage(source.a())-s.readings.voltage(source.b()));
            if(current<.01||volts>Math.abs(source.emf())*.15||volts/current>10||s.paused.containsKey(source.a()))continue;
            String ref=s.readings.reference().get(source.a());if(ref==null||!done.add(ref))continue;
            s.wires.values().stream().filter(wire->s.readings.commonReference(wire.a(),source.a())).max(Comparator.comparingDouble(wire->Math.abs(s.readings.current("w:"+wire.id())))).ifPresent(wire->{var p=middle(s,wire);FaultEffects.emit(w,p.add(0,.015,0),p.add(.15,.06,0),FaultEffects.ARC,current*Math.abs(source.emf()),source.emf());});
        }}
    }
    private static void shock(ServerWorld w,State s,LivingEntity e,double power){if(e instanceof PlayerEntity p&&(p.isCreative()||p.isSpectator())||s.shockUntil.getOrDefault(e.getUuid(),-1L)>w.getTime())return;s.shockUntil.put(e.getUuid(),w.getTime()+20);e.damage(w.getDamageSources().lightningBolt(),(float)Math.clamp(.5+Math.sqrt(power)*.5,1,6));e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,15,0,false,false,true));}
    private CableFaults(){}
}
