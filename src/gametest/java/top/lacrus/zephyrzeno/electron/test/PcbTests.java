package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.*;
import top.lacrus.zephyrzeno.electron.pcb.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class PcbTests implements FabricGameTest {
    private static ComponentEntity device(TestContext t,ComponentBlock block,int x,int z){
        var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p.down(),Blocks.SMOOTH_STONE.getDefaultState());t.getWorld().setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));return (ComponentEntity)t.getWorld().getBlockEntity(p);
    }
    private static void solder(PcbBenchEntity e,int ticks){e.voltage=24;e.current=1;for(int i=0;i<ticks;i++)e.process(false);}
    private static void process(TestContext t,ProcessingEntity e,int ticks){e.voltage=24;e.current=((ProcessingBlock)e.getCachedState().getBlock()).watts/24;for(int i=0;i<ticks;i++)Processing.update(t.getWorld(),e.getPos(),e,false);}
    private static void floor(TestContext t){var s=CircuitState.get(t.getWorld());for(int x=0;x<10;x++)for(int z=0;z<12;z++){var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p,Blocks.SMOOTH_STONE.getDefaultState());var bridge=new Bridge(UUID.randomUUID(),p,Direction.UP,"minecraft:smooth_stone");s.bridges.put(bridge.id(),bridge);}}
    private static void wire(TestContext t,ComponentEntity a,int ap,ComponentEntity b,int bp){
        var s=CircuitState.get(t.getWorld());var endpoints=s.endpoints();var from=endpoints.get(node(a.getPos(),ap));var to=endpoints.get(node(b.getPos(),bp));var path=s.connectionRoute(t.getWorld(),from,to,0);s.validateCapacity(path);
        var w=new Wire(UUID.randomUUID(),from.id(),to.id(),s.wires.size()%8,path,s.lengthUnits(path,from,to),true);s.wires.put(w.id(),w);s.changed();
    }
    @GameTest(templateName="electron:components_test")
    public void rearSocketsAreReachableOnRealCollisionInEveryOrientation(TestContext t){
        var blocks=new ArrayList<ComponentBlock>();blocks.add(Pcb.BENCH);blocks.add(Automation.EMERGENCY);
        PcbFabrication.STAGES.forEach(stage->blocks.add(stage.machine()));
        var pos=t.getAbsolutePos(new BlockPos(4,3,4));var world=t.getWorld();var circuit=CircuitState.get(world);
        for(var block:blocks)for(var face:Direction.values())for(int turn=0;turn<4;turn++){
            var state=block.getDefaultState().with(ComponentBlock.FACING,face).with(ComponentBlock.TURN,turn);
            world.setBlockState(pos,state);circuit.track(pos,state);
            for(int pin=0;pin<2;pin++){
                var point=circuit.endpoints().get(node(pos,pin)).point();var normal=Vec3d.of(point.face().getVector());var contact=circuit.position(point);
                var hit=state.getOutlineShape(world,pos).raycast(contact.add(normal.multiply(.8)),contact.subtract(normal.multiply(.2)),pos);
                t.assertTrue(hit!=null,"Rear connector is physically reachable: "+block+" "+face+" "+turn);
                t.assertEquals(CircuitPicking.pick(world,circuit,hit,0),point,"Actual raycast, HUD and server select the same rear socket");
                t.assertTrue(hit.getPos().distanceTo(contact)<.15,"Socket is beside the wire end, not buried inside the machine");
            }
        }
        t.complete();
    }
    @GameTest(templateName="electron:components_test")
    public void maskToImmersionGoldToSolderPreservesCustomBoardAndPaysMaterials(TestContext t){
        var bench=(PcbBenchEntity)device(t,Pcb.BENCH,2,2);var design=PcbLayout.inverter().cut(12,11);
        bench.draft=design;bench.setStack(0,new ItemStack(PcbFabrication.FILM));bench.setStack(2,new ItemStack(Items.BLACK_DYE));bench.edit(5,0,0,0,0);
        bench.voltage=0;bench.current=0;bench.process(false);t.assertTrue(bench.getStack(Pcb.OUTPUT_SLOT).isEmpty(),"No free photomask without energy");solder(bench,1000);
        var mask=bench.removeStack(Pcb.OUTPUT_SLOT);t.assertTrue(mask.isOf(PcbFabrication.MASK),"Photoplotter creates a mask, never an assembled circuit");
        t.assertEquals(Pcb.layout(mask),design,"Mask preserves holes and copper");t.assertTrue(bench.getStack(0).isEmpty()&&bench.getStack(2).isEmpty(),"Film and ink consumed once");
        ItemStack previous=mask;int wetWaste=0;
        for(var stage:PcbFabrication.STAGES){
            var e=(ProcessingEntity)device(t,stage.machine(),4,4);
            e.setStack(0,stage.input()==Pcb.BLANK?new ItemStack(Pcb.BLANK):previous);e.setStack(1,stage.input()==Pcb.BLANK?mask:new ItemStack(stage.reagent()));
            process(t,e,1);t.assertTrue(e.getStack(2).isEmpty(),"Every stage needs paid time");process(t,e,1600);
            previous=e.removeStack(2);t.assertTrue(previous.isOf(stage.output()),"Correct fabrication stage output");t.assertEquals(Pcb.layout(previous),design,"Every stage preserves exactly the same custom design");
            wetWaste+=e.removeStack(3).getCount();t.assertTrue(e.getStack(0).isEmpty()&&e.getStack(1).isEmpty(),"Stage consumes board and cartridge once");
        }
        t.assertEquals(wetWaste,2,"Etching and gold baths produce two waste cartridges");
        bench=(PcbBenchEntity)device(t,Pcb.ASSEMBLY,2,5);bench.setStack(0,previous);bench.edit(7,0,0,0,0);var costs=Pcb.costs(design);for(int i=1;i<Pcb.INPUT_SLOTS;i++)if(costs[i]>0)bench.setStack(i,new ItemStack(Pcb.MATERIALS.get(i),costs[i]));
        bench.edit(5,0,0,0,0);solder(bench,1000);var board=bench.getStack(Pcb.OUTPUT_SLOT);t.assertTrue(board.isOf(Pcb.BOARD),"Soldered board is finally usable");t.assertEquals(Pcb.layout(board),design,"Soldering preserves layout");t.assertTrue(bench.getStack(0).isEmpty()&&bench.getStack(1).isEmpty()&&bench.getStack(6).isEmpty(),"Gold board, solder and NOT consumed");
        solder(bench,1000);t.assertEquals(bench.getStack(Pcb.OUTPUT_SLOT).getCount(),1,"No repeated output without inputs");t.complete();
    }
    @GameTest(templateName="electron:components_test")
    public void bareCopperCannotBeRedesignedForFreeAndDamagedBenchCannotWork(TestContext t){
        var e=(PcbBenchEntity)device(t,Pcb.ASSEMBLY,3,3);var design=PcbLayout.inverter();e.setStack(0,Pcb.designed(PcbFabrication.GOLD,design));e.setStack(1,new ItemStack(Pcb.SOLDER,16));e.setStack(6,new ItemStack(Pcb.PARTS.get(PcbLayout.Type.NOT)));
        e.edit(9,12,11,0,0);t.assertEquals(e.draft,design,"Assembly cannot redesign paid copper");t.assertEquals(e.error,5,"Assembly rejects design edits");e.edit(5,0,0,0,0);t.assertTrue(e.working,"Reading the actual gold board permits assembly");
        e.voltage=24;e.current=1;for(int i=0;i<1000;i++)e.process(true);t.assertEquals(e.work,0d,"Broken bench pays no work");e.voltage=17;e.process(false);t.assertEquals(e.work,0d,"Undervoltage cannot process");
        solder(e,12);double saved=e.work;var copy=new PcbBenchEntity(e.getPos(),e.getCachedState());copy.read(e.createNbt(t.getWorld().getRegistryManager()),t.getWorld().getRegistryManager());t.assertEquals(copy.work,saved,"Paid work survives reload");t.assertEquals(copy.draft,design,"Custom design survives reload");t.assertEquals(copy.properties().get(8),1,"Reserved paid substrate survives reload");t.complete();
    }
    @GameTest(templateName="electron:components_test")
    public void differentDesignCannotMergeIntoMachineOutput(TestContext t){
        var stage=PcbFabrication.STAGES.get(0);var e=(ProcessingEntity)device(t,stage.machine(),3,3);e.setStack(0,new ItemStack(Pcb.BLANK));e.setStack(1,Pcb.designed(PcbFabrication.MASK,PcbLayout.inverter()));e.setStack(2,Pcb.designed(PcbFabrication.EXPOSED,PcbLayout.inverter().cut(12,11)));
        process(t,e,1000);t.assertEquals(e.processingEnergy,0d,"Existing board output blocks processing");t.assertEquals(e.getStack(0).getCount(),1,"Existing output protects inputs");e.removeStack(2);process(t,e,10);double paid=e.processingEnergy;
        e.setStack(1,Pcb.designed(PcbFabrication.MASK,PcbLayout.inverter().cut(12,11)));e.voltage=0;e.current=0;Processing.update(t.getWorld(),e.getPos(),e,false);t.assertTrue(paid>0&&e.processingEnergy==0,"Changing photomask resets work");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=70)
    public void installedBoardUsesWorldNetworkSupplyAndControlsLoad(TestContext t){
        floor(t);var source=device(t,Foundry.CRANK,1,4);source.energy=100;var board=(PcbSocketEntity)device(t,Pcb.SOCKET,7,4);board.plantItems.set(0,Pcb.board(PcbLayout.inverter()));board.boardChanged();var load=device(t,Foundry.RESISTOR_1000,7,9);
        wire(t,source,0,board,0);wire(t,source,1,board,1);wire(t,source,1,board,2);wire(t,board,4,load,0);wire(t,source,1,load,1);
        t.runAtTick(25,()->{t.assertTrue(load.current>.003,"PCB NOT drives actual world resistor");t.assertTrue(source.energy<100,"PCB load spends source energy");t.assertTrue(board.current>.003,"Meter includes PCB output load");source.energy=0;});
        t.runAtTick(50,()->{t.assertTrue(Math.abs(load.current)<1e-6,"Unpowered PCB cannot maintain output");t.complete();});
    }
    @GameTest(templateName="electron:components_test")
    public void pcbEntityFactoriesInventoryDropsAndClientSnapshotPreserveDesign(TestContext t){
        var bench=(PcbBenchEntity)device(t,Pcb.BENCH,3,3);bench.setStack(0,new ItemStack(PcbFabrication.FILM,3));bench.setStack(1,new ItemStack(Pcb.SOLDER,7));bench.draft=PcbLayout.inverter();
        t.assertTrue(Foundry.COMPONENT_ENTITY.instantiate(bench.getPos(),bench.getCachedState()) instanceof PcbBenchEntity,"Registry factory restores bench subclass");
        var socket=(PcbSocketEntity)device(t,Pcb.SOCKET,6,3);socket.plantItems.set(0,Pcb.board(bench.draft));socket.boardChanged();
        t.assertTrue(Foundry.COMPONENT_ENTITY.instantiate(socket.getPos(),socket.getCachedState()) instanceof PcbSocketEntity,"Registry factory restores socket subclass");
        var client=new PcbSocketEntity(socket.getPos(),socket.getCachedState());client.read(socket.toInitialChunkDataNbt(t.getWorld().getRegistryManager()),t.getWorld().getRegistryManager());t.assertEquals(client.circuit().layout,bench.draft,"Client renders real stored board");
        var p=bench.getPos();t.getWorld().setBlockState(p,Blocks.AIR.getDefaultState());var drops=t.getWorld().getEntitiesByClass(ItemEntity.class,new Box(p).expand(1),e->true);t.assertEquals(drops.stream().filter(e->e.getStack().isOf(Pcb.SOLDER)).mapToInt(e->e.getStack().getCount()).sum(),7,"Workbench materials drop once");t.complete();
    }
    @GameTest(templateName="electron:components_test")
    public void waterSensorHysteresisFailureAndIsolation(TestContext t){
        var sensor=device(t,Automation.WATER,4,4);var tankPos=sensor.getPos().east();t.getWorld().setBlockState(tankPos,Industrial.THERMAL_TANK.getDefaultState());var tank=(FluidEntity)t.getWorld().getBlockEntity(tankPos);
        var result=new DcSolver.Result(Map.of(),Map.of(),Map.of());sensor.voltage=5;Automation.update(t.getWorld(),sensor.getPos(),sensor,Automation.WATER,result);t.assertTrue(sensor.contactClosed,"Empty tank requests refill");
        tank.amount=tank.capacity()/2;sensor.voltage=5;Automation.update(t.getWorld(),sensor.getPos(),sensor,Automation.WATER,result);t.assertTrue(sensor.contactClosed,"Mid-level maintains refill latch");
        tank.amount=tank.capacity();sensor.voltage=5;Automation.update(t.getWorld(),sensor.getPos(),sensor,Automation.WATER,result);t.assertFalse(sensor.contactClosed,"Full tank stops refill");
        tank.amount=0;sensor.voltage=0;Automation.update(t.getWorld(),sensor.getPos(),sensor,Automation.WATER,result);t.assertFalse(sensor.contactClosed,"Unpowered sensor fails open");
        var branches=new ArrayList<DcSolver.Branch>();Automation.stamp(Automation.WATER,sensor,branches);t.assertTrue(branches.stream().allMatch(b->b.emf()==0),"Sensor creates no source voltage");t.assertTrue(branches.stream().noneMatch(b->b.a().equals(node(sensor.getPos(),0))&&b.b().equals(node(sensor.getPos(),2))),"Power and contacts remain isolated");
        t.getWorld().setBlockState(tankPos,Blocks.AIR.getDefaultState());sensor.voltage=5;Automation.update(t.getWorld(),sensor.getPos(),sensor,Automation.WATER,result);t.assertFalse(sensor.contactClosed,"Missing tank opens contacts");t.complete();
    }
    @GameTest(templateName="electron:components_test")
    public void emergencyLampChargesThenDepletesAndDropsStoredEnergy(TestContext t){
        var lamp=device(t,Automation.EMERGENCY,3,3);String id="d:"+lamp.getPos().asLong();var result=new DcSolver.Result(Map.of(),Map.of(id+":charge",.25),Map.of());
        t.assertEquals(lamp.energy,0d,"New lamp starts empty");lamp.voltage=24;lamp.current=.05;Automation.update(t.getWorld(),lamp.getPos(),lamp,Automation.EMERGENCY,result);t.assertTrue(Math.abs(lamp.energy-.27)<1e-9,"Actual six-watt charging pays 90% efficiency");
        lamp.energy=1.2;var empty=new DcSolver.Result(Map.of(),Map.of(),Map.of());lamp.voltage=0;lamp.current=0;
        int lit=Automation.update(t.getWorld(),lamp.getPos(),lamp,Automation.EMERGENCY,empty);t.assertTrue(lit>0&&lamp.energy<1.2,"Backup consumes real stored energy");
        var drops=Block.getDroppedStacks(lamp.getCachedState(),t.getWorld(),lamp.getPos(),lamp);t.assertEquals(drops.getFirst().getOrDefault(Foundry.BATTERY_ENERGY,0d),lamp.energy,"Breaking preserves paid stored energy");
        for(int i=0;i<100;i++)lit=Automation.update(t.getWorld(),lamp.getPos(),lamp,Automation.EMERGENCY,empty);t.assertEquals(lit,0,"Empty emergency lamp goes dark");t.assertTrue(lamp.energy>=0&&lamp.energy<.06,"Never overdraw battery");
        t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=45)
    public void hoppersMoveCarryingDesignsWithoutTakingPartsFromWorkbench(TestContext t){
        var e=(PcbBenchEntity)device(t,Pcb.BENCH,3,3);e.setStack(1,new ItemStack(Pcb.SOLDER,4));e.setStack(Pcb.OUTPUT_SLOT,Pcb.designed(PcbFabrication.MASK,PcbLayout.inverter()));
        t.getWorld().setBlockState(e.getPos().down(),Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING,Direction.DOWN));t.runAtTick(30,()->{var hopper=(HopperBlockEntity)t.getWorld().getBlockEntity(e.getPos().down());t.assertTrue(e.getStack(Pcb.OUTPUT_SLOT).isEmpty(),"Hopper extracts photomask");t.assertEquals(Pcb.layout(hopper.getStack(0)),PcbLayout.inverter(),"Hopper preserves layout data");t.assertEquals(e.getStack(1).getCount(),4,"Hopper cannot steal pending solder");t.complete();});
    }
}
