package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import net.minecraft.world.LightType;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.item.CircuitToolItem;
import top.lacrus.zephyrzeno.electron.mechanical.*;
import top.lacrus.zephyrzeno.electron.power.*;
import top.lacrus.zephyrzeno.electron.industrial.*;
import top.lacrus.zephyrzeno.electron.multiblock.Assembly;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class GridAndMechanicalTests implements FabricGameTest {
    private static BlockPos at(TestContext t,int x,int y,int z){return t.getAbsolutePos(new BlockPos(x,y,z));}
    private static BlockPos place(TestContext t,int x,int y,int z,Block b){var p=at(t,x,y,z);t.getWorld().setBlockState(p,b instanceof ComponentBlock?b.getDefaultState().with(ComponentBlock.FACING,Direction.UP):b.getDefaultState());return p;}
    private static ComponentEntity e(TestContext t,BlockPos p){return (ComponentEntity)t.getWorld().getBlockEntity(p);}
    private static void floor(TestContext t){for(int x=0;x<60;x++)for(int z=0;z<60;z++)place(t,x,3,z,Blocks.SMOOTH_STONE);}
    private static void connect(TestContext t,BlockPos a,int ap,BlockPos b,int bp){if(t.getWorld().getBlockState(a).isOf(Foundry.THERMAL)){a=a.south(7);ap+=2;}connectDirect(t,a,ap,b,bp);}
    private static void connectDirect(TestContext t,BlockPos a,int ap,BlockPos b,int bp){
        var s=CircuitState.get(t.getWorld());var start=s.endpoints().get(node(a,ap));var end=s.endpoints().get(node(b,bp));List<SurfacePoint> path;
        try{path=s.connectionRoute(t.getWorld(),start,end,0);}catch(CircuitError first){
            for(var old:s.wires.values())for(var point:old.path())if(point.host().isWithinDistance(a,18)&&!s.hasBridge(point))try{s.validateBridgeSpace(point);var bridge=new Bridge(UUID.randomUUID(),point.host(),point.face(),net.minecraft.registry.Registries.BLOCK.getId(t.getWorld().getBlockState(point.host()).getBlock()).toString());s.bridges.put(bridge.id(),bridge);}catch(CircuitError occupied){}
            try{path=s.connectionRoute(t.getWorld(),start,end,0);}catch(CircuitError failure){throw new IllegalStateException("Route "+a+":"+ap+" to "+b+":"+bp,failure);}
        }
        var wire=new Wire(UUID.randomUUID(),start.id(),end.id(),0,path,s.lengthUnits(path,start,end),true);s.wires.put(wire.id(),wire);s.changed();
    }
    private static BlockPos thermal(TestContext t){var p=at(t,12,4,13);var w=t.getWorld();IndustrialFixtures.plant(w,p,Foundry.THERMAL);t.assertEquals(Assembly.form(w,p,null),"formed","Assembled thermal plant");var hv=p.south(7);w.setBlockState(hv,Foundry.STATION_11K.getDefaultState().with(ComponentBlock.FACING,Direction.UP));for(var d:Direction.Type.HORIZONTAL)w.setBlockState(hv.offset(d),Foundry.PLANT_CASING.getDefaultState());t.assertEquals(Assembly.form(w,hv,null),"formed","Matched high-voltage station");connectDirect(t,p,0,hv,0);connectDirect(t,p,1,hv,1);return p;}
    @GameTest(templateName="electron:industrial_test",tickLimit=120)
    public void fueledPlantPowersIsolatedStationAndLightsActualWorld(TestContext t){
        floor(t);var gen=thermal(t);e(t,gen).fuelEnergy=16000;var sub=place(t,16,4,16,Foundry.SUBSTATION);for(var d:Direction.Type.HORIZONTAL)t.getWorld().setBlockState(sub.offset(d),Foundry.PLANT_CASING.getDefaultState());var lamp=place(t,16,4,22,Foundry.LAMP);
        connect(t,gen,0,sub,0);connect(t,gen,1,sub,1);connect(t,sub,2,lamp,0);connect(t,sub,3,lamp,1);
        t.runAtTick(40,()->{var s=CircuitState.get(t.getWorld());t.assertTrue(e(t,gen).fuelEnergy<16000,"Generation consumes fuel");t.assertTrue(e(t,lamp).voltage>23,"Regulated real output feeds lamp");t.assertEquals(t.getWorld().getBlockState(lamp).getLuminance(),15,"Rated lamp emits vanilla level 15");t.assertTrue(t.getWorld().getLightLevel(LightType.BLOCK,lamp.up())>=14,"Adjacent air actually receives block light");t.assertFalse(s.readings.commonReference(node(sub,0),node(sub,2)),"Station isolates input and output");t.getWorld().setBlockState(gen.north(4),Blocks.AIR.getDefaultState());e(t,gen.south(7)).converterEnergy=0;e(t,sub).converterEnergy=0;});
        t.runAtTick(60,()->{t.assertTrue(e(t,lamp).voltage<.001,"Broken structure stops output");t.assertEquals(t.getWorld().getBlockState(lamp).getLuminance(),0,"Unpowered lamp emits no light");t.complete();});
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=80)
    public void shaftDrivesMountedBlockAndDisconnectsWhenBroken(TestContext t){
        floor(t);var source=place(t,11,4,13,Foundry.GENERATOR);var motor=place(t,16,4,13,Foundry.MOTOR);e(t,source).energy=200;
        connect(t,source,0,motor,0);connect(t,source,1,motor,1);
        t.getWorld().setBlockState(motor.up(),Foundry.SHAFT.getDefaultState());t.getWorld().setBlockState(motor.up(2),Foundry.TURNTABLE.getDefaultState());
        var table=(MechanicalEntity)t.getWorld().getBlockEntity(motor.up(2));table.mounted=new ItemStack(Blocks.OAK_LOG);table.markDirty();
        t.runAtTick(35,()->{t.assertTrue(table.speed>100,"Real powered motor drives the mounted block");t.assertTrue(table.angle>0,"Mechanical rotation advances");var copy=new MechanicalEntity(table.getPos(),table.getCachedState());copy.read(table.createNbt(t.getWorld().getRegistryManager()),t.getWorld().getRegistryManager());t.assertTrue(copy.mounted.isOf(Items.OAK_LOG),"Mounted block survives save/reload");t.getWorld().setBlockState(motor.up(),Blocks.AIR.getDefaultState());});
        t.runAtTick(40,()->{t.assertEquals(table.speed,0d,"Breaking the shaft disconnects rotation");t.complete();});
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=100)
    public void emptyStationStartsACascadedBuckConverter(TestContext t){
        floor(t);var gen=thermal(t);e(t,gen).fuelEnergy=16000;
        var sub=place(t,16,4,16,Foundry.SUBSTATION);for(var d:Direction.Type.HORIZONTAL)t.getWorld().setBlockState(sub.offset(d),Foundry.PLANT_CASING.getDefaultState());
        var buck=place(t,16,4,21,Foundry.BUCK);var coil=place(t,16,4,26,Foundry.RELAY);
        connect(t,gen,0,sub,0);connect(t,gen,1,sub,1);connect(t,sub,2,buck,0);connect(t,sub,3,buck,1);connect(t,buck,2,coil,0);connect(t,buck,3,coil,1);
        t.runAtTick(60,()->{t.assertFalse(e(t,sub).paused||e(t,buck).paused,"Cascaded converters must converge from empty buffers");t.assertTrue(e(t,coil).inputVoltage>4.9&&e(t,coil).contactClosed,"Generated 240 V reaches a 5 V coil through both conversion stages");t.complete();});
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void failedInputSolverCannotDischargeAnIsolatedStationForFree(TestContext t){
        floor(t);var sub=place(t,12,4,13,Foundry.SUBSTATION);
        for(var d:Direction.Type.HORIZONTAL)t.getWorld().setBlockState(sub.offset(d),Foundry.PLANT_CASING.getDefaultState());
        var lamp=place(t,17,4,20,Foundry.LAMP);connect(t,sub,2,lamp,0);connect(t,sub,3,lamp,1);
        e(t,sub).converterEnergy=12;var s=CircuitState.get(t.getWorld());
        var point=s.endpoints().get(node(sub,0)).point();var extra=new ArrayList<UUID>();String previous=node(sub,0);
        // Synthetic branches deliberately exceed the solver budget without placing hundreds of blocks.
        for(int i=0;i<DcSolver.MAX_ISLAND_NODES+1;i++){var id=UUID.randomUUID();String next="budget:"+sub.asLong()+":"+i;s.wires.put(id,new Wire(id,previous,next,0,List.of(point,point),1,true));extra.add(id);previous=next;}
        try{
            s.simulate(t.getWorld());
            t.assertTrue(e(t,sub).paused&&e(t,lamp).paused,"Input failure pauses the isolated output and its loads");
            t.assertEquals(e(t,sub).converterEnergy,12d,"Paused station retains its buffer");
            t.assertEquals(s.readings.current("d:"+lamp.asLong()),0d,"No output power while energy accounting is paused");
        }finally{extra.forEach(s.wires::remove);}
        t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void coloredSpoolsHaveDistinctItemsAndRetainPaidLength(TestContext t){
        Set<Item> items=new HashSet<>();
        for(int color=0;color<8;color++)for(boolean cable:new boolean[]{false,true}){var spool=CircuitToolItem.spool(93,color,cable);items.add(spool.getItem());t.assertEquals(CircuitToolItem.color(spool),color,"Item fixes conductor color");var n=CircuitToolItem.data(spool);n.putInt("color",(color+1)%8);CircuitToolItem.data(spool,n);t.assertEquals(CircuitToolItem.color(spool),color,"Writing tool state cannot recolor a spool");t.assertEquals(CircuitToolItem.units(spool),93,"Fractional length retained");t.assertEquals(CircuitToolItem.cable(spool),cable,"Correct conductor rating");}
        t.assertEquals(items.size(),16,"Sixteen distinct item IDs");var old=new ItemStack(Foundry.CABLE);var n=CircuitToolItem.data(old);n.putInt("units",123);n.putInt("color",5);CircuitToolItem.data(old,n);t.assertEquals(CircuitToolItem.units(old),123,"Legacy length preserved");t.assertEquals(CircuitToolItem.color(old),5,"Legacy color preserved");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=70)
    public void reactorLosesPowerWhenCoolingIsRemovedAndInletIsNotASource(TestContext t){
        floor(t);var reactor=at(t,25,4,40);IndustrialFixtures.plant(t.getWorld(),reactor,Foundry.NUCLEAR);t.assertEquals(Assembly.form(t.getWorld(),reactor,null),"formed","Large reactor forms");e(t,reactor).fuelEnergy=200000;e(t,reactor).fuelLoaded=true;
        var inlet=place(t,47,4,12,Foundry.MAINS);var lamp=place(t,51,4,12,Foundry.LAMP);connect(t,inlet,2,lamp,0);connect(t,inlet,3,lamp,1);
        t.runAtTick(15,()->{t.assertTrue(e(t,reactor).energy>0,"Fuel and complete cooled structure generate energy");t.assertTrue(e(t,lamp).voltage<.001,"Mains inlet cannot make free energy");e(t,reactor).temperature=90;var tank=(FluidEntity)t.getWorld().getBlockEntity(PlantStructures.tankPos(reactor,t.getWorld().getBlockState(reactor)));tank.change(-tank.amount);});
        t.runAtTick(20,()->{t.assertFalse(t.getWorld().getBlockState(reactor).get(ComponentBlock.ENABLED),"Loss of cooling causes shutdown");t.assertEquals(e(t,reactor).energy,0d,"Invalid hot structure cannot supply buffered power");t.complete();});
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void cableClearanceIsMeasuredAgainstRealTerrain(TestContext t){
        floor(t);var a=Vec3d.of(at(t,1,5,3));var b=Vec3d.of(at(t,8,5,3));
        t.assertTrue(CableSafety.lowPoint(t.getWorld(),OverheadCable.curve(a,b)).isPresent(),"Low sagging span detects the terrain below");
        for(int x=0;x<10;x++)for(int y=5;y<=9;y++)for(int z=2;z<=4;z++)place(t,x,y,z,Blocks.AIR);
        t.assertTrue(CableSafety.lowPoint(t.getWorld(),OverheadCable.curve(a.add(0,5,0),b.add(0,5,0))).isEmpty(),"Raised span clears the terrain");t.complete();
    }
}
