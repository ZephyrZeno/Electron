package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class ProcessingTests implements FabricGameTest {
    private static ProcessingEntity machine(TestContext t,int x,int z,ProcessingBlock block){
        var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p.down(),Blocks.SMOOTH_STONE.getDefaultState());
        t.getWorld().setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));return (ProcessingEntity)t.getWorld().getBlockEntity(p);
    }
    private static void work(TestContext t,ProcessingEntity e,int ticks){e.voltage=24;e.current=((ProcessingBlock)e.getCachedState().getBlock()).watts/24;for(int i=0;i<ticks;i++)Processing.update(t.getWorld(),e.getPos(),e,false);}
    private static void connect(TestContext t,BlockPos a,int ap,BlockPos b,int bp){
        var s=CircuitState.get(t.getWorld());var ends=s.endpoints();var from=ends.get(node(a,ap));var to=ends.get(node(b,bp));var path=s.connectionRoute(t.getWorld(),from,to,0);s.validateCapacity(path);
        var wire=new Wire(UUID.randomUUID(),from.id(),to.id(),s.wires.size()%8,path,s.lengthUnits(path,from,to),true);s.wires.put(wire.id(),wire);s.changed();
    }
    @GameTest(templateName="electron:components_test",tickLimit=65)
    public void processingDrawsActualNetworkPowerAndStopsWithSource(TestContext t){
        var world=t.getWorld();var state=CircuitState.get(world);
        for(int x=0;x<10;x++)for(int z=0;z<9;z++){var p=t.getAbsolutePos(new BlockPos(x,2,z));world.setBlockState(p,Blocks.SMOOTH_STONE.getDefaultState());var bridge=new Bridge(UUID.randomUUID(),p,Direction.UP,"minecraft:smooth_stone");state.bridges.put(bridge.id(),bridge);}
        var core=machine(t,7,4,Processing.CENTRIFUGE);core.setStack(0,new ItemStack(Processing.CRUSHED_URANIUM,2));
        var source=t.getAbsolutePos(new BlockPos(1,2,4));world.setBlockState(source,Foundry.GENERATOR.getDefaultState().with(ComponentBlock.FACING,Direction.UP));var supply=(ComponentEntity)world.getBlockEntity(source);supply.energy=100;
        connect(t,source,0,core.getPos(),0);connect(t,source,1,core.getPos(),1);
        double[] before={0};
        t.runAtTick(25,()->{t.assertTrue(core.processingEnergy>10,"Network power performs work");t.assertTrue(supply.energy<85,"Work reduces the finite source");t.assertTrue(core.processingEnergy<=100-supply.energy+.001,"Output work never exceeds paid source energy");supply.energy=0;before[0]=core.processingEnergy;});
        t.runAtTick(42,()->{t.assertTrue(Math.abs(core.processingEnergy-before[0])<.001,"Disconnected source stops work");t.assertTrue(core.getStack(2).isEmpty(),"Incomplete batch produces nothing");t.assertEquals(core.getStack(0).getCount(),2,"Inputs remain until completion");t.complete();});
    }
    @GameTest(templateName="electron:components_test")
    public void fullFuelChainConservesInputsAndPaysEveryBatch(TestContext t){
        var crusher=machine(t,1,2,Processing.CRUSHER);var centrifuge=machine(t,3,2,Processing.CENTRIFUGE);var kiln=machine(t,5,2,Processing.KILN);var press=machine(t,7,2,Processing.FABRICATOR);
        crusher.setStack(0,new ItemStack(Foundry.RAW_URANIUM,4));work(t,crusher,600);
        t.assertEquals(crusher.getStack(2).getCount(),8,"Four ore produce eight crushed material");t.assertTrue(crusher.getStack(0).isEmpty(),"Crusher consumes all four ore");
        centrifuge.setStack(0,crusher.removeStack(2));work(t,centrifuge,1600);
        t.assertEquals(centrifuge.getStack(2).getCount(),4,"Separation yields four concentrate");t.assertEquals(centrifuge.getStack(3).getCount(),4,"Tailings are not discarded");
        kiln.setStack(0,centrifuge.removeStack(2));work(t,kiln,1000);t.assertEquals(kiln.getStack(2).getCount(),4,"Kiln sinters four pellets");
        press.setStack(0,kiln.removeStack(2));press.setStack(1,new ItemStack(Processing.CLADDING));work(t,press,266);
        t.assertTrue(press.getStack(2).isEmpty(),"The final fraction of required energy cannot be skipped");work(t,press,1);
        t.assertTrue(press.getStack(2).isOf(Foundry.FUEL_ROD)&&press.getStack(2).getCount()==1,"Exactly one fuel rod");t.assertTrue(press.getStack(0).isEmpty()&&press.getStack(1).isEmpty(),"Pellets and cladding consumed once");work(t,press,2000);t.assertEquals(press.getStack(2).getCount(),1,"Empty machine cannot duplicate products");t.complete();
    }
    @GameTest(templateName="electron:components_test")
    public void outputInterlockAndReloadKeepPaidProgress(TestContext t){
        var e=machine(t,2,3,Processing.CENTRIFUGE);e.setStack(0,new ItemStack(Processing.CRUSHED_URANIUM,2));work(t,e,55);double paid=e.processingEnergy;
        e.setStack(3,new ItemStack(Processing.TAILINGS,64));work(t,e,600);
        t.assertEquals(e.processingEnergy,paid,"Full byproduct slot prevents energy use");t.assertEquals(e.getStack(0).getCount(),2,"Output interlock consumes no ingredients");
        var copy=new ProcessingEntity(e.getPos(),e.getCachedState());copy.read(e.createNbt(t.getWorld().getRegistryManager()),t.getWorld().getRegistryManager());
        t.assertEquals(copy.processingEnergy,paid,"Partial energy survives NBT reload");t.assertEquals(copy.getStack(3).getCount(),64,"Full output survives reload");
        e.removeStack(3);work(t,e,345);t.assertEquals(e.getStack(2).getCount(),1,"Output space allows exactly the unpaid remaining work");
        e.setStack(0,new ItemStack(Processing.CRUSHED_URANIUM,2));work(t,e,10);e.removeStack(0);work(t,e,1);t.assertEquals(e.processingEnergy,0d,"Removing batch input resets progress");t.complete();
    }
    @GameTest(templateName="electron:components_test")
    public void invalidSupplyCannotCreateProductAndBatchSwitchResetsWork(TestContext t){
        var e=machine(t,3,3,Processing.FABRICATOR);e.setStack(0,new ItemStack(Items.IRON_INGOT,2));work(t,e,30);t.assertTrue(e.processingEnergy>0,"First batch has paid work");
        e.setStack(0,new ItemStack(Items.COPPER_INGOT,2));e.voltage=0;e.current=0;Processing.update(t.getWorld(),e.getPos(),e,false);t.assertEquals(e.processingEnergy,0d,"Incompatible recipe cannot inherit another product's work");
        for(double voltage:new double[]{0,17,31,-24}){e.voltage=voltage;e.current=.5;for(int n=0;n<100;n++)Processing.update(t.getWorld(),e.getPos(),e,false);}
        t.assertTrue(e.getStack(2).isEmpty()&&e.processingEnergy==0,"Zero, low, high and reverse supply never performs work");
        e.voltage=24;e.current=.5;Processing.update(t.getWorld(),e.getPos(),e,true);t.assertEquals(e.processingEnergy,0d,"Broken machines cannot process");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=60)
    public void vanillaHoppersFeedAndExtractOnlyPermittedSlots(TestContext t){
        var e=machine(t,3,3,Processing.CRUSHER);var w=t.getWorld();var p=e.getPos();w.setBlockState(p.up(),Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING,Direction.DOWN));
        ((HopperBlockEntity)w.getBlockEntity(p.up())).setStack(0,new ItemStack(Foundry.RAW_URANIUM,3));
        w.setBlockState(p.down(),Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING,Direction.DOWN));
        e.setStack(2,new ItemStack(Processing.CRUSHED_URANIUM,2));
        t.runAtTick(32,()->{t.assertEquals(e.getStack(0).getCount(),3,"Vanilla hopper feeds real machine inventory");t.assertTrue(e.getStack(2).isEmpty(),"Bottom hopper extracts products");var bottom=(HopperBlockEntity)w.getBlockEntity(p.down());t.assertTrue(bottom.getStack(0).isOf(Processing.CRUSHED_URANIUM),"Bottom hopper receives correct item");t.assertFalse(e.canExtract(0,e.getStack(0),Direction.DOWN),"Unprocessed input cannot leak downward");t.complete();});
    }
    @GameTest(templateName="electron:components_test")
    public void breakingMachineDropsStoredItemsExactlyOnce(TestContext t){
        var e=machine(t,3,3,Processing.KILN);e.setStack(0,new ItemStack(Processing.CONCENTRATE,3));e.setStack(2,new ItemStack(Foundry.URANIUM_PELLET,2));var p=e.getPos();
        t.getWorld().setBlockState(p,Blocks.AIR.getDefaultState());var drops=t.getWorld().getEntitiesByClass(ItemEntity.class,new Box(p).expand(1),i->true);
        t.assertEquals(drops.stream().filter(d->d.getStack().isOf(Processing.CONCENTRATE)).mapToInt(d->d.getStack().getCount()).sum(),3,"All inputs dropped once");t.assertEquals(drops.stream().filter(d->d.getStack().isOf(Foundry.URANIUM_PELLET)).mapToInt(d->d.getStack().getCount()).sum(),2,"All products dropped once");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=60)
    public void distinctLuminairesDrawRatedPowerAndStopWhenSupplyFails(TestContext t){
        var world=t.getWorld();var s=CircuitState.get(world);for(int x=0;x<10;x++)for(int z=0;z<10;z++){var p=t.getAbsolutePos(new BlockPos(x,2,z));world.setBlockState(p,Blocks.SMOOTH_STONE.getDefaultState());var bridge=new Bridge(UUID.randomUUID(),p,Direction.UP,"minecraft:smooth_stone");s.bridges.put(bridge.id(),bridge);}
        var source=t.getAbsolutePos(new BlockPos(1,2,4));world.setBlockState(source,Foundry.GENERATOR.getDefaultState().with(ComponentBlock.FACING,Direction.UP));var supply=(ComponentEntity)world.getBlockEntity(source);supply.energy=100;
        var a=t.getAbsolutePos(new BlockPos(7,2,2));var b=t.getAbsolutePos(new BlockPos(7,2,7));world.setBlockState(a,Lighting.WORKSHOP.getDefaultState().with(ComponentBlock.FACING,Direction.UP));world.setBlockState(b,Lighting.YARD.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
        for(var p:List.of(a,b)){connect(t,source,0,p,0);connect(t,source,1,p,1);}
        t.runAtTick(25,()->{var first=(ComponentEntity)world.getBlockEntity(a);var second=(ComponentEntity)world.getBlockEntity(b);t.assertTrue(second.current>first.current*2.3,"Larger lamp pays for its larger load");for(var p:List.of(a,b)){t.assertTrue(world.getBlockState(p).getLuminance()>=14,"Powered fixture emits actual vanilla light");t.assertTrue(world.getLightLevel(net.minecraft.world.LightType.BLOCK,p.up())>=12,"Light propagates beyond the model");}t.assertTrue(supply.energy<95,"Lighting reduces stored source energy");supply.energy=0;});
        t.runAtTick(43,()->{for(var p:List.of(a,b))t.assertEquals(world.getBlockState(p).getLuminance(),0,"Unpowered fixture stops emitting light");t.complete();});
    }
    @GameTest(templateName="electron:components_test")
    public void burntWorkshopEquipmentCannotBeReplacedForFree(TestContext t){
        var world=t.getWorld();var p=t.getAbsolutePos(new BlockPos(3,2,3));
        for(var block:List.of(Processing.CRUSHER,Processing.CENTRIFUGE,Processing.KILN,Processing.FABRICATOR,Lighting.WORKSHOP,Lighting.YARD)){
            world.setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
            var intact=Block.getDroppedStacks(world.getBlockState(p),world,p,world.getBlockEntity(p));t.assertTrue(intact.size()==1&&intact.getFirst().isOf(block.asItem()),"Intact equipment remains recoverable");
            var burnt=Block.getDroppedStacks(world.getBlockState(p).with(ComponentBlock.BROKEN,true),world,p,world.getBlockEntity(p));t.assertTrue(burnt.size()==1&&burnt.getFirst().isOf(Foundry.SCRAP),"Destroyed equipment returns scrap, not a free replacement");
        }
        t.complete();
    }
}
