package top.lacrus.zephyrzeno.electron.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.*;
import top.lacrus.zephyrzeno.electron.multiblock.*;

public final class PlantServiceTests implements FabricGameTest {
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void openingCreativeInventoryBuildsEveryActualTabWithoutDuplicates(TestContext t){
        ItemGroups.updateDisplayContext(FeatureFlags.VANILLA_FEATURES,false,t.getWorld().getRegistryManager());
        ItemGroups.updateDisplayContext(FeatureFlags.VANILLA_FEATURES,true,t.getWorld().getRegistryManager());
        var group=Registries.ITEM_GROUP.get(Electron.id("power"));
        t.assertEquals(group.getDisplayStacks().stream().filter(s->s.isOf(Industrial.PUMP.asItem())).count(),1L,"Water pump appears exactly once in the actual power tab");
        t.assertTrue(group.getDisplayStacks().stream().anyMatch(s->s.isOf(Industrial.FUEL_PORT.asItem())),"Fuel intake is available in creative inventory");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void placingOneModuleDoesNotCreateAFreeWorkshopAndFiveCannotCraft(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(7,4,7));var s=Industrial.TABLE.getDefaultState();
        for(int i=0;i<6;i++)w.setBlockState(p.add(IndustrialTableBlock.offset(Direction.NORTH,i)).down(),Blocks.SMOOTH_STONE.getDefaultState());
        w.setBlockState(p,s);Industrial.TABLE.onPlaced(w,p,s,null,new ItemStack(Industrial.TABLE));
        t.assertTrue(w.getBlockState(p.east()).isAir(),"Placing a single item leaves other cells empty");
        for(int i=1;i<5;i++)w.setBlockState(p.add(IndustrialTableBlock.offset(Direction.NORTH,i)),s);
        t.assertFalse(IndustrialTableBlock.form(w,p),"Five paid cells cannot form a workshop");
        w.setBlockState(p.add(IndustrialTableBlock.offset(Direction.NORTH,5)),s);
        t.assertTrue(IndustrialTableBlock.form(w,p),"Six paid cells form automatically");
        t.assertTrue(IndustrialTableBlock.complete(w,p,w.getBlockState(p)),"All six coordinates are required");t.complete();
    }
    private static ComponentEntity plant(TestContext t,boolean nuclear){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(25,4,36));IndustrialFixtures.plant(w,p,nuclear?Foundry.NUCLEAR:Foundry.THERMAL);
        t.assertEquals(Assembly.form(w,p,null),"formed","Plant and accessible service modules form");return (ComponentEntity)w.getBlockEntity(p);
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=35)
    public void vanillaHopperFeedsThermalPlantDuringWorldTicks(TestContext t){
        var e=plant(t,false);var w=t.getWorld();var port=PlantStructures.fuelPos(e.getPos(),e.getCachedState());
        w.setBlockState(port.up(),Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING,Direction.DOWN));
        ((HopperBlockEntity)w.getBlockEntity(port.up())).setStack(0,new ItemStack(Items.COAL,4));
        t.runAtTick(20,()->{t.assertTrue(e.fuelEnergy>0,"Vanilla hopper inserts fuel and the plant consumes it");t.assertTrue(((HopperBlockEntity)w.getBlockEntity(port.up())).getStack(0).getCount()<4,"Input coal actually leaves the hopper");t.complete();});
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=35)
    public void adjacentChestFeedsNuclearConsoleAndIntakeSharesOneInventory(TestContext t){
        var e=plant(t,true);var w=t.getWorld();var port=PlantStructures.fuelPos(e.getPos(),e.getCachedState());
        w.setBlockState(port.east(),Blocks.CHEST.getDefaultState());((ChestBlockEntity)w.getBlockEntity(port.east())).setStack(0,new ItemStack(Foundry.FUEL_ROD,3));
        t.runAtTick(26,()->{
            var console=(PlantServiceEntity)w.getBlockEntity(PlantStructures.consolePos(e.getPos(),e.getCachedState()));
            var input=(PlantServiceEntity)w.getBlockEntity(port);
            t.assertTrue(e.fuelLoaded&&e.fuelEnergy>0,"A chest feeds real finite nuclear fuel");
            t.assertEquals(console.getStack(0).getCount()+((ChestBlockEntity)w.getBlockEntity(port.east())).getStack(0).getCount(),2,"One active rod plus two pending rods, no duplication");
            t.assertTrue(console.getStack(0)==input.getStack(0),"Console and intake share a single stack");
            var saved=e.createNbt(w.getRegistryManager());var copy=new ComponentEntity(e.getPos(),e.getCachedState());copy.read(saved,w.getRegistryManager());
            t.assertEquals(copy.plantItems.get(0).getCount(),e.plantItems.get(0).getCount(),"Queued fuel survives save/load");t.assertTrue(copy.fuelLoaded,"Active fuel survives save/load");t.complete();
        });
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void spentRodAndBucketOutputsBlockSafelyAndHoppersExtractOnlyOutputs(TestContext t){
        var e=plant(t,true);var w=t.getWorld();var input=(PlantServiceEntity)w.getBlockEntity(PlantStructures.fuelPos(e.getPos(),e.getCachedState()));
        e.fuelLoaded=true;e.fuelEnergy=0;e.plantItems.set(0,new ItemStack(Foundry.FUEL_ROD,2));e.plantItems.set(2,new ItemStack(Foundry.SPENT_ROD,16));
        PlantOperations.feed(w,e);t.assertEquals(e.plantItems.get(0).getCount(),2,"Blocked spent output prevents loading another rod");
        input.removeStack(2,1);PlantOperations.feed(w,e);t.assertEquals(e.plantItems.get(0).getCount(),1,"Free output permits exactly one new rod");t.assertEquals(e.plantItems.get(2).getCount(),16,"Spent rod is retained exactly once");
        t.assertFalse(input.canExtract(0,e.plantItems.get(0),Direction.DOWN),"Pending fuel cannot leak to output hopper");
        t.assertTrue(input.canExtract(2,e.plantItems.get(2),Direction.DOWN),"Spent rods can leave through the bottom");
        t.assertFalse(input.insertOne(new ItemStack(Foundry.RAW_URANIUM)),"Raw uranium is not a finished fuel rod");
        var tank=(FluidEntity)w.getBlockEntity(PlantStructures.tankPos(e.getPos(),e.getCachedState()));tank.change(tank.capacity());
        e.plantItems.set(1,new ItemStack(Items.WATER_BUCKET));PlantOperations.feed(w,e);t.assertTrue(e.plantItems.get(1).isOf(Items.WATER_BUCKET),"Full reservoir does not consume a bucket");
        tank.change(-1000);PlantOperations.feed(w,e);t.assertTrue(e.plantItems.get(1).isEmpty()&&e.plantItems.get(3).isOf(Items.BUCKET),"A full bucket transfers exactly 1000 mB with empty bucket returned");t.assertEquals(tank.amount,tank.capacity(),"Water volume is conserved");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void dismantlingDoesNotDuplicateQueuedFuel(TestContext t){
        var e=plant(t,false);var w=t.getWorld();e.plantItems.set(0,new ItemStack(Items.COAL,17));e.plantItems.set(1,new ItemStack(Items.WATER_BUCKET));
        var p=e.getPos();w.breakBlock(p,true);int coal=0,buckets=0;
        for(var item:w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,new Box(p).expand(4),x->true)){if(item.getStack().isOf(Items.COAL))coal+=item.getStack().getCount();if(item.getStack().isOf(Items.WATER_BUCKET))buckets+=item.getStack().getCount();}
        t.assertEquals(coal,17,"Fuel is dropped once at the real inventory owner");t.assertEquals(buckets,1,"Bucket is dropped once");t.complete();
    }
    private static boolean blocked(AssemblyGeometry.Model model,Box region){return model.cells().entrySet().stream().anyMatch(e->e.getValue().getBoundingBoxes().stream().anyMatch(b->b.offset(e.getKey()).intersects(region)));}
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void nuclearControlRoomIsWalkableAndPoolHasSolidCover(TestContext t){
        var model=AssemblyGeometry.model(ComponentBlock.Kind.NUCLEAR,0,false);
        t.assertFalse(blocked(model,new Box(17.2,.2,-1.8,17.8,2.1,.6)),"Control room entrance has real player headroom");
        t.assertFalse(blocked(model,new Box(17.2,.2,-4,17.8,2.1,-3.3)),"Interior aisle is not a solid building cube");
        t.assertTrue(blocked(model,new Box(-12,2.42,-10,-11.4,3,-9.4)),"Visible grille keeps players out of the cooling basin");t.complete();
    }
}
