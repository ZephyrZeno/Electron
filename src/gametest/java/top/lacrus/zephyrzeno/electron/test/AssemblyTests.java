package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.multiblock.*;
import top.lacrus.zephyrzeno.electron.power.*;
import top.lacrus.zephyrzeno.electron.circuit.*;

public final class AssemblyTests implements FabricGameTest {
    private static BlockPos plant(TestContext t,ComponentBlock block){
        var p=t.getAbsolutePos(new BlockPos(5,2,5));var w=t.getWorld();
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)w.setBlockState(p.add(x,-1,z),Blocks.SMOOTH_STONE.getDefaultState());
        w.setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
        for(var d:Direction.Type.HORIZONTAL)w.setBlockState(p.offset(d),Foundry.PLANT_CASING.getDefaultState());
        if(block==Foundry.THERMAL)w.setBlockState(p.east(),Blocks.FURNACE.getDefaultState());
        return p;
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void formedThermalHasCollisionAndPreservesFurnaceInventory(TestContext t){
        var p=plant(t,Foundry.THERMAL);var w=t.getWorld();var furnace=(FurnaceBlockEntity)w.getBlockEntity(p.east());furnace.setStack(0,new ItemStack(Items.RAW_IRON,3));
        t.assertEquals(Assembly.form(w,p,null),"formed","Complete thermal assembly forms");
        t.assertEquals(PowerPlant.voltage(w.getBlockState(p)),11000d,"Formed plant has actual 11 kV output");
        t.assertTrue(w.getBlockState(p.north().up(2)).getCollisionShape(w,p.north().up(2)).getBoundingBoxes().size()>0,"Chimney has physical collision");
        t.assertEquals(furnace.getStack(0).getCount(),3,"Furnace inventory preserved");
        t.assertEquals(PowerPlant.requirement(w,p,ComponentBlock.Kind.THERMAL),"ready","Structure checks resolve originals");
        Assembly.dismantle(w,p,null);
        t.assertTrue(w.getBlockState(p.north()).isOf(Foundry.PLANT_CASING),"Casing restored");
        t.assertTrue(w.isAir(p.north().up(2)),"Claimed air restored");
        t.assertEquals(furnace.getStack(0).getCount(),3,"Unforming does not duplicate or erase furnace items");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void blockedAssemblyIsAtomicAndCannotConsumeOccupiedSpace(TestContext t){
        var p=plant(t,Foundry.THERMAL);var w=t.getWorld();w.setBlockState(p.north().up(2),Blocks.DIAMOND_BLOCK.getDefaultState());
        t.assertEquals(Assembly.form(w,p,null),"space","Occupied chimney volume rejected");
        t.assertFalse(Assembly.formed(w.getBlockState(p)),"Controller not half formed");
        t.assertTrue(w.getBlockState(p.west()).isOf(Foundry.PLANT_CASING),"All originals left intact");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void breakingAPartRestoresOthersAndDropsOnlyItsOriginal(TestContext t){
        var p=plant(t,Foundry.THERMAL);var w=t.getWorld();t.assertEquals(Assembly.form(w,p,null),"formed","Assembly forms");
        var part=p.west();var drops=Block.getDroppedStacks(w.getBlockState(part),w,part,w.getBlockEntity(part),null,new ItemStack(Items.DIAMOND_PICKAXE));
        t.assertEquals(drops.size(),1,"One original item stack");
        t.assertTrue(drops.getFirst().isOf(Foundry.PLANT_CASING.asItem()),"Drops casing, not invisible proxy");
        w.setBlockState(part,Blocks.AIR.getDefaultState());
        t.assertFalse(Assembly.formed(w.getBlockState(p)),"Breaking a part unforms the core");
        t.assertTrue(w.getBlockState(p.north()).isOf(Foundry.PLANT_CASING),"Unbroken casing restored");
        t.assertTrue(w.isAir(part),"Broken casing not duplicated");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=35)
    public void towerSizeContactsAndSavedPartsAgree(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(5,12,5));
        for(var q:BlockPos.iterate(p.add(-3,-8,-3),p.add(3,4,3)))w.setBlockState(q,Blocks.AIR.getDefaultState());
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)w.setBlockState(p.add(x,-8,z),Blocks.SMOOTH_STONE.getDefaultState());
        for(int i=1;i<=7;i++)w.setBlockState(p.down(i),Foundry.TOWER_SECTION.getDefaultState());
        w.setBlockState(p,Foundry.TOWER_TERMINAL.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
        t.assertEquals(Assembly.form(w,p,null),"formed","Tapered tower forms");
        t.assertTrue(OverheadCable.tower(w,p),"Formed tower retains valid structural column");
        t.assertTrue(AssemblyGeometry.baseRadius(20)>AssemblyGeometry.baseRadius(7),"More sections widen the tower base");
        var tip=p.add(-2,0,0);var e=(AssemblyPartEntity)w.getBlockEntity(tip);
        t.assertTrue(e!=null&&!e.shape().isEmpty(),"External terminal has a cell-local collision");
        var copy=new AssemblyPartEntity(tip,e.getCachedState());copy.read(e.createNbt(w.getRegistryManager()),w.getRegistryManager());
        t.assertEquals(copy.origin,p,"Original controller saved");t.assertEquals(copy.height,7,"Tower height saved");t.assertEquals(copy.shape().getBoundingBox(),e.shape().getBoundingBox(),"Saved collision matches");
        t.assertTrue(CircuitState.supports(w,p.down(4),Direction.SOUTH),"Tower provides a supported downlead");
        t.runAtTick(21,()->{t.assertTrue(Assembly.formed(w.getBlockState(p)),"Orphan cleanup leaves intact structures alone");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void unloadedOrphanRestoresInventoryFreeOriginal(TestContext t){
        var p=plant(t,Foundry.SUBSTATION);var w=t.getWorld();t.assertEquals(Assembly.form(w,p,null),"formed","Station forms");
        var part=(AssemblyPartEntity)w.getBlockEntity(p.north());
        part.origin=p.add(5,0,0); // Represents a restored chunk whose owner was removed while it was unloaded.
        t.runAtTick(21,()->{t.assertTrue(w.getBlockState(p.north()).isOf(Foundry.PLANT_CASING),"Loaded orphan restores its retained original");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=35)
    public void windDownleadReachesBenchAndCoreRemovalRestoresWholeTower(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(4,12,5));
        for(var q:BlockPos.iterate(p.add(-3,-8,-3),p.add(9,5,3)))w.setBlockState(q,Blocks.AIR.getDefaultState());
        for(int x=-3;x<=9;x++)for(int z=-3;z<=3;z++)w.setBlockState(p.add(x,-8,z),Blocks.SMOOTH_STONE.getDefaultState());
        for(int i=1;i<=7;i++)w.setBlockState(p.down(i),Foundry.TOWER_SECTION.getDefaultState());
        w.setBlockState(p,Foundry.WIND.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
        t.assertEquals(Assembly.form(w,p,null),"formed","Wind turbine forms");
        var load=p.add(6,-7,0);w.setBlockState(load,Foundry.MAINS.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
        var circuit=CircuitState.get(w);var a=circuit.endpoints().get(CircuitParts.node(p,0));var b=circuit.endpoints().get(CircuitParts.node(load,0));
        var path=circuit.connectionRoute(w,a,b,0);
        t.assertTrue(path.size()>20&&path.stream().anyMatch(point->point.host().getY()==p.getY()-8),"Supported downlead reaches the smooth-stone foundation");
        w.setBlockState(p,Blocks.AIR.getDefaultState());
        for(int i=1;i<=7;i++)t.assertTrue(w.getBlockState(p.down(i)).isOf(Foundry.TOWER_SECTION),"Removing controller restores each structural section");
        t.assertTrue(w.isAir(p.north().up(2)),"Rotor collision cells restored to air");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void assemblyGeometryIsBoundedAndExportable(TestContext t){
        var all=new com.google.gson.JsonObject();
        for(var kind:new ComponentBlock.Kind[]{ComponentBlock.Kind.THERMAL,ComponentBlock.Kind.HYDRO,ComponentBlock.Kind.NUCLEAR,ComponentBlock.Kind.WIND,ComponentBlock.Kind.LIGHTNING,ComponentBlock.Kind.SUBSTATION,ComponentBlock.Kind.MAINS,ComponentBlock.Kind.DISTRIBUTION}){
            var model=AssemblyGeometry.model(kind,kind==ComponentBlock.Kind.WIND||kind==ComponentBlock.Kind.LIGHTNING||kind==ComponentBlock.Kind.DISTRIBUTION?7:0,kind==ComponentBlock.Kind.DISTRIBUTION);
            var parts=new com.google.gson.JsonArray();
            for(var piece:model.pieces()){var obj=new com.google.gson.JsonObject();obj.add("a",new com.google.gson.Gson().toJsonTree(new double[]{piece.a().x,piece.a().y,piece.a().z}));obj.add("b",new com.google.gson.Gson().toJsonTree(new double[]{piece.b().x,piece.b().y,piece.b().z}));obj.addProperty("r",piece.radius());obj.addProperty("beam",piece.beam());obj.addProperty("sides",piece.sides());obj.addProperty("texture",piece.texture());parts.add(obj);}
            all.add(kind.name(),parts);
            for(var cell:model.cells().values())for(var box:cell.getBoundingBoxes())t.assertTrue(box.minX>=0&&box.maxX<=1&&box.minY>=0&&box.maxY<=1&&box.minZ>=0&&box.maxZ<=1,"Collision stays in its registered world cell");
        }
        try{var file=java.nio.file.Path.of("../assembly-meshes.json");java.nio.file.Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(all));}catch(java.io.IOException ex){throw new RuntimeException(ex);}t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void actualLightningIsCountedOnceAndReturnsFiniteCapturedEnergy(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(5,12,5));
        for(var q:BlockPos.iterate(p.add(-3,-8,-3),p.add(3,5,3)))w.setBlockState(q,Blocks.AIR.getDefaultState());
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)w.setBlockState(p.add(x,-8,z),Blocks.SMOOTH_STONE.getDefaultState());
        for(int i=1;i<=7;i++)w.setBlockState(p.down(i),Foundry.TOWER_SECTION.getDefaultState());
        w.setBlockState(p,Foundry.LIGHTNING.getDefaultState().with(ComponentBlock.FACING,Direction.UP));w.setBlockState(p.up(),Blocks.LIGHTNING_ROD.getDefaultState());
        t.assertEquals(Assembly.form(w,p,null),"formed","Collector forms on a real tower");
        var bolt=net.minecraft.entity.EntityType.LIGHTNING_BOLT.create(w);bolt.setCosmetic(true);bolt.setPosition(Vec3d.ofCenter(p.up()));w.spawnEntity(bolt);
        var e=(ComponentEntity)w.getBlockEntity(p);PowerPlant.prepare(w,p,e.getCachedState(),e);
        t.assertTrue(Math.abs(e.energy-3000)<1e-6,"Capture obeys the finite capacitor energy budget");
        t.assertEquals(e.lastPeakVoltage,300000000d,"Meter records the transient event voltage");
        t.assertEquals(e.lastPeakCurrent,30000d,"Meter records the transient event current");
        PowerPlant.prepare(w,p,e.getCachedState(),e);
        t.assertTrue(Math.abs(e.energy-3000)<1e-6,"The same lightning entity cannot be collected twice");
        t.assertEquals(PowerPlant.voltage(e.getCachedState()),240d,"Buffered output is regulated rather than a continuous lightning peak");
        bolt.discard();t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=65)
    public void coolantCannotWashAwayNonCubicMachines(TestContext t){
        var w=t.getWorld();var p=plant(t,Foundry.NUCLEAR);
        for(var d:Direction.Type.HORIZONTAL)w.setBlockState(p.offset(d),Foundry.REACTOR_CASING.getDefaultState());
        for(var water:List.of(p.east(2),p.west(2))){
            for(var d:Direction.Type.HORIZONTAL)if(w.isAir(water.offset(d)))w.setBlockState(water.offset(d),Blocks.GLASS.getDefaultState());
            w.setBlockState(water,Blocks.WATER.getDefaultState());
        }
        t.assertEquals(Assembly.form(w,p,null),"formed","Cooled reactor forms");
        var e=(ComponentEntity)w.getBlockEntity(p);e.fuelEnergy=200000;
        t.runAtTick(50,()->{
            t.assertTrue(Assembly.formed(w.getBlockState(p)),"Coolant does not wash away the controller");
            t.assertEquals(Assembly.integrity(w,p,e),"ready","Partial collision casings resist fluid replacement");
            t.assertTrue(e.energy>0,"Cooled reactor remains operational");t.complete();
        });
    }
}
