package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.*;
import top.lacrus.zephyrzeno.electron.multiblock.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.power.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class IndustrialTests implements FabricGameTest {
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void spawnedLightningActuallyRedirectsAndChargesDuringWorldTicks(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(20,30,20));
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)w.setBlockState(p.add(x,-8,z),Blocks.SMOOTH_STONE.getDefaultState());
        for(int y=1;y<=7;y++)w.setBlockState(p.down(y),Foundry.TOWER_SECTION.getDefaultState());
        w.setBlockState(p,Foundry.LIGHTNING.getDefaultState().with(ComponentBlock.FACING,Direction.UP));w.setBlockState(p.up(),Blocks.LIGHTNING_ROD.getDefaultState());
        t.assertEquals(Assembly.form(w,p,null),"formed","Live collector assembled");
        var bolt=net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(w);bolt.setCosmetic(true);bolt.setPosition(Vec3d.ofCenter(p.east(10).down()));w.spawnEntity(bolt);
        t.runAtTick(2,()->{t.assertEquals(bolt.getBlockPos(),p.up(),"The tick mixin moves the actual spawned entity to the tower");t.assertTrue(((ComponentEntity)w.getBlockEntity(p)).energy>2900,"The live circuit receives the captured energy");bolt.discard();t.complete();});
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void multipleLightningFlashesCannotRefillEnergyTwice(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(12,14,12));
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)w.setBlockState(p.add(x,-8,z),Blocks.SMOOTH_STONE.getDefaultState());
        for(int y=1;y<=7;y++)w.setBlockState(p.down(y),Foundry.TOWER_SECTION.getDefaultState());
        w.setBlockState(p,Foundry.LIGHTNING.getDefaultState().with(ComponentBlock.FACING,Direction.UP));w.setBlockState(p.up(),Blocks.LIGHTNING_ROD.getDefaultState());
        t.assertEquals(Assembly.form(w,p,null),"formed","Completed lightning collector");
        var first=net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(w);var second=net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(w);
        for(var bolt:List.of(first,second)){bolt.setPosition(Vec3d.ofCenter(p.up()));bolt.setCosmetic(true);w.spawnEntity(bolt);}
        var e=(ComponentEntity)w.getBlockEntity(p);PowerPlant.prepare(w,p,e.getCachedState(),e);t.assertEquals(e.energy,GridModel.CAPACITY,"Two strikes fill only the finite buffer");
        e.energy=0;PowerPlant.prepare(w,p,e.getCachedState(),e);t.assertEquals(e.energy,0d,"Repeated flashes of both existing bolts cannot create more energy");
        var s=CircuitState.read(CircuitState.get(w).writeNbt(new net.minecraft.nbt.NbtCompound(),w.getRegistryManager()));
        t.assertFalse(s.claimLightning(first.getUuid()),"Captured bolt remains consumed after reload");t.assertFalse(s.claimLightning(second.getUuid()),"Both simultaneous UUIDs survive reload");first.discard();second.discard();t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void breakingWorkshopSideKeepsFivePaidModulesAndReturnsInventory(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(6,4,6));var state=Industrial.TABLE.getDefaultState();
        for(int i=0;i<6;i++){var q=p.add(IndustrialTableBlock.offset(Direction.NORTH,i));w.setBlockState(q.down(),Blocks.SMOOTH_STONE.getDefaultState());w.setBlockState(q,state.with(IndustrialTableBlock.PART,i));}
        t.assertTrue(IndustrialTableBlock.form(w,p),"Six separately placed modules form");
        ((IndustrialTableEntity)w.getBlockEntity(p)).setStack(0,new ItemStack(Items.COPPER_INGOT,13));w.breakBlock(p.east(2).south(),true);
        for(int i=0;i<5;i++){var q=p.add(IndustrialTableBlock.offset(Direction.NORTH,i));t.assertTrue(w.getBlockState(q).isOf(Industrial.TABLE)&&!w.getBlockState(q).get(IndustrialTableBlock.FORMED),"Five paid modules remain but cannot be used");}
        int tables=0,copper=0;for(var drop:w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(p).expand(5),x->true)){if(drop.getStack().isOf(Industrial.TABLE.asItem()))tables+=drop.getStack().getCount();if(drop.getStack().isOf(Items.COPPER_INGOT))copper+=drop.getStack().getCount();}
        t.assertEquals(tables,1,"Exactly one workbench item is returned");t.assertEquals(copper,13,"Stored ingredients are returned exactly once");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void strikeRedirectChoosesOnlyEligibleHigherCollectors(TestContext t){
        var w=t.getWorld();var first=t.getAbsolutePos(new BlockPos(12,14,12));var second=first.east(22);
        for(var p:List.of(first,second)){
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)w.setBlockState(p.add(x,-8,z),Blocks.SMOOTH_STONE.getDefaultState());
            for(int y=1;y<=7;y++)w.setBlockState(p.down(y),Foundry.TOWER_SECTION.getDefaultState());
            w.setBlockState(p,Foundry.LIGHTNING.getDefaultState().with(ComponentBlock.FACING,Direction.UP));w.setBlockState(p.up(),Blocks.LIGHTNING_ROD.getDefaultState());t.assertEquals(Assembly.form(w,p,null),"formed","Collector built from paid tower sections");
        }
        Set<BlockPos> selected=new HashSet<>();var bolt=net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(w);
        for(int i=0;i<64;i++){bolt.setPosition(Vec3d.ofCenter(first.east(11).down(4)));LightningCapture.redirect(w,bolt);selected.add(bolt.getBlockPos().down());}
        t.assertEquals(selected,Set.of(first,second),"Both eligible towers participate in random choice");
        var high=Vec3d.ofCenter(first.east(11).up(8));bolt.setPosition(high);LightningCapture.redirect(w,bolt);t.assertEquals(bolt.getPos(),high,"Higher original targets are not intercepted");
        var far=Vec3d.ofCenter(first.east(110).down(4));bolt.setPosition(far);LightningCapture.redirect(w,bolt);t.assertEquals(bolt.getPos(),far,"Outside-radius strikes retain their target");bolt.discard();t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void prolongedCoolingLossProducesOneBoundedPressureFailure(TestContext t){
        var p=t.getAbsolutePos(new BlockPos(12,4,14));var w=t.getWorld();IndustrialFixtures.plant(w,p,Foundry.THERMAL);t.assertEquals(Assembly.form(w,p,null),"formed","Thermal machine assembled");
        var e=(ComponentEntity)w.getBlockEntity(p);var tank=(FluidEntity)w.getBlockEntity(PlantStructures.tankPos(p,e.getCachedState()));tank.change(-tank.amount);e.temperature=181;e.decayHeat=1;e.fuelEnergy=16000;
        var distant=p.east(12);w.setBlockState(distant,Blocks.GLASS.getDefaultState());PowerPlant.prepare(w,p,e.getCachedState(),e);
        t.assertEquals(e.fuelEnergy,0d,"Pressure accident clears the remaining plant fuel");t.assertEquals(e.decayHeat,0d,"Failure cannot repeatedly explode from the same residual heat");t.assertTrue(w.getBlockState(distant).isOf(Blocks.GLASS),"Accident remains local to the plant");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void completeThermalFormsAutomaticallyAndConsumesAssembledParts(TestContext t){
        var p=t.getAbsolutePos(new BlockPos(10,4,12));var w=t.getWorld();IndustrialFixtures.plant(w,p,Foundry.THERMAL);
        var e=(ComponentEntity)w.getBlockEntity(p);e.fuelEnergy=16000;
        t.runAtTick(15,()->{t.assertTrue(Assembly.formed(w.getBlockState(p)),"Completing the structure forms without a wrench");t.assertTrue(w.getBlockState(p.north(4)).isOf(Assembly.PART),"Paid boiler incorporated into the model");t.assertTrue(e.energy>0&&e.fuelEnergy<16000,"Finite fuel powers completed structure");t.complete();});
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void nuclearNeedsWholeThirtySixByThirtySixSiteAndSavedCoolingTank(TestContext t){
        var p=t.getAbsolutePos(new BlockPos(25,4,35));var w=t.getWorld();IndustrialFixtures.plant(w,p,Foundry.NUCLEAR);
        var missing=p.add(20,-1,-35);w.setBlockState(missing,Blocks.AIR.getDefaultState());
        t.assertEquals(Assembly.form(w,p,null),"nuclear_foundation","Even a corner of the full foundation is required");w.setBlockState(missing,Blocks.SMOOTH_STONE.getDefaultState());
        t.assertEquals(Assembly.form(w,p,null),"formed","Complete large facility forms");var e=(ComponentEntity)w.getBlockEntity(p);e.fuelEnergy=200000;
        var tank=(FluidEntity)w.getBlockEntity(PlantStructures.tankPos(p,w.getBlockState(p)));var copy=new FluidEntity(tank.getPos(),tank.getCachedState());copy.read(tank.createNbt(w.getRegistryManager()),w.getRegistryManager());t.assertEquals(copy.amount,200000,"Tank contents survive serialization");
        PowerPlant.prepare(w,p,e.getCachedState(),e);t.assertTrue(e.energy>0&&tank.amount<200000,"Generation requires and consumes cooling water");
        e.temperature=90;tank.change(-tank.amount);PowerPlant.prepare(w,p,e.getCachedState(),e);t.assertFalse(w.getBlockState(p).get(ComponentBlock.ENABLED),"Hot dry reactor trips");t.assertEquals(e.energy,0d,"Trip clears electrical output");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void stalledPumpCannotCreateWaterAndPoweredTransferConservesVolume(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(5,3,5));w.setBlockState(p,Industrial.PUMP.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
        var input=p.north();var pipe=p.south();var out=p.south(2);w.setBlockState(input,Industrial.THERMAL_TANK.getDefaultState());w.setBlockState(pipe,Industrial.GLASS_PIPE.getDefaultState());w.setBlockState(out,Industrial.THERMAL_TANK.getDefaultState());
        var source=(FluidEntity)w.getBlockEntity(input);source.change(1000);var conduit=(FluidEntity)w.getBlockEntity(pipe);var target=(FluidEntity)w.getBlockEntity(out);var motor=(ComponentEntity)w.getBlockEntity(p);
        FluidNetwork.pump(w,p,motor.getCachedState(),motor);t.assertEquals(source.amount,1000,"Unpowered pump does not transfer");motor.voltage=24;motor.current=.1;
        for(int i=0;i<20;i++)FluidNetwork.pump(w,p,motor.getCachedState(),motor);
        t.assertTrue(target.amount>0,"Powered flow reaches reservoir after priming pipes");t.assertEquals(source.amount+conduit.amount+target.amount,1000,"No duplicated water in tank or pipe buffers");
        w.setBlockState(pipe,Industrial.VALVE.getDefaultState().with(top.lacrus.zephyrzeno.electron.industrial.FluidBlock.OPEN,false));int before=source.amount;FluidNetwork.pump(w,p,motor.getCachedState(),motor);t.assertEquals(source.amount,before,"Closed valve stops extraction");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void workshopCraftIsAtomicAndCannotOverwriteOutput(TestContext t){
        var inv=new SimpleInventory(28);var recipe=AssemblyRecipes.recipes().getFirst();int i=0;for(var ingredient:recipe.inputs())inv.setStack(i++,new ItemStack(ingredient.item(),ingredient.count()));
        inv.setStack(27,new ItemStack(Items.DIAMOND));t.assertFalse(AssemblyRecipes.craft(inv,0),"Occupied output rejects craft");t.assertEquals(inv.getStack(0).getCount(),recipe.inputs().getFirst().count(),"Rejected craft retains materials");
        inv.setStack(27,ItemStack.EMPTY);t.assertTrue(AssemblyRecipes.craft(inv,0),"Complete recipe produces module");t.assertTrue(inv.getStack(27).isOf(Industrial.BOILER.asItem()),"Correct physical module");t.assertFalse(AssemblyRecipes.craft(inv,0),"Consumed ingredients cannot craft twice");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void verticalDropsNeedBothAnchorsAndDoNotPermitDiagonalSpans(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(8,12,8));var q=p.down(7);for(var x:BlockPos.iterate(q.add(-1,-1,-2),p.add(1,1,1)))w.setBlockState(x,Blocks.AIR.getDefaultState());
        for(var x:List.of(p,q))w.setBlockState(x,Foundry.DISTRIBUTION.getDefaultState().with(ComponentBlock.FACING,Direction.NORTH));
        var s=CircuitState.get(w);var a=s.endpoints().get(node(p,0));var b=s.endpoints().get(node(q,0));t.assertTrue(VerticalWire.connection(s,a,b),"Aligned secured terminals allow a hanging drop");var path=s.connectionRoute(w,a,b,0,true);var wire=new Wire(UUID.randomUUID(),a.id(),b.id(),0,path,VerticalWire.units(s,path),true,false,true);s.wires.put(wire.id(),wire);
        t.assertEquals(wire.units(),117,"Seven-block drop includes both actual terminal relief leads");var copy=CircuitState.read(s.snapshot());t.assertTrue(copy.wires.get(wire.id()).vertical(),"Drop mode survives save/reload");
        var diagonal=new Endpoint("c:test",new SurfacePoint(q.east(),Direction.NORTH,3,3,0),net.minecraft.text.Text.empty());t.assertFalse(VerticalWire.connection(s,a,diagonal),"No diagonal unsupported shortcuts");
        w.setBlockState(q,Blocks.AIR.getDefaultState());s.tick(w);t.assertFalse(s.wires.containsKey(wire.id()),"Removing lower anchor removes and refunds the conductor");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void waterAtTurbineHeadMustActuallyFlow(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(8,5,8));w.setBlockState(p.down(),Blocks.SMOOTH_STONE.getDefaultState());w.setBlockState(p,Foundry.HYDRO.getDefaultState().with(ComponentBlock.FACING,Direction.UP));var state=w.getBlockState(p);
        w.setBlockState(p.north(),Blocks.WATER.getDefaultState());t.assertFalse(PlantStructures.flowingWater(w,p,state),"A static source alone does not generate");
        w.setBlockState(p.north(),Blocks.WATER.getDefaultState().with(net.minecraft.block.FluidBlock.LEVEL,4));t.assertTrue(PlantStructures.flowingWater(w,p,state),"Flowing water drives the submerged turbine end");t.complete();
    }
}
