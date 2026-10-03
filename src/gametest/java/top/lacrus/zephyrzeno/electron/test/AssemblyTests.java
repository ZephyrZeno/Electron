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
import top.lacrus.zephyrzeno.electron.industrial.*;
import top.lacrus.zephyrzeno.electron.circuit.*;

public final class AssemblyTests implements FabricGameTest {
    private static BlockPos plant(TestContext t,ComponentBlock block){
        var p=t.getAbsolutePos(new BlockPos(24,4,34));var w=t.getWorld();
        if(block==Foundry.THERMAL||block==Foundry.NUCLEAR){IndustrialFixtures.plant(w,p,block);return p;}
        for(var q:BlockPos.iterate(p.add(-3,0,-3),p.add(3,5,3)))w.setBlockState(q,Blocks.AIR.getDefaultState());
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)w.setBlockState(p.add(x,-1,z),Blocks.SMOOTH_STONE.getDefaultState());
        w.setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
        for(var d:Direction.Type.HORIZONTAL)w.setBlockState(p.offset(d),Foundry.PLANT_CASING.getDefaultState());
        return p;
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void formedThermalHasCollisionAndPreservesModuleMaterials(TestContext t){
        var p=plant(t,Foundry.THERMAL);var w=t.getWorld();var tank=(FluidEntity)w.getBlockEntity(PlantStructures.tankPos(p,w.getBlockState(p)));
        t.assertEquals(Assembly.form(w,p,null),"formed","Complete thermal assembly forms");
        t.assertEquals(PowerPlant.voltage(w.getBlockState(p)),11000d,"Formed plant has actual 11 kV output");
        t.assertTrue(w.getBlockState(p.north(4).up(2)).getCollisionShape(w,p.north(4).up(2)).getBoundingBoxes().size()>0,"Chimney has physical collision");
        t.assertEquals(tank.amount,15000,"Existing tank contents are preserved");
        t.assertEquals(PowerPlant.requirement(w,p,ComponentBlock.Kind.THERMAL),"ready","Structure checks resolve originals");
        Assembly.dismantle(w,p,null);
        t.assertTrue(w.getBlockState(p.north(4)).isOf(Industrial.BOILER),"Boiler restored");
        t.assertTrue(w.isAir(p.north(4).up(2)),"Claimed air restored");
        t.assertEquals(tank.amount,15000,"Unforming preserves the reservoir");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void blockedAssemblyIsAtomicAndCannotConsumeOccupiedSpace(TestContext t){
        var p=plant(t,Foundry.THERMAL);var w=t.getWorld();w.setBlockState(p.north(4).up(2),Blocks.DIAMOND_BLOCK.getDefaultState());
        t.assertEquals(Assembly.form(w,p,null),"space","Occupied chimney volume rejected");
        t.assertFalse(Assembly.formed(w.getBlockState(p)),"Controller not half formed");
        t.assertTrue(w.getBlockState(p.north(4)).isOf(Industrial.BOILER),"All originals left intact");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void breakingAPartRestoresOthersAndDropsOnlyItsOriginal(TestContext t){
        var p=plant(t,Foundry.THERMAL);var w=t.getWorld();t.assertEquals(Assembly.form(w,p,null),"formed","Assembly forms");
        var part=p.north(4);var drops=Block.getDroppedStacks(w.getBlockState(part),w,part,w.getBlockEntity(part),null,new ItemStack(Items.DIAMOND_PICKAXE));
        t.assertEquals(drops.size(),1,"One original item stack");
        t.assertTrue(drops.getFirst().isOf(Industrial.BOILER.asItem()),"Drops casing, not invisible proxy");
        w.setBlockState(part,Blocks.AIR.getDefaultState());
        t.assertFalse(Assembly.formed(w.getBlockState(p)),"Breaking a part unforms the core");
        t.assertTrue(w.getBlockState(p.add(2,0,-3)).isOf(Industrial.TURBINE),"Unbroken module restored");
        t.assertTrue(w.isAir(part),"Broken casing not duplicated");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=35)
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
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void unloadedOrphanRestoresInventoryFreeOriginal(TestContext t){
        var p=plant(t,Foundry.SUBSTATION);var w=t.getWorld();t.assertEquals(Assembly.form(w,p,null),"formed","Station forms");
        var part=(AssemblyPartEntity)w.getBlockEntity(p.north());
        part.origin=p.add(5,0,0); // Represents a restored chunk whose owner was removed while it was unloaded.
        t.runAtTick(21,()->{t.assertTrue(w.getBlockState(p.north()).isOf(Foundry.PLANT_CASING),"Loaded orphan restores its retained original");t.complete();});
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=35)
    public void windDownleadReachesBenchAndCoreRemovalRestoresWholeTower(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(12,14,12));
        var base=IndustrialFixtures.wind(w,p,7);t.assertEquals(Assembly.form(w,p,null),"formed","Wind turbine forms");
        var circuit=CircuitState.get(w);t.assertFalse(circuit.endpoints().containsKey(CircuitParts.node(p,0)),"Top terminals are hidden after formation");
        t.assertTrue(circuit.endpoints().containsKey(CircuitParts.node(base,2)),"External output lives at base cabinet");
        circuit.simulate(w);t.assertTrue(circuit.lastBranches.stream().anyMatch(branch->branch.id().endsWith(":downlead+")),"A paid complete mast supplies its physical base");
        w.setBlockState(p,Blocks.AIR.getDefaultState());for(int i=1;i<=7;i++)t.assertTrue(w.getBlockState(p.down(i)).isOf(Industrial.MAST),"Removing core restores mast materials");
        t.assertTrue(w.getBlockState(base).isOf(Industrial.WIND_BASE),"Base cabinet is retained");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void assemblyGeometryIsBoundedAndExportable(TestContext t){
        var all=new com.google.gson.JsonObject();
        for(var kind:new ComponentBlock.Kind[]{ComponentBlock.Kind.THERMAL,ComponentBlock.Kind.HYDRO,ComponentBlock.Kind.NUCLEAR,ComponentBlock.Kind.WIND,ComponentBlock.Kind.LIGHTNING,ComponentBlock.Kind.SUBSTATION,ComponentBlock.Kind.MAINS,ComponentBlock.Kind.DISTRIBUTION}){
            var model=AssemblyGeometry.model(kind,kind==ComponentBlock.Kind.WIND||kind==ComponentBlock.Kind.LIGHTNING||kind==ComponentBlock.Kind.DISTRIBUTION?7:0,kind==ComponentBlock.Kind.DISTRIBUTION);
            var parts=new com.google.gson.JsonArray();
            for(var piece:model.pieces()){var obj=new com.google.gson.JsonObject();obj.add("a",new com.google.gson.Gson().toJsonTree(new double[]{piece.a().x,piece.a().y,piece.a().z}));obj.add("b",new com.google.gson.Gson().toJsonTree(new double[]{piece.b().x,piece.b().y,piece.b().z}));obj.addProperty("r",piece.radius());obj.addProperty("beam",piece.beam());obj.addProperty("sides",piece.sides());obj.addProperty("top_r",piece.topRadius());obj.addProperty("texture",piece.texture());parts.add(obj);}
            all.add(kind.name(),parts);
            for(var cell:model.cells().values())for(var box:cell.getBoundingBoxes())t.assertTrue(box.minX>=0&&box.maxX<=1&&box.minY>=0&&box.maxY<=1&&box.minZ>=0&&box.maxZ<=1,"Collision stays in its registered world cell");
        }
        try{var file=java.nio.file.Path.of("../assembly-meshes.json");java.nio.file.Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(all));}catch(java.io.IOException ex){throw new RuntimeException(ex);}t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
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
    @GameTest(templateName="electron:industrial_test",tickLimit=65)
    public void coolantCannotWashAwayNonCubicMachines(TestContext t){
        var w=t.getWorld();var p=plant(t,Foundry.NUCLEAR);
        // An exterior leak may flow against a precise partial-collision control cabinet.
        w.setBlockState(p.south(),Blocks.WATER.getDefaultState());
        t.assertEquals(Assembly.form(w,p,null),"formed","Cooled reactor forms");
        var e=(ComponentEntity)w.getBlockEntity(p);e.fuelEnergy=200000;
        t.runAtTick(50,()->{
            t.assertTrue(Assembly.formed(w.getBlockState(p)),"Coolant does not wash away the controller");
            t.assertEquals(Assembly.integrity(w,p,e),"ready","Partial collision casings resist fluid replacement");
            t.assertTrue(e.energy>0,"Cooled reactor remains operational");t.complete();
        });
    }
}
