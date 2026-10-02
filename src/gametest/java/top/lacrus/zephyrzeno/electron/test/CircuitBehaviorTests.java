package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.test.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.logic.LogicFunction;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

/** World fixtures use the same physical routing and finite-resistance wires as players. */
public final class CircuitBehaviorTests implements FabricGameTest {
    private static void floor(TestContext t){
        var s=CircuitState.get(t.getWorld());
        for(int x=0;x<10;x++)for(int z=0;z<18;z++){
            var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p,Blocks.STONE.getDefaultState());
            var b=new Bridge(UUID.randomUUID(),p,Direction.UP,"minecraft:stone");s.bridges.put(b.id(),b);
        }
    }
    private static BlockPos device(TestContext t,int x,int z,ComponentBlock block){
        var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));return p;
    }
    private static ComponentEntity entity(TestContext t,BlockPos p){return (ComponentEntity)t.getWorld().getBlockEntity(p);}
    private static Wire wire(TestContext t,BlockPos a,int ap,BlockPos b,int bp){
        var s=CircuitState.get(t.getWorld());var ends=s.endpoints();var start=ends.get(node(a,ap));var end=ends.get(node(b,bp));
        var path=s.connectionRoute(t.getWorld(),start,end,0);s.validateCapacity(path);
        var w=new Wire(UUID.randomUUID(),start.id(),end.id(),s.wires.size()%8,path,s.lengthUnits(path,start,end));s.wires.put(w.id(),w);s.changed();return w;
    }
    private static void near(TestContext t,double actual,double expected,double error,String message){t.assertTrue(Math.abs(actual-expected)<error,message+": "+actual+" expected "+expected);}
    private static void press(TestContext t,BlockPos p){var player=t.createMockCreativeServerPlayerInWorld();t.getWorld().getBlockState(p).onUse(t.getWorld(),player,new BlockHitResult(Vec3d.ofCenter(p),Direction.UP,p,false));}

    @GameTest(templateName="electron:components_test",tickLimit=35)
    public void seriesVoltageDivisionAndOpenLoop(TestContext t){
        floor(t);var source=device(t,1,2,Foundry.CRANK);var a=device(t,3,2,Foundry.RESISTOR_100);var b=device(t,5,2,Foundry.RESISTOR_220);
        entity(t,source).energy=100;wire(t,source,0,a,0);wire(t,a,1,b,0);var back=wire(t,b,1,source,1);
        t.runAtTick(8,()->{
            near(t,entity(t,a).current,5d/325,.00005,"Series current includes source and wire resistance");
            near(t,entity(t,a).current,entity(t,b).current,1e-8,"Series devices carry equal current");
            near(t,entity(t,a).voltage/entity(t,b).voltage,100d/220,1e-8,"Series voltage follows resistance ratio");
            var s=CircuitState.get(t.getWorld());s.wires.remove(back.id());s.changed();
        });
        t.runAtTick(14,()->{near(t,entity(t,a).current,0,1e-8,"Opening series return stops whole loop");near(t,entity(t,b).current,0,1e-8,"Both series devices lose current");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=35)
    public void parallelBranchCurrentsAndIndependentDisconnection(TestContext t){
        floor(t);var source=device(t,1,2,Foundry.CRANK);var a=device(t,4,2,Foundry.RESISTOR_100);var b=device(t,4,5,Foundry.RESISTOR_220);
        entity(t,source).energy=100;wire(t,source,0,a,0);wire(t,a,1,source,1);wire(t,source,0,b,0);var back=wire(t,b,1,source,1);
        t.runAtTick(8,()->{
            near(t,entity(t,a).voltage,entity(t,b).voltage,.04,"Parallel loads have nearly equal voltage including wire drop");
            near(t,entity(t,a).current/entity(t,b).current,2.2,.02,"Unequal parallel loads divide current");
            near(t,-entity(t,source).current,entity(t,a).current+entity(t,b).current,1e-8,"Source current is branch current sum");
            var s=CircuitState.get(t.getWorld());s.wires.remove(back.id());s.changed();
        });
        t.runAtTick(14,()->{near(t,entity(t,b).current,0,1e-8,"Removed branch is open");t.assertTrue(entity(t,a).current>.047,"Other branch still works");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=25)
    public void mixedSeriesParallelNetwork(TestContext t){
        floor(t);var source=device(t,1,2,Foundry.CRANK);var common=device(t,3,2,Foundry.RESISTOR_100);var a=device(t,6,2,Foundry.RESISTOR_220);var b=device(t,6,5,Foundry.RESISTOR_1000);
        entity(t,source).energy=100;wire(t,source,0,common,0);wire(t,common,1,a,0);wire(t,common,1,b,0);wire(t,a,1,source,1);wire(t,b,1,source,1);
        t.runAtTick(8,()->{
            double parallel=1/(1d/220+1d/1000);
            near(t,entity(t,common).current,5/(5+100+parallel),.0001,"Mixed equivalent resistance");
            near(t,entity(t,common).current,entity(t,a).current+entity(t,b).current,1e-8,"Common series current splits at branch node");t.complete();
        });
    }
    @GameTest(templateName="electron:components_test",tickLimit=105)
    public void momentaryPulseChargesCapacitorThenLedFades(TestContext t){
        floor(t);var source=device(t,1,2,Foundry.CRANK);var button=device(t,3,2,Foundry.BUTTON);var cap=device(t,5,2,Foundry.CAPACITOR);var r=device(t,5,5,Foundry.RESISTOR_220);var led=device(t,7,5,Foundry.LED);
        entity(t,source).energy=100;wire(t,source,0,button,0);wire(t,button,1,cap,0);wire(t,cap,1,source,1);wire(t,cap,0,r,0);wire(t,r,1,led,0);wire(t,led,1,cap,1);
        double[] charged={0,0};
        t.runAtTick(2,()->{t.assertFalse(t.getWorld().getBlockState(button).get(ComponentBlock.ENABLED),"New button is normally open");near(t,entity(t,led).current,0,1e-7,"No input means no generated energy");press(t,button);});
        t.runAtTick(12,()->press(t,button));
        t.runAtTick(25,()->{
            t.assertFalse(t.getWorld().getBlockState(button).get(ComponentBlock.ENABLED),"Repeated press does not extend the 20-tick pulse");
            charged[0]=entity(t,cap).capacitorVoltage;charged[1]=entity(t,led).current;
            t.assertTrue(charged[0]>3&&charged[0]<5,"Finite pulse charges the capacitor");t.assertTrue(charged[1]>.004,"Stored charge lights LED with button open");
            t.assertTrue(entity(t,cap).current<0,"Capacitor is now discharging");
        });
        t.runAtTick(90,()->{
            t.assertTrue(entity(t,cap).capacitorVoltage<charged[0],"Stored voltage decays with load");
            t.assertTrue(entity(t,led).current>0&&entity(t,led).current<charged[1],"LED fades instead of switching instantly off");
            t.assertFalse(t.getWorld().getBlockState(cap).get(ComponentBlock.BROKEN),"Normal 5V charging transient is safe");t.complete();
        });
    }
    @GameTest(templateName="electron:components_test",tickLimit=25)
    public void capacitorPersistencePausedNetworkAndDischargedDrop(TestContext t){
        floor(t);var p=device(t,3,3,Foundry.CAPACITOR);var c=entity(t,p);c.capacitorVoltage=4.25;
        var registry=t.getWorld().getRegistryManager();var saved=c.createNbt(registry);var restored=new ComponentEntity(p,c.getCachedState());restored.read(saved,registry);
        near(t,restored.capacitorVoltage,4.25,1e-12,"Saved capacitor restores exact voltage");
        var corrupt=saved.copy();corrupt.putDouble("capacitor_voltage",Double.NaN);restored.read(corrupt,registry);near(t,restored.capacitorVoltage,0,1e-12,"Invalid saved voltage cannot poison solver");
        var s=CircuitState.get(t.getWorld());t.getWorld().setBlockState(p.up(),Blocks.WATER.getDefaultState());s.simulate(t.getWorld());
        t.assertTrue(c.paused,"Wet endpoint pauses network");near(t,c.capacitorVoltage,4.25,1e-12,"Paused circuit preserves charge");
        t.getWorld().setBlockState(p.up(),Blocks.AIR.getDefaultState());s.simulate(t.getWorld());t.assertFalse(c.paused,"Cleared endpoint resumes network");
        t.assertTrue(c.capacitorVoltage<4.25&&c.capacitorVoltage>4.24,"Isolated capacitor only has small leakage");
        t.getWorld().setBlockState(p,Blocks.AIR.getDefaultState());t.getWorld().setBlockState(p,Foundry.CAPACITOR.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
        near(t,entity(t,p).capacitorVoltage,0,1e-12,"Newly placed item starts discharged");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=110)
    public void reversedCapacitorFailsOpen(TestContext t){
        floor(t);var source=device(t,1,2,Foundry.CRANK);var cap=device(t,4,2,Foundry.CAPACITOR);entity(t,source).energy=100;
        wire(t,source,0,cap,1);wire(t,source,1,cap,0);
        t.runAtTick(90,()->{t.assertTrue(t.getWorld().getBlockState(cap).get(ComponentBlock.BROKEN),"Sustained reverse polarity damages polarized capacitor");near(t,entity(t,cap).capacitorVoltage,0,1e-12,"Failed capacitor stores no reusable charge");t.assertTrue(Math.abs(entity(t,cap).current)<1e-8,"Failed capacitor is open circuit");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=70)
    public void tenKPullDownMakesButtonLogicDeterministic(TestContext t){
        floor(t);var source=device(t,1,2,Foundry.CRANK);var gate=device(t,5,2,Foundry.WIRE_GATES.get(LogicFunction.BUFFER));var r=device(t,5,6,Foundry.RESISTOR_10000);var button=device(t,2,6,Foundry.BUTTON);
        entity(t,source).energy=100;wire(t,source,0,gate,0);wire(t,source,1,gate,1);wire(t,gate,2,r,0);wire(t,r,1,source,1);wire(t,source,0,button,0);wire(t,button,1,gate,2);
        t.runAtTick(8,()->{t.assertEquals(entity(t,gate).logicState,0,"10k pull-down resolves unpressed input to zero");press(t,button);});
        t.runAtTick(16,()->{t.assertEquals(entity(t,gate).logicState,1,"Pressed button drives input high");});
        t.runAtTick(40,()->{t.assertEquals(entity(t,gate).logicState,0,"Released input returns to zero instead of floating X");t.complete();});
    }
}
