package top.lacrus.zephyrzeno.electron.showcase;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.display.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.pcb.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

/** Export-time construction only. Saved play uses ordinary components, copper and wires. */
final class DecimalCircuitLesson {
    static final BlockPos ORIGIN=new BlockPos(46,65,142);
    private final ServerWorld world;private final CircuitState circuit;private final Map<String,List<Endpoint>> nets=new LinkedHashMap<>();
    DecimalCircuitLesson(ServerWorld w,CircuitState c){world=w;circuit=c;}
    private BlockPos device(int x,int z,ComponentBlock block){var p=ORIGIN.add(x,1,z);var s=block.getDefaultState().with(ComponentBlock.FACING,Direction.UP);world.setBlockState(p,s);circuit.track(p,s);var bridge=new Bridge(UUID.randomUUID(),p,Direction.UP,net.minecraft.registry.Registries.BLOCK.getId(block).toString());circuit.bridges.put(bridge.id(),bridge);return p;}
    private void pin(String net,BlockPos p,int n){nets.computeIfAbsent(net,k->new ArrayList<>()).add(circuit.endpoints().get(node(p,n)));}
    void build(BlockPos supply){
        for(int x=0;x<27;x++)for(int z=0;z<32;z++){var p=ORIGIN.add(x,0,z);world.setBlockState(p,Blocks.SMOOTH_STONE.getDefaultState());var bridge=new Bridge(UUID.randomUUID(),p,Direction.UP,"minecraft:smooth_stone");circuit.bridges.put(bridge.id(),bridge);}
        var buck=device(2,3,Foundry.BUCK);wire(circuit.endpoints().get(node(supply,2)),circuit.endpoints().get(node(buck,0)),0,true);wire(circuit.endpoints().get(node(supply,3)),circuit.endpoints().get(node(buck,1)),1,true);pin("V",buck,2);pin("G",buck,3);
        var a=device(8,2,Displays.DIAL);var b=device(14,2,Displays.DIAL);((DisplayEntity)world.getBlockEntity(a)).setting=9;((DisplayEntity)world.getBlockEntity(b)).setting=7;
        for(var pos:List.of(a,b)){pin("V",pos,0);pin("G",pos,1);}for(int bit=0;bit<4;bit++){pin("A"+bit,a,bit+2);pin("B"+bit,b,bit+2);}
        var sw=device(20,2,Foundry.SWITCH);world.setBlockState(sw,world.getBlockState(sw).with(ComponentBlock.ENABLED,false));var pull=device(23,2,Foundry.RESISTOR_10000);pin("V",sw,0);pin("SUB",sw,1);pin("SUB",pull,0);pin("G",pull,1);
        var design=DecimalGateDesign.create();int index=0;
        for(var cell:design.cells()){
            int row=index/6,col=index%6;if((row&1)!=0)col=5-col;var p=device(4+col*3,6+row*3,cell.pcb()?Pcb.SOCKET:Foundry.WIRE_GATES.get(cell.gate()));index++;
            if(cell.pcb()){var e=(PcbSocketEntity)world.getBlockEntity(p);e.plantItems.set(0,Pcb.board(PcbLayout.halfAdder()));e.boardChanged();}
            for(int pin=0;pin<cell.pins().size();pin++)pin(cell.pins().get(pin),p,pin);
        }
        for(int display=0;display<3;display++){
            BlockPos master=null;for(int u=0;u<2;u++)for(int v=0;v<2;v++){var p=device(5+display*6+u,27+v,Displays.PANEL);if(u==0&&v==0)master=p;if(display==0)((DisplayEntity)world.getBlockEntity(p)).signMode=true;}
            pin("V",master,0);pin("G",master,1);for(int bit=0;bit<4;bit++)pin(display==0?(bit==0?design.negative():"G"):display==1?(bit==0?design.tens():"G"):design.units().get(bit),bit==0?master:master.east(),bit+2);
        }
        // Reserve a short, physical lead for every used pad before longer runs can surround it.
        // These are ordinary solder nodes on the tabletop, not hidden links or virtual buses.
        var escapes=new HashMap<String,Endpoint>();
        for(var entry:nets.entrySet()){var net=entry.getValue();for(int i=0;i<net.size();i++){
            var original=net.get(i);var escaped=escapes.get(original.id());
            if(escaped==null){escaped=escape(original,color(entry.getKey()));escapes.put(original.id(),escaped);}net.set(i,escaped);
        }}
        // Connect each net by a short spanning tree, preserving actual wire resistance and supports.
        for(var entry:nets.entrySet()){var todo=new ArrayList<>(entry.getValue());var done=new ArrayList<Endpoint>();done.add(todo.removeFirst());int color=color(entry.getKey());
            while(!todo.isEmpty()){
                record Candidate(Endpoint from,Endpoint to,double distance){}
                var candidates=new ArrayList<Candidate>();for(var a0:done){long degree=circuit.wires.values().stream().filter(w->w.a().equals(a0.id())||w.b().equals(a0.id())).count();if(degree>=3)continue;for(var b0:todo)candidates.add(new Candidate(a0,b0,circuit.position(a0.point()).squaredDistanceTo(circuit.position(b0.point()))));}candidates.sort(Comparator.comparingDouble(Candidate::distance));
                IllegalStateException failure=null;Endpoint connected=null;for(var candidate:candidates){try{wire(candidate.from,candidate.to,color,entry.getKey().equals("V")||entry.getKey().equals("G"));connected=candidate.to;break;}catch(IllegalStateException blocked){failure=blocked;}}
                if(connected==null)throw new IllegalStateException("Cannot complete decimal net "+entry.getKey(),failure);done.add(connected);todo.remove(connected);
            }
        }
        circuit.changed();
    }
    private static int color(String net){return net.equals("V")?0:net.equals("G")?1:net.equals("SUB")?4:net.startsWith("A")?2:net.startsWith("B")?3:5;}
    private Endpoint escape(Endpoint from,int color){
        var p=from.point();var kind=((ComponentBlock)world.getBlockState(p.host()).getBlock()).kind;
        Direction direction;int u=p.u(),v=p.v();
        if(kind==ComponentBlock.Kind.PCB){direction=u<8?Direction.WEST:Direction.EAST;u=u<8?13:3;}
        else if(kind==ComponentBlock.Kind.GATE&&v!=7||kind==ComponentBlock.Kind.BUCK){direction=v<8?Direction.NORTH:Direction.SOUTH;v=v<8?13:3;}
        else {direction=u<7?Direction.WEST:Direction.EAST;u=u<7?13:3;}
        var target=new SurfacePoint(p.host().offset(direction).down(),Direction.UP,u,v,0);
        var node=circuit.joinAt(world,target);wire(from,node,color,false);return node;
    }
    private void wire(Endpoint a,Endpoint b,int color,boolean cable){
        IllegalArgumentException failure=null;
        for(int layer=0;layer<=1;layer++)try{var path=circuit.connectionRoute(world,a,b,layer);circuit.validateCapacity(path);var wire=new Wire(UUID.randomUUID(),a.id(),b.id(),color,path,circuit.lengthUnits(path,a,b),cable);circuit.wires.put(wire.id(),wire);circuit.changed();return;}catch(IllegalArgumentException ex){failure=ex;}
        throw new IllegalStateException("Decimal circuit route "+a.point()+" -> "+b.point(),failure);
    }
    static void inputs(ServerWorld world,int a,int b,boolean subtract){
        var left=(DisplayEntity)world.getBlockEntity(ORIGIN.add(8,1,2));var right=(DisplayEntity)world.getBlockEntity(ORIGIN.add(14,1,2));left.setting=a;right.setting=b;left.sync();right.sync();
        var op=ORIGIN.add(20,1,2);world.setBlockState(op,world.getBlockState(op).with(ComponentBlock.ENABLED,subtract));CircuitState.get(world).changed();
    }
    static void verify(ServerWorld world){verify(world,16);}
    static void verify(ServerWorld world,int expected){
        var sign=(DisplayEntity)world.getBlockEntity(ORIGIN.add(5,1,27));var tens=(DisplayEntity)world.getBlockEntity(ORIGIN.add(11,1,27));var ones=(DisplayEntity)world.getBlockEntity(ORIGIN.add(17,1,27));
        if(sign==null||tens==null||ones==null||sign.value!=(expected<0?1:0)||tens.value!=Math.abs(expected)/10||ones.value!=Math.abs(expected)%10)throw new IllegalStateException("Gate/PCB calculator must display "+expected+": "+diagnostics(world));
    }
    static String diagnostics(ServerWorld world){var c=CircuitState.get(world);var out=new StringBuilder("paused=").append(new HashSet<>(c.paused.values()));for(var p:c.devices.keySet())if(p.getZ()>=142&&world.getBlockEntity(p) instanceof ComponentEntity e)out.append("\n").append(p).append(" V=").append(e.voltage).append(" A=").append(e.current).append(" logic=").append(e.logicState).append(e instanceof DisplayEntity d?" display="+d.value:"");return out.toString();}
}
