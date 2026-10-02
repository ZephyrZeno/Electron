package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class PowerModuleTests implements FabricGameTest {
    private static void floor(TestContext t){var s=CircuitState.get(t.getWorld());for(int x=0;x<10;x++)for(int z=0;z<18;z++){var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p,Blocks.STONE.getDefaultState());var b=new Bridge(UUID.randomUUID(),p,Direction.UP,"minecraft:stone");s.bridges.put(b.id(),b);}}
    private static BlockPos device(TestContext t,int x,int z,ComponentBlock block){var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));return p;}
    private static ComponentEntity entity(TestContext t,BlockPos p){return (ComponentEntity)t.getWorld().getBlockEntity(p);}
    private static Wire wire(TestContext t,BlockPos a,int ap,BlockPos b,int bp){var s=CircuitState.get(t.getWorld());var ends=s.endpoints();var start=ends.get(node(a,ap));var end=ends.get(node(b,bp));var path=s.connectionRoute(t.getWorld(),start,end,0);s.validateCapacity(path);var w=new Wire(UUID.randomUUID(),start.id(),end.id(),s.wires.size()%8,path,s.lengthUnits(path,start,end));s.wires.put(w.id(),w);s.changed();return w;}
    private static void near(TestContext t,double actual,double expected,double tolerance,String message){t.assertTrue(Math.abs(actual-expected)<tolerance,message+": "+actual+" expected "+expected);}

    @GameTest(templateName="electron:components_test",tickLimit=65)
    public void batteryChargesFromWiresAndPowersLedAfterUnplug(TestContext t){
        floor(t);var source=device(t,1,2,Foundry.CRANK);var battery=device(t,4,2,Foundry.BATTERY);var r=device(t,4,6,Foundry.RESISTOR_220);var led=device(t,7,6,Foundry.LED);entity(t,source).energy=100;
        var feed=wire(t,source,0,battery,0);wire(t,source,1,battery,1);wire(t,battery,2,r,0);wire(t,r,1,led,0);wire(t,led,1,battery,3);
        double[] before={0};
        t.runAtTick(15,()->{before[0]=entity(t,battery).energy;t.assertTrue(before[0]>.2,"Battery accepts actual charge power");t.assertTrue(entity(t,source).energy<99.8,"Crank loses supplied energy");t.assertTrue(entity(t,led).current>.01,"Battery also supplies its separate output");var s=CircuitState.get(t.getWorld());s.wires.remove(feed.id());s.changed();});
        t.runAtTick(45,()->{t.assertTrue(entity(t,battery).energy>0&&entity(t,battery).energy<before[0],"Stored energy is used after charger is unplugged");t.assertTrue(entity(t,led).current>.01,"LED remains powered from battery");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=25)
    public void batteryLootAndActualPlacementPreserveCharge(TestContext t){
        floor(t);var p=device(t,2,2,Foundry.BATTERY);var original=entity(t,p);original.energy=37.125;
        var registry=t.getWorld().getRegistryManager();var loaded=new ComponentEntity(p,original.getCachedState());loaded.read(original.createNbt(registry),registry);near(t,loaded.energy,37.125,1e-12,"World save retains charge");
        var loot=Block.getDroppedStacks(original.getCachedState(),t.getWorld(),p,original);t.assertEquals(loot.size(),1,"Battery drops one item");var stack=loot.getFirst();near(t,stack.getOrDefault(Foundry.BATTERY_ENERGY,0d),37.125,1e-12,"Loot copies battery charge component");t.assertEquals(stack.getMaxCount(),1,"Partially charged modules do not stack");
        var player=t.createMockCreativeServerPlayerInWorld();player.setStackInHand(Hand.MAIN_HAND,stack);var support=t.getAbsolutePos(new BlockPos(6,2,6));var target=support.up();
        var hit=new BlockHitResult(Vec3d.ofCenter(support).add(0,.5,0),Direction.UP,support,false);
        ((BlockItem)Foundry.BATTERY.asItem()).place(new ItemPlacementContext(player,Hand.MAIN_HAND,stack,hit));
        t.assertTrue(t.getWorld().getBlockState(target).isOf(Foundry.BATTERY),"Dropped battery places as a real block");near(t,entity(t,target).energy,37.125,1e-12,"Placement restores stored energy without a refill");
        var burnt=Block.getDroppedStacks(original.getCachedState().with(ComponentBlock.BROKEN,true),t.getWorld(),p,original);t.assertTrue(burnt.size()==1&&burnt.getFirst().isOf(Foundry.SCRAP),"Burnt battery only drops scrap");
        var fresh=device(t,8,2,Foundry.BATTERY);near(t,entity(t,fresh).energy,0,1e-12,"Fresh battery starts empty");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=70)
    public void batteryShortLimitsCurrentThenStopsWhenEmpty(TestContext t){
        floor(t);var battery=device(t,4,4,Foundry.BATTERY);entity(t,battery).energy=1;wire(t,battery,2,battery,3);
        t.runAtTick(6,()->{near(t,-entity(t,battery).current,.1,1e-7,"Short is limited to 100mA");t.assertTrue(entity(t,battery).voltage<.1,"Output voltage collapses under short");});
        t.runAtTick(60,()->{near(t,entity(t,battery).energy,0,1e-7,"Short cannot spend absent energy");near(t,entity(t,battery).current,0,1e-7,"Empty battery stops sourcing current");t.assertFalse(t.getWorld().getBlockState(battery).get(ComponentBlock.BROKEN),"Normal output limiting prevents a short from burning this module");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void terminalStripHasTwoIndependentConductingBuses(TestContext t){
        floor(t);var strip=device(t,4,3,Foundry.TERMINAL_STRIP);var s=CircuitState.get(t.getWorld());s.simulate(t.getWorld());
        t.assertTrue(s.readings.commonReference(node(strip,0),node(strip,3)),"A1 to A4 form one bus");t.assertTrue(s.readings.commonReference(node(strip,4),node(strip,7)),"B1 to B4 form another bus");t.assertFalse(s.readings.commonReference(node(strip,0),node(strip,4)),"A and B are isolated");
        var source=device(t,1,3,Foundry.CRANK);var load=device(t,7,3,Foundry.RESISTOR_220);entity(t,source).energy=100;wire(t,source,0,strip,0);wire(t,source,1,strip,4);wire(t,strip,3,load,0);wire(t,strip,7,load,1);
        t.runAtTick(10,()->{near(t,entity(t,load).current,5d/225,.0001,"Separate power and return buses deliver current to load");var restored=CircuitState.read(s.snapshot());t.assertEquals(restored.endpoints().get(node(strip,7)),s.endpoints().get(node(strip,7)),"Bus pin identity and orientation persist");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=120)
    public void electricityOnlyActuatesExistingRedstoneAndPausesOpen(TestContext t){
        floor(t);var source=device(t,1,4,Foundry.CRANK);var contact=device(t,5,4,Foundry.REDSTONE_SWITCH);var side=ComponentBlock.redstoneSide(t.getWorld().getBlockState(contact));var input=contact.offset(side.getOpposite());var lamp=contact.offset(side);t.getWorld().setBlockState(lamp,Blocks.REDSTONE_LAMP.getDefaultState());wire(t,source,0,contact,0);wire(t,source,1,contact,1);
        t.getWorld().setBlockState(input,Blocks.REDSTONE_BLOCK.getDefaultState());double[] coil={0};
        t.runAtTick(8,()->{t.assertFalse(entity(t,contact).contactClosed,"Redstone alone cannot energize electric coil");near(t,entity(t,contact).current,0,1e-8,"Redstone injects no electrical current");t.assertEquals(t.getWorld().getBlockState(contact).get(ComponentBlock.LEVEL),0,"Open contact blocks existing redstone");t.getWorld().setBlockState(input,Blocks.STONE.getDefaultState());entity(t,source).energy=100;});
        t.runAtTick(18,()->{t.assertTrue(entity(t,contact).contactClosed,"Electric coil closes contact");t.assertEquals(t.getWorld().getBlockState(contact).get(ComponentBlock.LEVEL),0,"Electricity without redstone input creates no redstone output");coil[0]=entity(t,contact).current;t.getWorld().setBlockState(input,Blocks.REDSTONE_BLOCK.getDefaultState());});
        t.runAtTick(28,()->{t.assertEquals(t.getWorld().getBlockState(contact).get(ComponentBlock.LEVEL),15,"Closed contact passes existing input strength");t.assertTrue(t.getWorld().getBlockState(lamp).get(RedstoneLampBlock.LIT),"Original redstone lamp responds to passed signal");near(t,entity(t,contact).current,coil[0],1e-9,"Redstone strength does not change coil current");
            // Sample a wet port without letting unrelated fluid flow flood the whole fixture.
            t.getWorld().setBlockState(contact.up(),Blocks.WATER.getDefaultState());CircuitState.get(t.getWorld()).simulate(t.getWorld());
            t.assertTrue(entity(t,contact).paused,"Wet control port pauses the electric network");t.assertFalse(entity(t,contact).contactClosed,"Paused control fails open");t.assertEquals(t.getWorld().getBlockState(contact).get(ComponentBlock.LEVEL),0,"Paused control blocks redstone");t.getWorld().setBlockState(contact.up(),Blocks.AIR.getDefaultState());});
        t.runAtTick(52,()->{t.assertTrue(entity(t,contact).contactClosed,"Control recovers after pause");t.assertEquals(t.getWorld().getBlockState(contact).get(ComponentBlock.LEVEL),15,"Independent original input is passed again");entity(t,source).energy=0;});
        t.runAtTick(65,()->{t.assertFalse(entity(t,contact).contactClosed,"Coil releases when supply is removed");t.assertFalse(t.getWorld().getBlockState(lamp).get(RedstoneLampBlock.LIT),"Lamp loses passed redstone after release");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=25)
    public void redstoneSwitchOutputFollowsAllMountingOrientations(TestContext t){
        var p=t.getAbsolutePos(new BlockPos(4,4,4));
        for(var face:Direction.values())for(int turn=0;turn<4;turn++){
            var state=Foundry.REDSTONE_SWITCH.getDefaultState().with(ComponentBlock.FACING,face).with(ComponentBlock.TURN,turn).with(ComponentBlock.ENABLED,true).with(ComponentBlock.LEVEL,11);t.getWorld().setBlockState(p,state);entity(t,p).contactClosed=true;
            var expected=face.getAxis()==Direction.Axis.Y?new Direction[]{Direction.SOUTH,Direction.WEST,Direction.NORTH,Direction.EAST}[turn]:face.getAxis()==Direction.Axis.Z?new Direction[]{Direction.UP,Direction.WEST,Direction.DOWN,Direction.EAST}[turn]:new Direction[]{Direction.UP,Direction.NORTH,Direction.DOWN,Direction.SOUTH}[turn];
            for(var receiver:Direction.values())t.assertEquals(state.getWeakRedstonePower(t.getWorld(),p,receiver.getOpposite()),receiver==expected?11:0,"Only the arrow side emits, for "+face+" turn "+turn);
        }
        t.complete();
    }
}
