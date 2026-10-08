package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.Automation;
import top.lacrus.zephyrzeno.electron.pcb.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class AnalogExpansionTests implements FabricGameTest {
    private static ComponentEntity device(TestContext t,ComponentBlock block,int x,int z){
        var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p.down(),Blocks.SMOOTH_STONE.getDefaultState());
        t.getWorld().setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));return (ComponentEntity)t.getWorld().getBlockEntity(p);
    }
    @GameTest(templateName="electron:components_test")
    public void versionFourInventoryAndNewTrimmerSurviveReload(TestContext t){
        var bench=(PcbBenchEntity)device(t,Pcb.ASSEMBLY,3,3);var layout=PcbLayout.halfAdder();bench.setStack(29,Pcb.board(layout));
        var n=bench.createNbt(t.getWorld().getRegistryManager());n.putInt("pcb_inventory_version",4);bench.read(n,t.getWorld().getRegistryManager());
        t.assertTrue(bench.getStack(29).isEmpty(),"Former output is not interpreted as an R220 package");t.assertEquals(Pcb.layout(bench.getStack(Pcb.OUTPUT_SLOT)),layout,"v4 board moves to current output");
        for(var type:PcbLayout.Type.values())if(type.analog()){var stack=new ItemStack(Pcb.PARTS.get(type),3);t.assertTrue(bench.isValid(3+type.ordinal(),stack),"New packages have dedicated input slots");bench.setStack(3+type.ordinal(),stack);}
        n=bench.createNbt(t.getWorld().getRegistryManager());bench.read(n,t.getWorld().getRegistryManager());
        for(var type:PcbLayout.Type.values())if(type.analog())t.assertEquals(bench.getStack(3+type.ordinal()).getCount(),3,"All new material slots persist");
        var socket=(PcbSocketEntity)device(t,Pcb.SOCKET,7,3);socket.plantItems.set(0,Pcb.board(PcbExamples.regulated()));socket.boardChanged();
        t.assertEquals(socket.circuit().controls[PcbExamples.REGULATED_POT],5,"Trimmer starts centered");socket.setControl(PcbExamples.REGULATED_POT,10);
        n=socket.createNbt(t.getWorld().getRegistryManager());socket.read(n,t.getWorld().getRegistryManager());t.assertEquals(socket.circuit().controls[PcbExamples.REGULATED_POT],10,"Full trimmer range survives socket NBT");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void environmentalContactsNeedPowerAndStayElectricallyIsolated(TestContext t){
        var light=device(t,Automation.LIGHT,3,3);var rain=device(t,Automation.RAIN,7,3);light.loadMode=1;rain.loadMode=1;
        t.getWorld().setBlockState(light.getPos().up(),Blocks.SEA_LANTERN.getDefaultState());t.getWorld().setBlockState(rain.getPos().up(2),Blocks.STONE.getDefaultState());
        t.runAtTick(12,()->{
            for(var e:List.of(light,rain)){
                var b=(ComponentBlock)e.getCachedState().getBlock();var edges=new ArrayList<DcSolver.Branch>();Automation.stamp(b,e,edges);
                edges.add(DcSolver.Branch.source("source",node(e.getPos(),0),node(e.getPos(),1),5,.01));var r=DcSolver.solve(edges);
                e.voltage=r.voltage(node(e.getPos(),0))-r.voltage(node(e.getPos(),1));Automation.update(t.getWorld(),e.getPos(),e,b,r);
                t.assertTrue(e.contactClosed,"Exposed bright light or sheltered dry mode closes contact");t.assertTrue(e.inputCurrent>.0019,"Sensing consumes supply current");
                edges.clear();Automation.stamp(b,e,edges);edges.add(DcSolver.Branch.resistor("load",node(e.getPos(),2),node(e.getPos(),3),1000));r=DcSolver.solve(edges);
                t.assertTrue(Math.abs(r.current("load"))<1e-9,"Contact cannot generate electricity");e.voltage=0;Automation.update(t.getWorld(),e.getPos(),e,b,r);t.assertFalse(e.contactClosed,"Power loss releases contact");
            }
            t.assertTrue(Automation.lightContact(6,0,true),"Night threshold retains state in hysteresis band");t.assertFalse(Automation.lightContact(6,0,false),"Night threshold will not chatter");
            t.assertTrue(Automation.rainContact(true,true,2),"Thunder mode requires rain and thunder");t.assertFalse(Automation.rainContact(false,true,2),"Shelter prevents thunder contact");t.complete();
        });
    }
    private static void wire(TestContext t,ComponentEntity a,int ap,ComponentEntity b,int bp){
        var circuit=CircuitState.get(t.getWorld());var endpoints=circuit.endpoints();var from=endpoints.get(node(a.getPos(),ap));var to=endpoints.get(node(b.getPos(),bp));
        var path=circuit.connectionRoute(t.getWorld(),from,to,0);circuit.validateCapacity(path);var w=new Wire(UUID.randomUUID(),from.id(),to.id(),circuit.wires.size()%8,path,circuit.lengthUnits(path,from,to),false);circuit.wires.put(w.id(),w);circuit.changed();
    }
    @GameTest(templateName="electron:components_test",tickLimit=100)
    public void timerSampleRunsOnTheWorldCircuitAndKeepsItsCapacitorState(TestContext t){
        var network=CircuitState.get(t.getWorld());for(int x=0;x<10;x++)for(int z=0;z<12;z++){var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p,Blocks.SMOOTH_STONE.getDefaultState());var bridge=new Bridge(UUID.randomUUID(),p,Direction.UP,"minecraft:smooth_stone");network.bridges.put(bridge.id(),bridge);}
        var source=device(t,Foundry.CRANK,1,4);source.energy=100;var board=(PcbSocketEntity)device(t,Pcb.SOCKET,7,4);board.plantItems.set(0,Pcb.board(PcbExamples.timer()));board.boardChanged();wire(t,source,0,board,0);wire(t,source,1,board,1);
        t.runAtTick(18,()->{t.assertFalse(board.leds.get(PcbExamples.TIMER_LED),"Idle timer does not light itself");board.setControl(PcbExamples.TIMER_SWITCH,1);});
        t.runAtTick(24,()->t.assertFalse(board.leds.get(PcbExamples.TIMER_LED),"Delay is physically charged"));
        t.runAtTick(63,()->{t.assertTrue(board.leds.get(PcbExamples.TIMER_LED),"World solver crosses capacitor threshold");t.assertTrue(source.energy<100,"Board spends actual source energy");var saved=board.circuit().snapshot();var n=board.createNbt(t.getWorld().getRegistryManager());board.read(n,t.getWorld().getRegistryManager());t.assertTrue(Arrays.equals(saved,board.circuit().snapshot()),"Capacitor and pending comparator state survive reload");board.setControl(PcbExamples.TIMER_SWITCH,0);});
        t.runAtTick(86,()->{t.assertFalse(board.leds.get(PcbExamples.TIMER_LED),"Reset diode discharges actual capacitor");t.assertFalse(board.getCachedState().get(ComponentBlock.BROKEN),"Sample remains within component ratings");t.complete();});
    }
}
