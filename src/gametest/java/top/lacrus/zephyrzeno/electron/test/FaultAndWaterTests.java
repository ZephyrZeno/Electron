package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.circuit.CircuitParts.Wire;
import top.lacrus.zephyrzeno.electron.power.*;
import top.lacrus.zephyrzeno.electron.industrial.*;
import top.lacrus.zephyrzeno.electron.foundry.*;

public final class FaultAndWaterTests implements FabricGameTest {
    private static BlockPos pond(TestContext t,int width,int depth){
        var p=t.getAbsolutePos(new BlockPos(5,4,5));var w=t.getWorld();
        for(int x=-1;x<=width;x++)for(int z=-1;z<=width;z++)for(int y=-1;y<depth;y++)w.setBlockState(p.add(x,y,z),(x>=0&&x<width&&z>=0&&z<width&&y>=0?Blocks.WATER:Blocks.GLASS).getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
        return p;
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void twoIntakesShareOneLayerAndSavedDebt(TestContext t){
        var p=pond(t,3,2);var w=t.getWorld();var ledger=WaterBodies.get(w);
        t.assertEquals(ledger.withdraw(w,p,4000),4000,"First pump pays its actual extracted water");
        var restored=WaterBodies.read(ledger.writeNbt(new NbtCompound(),w.getRegistryManager()));
        t.assertEquals(restored.withdraw(w,p.add(2,0,2),4999),4999,"Second intake shares saved lake consumption");
        for(int x=0;x<3;x++)for(int z=0;z<3;z++)t.assertTrue(w.getBlockState(p.add(x,1,z)).isOf(Blocks.WATER),"No single-cell holes before one layer is consumed");
        t.assertEquals(restored.withdraw(w,p,1),1,"Final millibucket completes the layer");
        for(int x=0;x<3;x++)for(int z=0;z<3;z++){t.assertTrue(w.getBlockState(p.add(x,1,z)).isAir(),"Whole top layer disappears together");t.assertTrue(w.getBlockState(p.add(x,0,z)).isOf(Blocks.WATER),"Lower layer remains");}
        t.assertEquals(restored.withdraw(w,p,9001),9000,"Finite pond cannot supply beyond its remaining whole layer");t.assertEquals(restored.withdraw(w,p,50),0,"Empty intake gives no free water");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void largeVerifiedWaterAreaIsRenewable(TestContext t){
        var p=pond(t,32,4);var w=t.getWorld();t.assertEquals(WaterBodies.get(w).withdraw(w,p,2000000),2000000,"A bottom intake reaches the 1024-cell renewable surface");
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)t.assertTrue(w.getBlockState(p.add(x,0,z)).isOf(Blocks.WATER),"Large water surface never lowers");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void layersDoNotRegenerateFromIntermediateRemoval(TestContext t){
        var p=pond(t,2,2);var w=t.getWorld();WaterBodies.get(w).withdraw(w,p,4000);
        t.runAtTick(15,()->{for(int x=0;x<2;x++)for(int z=0;z<2;z++)t.assertTrue(w.getBlockState(p.add(x,1,z)).isAir(),"Neighbor updates do not regenerate the exhausted layer");t.complete();});
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void realPumpDrawsWorldWaterWithoutExceedingPaidEnergy(TestContext t){
        var intake=pond(t,3,2);var w=t.getWorld();var p=intake.west();w.setBlockState(p.down(),Blocks.SMOOTH_STONE.getDefaultState());
        w.setBlockState(p,Industrial.PUMP.getDefaultState().with(ComponentBlock.FACING,Direction.UP).with(ComponentBlock.TURN,Direction.WEST.getHorizontal()));
        w.setBlockState(p.west(),Industrial.THERMAL_TANK.getDefaultState());var pump=(ComponentEntity)w.getBlockEntity(p);var tank=(FluidEntity)w.getBlockEntity(p.west());
        t.assertEquals(FluidNetwork.pump(w,p,pump.getCachedState(),pump),0,"An unpowered pump cannot drain a lake");pump.voltage=24;pump.current=.1;
        int total=0;for(int i=0;i<20;i++)total+=FluidNetwork.pump(w,p,pump.getCachedState(),pump);
        t.assertEquals(total,1000,"Rated 2.4 W transfers exactly one bucket per second");t.assertEquals(tank.amount,1000,"All paid fluid reaches the tank");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void arcFlashLightsLocallyAndExpiresWithoutReplacingSolids(TestContext t){
        var p=t.getAbsolutePos(new BlockPos(5,8,5));var w=t.getWorld();w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState());FaultEffects.pulse(w,Vec3d.ofCenter(p));
        t.assertTrue(w.getBlockState(p).isOf(Blocks.IRON_BLOCK),"A pulse never destroys the struck surface");t.assertTrue(w.getBlockState(p.up()).isOf(FaultEffects.FLASH),"Air receives a real local light pulse");
        t.assertEquals(w.getBlockState(p.up()).getLuminance(),15,"Uses the vanilla 15-level light engine");
        t.runAtTick(6,()->{t.assertTrue(w.getBlockState(p.up()).isAir(),"Scheduled light expires");t.assertTrue(w.getBlockState(p).isOf(Blocks.IRON_BLOCK),"The original block remains");t.complete();});
    }
    private static CircuitState wire(TestContext t){
        var s=new CircuitState();var p=t.getAbsolutePos(new BlockPos(10,5,10));var path=List.of(new SurfacePoint(p.up(6),Direction.SOUTH,3,7,0),new SurfacePoint(p,Direction.SOUTH,3,7,0));
        var id=UUID.randomUUID();s.wires.put(id,new Wire(id,"live","tip",0,path,96,true,false,true));return s;
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void verticalGroundFaultIsARealEnergyConsumingBranch(TestContext t){
        var s=wire(t);var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(9,8,11));w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState());
        var source=DcSolver.Branch.source("s","live","return",240,2);var branches=new ArrayList<DcSolver.Branch>(List.of(source,DcSolver.Branch.resistor("line","live","tip",.01)));
        s.readings=DcSolver.solve(branches);var safety=new CableFaults.State();CableFaults.stamp(w,s,branches,safety);
        t.assertTrue(!safety.faults.isEmpty(),"Nearby metal creates a vertical-span fault");var result=DcSolver.solve(branches);double sourcePower=-result.current("s")*240,loss=0;
        for(var f:safety.faults.values()){double i=result.current(f.id());t.assertTrue(i>0,"Actual leakage current");loss+=i*i*f.resistance();t.assertTrue(f.to().squaredDistanceTo(f.from())>0,"Arc has distinct real endpoints");}
        t.assertTrue(sourcePower>=loss&&sourcePower-loss<2,"Fault loss is charged to source including source resistance");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void lowVoltageAndUnreferencedWiresDoNotDischarge(TestContext t){
        var s=wire(t);var w=t.getWorld();w.setBlockState(t.getAbsolutePos(new BlockPos(9,8,11)),Blocks.IRON_BLOCK.getDefaultState());
        var branches=new ArrayList<DcSolver.Branch>(List.of(DcSolver.Branch.source("s","live","return",24,2),DcSolver.Branch.resistor("line","live","tip",.01)));s.readings=DcSolver.solve(branches);
        var safety=new CableFaults.State();CableFaults.stamp(w,s,branches,safety);t.assertTrue(safety.faults.isEmpty(),"24 V insulated wiring does not produce HV arcs");
        s.readings=new DcSolver.Result(Map.of("live",240d),Map.of(),Map.of());safety=new CableFaults.State();CableFaults.stamp(w,s,branches,safety);t.assertTrue(safety.faults.isEmpty(),"A voltage without a source reference cannot create energy");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void actualTerrainFaceAndVoltageTierDetermineClearance(TestContext t){
        var p=t.getAbsolutePos(new BlockPos(8,6,8));var w=t.getWorld();w.setBlockState(p,Blocks.IRON_BLOCK.getDefaultState());
        var curve=List.of(Vec3d.of(p).add(-2,1.7,.5),Vec3d.of(p).add(3,1.7,.5));
        t.assertTrue(CableFaults.terrain(w,curve,CableFaults.clearance(240)).isEmpty(),"Low-voltage span has sufficient separation");var hit=CableFaults.terrain(w,curve,CableFaults.clearance(6600)).orElseThrow();
        t.assertTrue(Math.abs(hit.surface().y-(p.getY()+1))<1e-6,"Discharge terminates on the physical top face");t.assertTrue(CableFaults.clearance(24000)>CableFaults.clearance(6600),"Higher voltage has larger clearance");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void nearbyReturnWireArcsButIndependentIslandsStayIsolated(TestContext t){
        var s=wire(t);var first=s.wires.values().iterator().next();var path=first.path().stream().map(p->new SurfacePoint(p.host(),p.face(),7,p.v(),p.layer())).toList();var second=new Wire(UUID.randomUUID(),"other","other_tip",1,path,96,true,false,true);s.wires.put(second.id(),second);
        var branches=new ArrayList<DcSolver.Branch>(List.of(DcSolver.Branch.source("s","live","other",240,2),DcSolver.Branch.resistor("first","live","tip",.01),DcSolver.Branch.resistor("second","other","other_tip",.01)));
        s.readings=DcSolver.solve(branches);var safety=new CableFaults.State();CableFaults.stamp(t.getWorld(),s,branches,safety);t.assertTrue(safety.faults.keySet().stream().anyMatch(k->k.startsWith("cross:")),"Opposite conductors with insufficient clearance form a fault");
        branches=new ArrayList<>(List.of(DcSolver.Branch.source("s","live","return",240,2),DcSolver.Branch.source("s2","other","isolated_return",-240,2),DcSolver.Branch.resistor("first","live","tip",.01),DcSolver.Branch.resistor("second","other","other_tip",.01)));s.readings=DcSolver.solve(branches);safety=new CableFaults.State();CableFaults.stamp(t.getWorld(),s,branches,safety);t.assertTrue(safety.faults.keySet().stream().noneMatch(k->k.startsWith("cross:")),"Unrelated voltage references never fabricate a cross-island potential");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void shockRequiresCurrentAndHasOneSecondCooldown(TestContext t){
        var s=wire(t);var w=t.getWorld();var wire=s.wires.values().iterator().next();var middle=CableFaults.middle(s,wire);var pig=net.minecraft.entity.EntityType.PIG.create(w);pig.setPosition(middle.add(0,-.3,0));w.spawnEntity(pig);
        var branches=new ArrayList<DcSolver.Branch>(List.of(DcSolver.Branch.source("s","live","return",240,2),DcSolver.Branch.resistor("line","live","tip",.01)));s.readings=DcSolver.solve(branches);var safety=new CableFaults.State();CableFaults.stamp(w,s,branches,safety);s.readings=DcSolver.solve(branches);
        t.assertTrue(safety.faults.values().stream().anyMatch(f->pig.getUuid().equals(f.victim())),"Body contact becomes a real current path");
        for(int tick=1;tick<=6;tick++)t.runAtTick(tick,()->{if(w.getTime()%5!=0)return;float before=pig.getHealth();CableFaults.effects(w,s,safety);t.assertTrue(pig.getHealth()<before,"Current injures the contacting entity");float after=pig.getHealth();CableFaults.effects(w,s,safety);t.assertEquals(pig.getHealth(),after,"Multiple faults cannot bypass cooldown");pig.discard();t.complete();});
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void remeshPreservesFuelAndStoredEnergy(TestContext t){
        var p=t.getAbsolutePos(new BlockPos(15,4,17));var w=t.getWorld();IndustrialFixtures.plant(w,p,Foundry.THERMAL);t.assertEquals(top.lacrus.zephyrzeno.electron.multiblock.Assembly.form(w,p,null),"formed","Machine initially forms");
        var core=(ComponentEntity)w.getBlockEntity(p);core.energy=99;core.fuelEnergy=5000;core.assemblyRevision=0;
        t.assertEquals(top.lacrus.zephyrzeno.electron.multiblock.Assembly.refresh(w,p,core),"ready","Old geometry metadata upgrades");t.assertEquals(core.energy,99d,"Remeshing does not reset stored energy");t.assertEquals(core.fuelEnergy,5000d,"Fuel remains intact");t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void effectPacketAndMeshPreservePreciseContacts(TestContext t){
        var a=new Vec3d(.183,4.214,.971);var b=new Vec3d(1.345,3.142,.571);var packet=FaultEffects.packet(t.getWorld(),a,b,FaultEffects.ARC,80);
        t.assertEquals(packet.getDouble("X"),b.x,"Impact x retains sub-block precision");t.assertEquals(packet.getDouble("Y"),b.y,"Impact y retains sub-block precision");
        var mesh=ArcGeometry.create(a,b,125);t.assertEquals(mesh.getFirst().points().getFirst(),a,"Discharge starts at the cable");t.assertEquals(mesh.getFirst().points().getLast(),b,"Discharge reaches the struck surface");t.assertTrue(mesh.size()>1,"Secondary branches exist");
        var frames=new com.google.gson.JsonArray();for(int tick=0;tick<8;tick++)frames.add(new com.google.gson.Gson().toJsonTree(ArcGeometry.create(a,b,125+tick*31)));
        try{java.nio.file.Files.writeString(java.nio.file.Path.of("../arc-meshes.json"),frames.toString());}catch(java.io.IOException ex){throw new RuntimeException(ex);}t.complete();
    }
    @GameTest(templateName="electron:industrial_test",tickLimit=30)
    public void lowSocketKeepsBusesIsolatedAndThin(TestContext t){
        var w=t.getWorld();var p=t.getAbsolutePos(new BlockPos(5,4,5));w.setBlockState(p.down(),Blocks.SMOOTH_STONE.getDefaultState());w.setBlockState(p,Industrial.LOW_SOCKET.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
        var s=CircuitState.get(w);s.simulate(w);t.assertEquals(ComponentPins.count(Industrial.LOW_SOCKET.kind),8,"Eight accessible taps");
        t.assertFalse(s.readings.commonReference(CircuitParts.node(p,0),CircuitParts.node(p,4)),"Bus A and B are electrically isolated");t.assertTrue(w.getBlockState(p).getOutlineShape(w,p).getMax(Direction.Axis.Y)<.25,"Fits below the standard small-circuit height");t.complete();
    }
}
