package top.lacrus.zephyrzeno.electron.power;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;

/** A vanilla light-engine pulse. Scheduled removal survives chunk saves; no terrain or fluids displaced. */
public final class ArcFlashBlock extends Block {
    public ArcFlashBlock(Settings settings){super(settings);}
    @Override protected MapCodec<? extends Block> getCodec(){return createCodec(ArcFlashBlock::new);}
    @Override protected BlockRenderType getRenderType(BlockState state){return BlockRenderType.INVISIBLE;}
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return VoxelShapes.empty();}
    @Override protected void onBlockAdded(BlockState s,net.minecraft.world.World w,BlockPos p,BlockState old,boolean notify){if(w instanceof ServerWorld server)server.scheduleBlockTick(p,this,3);}
    @Override protected void scheduledTick(BlockState s,ServerWorld w,BlockPos p,Random random){if(w.getBlockState(p).isOf(this))w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);}
}
