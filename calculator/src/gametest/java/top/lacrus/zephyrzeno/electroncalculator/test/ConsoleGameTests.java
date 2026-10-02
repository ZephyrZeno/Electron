package top.lacrus.zephyrzeno.electroncalculator.test;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.*;

public final class ConsoleGameTests implements FabricGameTest {
    @GameTest(templateName="electron_calculator:components_test",tickLimit=250,batchId="console")
    public void buttonRequestMemoryAndBusyLock(TestContext ctx) {
        KeypadCircuit k=KeypadCircuit.create();ConsoleLayout layout=ConsoleLayout.create(k);CompactBlueprint fixture=new CompactBlueprint();
        layout.fixed.blocks.forEach((p,t)->{if(p.x()>=116&&p.x()<=125&&p.y()<=4&&p.z()>=25&&p.z()<=33)fixture.blocks.put(p.add(-116,0,-25),t);});
        BlockPos origin=ctx.getAbsolutePos(new BlockPos(0,2,2));var world=ctx.getWorld();CompactGameTests.place(world,origin,fixture);
        BlockPos button=origin.add(4,2,2),source=origin.add(4,2,7),reset=origin.add(6,1,4),lock=origin.add(1,1,3);
        world.setBlockState(reset,Blocks.LEVER.getDefaultState().with(LeverBlock.FACE,net.minecraft.block.enums.BlockFace.FLOOR),Block.NOTIFY_ALL);
        world.setBlockState(lock,Blocks.LEVER.getDefaultState().with(LeverBlock.FACE,net.minecraft.block.enums.BlockFace.FLOOR),Block.NOTIFY_ALL);
        ctx.runAtTick(20,()->press(world,button));
        ctx.runAtTick(60,()->{
            ctx.assertTrue(world.getBlockState(source).get(RedstoneWireBlock.POWER)>0,"Button pulse remains stored in copper bulb");
            setLever(world,reset,true);
        });
        ctx.runAtTick(80,()->{
            ctx.assertEquals(world.getBlockState(source).get(RedstoneWireBlock.POWER),0,"Reset pulse clears request");
            setLever(world,reset,false);setLever(world,lock,true);
        });
        ctx.runAtTick(100,()->press(world,button));
        ctx.runAtTick(140,()->{ctx.assertEquals(world.getBlockState(source).get(RedstoneWireBlock.POWER),0,"Busy lock rejects a key press");setLever(world,lock,false);});
        ctx.runAtTick(160,()->press(world,button));
        ctx.runAtTick(200,()->{ctx.assertTrue(world.getBlockState(source).get(RedstoneWireBlock.POWER)>0,"Key accepted after unlock");ctx.complete();});
    }
    public static void press(net.minecraft.server.world.ServerWorld world,BlockPos button) {
        BlockState state=world.getBlockState(button);((ButtonBlock)state.getBlock()).powerOn(state,world,button,null);
    }
    private static void setLever(net.minecraft.server.world.ServerWorld world,BlockPos p,boolean on){world.setBlockState(p,world.getBlockState(p).with(LeverBlock.POWERED,on),Block.NOTIFY_ALL);}
}
