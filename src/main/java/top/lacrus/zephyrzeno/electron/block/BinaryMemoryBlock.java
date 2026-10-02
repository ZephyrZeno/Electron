package top.lacrus.zephyrzeno.electron.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import top.lacrus.zephyrzeno.electron.logic.LogicFunction;

/** One-bit D flip-flop. Rear=data, left=clock (rising edge), right=asynchronous reset. */
public final class BinaryMemoryBlock extends LogicGateBlock {
    public static final BooleanProperty CLOCK_HIGH = BooleanProperty.of("clock_high");
    public static final MapCodec<BinaryMemoryBlock> CODEC = createCodec(BinaryMemoryBlock::new);

    public BinaryMemoryBlock(Settings settings) {
        super(settings, LogicFunction.BUFFER);
        setDefaultState(getDefaultState().with(CLOCK_HIGH, false));
    }

    @Override protected MapCodec<? extends Block> getCodec() { return CODEC; }
    @Override protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(CLOCK_HIGH);
    }

    @Override protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClient) sample(state, world, pos);
    }

    @Override protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) { sample(state, world, pos); }

    private void sample(BlockState state, World world, BlockPos pos) {
        Direction front = state.get(FACING);
        boolean clock = input(world, pos, front.rotateYCounterclockwise());
        boolean reset = input(world, pos, front.rotateYClockwise());
        boolean value = reset ? false : clock && !state.get(CLOCK_HIGH)
                ? input(world, pos, front.getOpposite()) : state.get(POWERED);
        BlockState next = state.with(POWERED, value).with(CLOCK_HIGH, clock);
        if (next != state) {
            world.setBlockState(pos, next, Block.NOTIFY_ALL);
            if (value != state.get(POWERED)) world.updateNeighborsAlways(pos.offset(front), this);
        }
    }
}
