package top.lacrus.zephyrzeno.electron.test;
import top.lacrus.zephyrzeno.electron.item.CircuitToolItem;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.logic.LogicFunction;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class FoundryGameTests implements FabricGameTest {
    private static BlockPos device(TestContext t,int x,int z,ComponentBlock block){
        BlockPos pos=t.getAbsolutePos(new BlockPos(x,2,z));
        t.getWorld().setBlockState(pos,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP),Block.NOTIFY_ALL);
        return pos;
    }
    private static ComponentEntity entity(TestContext t,BlockPos pos){return (ComponentEntity)t.getWorld().getBlockEntity(pos);}
    private static void floor(TestContext t){for(int x=0;x<10;x++)for(int z=0;z<18;z++)t.getWorld().setBlockState(t.getAbsolutePos(new BlockPos(x,2,z)),Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);}
    private static Wire wire(TestContext t,BlockPos a,int pa,BlockPos b,int pb){
        CircuitState s=CircuitState.get(t.getWorld());var pins=s.endpoints();var start=pins.get(node(a,pa));var end=pins.get(node(b,pb));
        var path=s.connectionRoute(t.getWorld(),start,end,0);s.validateCapacity(path);
        Wire w=new Wire(UUID.randomUUID(),start.id(),end.id(),0,path,s.lengthUnits(path,start,end));s.wires.put(w.id(),w);s.changed();return w;
    }
    @GameTest(templateName="electron:components_test",tickLimit=90)
    public void closedLoopEnergyOpenReturnAndSavedWiring(TestContext t){
        floor(t);var source=device(t,1,2,Foundry.CRANK);var r=device(t,3,2,Foundry.RESISTOR_220);var led=device(t,5,2,Foundry.LED);
        entity(t,source).energy=100;
        wire(t,source,0,r,0);wire(t,r,1,led,0);var back=wire(t,led,1,source,1);
        var s=CircuitState.get(t.getWorld());var saved=CircuitState.read(s.snapshot());
        t.assertEquals(saved.wires,s.wires,"Attachment save restores exact routes, endpoints and material length");
        t.runAtTick(10,()->{
            t.assertTrue(t.getWorld().getBlockState(led).get(ComponentBlock.LEVEL)>0,"Closed loop lights LED");
            t.assertTrue(entity(t,led).current>.011&&entity(t,led).current<.014,"220 ohm resistor limits LED current");
            t.assertTrue(entity(t,source).energy<100&&entity(t,source).energy>99,"Electrical work consumes stored energy");
            s.wires.remove(back.id());s.changed();
        });
        t.runAtTick(20,()->{t.assertEquals(t.getWorld().getBlockState(led).get(ComponentBlock.LEVEL),0,"Missing return extinguishes LED");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=110)
    public void allWireGateTruthTablesAndPowerLoss(TestContext t){
        floor(t);var fixture=CircuitState.get(t.getWorld());
        for(int x=0;x<10;x++)for(int z=0;z<18;z++){var bridge=new Bridge(UUID.randomUUID(),t.getAbsolutePos(new BlockPos(x,2,z)),Direction.UP,"minecraft:stone");fixture.bridges.put(bridge.id(),bridge);}
        List<BlockPos> gates=new ArrayList<>(),sources=new ArrayList<>();List<Wire> inputs=new ArrayList<>();int i=0;
        for(var fn:LogicFunction.values()){
            int x=1+(i%2)*4,z=1+(i/2)*4;var source=device(t,x,z,Foundry.CRANK);var gate=device(t,x+2,z,Foundry.WIRE_GATES.get(fn));
            entity(t,source).energy=100;sources.add(source);gates.add(gate);wire(t,source,0,gate,0);wire(t,source,1,gate,1);i++;
        }
        var s=CircuitState.get(t.getWorld());
        t.runAtTick(8,()->{for(var gate:gates)t.assertEquals(entity(t,gate).logicState,2,"Powered floating inputs are X");});
        for(int mask=0;mask<4;mask++){
            final int bits=mask;
            t.runAtTick(12+mask*16,()->{
                inputs.forEach(w->s.wires.remove(w.id()));inputs.clear();
                for(int j=0;j<gates.size();j++){
                    inputs.add(wire(t,sources.get(j),(bits&1)!=0?0:1,gates.get(j),2));
                    inputs.add(wire(t,sources.get(j),(bits&2)!=0?0:1,gates.get(j),3));
                }
                s.changed();
            });
            t.runAtTick(22+mask*16,()->{
                int j=0;for(var fn:LogicFunction.values()){
                    var gate=gates.get(j++);boolean value=fn.apply((bits&1)!=0,(bits&2)!=0);
                    t.assertEquals(entity(t,gate).logicState,value?1:0,fn+" electrical truth table "+bits);
                    double output=s.readings.voltage(node(gate,4))-s.readings.voltage(node(gate,1));
                    t.assertTrue(value?output>4.5:Math.abs(output)<.01,fn+" finite rail-referenced Y voltage");
                }
            });
        }
        t.runAtTick(80,()->{for(var pos:sources)entity(t,pos).energy=0;});
        t.runAtTick(90,()->{for(var pos:gates)t.assertEquals(entity(t,pos).logicState,3,"Unpowered output becomes Z");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=100)
    public void sustainedOvercurrentBurnsAndDoesNotRecover(TestContext t){
        floor(t);var source=device(t,2,2,Foundry.CRANK);var led=device(t,4,2,Foundry.LED);entity(t,source).energy=100;
        wire(t,source,0,led,0);wire(t,led,1,source,1);
        t.runAtTick(35,()->{t.assertTrue(t.getWorld().getBlockState(led).get(ComponentBlock.BROKEN),"Direct 5V connection burns LED after sustained overcurrent");});
        t.runAtTick(60,()->{t.assertEquals(t.getWorld().getBlockState(led).get(ComponentBlock.LEVEL),0,"Burnt LED stays dark");t.assertTrue(Math.abs(entity(t,led).current)<1e-8,"Burnt LED has high impedance");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=45)
    public void sixFaceWallCoresRemainIndependentAndBreakWithHost(TestContext t){
        var world=t.getWorld();var s=CircuitState.get(world);List<WallCore> cores=new ArrayList<>();int i=0;
        for(Direction face:Direction.values()){
            var pos=t.getAbsolutePos(new BlockPos(2+(i%2)*4,2,2+(i/2)*4));world.setBlockState(pos,Blocks.STONE.getDefaultState(),Block.NOTIFY_ALL);
            var core=new WallCore(UUID.randomUUID(),pos,face,0,"minecraft:stone");s.cores.put(core.id(),core);cores.add(core);i++;
            t.assertFalse(PistonBlock.isMovable(world.getBlockState(pos),world,pos,Direction.EAST,false,Direction.EAST),"Attachments stop piston duplication");
        }
        s.changed();
        t.runAtTick(5,()->{
            for(var c:cores){
                t.assertTrue(world.getBlockState(c.host()).isOf(Blocks.STONE),"Wall block stays intact");
                t.assertTrue(s.readings.commonReference(c.endpoint(0,0).id(),c.endpoint(1,0).id()),"Same core reaches opposite face");
                t.assertFalse(s.readings.commonReference(c.endpoint(0,0).id(),c.endpoint(1,1).id()),"Two cores stay electrically isolated");
            }
            var restored=CircuitState.read(s.snapshot());t.assertEquals(restored.cores,s.cores,"Six-axis core orientation persists");
            world.setBlockState(cores.getFirst().host(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        });
        t.runAtTick(12,()->{t.assertFalse(s.cores.containsKey(cores.getFirst().id()),"Removing host removes through-wall connection");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void layeredCrossingsAndExactMaterialRoundTrip(TestContext t){
        floor(t);var s=CircuitState.get(t.getWorld());var pos=t.getAbsolutePos(new BlockPos(3,2,3));
        var bridge=new Bridge(UUID.randomUUID(),pos,Direction.UP,"minecraft:stone");s.bridges.put(bridge.id(),bridge);
        var points=List.of(new SurfacePoint(pos,Direction.UP,1,7,0),new SurfacePoint(pos,Direction.UP,15,7,0),new SurfacePoint(pos,Direction.UP,7,1,0),new SurfacePoint(pos,Direction.UP,7,15,0));
        List<Endpoint> ends=new ArrayList<>();for(var p:points){var j=new Junction(UUID.randomUUID(),p,"minecraft:stone");s.junctions.put(j.id(),j);ends.add(j.endpoint());}
        var first=s.route(t.getWorld(),ends.get(0),ends.get(1),0);var w=new Wire(UUID.randomUUID(),ends.get(0).id(),ends.get(1).id(),0,first,SurfaceRouter.lengthUnits(first));s.wires.put(w.id(),w);
        var second=s.route(t.getWorld(),ends.get(2),ends.get(3),1);var upper=new Wire(UUID.randomUUID(),ends.get(2).id(),ends.get(3).id(),2,second,SurfaceRouter.lengthUnits(second));s.wires.put(upper.id(),upper);s.changed();
        t.assertEquals(upper.units(),18,"Seven 1/8 runs and two 1/8 risers consume eighteen units");
        var recovered=CircuitToolItem.spool(upper.units(),upper.color());t.assertEquals(CircuitToolItem.units(recovered),18,"Cut spool retains fractional material");
        t.runAtTick(8,()->{t.assertFalse(s.readings.commonReference(w.a(),upper.a()),"Same-cell two-layer crossing stays isolated");t.complete();});
    }
}
