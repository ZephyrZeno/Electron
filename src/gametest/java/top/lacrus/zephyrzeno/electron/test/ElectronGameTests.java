package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.block.*;
import top.lacrus.zephyrzeno.electron.logic.LogicFunction;

public final class ElectronGameTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void gatesDriveVanillaDustAndLamps(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 4));
        world.setBlockState(pos, Electron.GATES.get(LogicFunction.AND).getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(pos.north().down(), Blocks.SMOOTH_STONE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(pos.north(), Blocks.REDSTONE_WIRE.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(pos.north(2), Blocks.REDSTONE_LAMP.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(pos.east(), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(pos.west(), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        context.runAtTick(10, () -> {
            context.assertTrue(world.getBlockState(pos.north(2)).get(RedstoneLampBlock.LIT), "Gate lights vanilla lamp through dust");
            world.setBlockState(pos.west(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        });
        context.runAtTick(25, () -> { context.assertFalse(world.getBlockState(pos.north(2)).get(RedstoneLampBlock.LIT), "Lamp clears after input removal"); context.complete(); });
    }

    @GameTest(templateName = "electron:components_test", tickLimit = 120)
    public void directionalGates(TestContext context) {
        ServerWorld world = context.getWorld();
        int index = 0;
        for (LogicFunction fn : LogicFunction.values()) {
            BlockPos pos = context.getAbsolutePos(new BlockPos(1 + index % 2 * 4, 2, 1 + index / 2 * 4));
            world.setBlockState(pos, Electron.GATES.get(fn).getDefaultState().with(LogicGateBlock.FACING, Direction.NORTH), Block.NOTIFY_ALL);
            for (int combination = 0; combination < 4; combination++) {
                final int mask = combination;
                context.runAtTick(2 + combination * 20L, () -> {
                    world.setBlockState(pos.offset(fn.unary() ? Direction.SOUTH : Direction.WEST), (mask & 1) != 0 ? Blocks.REDSTONE_BLOCK.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    if (!fn.unary()) world.setBlockState(pos.east(), (mask & 2) != 0 ? Blocks.REDSTONE_BLOCK.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                });
                context.runAtTick(12 + combination * 20L, () -> {
                    boolean expected = fn.apply((mask & 1) != 0, (mask & 2) != 0);
                    context.assertEquals(world.getBlockState(pos).get(LogicGateBlock.POWERED), expected, fn + " truth table " + mask);
                    context.assertEquals(world.getEmittedRedstonePower(pos, Direction.SOUTH), expected ? 15 : 0, "Forward output");
                    context.assertEquals(world.getEmittedRedstonePower(pos, Direction.NORTH), 0, "No rear output");
                });
            }
            index++;
        }
        context.runAtTick(85, context::complete);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 80)
    public void memoryCapturesOnlyRisingEdgesAndReset(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos pos = context.getAbsolutePos(new BlockPos(3, 2, 3));
        world.setBlockState(pos, Electron.MEMORY.getDefaultState(), Block.NOTIFY_ALL);
        context.runAtTick(3, () -> world.setBlockState(pos.south(), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL));
        context.runAtTick(6, () -> context.assertFalse(world.getBlockState(pos).get(LogicGateBlock.POWERED), "Data alone cannot write"));
        context.runAtTick(9, () -> world.setBlockState(pos.west(), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL));
        context.runAtTick(12, () -> {
            context.assertTrue(world.getBlockState(pos).get(LogicGateBlock.POWERED), "Rising edge captures one");
            world.setBlockState(pos.south(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        });
        context.runAtTick(15, () -> context.assertTrue(world.getBlockState(pos).get(LogicGateBlock.POWERED), "High clock holds value"));
        context.runAtTick(18, () -> world.setBlockState(pos.west(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL));
        context.runAtTick(21, () -> context.assertTrue(world.getBlockState(pos).get(LogicGateBlock.POWERED), "Falling edge holds"));
        context.runAtTick(24, () -> world.setBlockState(pos.west(), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL));
        context.runAtTick(27, () -> context.assertFalse(world.getBlockState(pos).get(LogicGateBlock.POWERED), "Next rising edge captures zero"));
        context.runAtTick(30, () -> {
            world.setBlockState(pos.west(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(pos.south(), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
            world.setBlockState(pos.west(), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        });
        context.runAtTick(33, () -> world.setBlockState(pos.east(), Blocks.REDSTONE_BLOCK.getDefaultState(), Block.NOTIFY_ALL));
        context.runAtTick(36, () -> { context.assertFalse(world.getBlockState(pos).get(LogicGateBlock.POWERED), "Reset wins"); context.complete(); });
    }

}
