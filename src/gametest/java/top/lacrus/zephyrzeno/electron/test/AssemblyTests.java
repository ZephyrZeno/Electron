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
    @GameTest(templateName="electron:industrial_test",tickLimit=60)
    public void nuclearPipesEndAtAnotherPipeMachineReservoirOrServicePort(TestContext t){
        var kind=ComponentBlock.Kind.NUCLEAR;
        for(int turn=0;turn<4;turn++){
            var model=AssemblyGeometry.model(kind,0,false,turn);
            var pipes=model.pieces().stream().filter(p->p.texture().equals("steel_pipe")).toList();
            var state=Foundry.NUCLEAR.getDefaultState().with(ComponentBlock.TURN,turn);
            for(var pipe:pipes)for(var point:List.of(pipe.a(),pipe.b())){
                boolean joined=pipes.stream().anyMatch(other->other!=pipe&&segmentDistance(point,other.a(),other.b())<pipe.radius()+other.radius()+.015);
                if(!joined){
                    var canonical=AssemblyGeometry.rotate(point,(4-turn)&3);
                    joined=new Box(-13.7,.2,-11.7,-9,2.4,-5.4).contains(canonical);
                    for(var port:List.of(PlantStructures.waterPos(BlockPos.ORIGIN,state),PlantStructures.tankPos(BlockPos.ORIGIN,state),PlantStructures.fuelPos(BlockPos.ORIGIN,state),PlantStructures.outputPos(BlockPos.ORIGIN,state)))joined|=new Box(port).contains(point);
                }
                if(!joined)joined=model.pieces().stream().anyMatch(body->{
                    if(!body.beam())return new Box(body.a(),body.b()).expand(.01).contains(point);
                    if(body.sides()<=4||!Set.of("reactor_shell","hydro_blue","cast_base","cooling_concrete","transformer_oil").contains(body.texture()))return false;
                    double length=body.b().distanceTo(body.a()),along=point.subtract(body.a()).dotProduct(body.b().subtract(body.a()).normalize());
                    return along>=0&&along<=length&&segmentDistance(point,body.a(),body.b())<=Math.max(body.radius(),body.topRadius());
                });
                t.assertTrue(joined,"Pipe endpoint must enter a physical destination, turn "+turn+": "+point);
            }
        }
        t.complete();
    }
    private static double segmentDistance(Vec3d p,Vec3d a,Vec3d b){var delta=b.subtract(a);return p.distanceTo(a.add(delta.multiply(Math.clamp(p.subtract(a).dotProduct(delta)/delta.lengthSquared(),0,1))));}
    @GameTest(templateName="electron:industrial_test",tickLimit=60)
    public void bakedMeshesHaveFiniteVerticesUnitNormalsAndReusableDistanceVariants(TestContext t){
        var report=new com.google.gson.JsonObject();
        for(var kind:new ComponentBlock.Kind[]{ComponentBlock.Kind.THERMAL,ComponentBlock.Kind.NUCLEAR,ComponentBlock.Kind.WIND,ComponentBlock.Kind.HYDRO}){
            int height=kind==ComponentBlock.Kind.WIND?7:0;
            for(int turn=0;turn<4;turn++){
                var near=AssemblyMesh.get(kind,height,false,turn,false);var far=AssemblyMesh.get(kind,height,false,turn,true);
                t.assertTrue(near==AssemblyMesh.get(kind,height,false,turn,false),"Static geometry is cached across frames");
                t.assertTrue(far.vertices()<=near.vertices(),"Distance variant never adds geometry");
                for(var batch:near.batches())for(int i=0;i<batch.vertices().length;i+=8){
                    var data=batch.vertices();for(int j=0;j<8;j++)t.assertTrue(Float.isFinite(data[i+j]),"Position, UV and normal must be finite");
                    double length=data[i+5]*data[i+5]+data[i+6]*data[i+6]+data[i+7]*data[i+7];t.assertTrue(Math.abs(length-1)<.002,"Lighting normal has unit length");
                }
                if(turn==0){var row=new com.google.gson.JsonObject();row.addProperty("near_vertices",near.vertices());row.addProperty("distant_vertices",far.vertices());row.addProperty("material_batches",near.batches().size());report.add(kind.name(),row);}
            }
        }
        try{java.nio.file.Files.writeString(java.nio.file.Path.of("../assembly-render-audit.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException e){throw new RuntimeException(e);}t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=35)
    public void wrenchRepairsOnlyPaidMissingModulesAndRetainsInventory(TestContext t){
        var p=plant(t,Foundry.THERMAL);var w=t.getWorld();Assembly.form(w,p,null);
        var core=(ComponentEntity)w.getBlockEntity(p);core.plantItems.set(0,new ItemStack(Items.COAL,9));
        var tank=(FluidEntity)w.getBlockEntity(PlantStructures.tankPos(p,w.getBlockState(p)));
        var missing=p.north(4);w.setBlockState(missing,Blocks.AIR.getDefaultState());
        t.assertTrue(core.manualAssemblyLock,"Breaking a formed part pauses automatic recreation");
        var player=t.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        t.assertEquals(AssemblyRepair.repair(w,p,player),"repair_materials","No free boiler restoration");
        t.assertTrue(w.isAir(missing),"Missing material leaves the world unchanged");
        player.getInventory().setStack(0,new ItemStack(Industrial.BOILER,2));
        t.assertEquals(AssemblyRepair.repair(w,p,player),"repaired","Wrench rebuilds the stored shape with paid parts");
        t.assertEquals(player.getInventory().getStack(0).getCount(),1,"Exactly one boiler consumed");
        t.assertTrue(Assembly.formed(w.getBlockState(p)),"Whole assembly restored");
        t.assertEquals(core.plantItems.get(0).getCount(),9,"Queued fuel retained");t.assertEquals(tank.amount,15000,"Reservoir retained");
        t.assertEquals(AssemblyRepair.repair(w,p,player),"repair_intact","Ordinary wrench use never dismantles a healthy plant");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void repairBlueprintSurvivesSaveAndRejectsOccupiedCellsWithoutPayment(TestContext t){
        var p=plant(t,Foundry.THERMAL);var w=t.getWorld();Assembly.form(w,p,null);var core=(ComponentEntity)w.getBlockEntity(p);
        w.setBlockState(p.north(4),Blocks.AIR.getDefaultState());
        var copy=new ComponentEntity(p,core.getCachedState());copy.read(core.createNbt(w.getRegistryManager()),w.getRegistryManager());
        t.assertTrue(copy.assemblyBlueprint.get(new BlockPos(0,0,-4)).isOf(Industrial.BOILER),"Broken original block is retained in saved repair layout");
        w.setBlockState(p.north(4),Blocks.DIAMOND_BLOCK.getDefaultState());var player=t.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        player.getInventory().setStack(0,new ItemStack(Industrial.BOILER));
        t.assertEquals(AssemblyRepair.repair(w,p,player),"repair_obstructed","Repair must not replace occupied space");
        t.assertEquals(player.getInventory().getStack(0).getCount(),1,"Blocked repair consumes nothing");
        t.assertTrue(w.getBlockState(p.north(4)).isOf(Blocks.DIAMOND_BLOCK),"Player block remains");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void failedRepairReturnsNewPartsWithoutRefillingTank(TestContext t){
        var p=plant(t,Foundry.THERMAL);var w=t.getWorld();Assembly.form(w,p,null);w.setBlockState(p.north(4),Blocks.AIR.getDefaultState());
        w.setBlockState(p.north(4).up(2),Blocks.DIAMOND_BLOCK.getDefaultState());
        var player=t.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);player.getInventory().setStack(0,new ItemStack(Industrial.BOILER));
        t.assertEquals(AssemblyRepair.repair(w,p,player),"space","Obstructed visual volume prevents full reconstruction");
        t.assertTrue(w.isAir(p.north(4)),"Staged replacement is rolled back");t.assertEquals(player.getInventory().getStack(0).getCount(),1,"Failed formation never charges the player");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void damagedControllerRequiresReplacementAndRestartsOff(TestContext t){
        var p=plant(t,Foundry.THERMAL);var w=t.getWorld();Assembly.form(w,p,null);var core=(ComponentEntity)w.getBlockEntity(p);
        w.setBlockState(p,w.getBlockState(p).with(ComponentBlock.BROKEN,true));core.temperature=90;
        var player=t.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);player.getInventory().setStack(0,new ItemStack(Foundry.THERMAL));
        t.assertEquals(AssemblyRepair.repair(w,p,player),"repair_hot","Hot plant cannot be repaired during the accident");
        core.temperature=20;t.assertEquals(AssemblyRepair.repair(w,p,player),"repair_controller_done","Replacement controller repairs the burnt original");
        t.assertTrue(player.getInventory().getStack(0).isEmpty(),"Replacement controller actually consumed");
        t.assertFalse(w.getBlockState(p).get(ComponentBlock.ENABLED),"Repair leaves output off for deliberate restart");
        t.assertFalse(w.getBlockState(p).get(ComponentBlock.BROKEN),"Burnt state cleared");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void windHatchIsEntirelyInFrontOfTheCone(TestContext t){
        var model=AssemblyGeometry.model(ComponentBlock.Kind.WIND,7,false);
        var hatch=model.pieces().stream().filter(p->p.texture().equals("hatch")).findFirst().orElseThrow();
        t.assertTrue(Math.max(hatch.a().z,hatch.b().z)<.5-.58,"Hatch stays outside even the widest tower skin");
        t.assertTrue(hatch.a().x>.1&&hatch.b().x<.9,"Single door is centered on the mast");t.complete();
    }
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
            if(kind==ComponentBlock.Kind.THERMAL||kind==ComponentBlock.Kind.NUCLEAR)t.assertTrue(model.cells().keySet().stream().noneMatch(p->p.getY()<0),"Machine details never intrude into their paid foundation");
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
