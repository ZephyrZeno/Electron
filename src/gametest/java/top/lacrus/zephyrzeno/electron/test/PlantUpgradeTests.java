package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.appliance.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.*;
import top.lacrus.zephyrzeno.electron.multiblock.*;
import top.lacrus.zephyrzeno.electron.power.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class PlantUpgradeTests implements FabricGameTest {
    private static BlockPos device(TestContext t,int x,int z,ComponentBlock block){
        var p=t.getAbsolutePos(new BlockPos(x,3,z));t.getWorld().setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));CircuitState.get(t.getWorld()).track(p,t.getWorld().getBlockState(p));return p;
    }
    private static void floor(TestContext t){for(var p:BlockPos.iterate(t.getAbsolutePos(new BlockPos(0,2,0)),t.getAbsolutePos(new BlockPos(17,2,12))))t.getWorld().setBlockState(p,Blocks.SMOOTH_STONE.getDefaultState());}
    private static void wire(TestContext t,BlockPos a,int ap,BlockPos b,int bp){
        var s=CircuitState.get(t.getWorld());var ea=s.endpoints().get(node(a,ap));var eb=s.endpoints().get(node(b,bp));var path=s.connectionRoute(t.getWorld(),ea,eb,0,true);
        var wire=new Wire(UUID.randomUUID(),ea.id(),eb.id(),0,path,s.lengthUnits(path,ea,eb),true);s.wires.put(wire.id(),wire);s.changed();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void bodyAndReadonlyConsoleCannotAcceptFuelAndCabinetClosesWithoutDisconnecting(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(10,4,12));IndustrialFixtures.plant(w,p,Foundry.THERMAL);Assembly.form(w,p,null);
        var e=(ComponentEntity)w.getBlockEntity(p);var player=t.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);var coal=new ItemStack(Items.COAL,5);
        PowerPlant.interact(w,p,e.getCachedState(),e,player,coal);
        t.assertEquals(coal.getCount(),5,"Body use cannot consume held coal");t.assertEquals(e.fuelEnergy,0d,"Body use grants no fuel energy");
        t.assertTrue(e.cabinetOpen,"Direct controller use opens its cabinet");
        WiringCabinet.toggle(w,e);t.assertFalse(WiringCabinet.accessible(w,p),"Closed cabinet rejects new wire work");
        t.assertTrue(e.getCachedState().get(ComponentBlock.ENABLED),"Closing does not switch the generator off");
        var service=(PlantServiceEntity)w.getBlockEntity(PlantStructures.consolePos(p,e.getCachedState()));
        var menu=service.createMenu(5,player.getInventory(),player);
        for(int i=0;i<4;i++){t.assertFalse(menu.slots.get(i).canInsert(coal),"Read-only machine slot");t.assertFalse(menu.slots.get(i).canTakeItems(player),"No manual output bypass");}
        t.assertFalse(service.insertOne(coal),"Console cannot feed fuel");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=35)
    public void outputChestReceivesAshOnlyAtMarkedOutletAndInputRejectsWater(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(10,4,12));IndustrialFixtures.plant(w,p,Foundry.THERMAL);Assembly.form(w,p,null);
        var e=(ComponentEntity)w.getBlockEntity(p);var in=PlantStructures.fuelPos(p,e.getCachedState());var out=PlantStructures.outputPos(p,e.getCachedState());
        w.setBlockState(in.west(),Blocks.CHEST.getDefaultState());w.setBlockState(out.west(),Blocks.CHEST.getDefaultState());
        var chest=(ChestBlockEntity)w.getBlockEntity(in.west());chest.setStack(0,new ItemStack(Items.WATER_BUCKET));chest.setStack(1,new ItemStack(Items.COAL,5));
        t.runAtTick(24,()->{var result=(ChestBlockEntity)w.getBlockEntity(out.west());t.assertTrue(result.getStack(0).isOf(Industrial.ASH),"Ash reaches the output chest");
            t.assertTrue(chest.getStack(0).isOf(Items.WATER_BUCKET),"Wrong input item stays in source chest");t.assertTrue(e.fuelEnergy>0,"Accepted coal is actually consumed");t.complete();});
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=50)
    public void cordsPowerTwoLoadsThroughStripWithFiniteSourceAndSurviveSnapshot(TestContext t){
        floor(t);var w=t.getWorld();var s=CircuitState.get(w);var generator=device(t,2,4,Foundry.GENERATOR);var outlet=device(t,5,4,Appliances.WALL);
        var strip=device(t,8,4,Appliances.STRIP);var lamp=device(t,11,3,Foundry.LAMP);var second=device(t,11,7,Foundry.LAMP);
        ((ComponentEntity)w.getBlockEntity(generator)).energy=2000;wire(t,generator,0,outlet,0);wire(t,generator,1,outlet,1);
        for(var pair:List.of(new BlockPos[]{outlet,strip},new BlockPos[]{strip,lamp},new BlockPos[]{strip,second})){
            var c=CordNetwork.connect(w,s,pair[0],pair[1]);s.cords.put(c.id(),c);
        }s.changed();
        t.runAtTick(28,()->{
            t.assertTrue(w.getBlockState(lamp).get(ComponentBlock.LEVEL)>10&&w.getBlockState(second).get(ComponentBlock.LEVEL)>10,"Parallel cord-fed lamps light");
            t.assertTrue(((ComponentEntity)w.getBlockEntity(generator)).energy<1999,"Cord-fed loads spend source energy");
            var receiver=new CircuitSnapshots.Receiver();net.minecraft.nbt.NbtCompound received=null;
            for(var packet:CircuitSnapshots.split(s.snapshot())){var result=receiver.accept(packet);if(result.isPresent())received=result.get();}
            t.assertTrue(received!=null,"Snapshot arrives atomically");var copy=CircuitState.read(received);
            t.assertEquals(copy.cords.size(),3,"Both-pole cords persist and sync");t.assertTrue(copy.cords.values().stream().allMatch(c->c.length()<=12),"Physical length cap retained");t.complete();
        });
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void adapterRequiresInputEnergyAndNeverCreatesFreeVoltage(TestContext t){
        floor(t);var w=t.getWorld();var p=device(t,4,4,Appliances.ADAPTER);var e=(ComponentEntity)w.getBlockEntity(p);double supplied=0,delivered=0;
        for(int i=0;i<160;i++){
            var branches=new ArrayList<DcSolver.Branch>();Appliances.stamp(w,p,e.getCachedState(),e,branches);
            if(i>=20)branches.add(DcSolver.Branch.source("test_supply",node(p,0),node(p,1),240,.5));
            branches.add(DcSolver.Branch.resistor("load",node(p,2),node(p,3),120));
            var result=DcSolver.solve(branches);
            if(i<20)t.assertTrue(Math.abs(result.voltage(node(p,2))-result.voltage(node(p,3)))<.01,"Adapter has no output before input power");
            supplied+=Math.max(0,(result.voltage(node(p,0))-result.voltage(node(p,1)))*result.current("in:d:"+p.asLong()))*.05;
            delivered+=Math.max(0,-result.current("d:"+p.asLong()))*24*.05;
            Appliances.update(w,p,e.getCachedState(),e,result);
        }
        t.assertTrue(e.voltage>23,"Paid input starts regulated output");t.assertTrue(delivered>5,"Real useful output supplied");
        t.assertTrue(delivered+e.converterEnergy<=supplied*.88+1e-5,"Conversion loss and stored energy are conserved");
        var copy=new ComponentEntity(p,e.getCachedState());copy.read(e.createNbt(w.getRegistryManager()),w.getRegistryManager());t.assertEquals(copy.converterEnergy,e.converterEnergy,"Adapter energy persists without truncation");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void lowVoltageFaultsHaveNoLongArcAndMachineSoundsAreDistinct(TestContext t){
        t.assertFalse(FaultEffects.hasArc(24,.1),"Low voltage emits sparks rather than a long discharge");
        t.assertFalse(FaultEffects.hasArc(240,.1),"Mains sparks do not imply an arbitrary long arc");
        t.assertTrue(FaultEffects.hasArc(11000,.5),"High voltage supports a visible gap");
        t.assertFalse(FaultEffects.hasArc(11000,20),"No arc across an implausible gap");
        var events=new HashSet<net.minecraft.sound.SoundEvent>();
        for(var block:new ComponentBlock[]{Foundry.WIND,Foundry.HYDRO,Foundry.THERMAL,Foundry.NUCLEAR,Industrial.PUMP,Processing.CENTRIFUGE,Processing.CRUSHER})events.add(MachineSoundProfile.of(block.getDefaultState()).sound());
        t.assertEquals(events.size(),7,"Seven machine families use different sound events");t.complete();
    }
}
