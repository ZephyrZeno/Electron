package top.lacrus.zephyrzeno.electron.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import top.lacrus.zephyrzeno.electron.logic.LogicFunction;

/** Front = output; binary inputs left/right, unary input rear. One redstone tick delay. */
public class LogicGateBlock extends Block {
    private static final VoxelShape HOUSING = Block.createCuboidShape(0, 0, 0, 16, 4, 16);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = Properties.POWERED;
    private final LogicFunction function;
    private final MapCodec<LogicGateBlock> codec;

    public LogicGateBlock(Settings settings, LogicFunction function) {
        super(settings);
        this.function = function;
        this.codec = createCodec(s -> new LogicGateBlock(s, function));
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(POWERED, false));
    }

    @Override protected MapCodec<? extends Block> getCodec() { return codec; }
    @Override protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return HOUSING; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING, POWERED); }
    @Override public BlockState getPlacementState(ItemPlacementContext ctx) { return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing()); }
    @Override protected BlockState rotate(BlockState state, BlockRotation rotation) { return state.with(FACING, rotation.rotate(state.get(FACING))); }
    @Override protected BlockState mirror(BlockState state, BlockMirror mirror) { return rotate(state, mirror.getRotation(state.get(FACING))); }
    @Override protected boolean emitsRedstonePower(BlockState state) { return true; }

    @Override protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        // Minecraft queries the direction from the receiver towards this block.
        return state.get(POWERED) && direction == state.get(FACING).getOpposite() ? 15 : 0;
    }

    @Override protected int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return getWeakRedstonePower(state, world, pos, direction);
    }

    protected boolean input(World world, BlockPos pos, Direction side) {
        return world.getEmittedRedstonePower(pos.offset(side), side) > 0;
    }

    protected boolean evaluate(BlockState state, World world, BlockPos pos) {
        Direction front = state.get(FACING);
        boolean a = input(world, pos, function.unary() ? front.getOpposite() : front.rotateYCounterclockwise());
        boolean b = !function.unary() && input(world, pos, front.rotateYClockwise());
        return function.apply(a, b);
    }

    protected void schedule(World world, BlockPos pos) {
        if (!world.isClient && !world.getBlockTickScheduler().isQueued(pos, this)) world.scheduleBlockTick(pos, this, 2);
    }

    @Override protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.isOf(this)) schedule(world, pos);
    }

    @Override protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClient && evaluate(state, world, pos) != state.get(POWERED)) schedule(world, pos);
    }

    @Override protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        boolean power = evaluate(state, world, pos);
        if (power != state.get(POWERED)) {
            world.setBlockState(pos, state.with(POWERED, power), Block.NOTIFY_ALL);
            world.updateNeighborsAlways(pos.offset(state.get(FACING)), this);
        }
    }

    @Override protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        if (!state.isOf(newState.getBlock())) world.updateNeighborsAlways(pos.offset(state.get(FACING)), this);
    }
}
