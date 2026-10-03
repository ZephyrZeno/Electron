package top.lacrus.zephyrzeno.electron.circuit;
import top.lacrus.zephyrzeno.electron.item.CircuitToolItem;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;
import top.lacrus.zephyrzeno.electron.logic.WireLogic;

/** Persistent attachments on ordinary blocks; no replacement wall or hidden remote link. */
public final class CircuitState extends PersistentState {
    public static final int MAX_CORES=128,MAX_JUNCTIONS=256,MAX_BRIDGES=256;
    private static final Map<ServerWorld,CircuitState> LIVE=new WeakHashMap<>();
    private static final Type<CircuitState> TYPE=new Type<>(CircuitState::new,(n,r)->read(n),null);
    public record Device(String block,Direction face,int turn,boolean formed) {public Device(String block,Direction face,int turn){this(block,face,turn,false);}}
    public final Map<UUID,Wire> wires=new LinkedHashMap<>();
    public final Map<UUID,Junction> junctions=new LinkedHashMap<>();
    public final Map<UUID,WallCore> cores=new LinkedHashMap<>();
    public final Map<UUID,Bridge> bridges=new LinkedHashMap<>();
    public final Map<BlockPos,Device> devices=new HashMap<>();
    public DcSolver.Result readings=new DcSolver.Result(Map.of(),Map.of(),Map.of());
    public final Map<String,String> paused=new HashMap<>();
    private final top.lacrus.zephyrzeno.electron.power.CableFaults.State lowCables=new top.lacrus.zephyrzeno.electron.power.CableFaults.State();
    public List<DcSolver.Branch> lastBranches=List.of();
    public final Map<UUID,Double> wireHeat=new HashMap<>();
    private final Map<Wire,List<Vec3d>> overheadCurves=new HashMap<>();
    public List<Vec3d> overheadVertices(Wire wire){return overheadCurves.computeIfAbsent(wire,w->OverheadCable.vertices(this,w.path()));}
    private boolean syncPending=true;
    private final LinkedHashSet<UUID> capturedLightning=new LinkedHashSet<>();
    /** A bolt may flash repeatedly or overlap another collector; its energy is captured only once. */
    public boolean claimLightning(UUID id){
        if(!capturedLightning.add(id))return false;
        if(capturedLightning.size()>4096)capturedLightning.removeFirst();
        markDirty();return true;
    }
    public static CircuitState get(ServerWorld world){return LIVE.computeIfAbsent(world,w->w.getPersistentStateManager().getOrCreate(TYPE,"electron_circuits"));}
    public static void register(){ServerTickEvents.END_WORLD_TICK.register(world->get(world).tick(world));ServerLifecycleEvents.SERVER_STOPPED.register(server->LIVE.clear());}
    public static boolean attached(World world,BlockPos pos){return world instanceof ServerWorld s&&LIVE.containsKey(s)&&LIVE.get(s).hasAttachment(pos);}
    public boolean hasAttachment(BlockPos pos){return junctions.values().stream().anyMatch(j->j.point().host().equals(pos))||cores.values().stream().anyMatch(c->c.host().equals(pos))||bridges.values().stream().anyMatch(b->b.host().equals(pos))||wires.values().stream().anyMatch(w->w.path().stream().anyMatch(p->p.host().equals(pos)));}
    public void changed(){overheadCurves.keySet().retainAll(new HashSet<>(wires.values()));markDirty();syncPending=true;}
    public void track(BlockPos pos,BlockState state) {
        if(!(state.getBlock() instanceof ComponentBlock))return;
        Device d=new Device(Registries.BLOCK.getId(state.getBlock()).toString(),state.get(ComponentBlock.FACING),state.get(ComponentBlock.TURN),top.lacrus.zephyrzeno.electron.multiblock.Assembly.formed(state));
        if(!d.equals(devices.put(pos.toImmutable(),d))){overheadCurves.clear();changed();}
    }
    public Map<String,Endpoint> endpoints() {
        Map<String,Endpoint> result=new HashMap<>();
        devices.forEach((pos,d)->{
            if(!(Registries.BLOCK.get(net.minecraft.util.Identifier.of(d.block)) instanceof ComponentBlock c))return;
            if(c.kind==ComponentBlock.Kind.WIND&&d.formed)return;
            for(int pin=0;pin<ComponentPins.count(c.kind);pin++){if(c==top.lacrus.zephyrzeno.electron.industrial.Industrial.WIND_BASE&&pin<2)continue;var e=ComponentPins.endpoint(pos,d.face,d.turn,c,pin);result.put(e.id(),e);}
        });
        junctions.values().forEach(j->{var e=j.endpoint();result.put(e.id(),e);});
        cores.values().forEach(c->c.endpoints().forEach(e->result.put(e.id(),e)));return result;
    }
    public Optional<Endpoint> endpoint(BlockPos host,Direction face,Vec3d hit) {
        return endpoints().values().stream().filter(e->e.point().host().equals(host)&&e.point().face()==face)
            .min(Comparator.comparingDouble(e->position(e.point()).squaredDistanceTo(hit))).filter(e->position(e.point()).squaredDistanceTo(hit)<.18);
    }
    public int inset(SurfacePoint p){var d=devices.get(p.host());return d!=null&&d.face==p.face()&&Registries.BLOCK.get(net.minecraft.util.Identifier.of(d.block)) instanceof ComponentBlock b?ComponentGeometry.inset(b.kind):0;}
    public Vec3d position(SurfacePoint p){return p.position().subtract(Vec3d.of(p.face().getVector()).multiply(inset(p)/16d));}
    public int lengthUnits(List<SurfacePoint> path){return WireGeometry.lengthUnits(path,this::inset);}
    public boolean traceClear(SurfacePoint p){if(hasBridge(p)&&BridgeGeometry.reserved(p))return false;var d=devices.get(p.host());if(d==null||d.face!=p.face())return true;var uv=ComponentGeometry.rotate(p.u(),p.v(),4-d.turn);return !(Registries.BLOCK.get(net.minecraft.util.Identifier.of(d.block)) instanceof ComponentBlock b)||ComponentGeometry.traceClear(b.kind,(int)uv[0],(int)uv[1],p.layer());}
    public static boolean supports(World world,BlockPos pos,Direction face) {
        if(!world.isChunkLoaded(pos)||!world.isChunkLoaded(pos.offset(face)))return false;
        BlockState state=world.getBlockState(pos);
        boolean support=top.lacrus.zephyrzeno.electron.multiblock.Assembly.serviceSupport(world,pos)||(state.getBlock() instanceof ComponentBlock component?(component.kind.large()||state.get(ComponentBlock.FACING)==face):Block.isShapeFullCube(state.getCollisionShape(world,pos)));
        var neighbor=world.getBlockState(pos.offset(face));
        boolean shaft=state.isOf(Foundry.MOTOR)&&state.get(ComponentBlock.FACING)==face&&neighbor.getBlock() instanceof top.lacrus.zephyrzeno.electron.mechanical.MechanicalBlock&&neighbor.get(top.lacrus.zephyrzeno.electron.mechanical.MechanicalBlock.FACING).getAxis()==face.getAxis();
        boolean partialAssembly=world.getBlockEntity(pos.offset(face)) instanceof top.lacrus.zephyrzeno.electron.multiblock.AssemblyPartEntity&&!Block.isShapeFullCube(neighbor.getCollisionShape(world,pos.offset(face)));
        return support&&!state.isOf(Blocks.BEDROCK)&&(shaft||partialAssembly||top.lacrus.zephyrzeno.electron.multiblock.Assembly.internalNeighbor(world,pos,face)||neighbor.getCollisionShape(world,pos.offset(face)).isEmpty());
    }
    /** A thin tower brace must not block every point of the foundation face below it. */
    public boolean assemblyClear(World world,SurfacePoint p){
        var neighbor=p.host().offset(p.face());
        if(!(world.getBlockEntity(neighbor) instanceof top.lacrus.zephyrzeno.electron.multiblock.AssemblyPartEntity)||top.lacrus.zephyrzeno.electron.multiblock.Assembly.internalNeighbor(world,p.host(),p.face()))return true;
        var at=position(p);
        return world.getBlockState(neighbor).getCollisionShape(world,neighbor).getBoundingBoxes().stream().noneMatch(box->box.offset(neighbor).expand(.011).contains(at));
    }
    public boolean hasBridge(SurfacePoint p){return bridges.values().stream().anyMatch(b->b.host().equals(p.host())&&b.face()==p.face());}
    public void validateBridgeSpace(SurfacePoint point){
        java.util.function.Predicate<SurfacePoint> occupied=p->p.host().equals(point.host())&&p.face()==point.face()&&BridgeGeometry.reserved(p);
        if(endpoints().values().stream().anyMatch(e->occupied.test(e.point()))||wires.values().stream().anyMatch(w->w.path().stream().anyMatch(occupied)))throw new CircuitError("error.electron.bridge_feet_occupied");
    }
    public Optional<Endpoint> exactEndpoint(SurfacePoint p){return endpoints().values().stream().filter(e->e.point().equals(p)).findFirst();}
    public Optional<Endpoint> contactAt(SurfacePoint p){
        var pins=endpoints();var exact=exactEndpoint(p);if(exact.isPresent())return exact;
        for(var w:wires.values()){
            if(w.path().getFirst().equals(p))return Optional.ofNullable(pins.get(w.a()));
            if(w.path().getLast().equals(p))return Optional.ofNullable(pins.get(w.b()));
        }
        // Clicking the elevated conductor above a ground-level node must not short the layers.
        if(wires.values().stream().anyMatch(w->w.path().contains(p)))return Optional.empty();
        return p.layer()==1?exactEndpoint(p.layer(0)):Optional.empty();
    }
    public CircuitState wiringCopy(){var copy=new CircuitState();copy.devices.putAll(devices);copy.wires.putAll(wires);copy.junctions.putAll(junctions);copy.cores.putAll(cores);copy.bridges.putAll(bridges);return copy;}
    public void commitWiring(CircuitState copy){wires.clear();wires.putAll(copy.wires);junctions.clear();junctions.putAll(copy.junctions);changed();}
    /** Called on a transaction copy. A solder node splits every conductor at this exact layer. */
    public Endpoint joinAt(World world,SurfacePoint p){return joinAt(world,p,false);}
    public Endpoint joinAt(World world,SurfacePoint p,boolean cable){
        var existing=contactAt(p);if(existing.isPresent())return existing.get();
        if(!supports(world,p.host(),p.face())||!traceClear(p)||!assemblyClear(world,p)||(p.layer()==1&&!hasBridge(p)))throw new CircuitError("error.electron.node_surface");
        if(junctions.size()>=MAX_JUNCTIONS)throw new CircuitError("error.electron.node_budget");
        var j=new Junction(UUID.randomUUID(),p,Registries.BLOCK.getId(world.getBlockState(p.host()).getBlock()).toString(),true,cable);
        for(var old:new ArrayList<>(wires.values())){
            int index=old.path().indexOf(p);if(index<=0||index>=old.path().size()-1)continue;
            // Paid material is partitioned, never recomputed or duplicated when a run is tapped.
            int left=(int)((long)old.units()*index/(old.path().size()-1));
            var a=new Wire(old.id(),old.a(),j.endpoint().id(),old.color(),List.copyOf(old.path().subList(0,index+1)),left,old.cable());
            var b=new Wire(UUID.randomUUID(),j.endpoint().id(),old.b(),old.color(),List.copyOf(old.path().subList(index,old.path().size())),old.units()-left,old.cable());
            wires.put(a.id(),a);wires.put(b.id(),b);
        }
        long conductors=wires.values().stream().filter(w->w.hosts().contains(p.host())).count()+cores.values().stream().filter(c->c.host().equals(p.host())).count()*2;
        if(conductors>64)throw new CircuitError("error.electron.tap_cell_budget");
        junctions.put(j.id(),j);return j.endpoint();
    }
    public int lengthUnits(List<SurfacePoint> path,Endpoint a,Endpoint b){return lengthUnits(path)-2*path.getFirst().layer()-2*path.getLast().layer()+2*Math.abs(path.getFirst().layer()-a.point().layer())+2*Math.abs(path.getLast().layer()-b.point().layer());}
    public List<SurfacePoint> connectionRoute(World world,Endpoint a,Endpoint b,int layer){
        var start=a.point().layer(layer);var end=b.point().layer(layer);Set<SurfacePoint> used=new HashSet<>();wires.values().forEach(w->used.addAll(w.path()));
        Set<SurfacePoint> pins=new HashSet<>();endpoints().values().forEach(e->{if(!e.id().equals(a.id())&&!e.id().equals(b.id()))pins.add(e.point());});
        java.util.function.Function<SurfacePoint,SurfacePoint> height=p->layer==0&&!p.equals(start)&&!p.equals(end)&&(used.contains(p)||pins.contains(p))&&hasBridge(p)?p.layer(1):p;
        java.util.function.Predicate<SurfacePoint> available=point->{var p=height.apply(point);boolean endpoint=point.equals(start)||point.equals(end);
            return supports(world,p.host(),p.face())&&(p.layer()==0||hasBridge(p))&&traceClear(p)&&assemblyClear(world,p)&&(endpoint||(!used.contains(p)&&!pins.contains(p)));};
        if(!available.test(start)||!available.test(end))throw new CircuitError("error.electron.endpoint_blocked");
        List<SurfacePoint> base;
        try{base=SurfaceRouter.straight(start,end,available);}catch(IllegalArgumentException blocked){base=SurfaceRouter.route(start,end,available,this::inset);}
        var result=base.stream().map(height).toList();
        if(lengthUnits(result,a,b)>512)throw new CircuitError("error.electron.length");
        return result;
    }
    public List<SurfacePoint> connectionRoute(World world,Endpoint a,Endpoint b,int layer,boolean cable){
        if(VerticalWire.connection(this,a,b))return VerticalWire.route(world,this,a,b,layer,cable);
        return OverheadCable.connection(this,a,b,cable)?OverheadCable.route(world,this,a,b,layer):connectionRoute(world,a,b,layer);
    }
    public List<SurfacePoint> route(ServerWorld world,Endpoint a,Endpoint b,int layer) {
        SurfacePoint start=a.point().layer(layer),end=b.point().layer(layer);
        if(!supports(world,start.host(),start.face())||!supports(world,end.host(),end.face()))throw new CircuitError("error.electron.port_blocked");
        Set<SurfacePoint> used=new HashSet<>();wires.values().forEach(w->used.addAll(w.path()));
        Set<SurfacePoint> otherPins=new HashSet<>();endpoints().values().forEach(e->{if(!e.id().equals(a.id())&&!e.id().equals(b.id())){otherPins.add(e.point().layer(0));otherPins.add(e.point().layer(1));}});
        return SurfaceRouter.route(start,end,p->supports(world,p.host(),p.face())&&(p.layer()==0||hasBridge(p))&&traceClear(p)&&assemblyClear(world,p)&&!otherPins.contains(p)&&(!used.contains(p)||p.equals(start)||p.equals(end)),this::inset);
    }
    public void validateCoreCapacity(BlockPos host) {
        long count=wires.values().stream().filter(w->w.hosts().contains(host)).count()+cores.values().stream().filter(c->c.host().equals(host)).count()*2;
        if(count+2>64)throw new CircuitError("error.electron.core_cell_budget");
    }
    public void validateCapacity(List<SurfacePoint> path) {
        Set<BlockPos> hosts=new HashSet<>();path.forEach(p->hosts.add(p.host()));
        for(BlockPos host:hosts) {
            long conductors=wires.values().stream().filter(w->w.path().stream().anyMatch(p->p.host().equals(host))).count()+cores.values().stream().filter(c->c.host().equals(host)).count()*2;
            if(conductors>=64)throw new CircuitError("error.electron.cell_budget");
            long turns=0;for(var w:wires.values())turns+=segments(w.path(),host);turns+=segments(path,host);
            if(turns>128)throw new CircuitError("error.electron.geometry_budget");
        }
    }
    private static int segments(List<SurfacePoint> points,BlockPos host){int count=0,axis=-1;for(int i=1;i<points.size();i++){var a=points.get(i-1);var b=points.get(i);if(!a.host().equals(host)&&!b.host().equals(host))continue;int next=a.position().x!=b.position().x?0:a.position().y!=b.position().y?1:2;if(axis!=next){count++;axis=next;}}return count;}
    private static boolean hostValid(ServerWorld world,BlockPos pos,String expected){return Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString().equals(expected)&&!world.getBlockState(pos).hasBlockEntity()&&Block.isShapeFullCube(world.getBlockState(pos).getCollisionShape(world,pos));}
    private void validate(ServerWorld world) {
        boolean change=false;
        for(var it=devices.entrySet().iterator();it.hasNext();) {var e=it.next();if(world.isChunkLoaded(e.getKey())&&(!(world.getBlockState(e.getKey()).getBlock() instanceof ComponentBlock)||!Registries.BLOCK.getId(world.getBlockState(e.getKey()).getBlock()).toString().equals(e.getValue().block))){it.remove();change=true;}}
        for(var it=cores.values().iterator();it.hasNext();){var c=it.next();if(world.isChunkLoaded(c.host())&&!hostValid(world,c.host(),c.hostBlock())){it.remove();drop(world,c.host(),new ItemStack(Foundry.WALL_PORT));change=true;}}
        for(var it=junctions.values().iterator();it.hasNext();){var j=it.next();if(world.isChunkLoaded(j.point().host())&&world.isChunkLoaded(j.point().host().offset(j.point().face()))&&(!supports(world,j.point().host(),j.point().face())||!assemblyClear(world,j.point())||(j.point().layer()==1&&!hasBridge(j.point())))){it.remove();drop(world,j.point().host(),j.wireNode()?CircuitToolItem.spool(1,0,j.cable()):new ItemStack(Foundry.TERMINAL));change=true;}}
        for(var it=bridges.values().iterator();it.hasNext();){var b=it.next();if(world.isChunkLoaded(b.host())&&world.isChunkLoaded(b.host().offset(b.face()))&&!supports(world,b.host(),b.face())){it.remove();drop(world,b.host(),new ItemStack(Foundry.BRIDGE));change=true;}}
        var pins=endpoints();
        for(var it=wires.values().iterator();it.hasNext();) {
            Wire w=it.next();boolean invalid=!pins.containsKey(w.a())||!pins.containsKey(w.b());
            if(!invalid&&w.overhead()&&world.getTime()%20==0){
                var curve=overheadVertices(w);
                if(OverheadCable.loaded(world,curve))invalid=!OverheadCable.tower(world,w.path().getFirst().host())||!OverheadCable.tower(world,w.path().getLast().host())||!OverheadCable.clear(world,curve,w.path().getFirst().host(),w.path().getLast().host());
            }
            if(!invalid&&w.vertical()&&VerticalWire.cells(this,w.path()).stream().allMatch(world::isChunkLoaded))invalid=!VerticalWire.connection(this,pins.get(w.a()),pins.get(w.b()))||!VerticalWire.clear(world,this,w.path(),w.cable());
            if(!invalid)for(var p:w.path())if(world.isChunkLoaded(p.host())&&world.isChunkLoaded(p.host().offset(p.face()))&&(!supports(world,p.host(),p.face())||!assemblyClear(world,p)||(p.layer()==1&&!hasBridge(p)))){invalid=true;break;}
            if(invalid){it.remove();drop(world,w.path().getFirst().host(),CircuitToolItem.spool(w.units(),w.color(),w.cable()));change=true;}
        }
        if(change)changed();
    }
    private static void drop(ServerWorld world,BlockPos pos,ItemStack stack){if(world.isChunkLoaded(pos))Block.dropStack(world,pos,stack);}
    public void tick(ServerWorld world) {
        validate(world);simulate(world);
        if(syncPending){CircuitNetworking.broadcast(world,this);syncPending=false;}
    }
    public void simulate(ServerWorld world) {
        var pins=endpoints();List<DcSolver.Branch> branches=new ArrayList<>();Set<String> unavailable=new HashSet<>();
        for(var w:wires.values()) {
            branches.add(DcSolver.Branch.resistor("w:"+w.id(),w.a(),w.b(),w.resistance()));
            if(w.vertical()&&VerticalWire.cells(this,w.path()).stream().anyMatch(p->!world.isChunkLoaded(p))){unavailable.add(w.a());unavailable.add(w.b());}
            if(w.overhead()&&!OverheadCable.loaded(world,overheadVertices(w))){unavailable.add(w.a());unavailable.add(w.b());}
            for(var p:w.path())if(!world.isChunkLoaded(p.host())||!world.isChunkLoaded(p.host().offset(p.face()))){unavailable.add(w.a());unavailable.add(w.b());break;}
        }
        for(var core:cores.values())for(int pin=0;pin<2;pin++)branches.add(DcSolver.Branch.resistor("core:"+core.id()+":"+pin,core.endpoint(0,pin).id(),core.endpoint(1,pin).id(),.02));
        List<Endpoint> portPins=cores.values().stream().flatMap(c->c.endpoints().stream()).toList();
        Map<BlockPos,List<Integer>> portsByHost=new HashMap<>();
        for(int i=0;i<portPins.size();i++)portsByHost.computeIfAbsent(portPins.get(i).point().host(),p->new ArrayList<>()).add(i);
        for(int i=0;i<portPins.size();i++){
            var a=portPins.get(i);
            for(int j:portsByHost.getOrDefault(a.point().host().offset(a.point().face()),List.of())){
                if(j<=i)continue;var b=portPins.get(j);
                if(a.point().face()==b.point().face().getOpposite()&&a.point().position().squaredDistanceTo(b.point().position())<.004)
                    branches.add(DcSolver.Branch.resistor("contact:"+a.id()+":"+b.id(),a.id(),b.id(),.01));
            }
        }
        for(var entry:devices.entrySet()) {
            BlockPos pos=entry.getKey();String a=node(pos,0),b=node(pos,1),id="d:"+pos.asLong();
            if(!world.isChunkLoaded(pos)){
                if(!(Registries.BLOCK.get(net.minecraft.util.Identifier.of(entry.getValue().block)) instanceof ComponentBlock stored))continue;
                if(stored.kind==ComponentBlock.Kind.TERMINAL_STRIP||stored.kind==ComponentBlock.Kind.DISTRIBUTION){terminalBranches(branches,pos,.002);for(int pin=0;pin<8;pin++)unavailable.add(node(pos,pin));continue;}
                if(stored.kind==ComponentBlock.Kind.RELAY){branches.add(DcSolver.Branch.resistor(id,a,b,1e12));branches.add(DcSolver.Branch.resistor(id+":contact",node(pos,2),node(pos,3),1e12));for(int pin=0;pin<4;pin++)unavailable.add(node(pos,pin));continue;}
                unavailable.add(a);unavailable.add(b);branches.add(DcSolver.Branch.resistor(id,a,b,1e12));
                for(int pin=2;pin<ComponentPins.count(stored.kind);pin++){unavailable.add(node(pos,pin));branches.add(DcSolver.Branch.resistor(id+":"+pin,node(pos,pin),b,1e12));}
                continue;
            }
            if(!(world.getBlockEntity(pos) instanceof ComponentEntity entity))continue;
            BlockState state=world.getBlockState(pos);ComponentBlock block=(ComponentBlock)state.getBlock();
            boolean broken=state.get(ComponentBlock.BROKEN);
            if(top.lacrus.zephyrzeno.electron.power.GridDevices.handles(block.kind)){top.lacrus.zephyrzeno.electron.power.GridDevices.stamp(world,pos,state,entity,branches);continue;}
            if(entity instanceof top.lacrus.zephyrzeno.electron.pcb.PcbSocketEntity pcb){pcb.stamp(branches,world.getTime());continue;}
            if(PowerDevices.handles(block.kind)){PowerDevices.stamp(block,state,entity,pos,branches);continue;}
            if(block.kind==ComponentBlock.Kind.TERMINAL_STRIP){terminalBranches(branches,pos,broken?1e12:.002);continue;}
            if(block.kind==ComponentBlock.Kind.BATTERY){
                branches.add(broken?DcSolver.Branch.resistor("charge:"+id,a,b,1e12):BatteryModel.input("charge:"+id,a,b,entity.energy));
                branches.add(BatteryModel.output(id,node(pos,2),node(pos,3),broken?0:entity.energy,state.get(ComponentBlock.ENABLED)));
                branches.add(DcSolver.Branch.resistor("return:"+id,b,node(pos,3),.02));continue;
            }
            if(block.kind==ComponentBlock.Kind.GATE) {
                if(entity.logicDue<=world.getTime()&&entity.logicState!=entity.pendingLogic){entity.logicState=entity.pendingLogic;entity.markDirty();}
                branches.add(DcSolver.Branch.resistor(id,a,b,broken?1e12:10000));
                branches.add(DcSolver.Branch.resistor("inA:"+id,node(pos,2),b,1e7));branches.add(DcSolver.Branch.resistor("inB:"+id,node(pos,3),b,1e7));
                branches.add(DcSolver.Branch.resistor("out:"+id,node(pos,4),entity.logicState==1?a:b,!broken&&entity.logicState<2?100:1e12));continue;
            }
            if(state.get(ComponentBlock.BROKEN))branches.add(DcSolver.Branch.resistor(id,a,b,1e12));
            else switch(block.kind) {
                case RESISTOR -> branches.add(DcSolver.Branch.resistor(id,a,b,block.resistance));
                case SWITCH, BUTTON -> branches.add(DcSolver.Branch.resistor(id,a,b,state.get(ComponentBlock.ENABLED)?.02:1e12));
                case CAPACITOR -> branches.add(CapacitorModel.branch(id,a,b,entity.capacitorVoltage));
                case LED -> branches.add(DcSolver.Branch.led(id,a,b));
                case DIODE -> branches.add(DcSolver.Branch.diode(id,a,b));
                case FUSE -> branches.add(DcSolver.Branch.resistor(id,a,b,.2));
                case REDSTONE_SWITCH -> branches.add(DcSolver.Branch.resistor(id,a,b,RedstoneSwitchModel.COIL_OHMS));
                case CRANK -> {double emf=state.get(ComponentBlock.ENABLED)&&entity.energy>1e-9?5*Math.min(1,entity.energy/.25):0;branches.add(DcSolver.Branch.source(id,a,b,emf,emf>0?5:1e12));}
                case PCB,GATE -> throw new AssertionError("Gate stamp handled above");
                case BATTERY,TERMINAL_STRIP -> throw new AssertionError("Multi-pin stamp handled above");
                case THERMAL,HYDRO,WIND,LIGHTNING,NUCLEAR,SUBSTATION,MAINS,MOSFET,RELAY,BOOST,BUCK,GENERATOR,DISTRIBUTION,BREAKER,LAMP,MOTOR->throw new AssertionError("Power stamp handled above");
            }
        }
        top.lacrus.zephyrzeno.electron.power.CableFaults.stamp(world,this,branches,lowCables);
        lastBranches=List.copyOf(branches);
        for(var e:pins.values())if(!world.isChunkLoaded(e.point().host()))unavailable.add(e.id());else if(world.isChunkLoaded(e.point().host().offset(e.point().face()))&&!world.getFluidState(e.point().host().offset(e.point().face())).isEmpty())unavailable.add(e.id());
        Map<String,String> parent=new HashMap<>();for(var b:branches){parent.putIfAbsent(b.a(),b.a());parent.putIfAbsent(b.b(),b.b());parent.put(find(parent,b.a()),find(parent,b.b()));}
        Map<String,List<DcSolver.Branch>> groups=new LinkedHashMap<>();for(var b:branches)groups.computeIfAbsent(find(parent,b.a()),k->new ArrayList<>()).add(b);
        Set<String> excluded=new HashSet<>();for(String n:unavailable)if(parent.containsKey(n))excluded.add(find(parent,n));
        // Stored-energy devices couple availability even when their electrical ports are isolated.
        // Otherwise an unloaded input could freeze energy accounting while its output still powers a load.
        boolean propagated;
        do{propagated=false;for(var entry:devices.entrySet()){
            var registered=Registries.BLOCK.get(net.minecraft.util.Identifier.of(entry.getValue().block));
            if(!(registered instanceof ComponentBlock device)||ComponentPins.count(device.kind)<=2)continue;
            Set<String> sides=new HashSet<>();for(int pin=0;pin<ComponentPins.count(device.kind);pin++){var n=node(entry.getKey(),pin);if(parent.containsKey(n))sides.add(find(parent,n));}
            if(sides.stream().anyMatch(excluded::contains))for(var side:sides)propagated|=excluded.add(side);
        }}while(propagated);
        Map<String,DcSolver.Result> solved=new HashMap<>();Map<String,String> failures=new HashMap<>();
        for(var group:groups.entrySet()) {
            String error=excluded.contains(group.getKey())?"error.electron.network_unavailable":null;
            if(error==null)try{solved.put(group.getKey(),DcSolver.solve(group.getValue()));}catch(IllegalStateException failure){error=failure.getMessage();}
            if(error!=null)failures.put(group.getKey(),error);
        }
        // A solver failure must also pause the isolated side before any load consumes power.
        do{propagated=false;for(var entry:devices.entrySet()){
            var registered=Registries.BLOCK.get(net.minecraft.util.Identifier.of(entry.getValue().block));
            if(!(registered instanceof ComponentBlock device)||ComponentPins.count(device.kind)<=2)continue;
            Set<String> sides=new HashSet<>();for(int pin=0;pin<ComponentPins.count(device.kind);pin++){var n=node(entry.getKey(),pin);if(parent.containsKey(n))sides.add(find(parent,n));}
            String error=sides.stream().filter(failures::containsKey).map(failures::get).findFirst().orElse(null);
            if(error!=null)for(var side:sides)if(failures.putIfAbsent(side,error)==null)propagated=true;
        }}while(propagated);
        Map<String,Double> volts=new HashMap<>(),amps=new HashMap<>();Map<String,String> refs=new HashMap<>();paused.clear();
        for(var group:groups.entrySet()){
            String error=failures.get(group.getKey());
            if(error!=null)for(var b:group.getValue()){paused.put(b.a(),error);paused.put(b.b(),error);}
            else{var result=solved.get(group.getKey());volts.putAll(result.voltage());amps.putAll(result.current());refs.putAll(result.reference());}
        }
        readings=new DcSolver.Result(Map.copyOf(volts),Map.copyOf(amps),Map.copyOf(refs));
        Map<String,String> strong=new HashMap<>();Set<String> driven=new HashSet<>();
        for(var branch:branches){strong.putIfAbsent(branch.a(),branch.a());strong.putIfAbsent(branch.b(),branch.b());if(branch.resistance()<1e6&&(!branch.led()||Math.abs(readings.current(branch.id()))>1e-7))strong.put(find(strong,branch.a()),find(strong,branch.b()));}
        for(var branch:branches)if(branch.emf()>0&&!branch.led()&&!paused.containsKey(branch.a()))driven.add(find(strong,branch.a()));
        Map<String,DcSolver.Branch> model=new HashMap<>();branches.forEach(b->model.put(b.id(),b));
        for(BlockPos pos:devices.keySet())if(world.isChunkLoaded(pos)&&world.getBlockEntity(pos) instanceof ComponentEntity entity) {
            String id="d:"+pos.asLong();BlockState state=world.getBlockState(pos);var block=(ComponentBlock)state.getBlock();
            entity.paused=paused.containsKey(node(pos,0));
            for(int pin=1;pin<ComponentPins.count(block.kind);pin++)entity.paused|=paused.containsKey(node(pos,pin));
            entity.current=readings.current(id);entity.voltage=readings.voltage(node(pos,0))-readings.voltage(node(pos,1));
            if(entity.paused){
                if(entity instanceof top.lacrus.zephyrzeno.electron.pcb.PcbSocketEntity pcb)pcb.pause();
                if(top.lacrus.zephyrzeno.electron.power.GridDevices.handles(block.kind)&&state.get(ComponentBlock.LEVEL)!=0)world.setBlockState(pos,state.with(ComponentBlock.LEVEL,0),Block.NOTIFY_ALL);
                if(PowerDevices.handles(block.kind))PowerDevices.pause(world,pos,state,entity);
                if(block.kind==ComponentBlock.Kind.REDSTONE_SWITCH){entity.signal=0;entity.contactClosed=entity.pendingContact=false;if(state.get(ComponentBlock.LEVEL)!=0||state.get(ComponentBlock.ENABLED))world.setBlockState(pos,state.with(ComponentBlock.LEVEL,0).with(ComponentBlock.ENABLED,false),Block.NOTIFY_ALL);}
                continue;
            }
            if(top.lacrus.zephyrzeno.electron.power.GridDevices.handles(block.kind)){top.lacrus.zephyrzeno.electron.power.GridDevices.update(world,pos,state,entity,readings);continue;}
            if(entity instanceof top.lacrus.zephyrzeno.electron.pcb.PcbSocketEntity pcb){pcb.update(readings,n->strong.containsKey(n)&&driven.contains(find(strong,n)));continue;}
            if(PowerDevices.handles(block.kind)){PowerDevices.update(world,pos,state,entity,readings);continue;}
            if(block.kind==ComponentBlock.Kind.GATE) {
                double supply=entity.voltage;
                int a=WireLogic.input(readings.voltage(node(pos,2))-readings.voltage(node(pos,1)),supply,driven.contains(find(strong,node(pos,2))));
                int b=WireLogic.input(readings.voltage(node(pos,3))-readings.voltage(node(pos,1)),supply,driven.contains(find(strong,node(pos,3))));
                int value=state.get(ComponentBlock.BROKEN)||supply<.5?3:supply<4.5||supply>5.5?2:WireLogic.evaluate(block.logic,a,b);
                if(value!=entity.pendingLogic){entity.pendingLogic=value;entity.logicDue=world.getTime()+2;}
                entity.current+=entity.logicState==1?Math.max(0,-readings.current("out:"+id)):0;
            }
            if(block.kind==ComponentBlock.Kind.CRANK){entity.energy=Math.max(0,entity.energy-Math.max(0,-entity.current*model.get(id).emf())*.05);if(entity.energy<1e-8)entity.energy=0;entity.markDirty();}
            if(block.kind==ComponentBlock.Kind.BATTERY){
                entity.inputVoltage=entity.voltage;entity.inputCurrent=readings.current("charge:"+id);
                entity.voltage=readings.voltage(node(pos,2))-readings.voltage(node(pos,3));
                entity.energy=state.get(ComponentBlock.BROKEN)?0:BatteryModel.nextEnergy(entity.energy,entity.inputVoltage,entity.inputCurrent,entity.current);entity.markDirty();
            }
            if(block.kind==ComponentBlock.Kind.REDSTONE_SWITCH){
                boolean requested=!state.get(ComponentBlock.BROKEN)&&RedstoneSwitchModel.contact(entity.voltage,entity.contactClosed);
                if(requested!=entity.pendingContact){entity.pendingContact=requested;entity.contactDue=world.getTime()+2;}
                if(entity.contactDue<=world.getTime()&&entity.contactClosed!=entity.pendingContact){entity.contactClosed=entity.pendingContact;ElectricalEffects.play(world,pos,ElectricalEffects.SWITCH,.25f,entity.contactClosed?1.1f:.85f);}
                if(state.get(ComponentBlock.BROKEN))entity.contactClosed=entity.pendingContact=false;
                var rear=ComponentBlock.redstoneSide(state).getOpposite();
                entity.signal=RedstoneSwitchModel.transmit(world.getEmittedRedstonePower(pos.offset(rear),rear),entity.contactClosed);
            }
            if(block.kind==ComponentBlock.Kind.CAPACITOR){
                double next=state.get(ComponentBlock.BROKEN)?0:CapacitorModel.nextVoltage(entity.capacitorVoltage,entity.current);
                if(next!=entity.capacitorVoltage){entity.capacitorVoltage=next;entity.markDirty();}
            }
            double ratio=block.kind==ComponentBlock.Kind.LED?Math.max(0,entity.current)/.03:block.kind==ComponentBlock.Kind.RESISTOR?Math.abs(entity.current*entity.voltage)/.25:block.kind==ComponentBlock.Kind.GATE?Math.max(Math.abs(readings.current("out:"+id))/.02,Math.max(0,entity.voltage)/5.5):block.kind==ComponentBlock.Kind.DIODE?Math.abs(entity.current)/.05:block.kind==ComponentBlock.Kind.FUSE?Math.abs(entity.current)/.025:block.kind==ComponentBlock.Kind.CAPACITOR?CapacitorModel.overloadRatio(entity.capacitorVoltage,entity.current):0;
            if(block.kind==ComponentBlock.Kind.BATTERY)ratio=Math.max(Math.max(entity.inputVoltage/6,-entity.inputVoltage/.5),Math.max(entity.voltage/6,-entity.voltage/.5));
            if(block.kind==ComponentBlock.Kind.REDSTONE_SWITCH)ratio=Math.max(entity.voltage/6,-entity.voltage/.5);
            double heat=Math.max(0,entity.overload+(ratio>1?(block.kind==ComponentBlock.Kind.FUSE?ratio*ratio-1:ratio-1)*.05:-.05));if(heat!=entity.overload){entity.overload=heat;entity.markDirty();}
            if(entity.overload>=(block.kind==ComponentBlock.Kind.FUSE?.25:3)&&!state.get(ComponentBlock.BROKEN)){ElectricalEffects.burn(world,pos,state,block.kind==ComponentBlock.Kind.CAPACITOR||(block.kind!=ComponentBlock.Kind.FUSE&&ratio>=2.5));state=state.with(ComponentBlock.BROKEN,true);if(block.kind==ComponentBlock.Kind.CAPACITOR){entity.capacitorVoltage=0;entity.markDirty();}if(block.kind==ComponentBlock.Kind.BATTERY){entity.energy=0;entity.markDirty();}entity.signal=0;entity.contactClosed=entity.pendingContact=false;}
            else if(entity.overload>.45&&ratio>1&&!state.get(ComponentBlock.BROKEN)&&world.getTime()-entity.lastEffectTick>=16){entity.lastEffectTick=world.getTime();ElectricalEffects.overload(world,pos,state);}
            int level=block.kind==ComponentBlock.Kind.LED&&!state.get(ComponentBlock.BROKEN)?(int)Math.clamp(Math.round(Math.max(0,entity.current)/.015*15),0,15):block.kind==ComponentBlock.Kind.CRANK?(int)Math.ceil(entity.energy/200*15):block.kind==ComponentBlock.Kind.GATE?entity.logicState==1?15:entity.logicState==2?7:0:0;
            if(block.kind==ComponentBlock.Kind.BATTERY)level=state.get(ComponentBlock.BROKEN)?0:(int)Math.ceil(entity.energy/BatteryModel.CAPACITY*15);
            if(block.kind==ComponentBlock.Kind.REDSTONE_SWITCH)level=state.get(ComponentBlock.BROKEN)?0:entity.signal;
            if(block.kind==ComponentBlock.Kind.REDSTONE_SWITCH)state=state.with(ComponentBlock.ENABLED,entity.contactClosed);
            if(block.kind==ComponentBlock.Kind.LED&&level>0&&state.get(ComponentBlock.LEVEL)==0&&world.getTime()-entity.lastLedSoundTick>=20){entity.lastLedSoundTick=world.getTime();ElectricalEffects.play(world,pos,ElectricalEffects.LED,.08f,1.6f);}
            if(state!=world.getBlockState(pos)||state.get(ComponentBlock.LEVEL)!=level)world.setBlockState(pos,state.with(ComponentBlock.LEVEL,level),Block.NOTIFY_ALL);
        }
        top.lacrus.zephyrzeno.electron.power.CableFaults.effects(world,this,lowCables);
        wireHeat.keySet().retainAll(wires.keySet());
        for(var wire:new ArrayList<>(wires.values())){
            if(paused.containsKey(wire.a()))continue;double ratio=Math.abs(readings.current("w:"+wire.id()))/wire.rating();
            double before=wireHeat.getOrDefault(wire.id(),0d),heat=Math.max(0,before+(ratio>1?(ratio-1)*.05:-.05));
            if(heat!=before){if(heat==0)wireHeat.remove(wire.id());else wireHeat.put(wire.id(),heat);markDirty();}
            if(heat>.6&&ratio>1&&world.getTime()%10==0){var point=top.lacrus.zephyrzeno.electron.power.CableFaults.middle(this,wire);world.spawnParticles(ParticleTypes.SMOKE,point.x,point.y,point.z,2,.02,.02,.02,.008);top.lacrus.zephyrzeno.electron.power.FaultEffects.emit(world,point.add(-.1,0,0),point,1,ratio*heat);}
            if(heat>=3){var point=top.lacrus.zephyrzeno.electron.power.CableFaults.middle(this,wire);var at=BlockPos.ofFloored(point);if(world.isChunkLoaded(at)){top.lacrus.zephyrzeno.electron.power.FaultEffects.emit(world,point.add(-.14,0,0),point.add(.14,0,0),2,ratio*24);drop(world,at,new ItemStack(Foundry.SCRAP));}wires.remove(wire.id());wireHeat.remove(wire.id());changed();}
        }
    }
    private static void terminalBranches(List<DcSolver.Branch> branches,BlockPos pos,double resistance){
        for(int rail=0;rail<2;rail++)for(int pin=0;pin<3;pin++)branches.add(DcSolver.Branch.resistor("bus:"+pos.asLong()+":"+rail+":"+pin,node(pos,rail*4+pin),node(pos,rail*4+pin+1),resistance));
    }
    private static String find(Map<String,String> p,String n){String x=p.get(n);if(!x.equals(n)){x=find(p,x);p.put(n,x);}return x;}
    public NbtCompound snapshot(){var n=writeNbt(new NbtCompound(),null);n.remove("captured_lightning");return n;}
    @Override public NbtCompound writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup registries) {
        NbtList strikes=new NbtList();for(var id:capturedLightning){var t=new NbtCompound();t.putUuid("id",id);strikes.add(t);}n.put("captured_lightning",strikes);
        NbtList ws=new NbtList();for(var w:wires.values()){var t=new NbtCompound();t.putUuid("id",w.id());t.putString("a",w.a());t.putString("b",w.b());t.putInt("color",w.color());t.putInt("units",w.units());t.putBoolean("cable",w.cable());t.putBoolean("overhead",w.overhead());t.putBoolean("vertical",w.vertical());t.putDouble("heat",wireHeat.getOrDefault(w.id(),0d));long[] hosts=new long[w.path().size()];int[] points=new int[hosts.length];for(int i=0;i<hosts.length;i++){var p=w.path().get(i);hosts[i]=p.host().asLong();points[i]=p.face().getId()|p.u()<<3|p.v()<<7|p.layer()<<11;}t.putLongArray("hosts",hosts);t.putIntArray("points",points);ws.add(t);}n.put("wires",ws);
        NbtList js=new NbtList();junctions.values().forEach(j->{var t=j.point().nbt();t.putUuid("id",j.id());t.putString("block",j.hostBlock());t.putBoolean("wire_node",j.wireNode());t.putBoolean("cable_node",j.cable());js.add(t);});n.put("junctions",js);
        NbtList cs=new NbtList();cores.values().forEach(c->{var t=new NbtCompound();t.putUuid("id",c.id());t.putLong("p",c.host().asLong());t.putInt("f",c.face().getId());t.putInt("slot",c.slot());t.putString("block",c.hostBlock());cs.add(t);});n.put("cores",cs);
        NbtList bs=new NbtList();bridges.values().forEach(b->{var t=new NbtCompound();t.putUuid("id",b.id());t.putLong("p",b.host().asLong());t.putInt("f",b.face().getId());t.putString("block",b.hostBlock());bs.add(t);});n.put("bridges",bs);
        NbtList ds=new NbtList();devices.forEach((p,d)->{var t=new NbtCompound();t.putLong("p",p.asLong());t.putString("block",d.block);t.putInt("f",d.face.getId());t.putInt("turn",d.turn);t.putBoolean("formed",d.formed);ds.add(t);});n.put("devices",ds);return n;
    }
    public static CircuitState read(NbtCompound n) {
        CircuitState s=new CircuitState();
        for(var tag:n.getList("captured_lightning",NbtElement.COMPOUND_TYPE)){var t=(NbtCompound)tag;if(t.containsUuid("id"))s.capturedLightning.add(t.getUuid("id"));if(s.capturedLightning.size()>4096)s.capturedLightning.removeFirst();}
        for(var tag:n.getList("wires",NbtElement.COMPOUND_TYPE)){var t=(NbtCompound)tag;long[] hosts=t.getLongArray("hosts");int[] packed=t.getIntArray("points");if(hosts.length!=packed.length||hosts.length<2||hosts.length>513)continue;List<SurfacePoint> path=new ArrayList<>();for(int i=0;i<hosts.length;i++)path.add(new SurfacePoint(BlockPos.fromLong(hosts[i]),Direction.byId(packed[i]&7),(packed[i]>>3)&15,(packed[i]>>7)&15,(packed[i]>>11)&1));var w=new Wire(t.getUuid("id"),t.getString("a"),t.getString("b"),t.getInt("color")&7,path,t.contains("units")?Math.clamp(t.getInt("units"),0,16384):SurfaceRouter.lengthUnits(path),t.getBoolean("cable"),t.getBoolean("overhead")&&t.getBoolean("cable")&&hosts.length==2,t.getBoolean("vertical")&&hosts.length==2);s.wires.put(w.id(),w);double heat=t.getDouble("heat");if(Double.isFinite(heat)&&heat>0)s.wireHeat.put(w.id(),Math.min(heat,3));}
        for(var tag:n.getList("junctions",NbtElement.COMPOUND_TYPE)){var t=(NbtCompound)tag;var j=new Junction(t.getUuid("id"),SurfacePoint.read(t),t.getString("block"),t.getBoolean("wire_node"),t.getBoolean("cable_node"));s.junctions.put(j.id(),j);}
        for(var tag:n.getList("cores",NbtElement.COMPOUND_TYPE)){var t=(NbtCompound)tag;var c=new WallCore(t.getUuid("id"),BlockPos.fromLong(t.getLong("p")),Direction.byId(t.getInt("f")),t.getInt("slot")&3,t.getString("block"));s.cores.put(c.id(),c);}
        for(var tag:n.getList("bridges",NbtElement.COMPOUND_TYPE)){var t=(NbtCompound)tag;var b=new Bridge(t.getUuid("id"),BlockPos.fromLong(t.getLong("p")),Direction.byId(t.getInt("f")),t.getString("block"));s.bridges.put(b.id(),b);}
        for(var tag:n.getList("devices",NbtElement.COMPOUND_TYPE)){var t=(NbtCompound)tag;s.devices.put(BlockPos.fromLong(t.getLong("p")),new Device(t.getString("block"),Direction.byId(t.getInt("f")),t.getInt("turn")&3,t.getBoolean("formed")));}return s;
    }
}
