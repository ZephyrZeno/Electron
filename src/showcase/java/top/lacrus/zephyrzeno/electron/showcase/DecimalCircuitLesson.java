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

/** One calculator PCB; the separate pixel tutorial has no role in its arithmetic. */
final class DecimalCircuitLesson {
    static final BlockPos ORIGIN=new BlockPos(46,65,142),CALCULATOR=ORIGIN.add(8,1,3),PIXELS=ORIGIN.add(8,1,11),SCREEN=ORIGIN.add(11,1,11);
    private final ServerWorld world;private final CircuitState circuit;
    DecimalCircuitLesson(ServerWorld w,CircuitState c){world=w;circuit=c;}
    private BlockPos device(int x,int z,ComponentBlock block){var p=ORIGIN.add(x,1,z);var s=block.getDefaultState().with(ComponentBlock.FACING,Direction.UP);world.setBlockState(p,s);circuit.track(p,s);var bridge=new Bridge(UUID.randomUUID(),p,Direction.UP,"minecraft:smooth_stone");circuit.bridges.put(bridge.id(),bridge);return p;}
    void build(BlockPos supply){
        for(int x=0;x<15;x++)for(int z=0;z<17;z++){var p=ORIGIN.add(x,0,z);world.setBlockState(p,Blocks.SMOOTH_STONE.getDefaultState());var bridge=new Bridge(UUID.randomUUID(),p,Direction.UP,"minecraft:smooth_stone");circuit.bridges.put(bridge.id(),bridge);}
        var buck=device(6,3,Foundry.BUCK);wire(supply,2,buck,0,0);wire(supply,3,buck,1,1);
        var p=device(8,3,Pcb.SOCKET);var e=(PcbSocketEntity)world.getBlockEntity(p);e.plantItems.set(0,Pcb.board(SingleBoardCalculator.layout()));e.boardChanged();for(var support:e.supportPositions(e.circuit().layout))if(!support.equals(p))world.setBlockState(support,Pcb.EXTENSION.getDefaultState());
        wire(buck,2,p,0,0);wire(buck,3,p,1,1);inputs(world,9,7,false);
        var demoBuck=device(6,11,Foundry.BUCK);wire(buck,0,demoBuck,0,0);wire(buck,1,demoBuck,1,1);
        var demo=device(8,11,Pcb.SOCKET);var d=(PcbSocketEntity)world.getBlockEntity(demo);d.plantItems.set(0,Pcb.board(PcbExamples.pixel()));d.boardChanged();d.setControl(0,1);
        wire(demoBuck,2,demo,0,0);wire(demoBuck,3,demo,1,1);var panel=device(11,11,Displays.PIXEL);
        for(int i=0;i<6;i++)wire(demo,i,panel,i,i<2?i:i+1);circuit.changed();
    }
    private void wire(BlockPos from,int a,BlockPos to,int b,int color){var pins=circuit.endpoints();var start=pins.get(node(from,a));var end=pins.get(node(to,b));IllegalArgumentException failure=null;
        for(int layer=0;layer<=1;layer++)try{var path=circuit.connectionRoute(world,start,end,layer);circuit.validateCapacity(path);var w=new Wire(UUID.randomUUID(),start.id(),end.id(),color,path,circuit.lengthUnits(path,start,end),false);circuit.wires.put(w.id(),w);circuit.changed();return;}catch(IllegalArgumentException ex){failure=ex;}throw new IllegalStateException("Single PCB wire "+from+":"+a+" -> "+to+":"+b,failure);
    }
    static void inputs(ServerWorld w,int a,int b,boolean subtract){var e=(PcbSocketEntity)w.getBlockEntity(CALCULATOR);e.setControl(0,a);e.setControl(1,b);e.setControl(2,subtract?1:0);}
    static void tickPixels(ServerWorld w,int age){var e=(PcbSocketEntity)w.getBlockEntity(PIXELS);if(e==null)return;if(age>=24&&age<600){e.setControl(0,1);e.setControl(2,0);e.setControl(3,0);e.setControl(1,((age-24)/2)&1);}if(age==620)e.setControl(2,1);}
    static void verify(ServerWorld w){verify(w,16);var e=(PcbSocketEntity)w.getBlockEntity(PIXELS);var screen=(DisplayEntity)w.getBlockEntity(SCREEN);if(e.pixels.length<6||e.pixels[5]!=-1L||screen.pixelBits!=-1L||screen.value<0)throw new IllegalStateException("Serial pixel tutorial failed");}
    static void verify(ServerWorld w,int expected){var e=(PcbSocketEntity)w.getBlockEntity(CALCULATOR);if(e==null||e.displays.length<6||!e.supported(e.circuit().layout)||e.displays[3]!=(expected<0?1:0)||e.displays[4]!=Math.abs(expected)/10||e.displays[5]!=Math.abs(expected)%10)throw new IllegalStateException("Single PCB must display "+expected+": "+diagnostics(w));}
    static String diagnostics(ServerWorld w){var c=CircuitState.get(w);var out=new StringBuilder("paused=").append(new HashSet<>(c.paused.values()));for(var p:List.of(CALCULATOR,PIXELS,SCREEN))if(w.getBlockEntity(p) instanceof ComponentEntity e)out.append("\n").append(p).append(" V=").append(e.voltage).append(" A=").append(e.current).append(e instanceof PcbSocketEntity pcb?" values="+Arrays.toString(pcb.displays):"");return out.toString();}
}
