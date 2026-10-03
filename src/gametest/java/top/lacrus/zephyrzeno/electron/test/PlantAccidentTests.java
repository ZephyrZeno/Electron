package top.lacrus.zephyrzeno.electron.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.power.*;
import top.lacrus.zephyrzeno.electron.industrial.*;
import top.lacrus.zephyrzeno.electron.multiblock.Assembly;

public final class PlantAccidentTests implements FabricGameTest {
    @GameTest(templateName="electron:components_test")
    public void eventUsesActualVesselPositionAndSuppressesDuplicates(TestContext t){var p=t.getAbsolutePos(new BlockPos(5,3,5));var s=Foundry.NUCLEAR.getDefaultState().with(ComponentBlock.FACING,Direction.UP).with(ComponentBlock.TURN,1);var origin=PlantAccident.origin(p,s);t.assertEquals(origin,Vec3d.ofCenter(PlantStructures.at(p,s,-3,0,-20)).add(0,3,0),"Rotated vessel defines both sound and effect position");var n=PlantAccident.packet(t.getWorld(),origin,PlantAccident.NUCLEAR,p.getY()+.12);t.assertEquals(n.getDouble("ground_y"),p.getY()+.12,"Ground dust stays at the foundation instead of floating at vessel height");t.assertEquals(n.getDouble("x"),origin.x,"Packet preserves exact position");t.assertEquals(n.getInt("kind"),PlantAccident.NUCLEAR,"Nuclear accident selects mushroom plume");t.assertTrue(PlantAccident.emit(t.getWorld(),p,Foundry.CRANK.getDefaultState(),PlantAccident.NUCLEAR),"First accident emits");t.assertFalse(PlantAccident.emit(t.getWorld(),p,Foundry.CRANK.getDefaultState(),PlantAccident.NUCLEAR),"Same accident cannot restart every tick");t.complete();}
    @GameTest(templateName="electron:industrial_test",tickLimit=45)
    public void realDryBoilerFailureTriggersOneAccidentAndClearsEnergy(TestContext t){var p=t.getAbsolutePos(new BlockPos(12,4,18));IndustrialFixtures.plant(t.getWorld(),p,Foundry.THERMAL);t.assertEquals(Assembly.form(t.getWorld(),p,null),"formed","Physical boiler forms");var e=(ComponentEntity)t.getWorld().getBlockEntity(p);var s=e.getCachedState();var tank=(FluidEntity)t.getWorld().getBlockEntity(PlantStructures.tankPos(p,s));tank.change(-tank.amount);e.temperature=181;e.decayHeat=.8;e.energy=100;e.fuelEnergy=500;PowerPlant.prepare(t.getWorld(),p,s,e);t.assertEquals(e.energy,0d,"Failed boiler cannot supply energy");t.assertEquals(e.fuelEnergy,0d,"Failure discards remaining hot fuel energy");t.assertFalse(PlantAccident.emit(t.getWorld(),p,s,PlantAccident.THERMAL),"Real failure already emitted its event");t.complete();}
}
