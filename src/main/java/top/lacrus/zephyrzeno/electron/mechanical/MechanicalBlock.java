package top.lacrus.zephyrzeno.electron.mechanical;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.item.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;
import top.lacrus.zephyrzeno.electron.foundry.Foundry;

/** Straight shafts transmit rotation along their axis; a turntable holds one plain block. */
public final class MechanicalBlock extends BlockWithEntity {
    public static final DirectionProperty FACING=Properties.FACING;
    public final boolean table;
    public MechanicalBlock(Settings settings,boolean table){super(settings);this.table=table;setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.UP));}
    @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return createCodec(s->new MechanicalBlock(s,table));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getPlacementState(ItemPlacementContext c){return getDefaultState().with(FACING,c.getSide());}
    @Override protected BlockRenderType getRenderType(BlockState s){return BlockRenderType.MODEL;}
    @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s){return new MechanicalEntity(p,s);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World w,BlockState s,BlockEntityType<T> type){return validateTicker(type,Foundry.MECHANICAL_ENTITY,MechanicalEntity::tick);}
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return switch(s.get(FACING).getAxis()){case X->Block.createCuboidShape(0,5,5,16,11,11);case Y->Block.createCuboidShape(5,0,5,11,16,11);case Z->Block.createCuboidShape(5,5,0,11,11,16);};}
    @Override protected ItemActionResult onUseWithItem(ItemStack stack,BlockState s,World w,BlockPos p,PlayerEntity user,Hand hand,BlockHitResult hit){
        if(!table||!(stack.getItem() instanceof BlockItem item))return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(!(w.getBlockEntity(p) instanceof MechanicalEntity e)||!e.mounted.isEmpty())return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // No inventories, data-bearing blocks, recursive machines or arbitrary block entity NBT.
        if(item.getBlock().getDefaultState().hasBlockEntity()||item.getBlock()==Blocks.AIR||!stack.getComponents().equals(item.getComponents()))return ItemActionResult.FAIL;
        if(!w.isClient){e.mounted=stack.copyWithCount(1);if(!user.isCreative())stack.decrement(1);e.sync();}
        return ItemActionResult.SUCCESS;
    }
    @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity user,BlockHitResult h){
        if(!table||!(w.getBlockEntity(p) instanceof MechanicalEntity e))return ActionResult.PASS;
        if(!w.isClient&&user.isSneaking()&&!e.mounted.isEmpty()){var item=e.mounted;e.mounted=ItemStack.EMPTY;e.sync();if(!user.getInventory().insertStack(item))user.dropItem(item,false);}
        return ActionResult.SUCCESS;
    }
    @Override protected void onStateReplaced(BlockState s,World w,BlockPos p,BlockState n,boolean moved){
        if(!n.isOf(this)&&!w.isClient&&w.getBlockEntity(p) instanceof MechanicalEntity e&&!e.mounted.isEmpty()){Block.dropStack(w,p,e.mounted);e.mounted=ItemStack.EMPTY;}
        super.onStateReplaced(s,w,p,n,moved);
    }
}
