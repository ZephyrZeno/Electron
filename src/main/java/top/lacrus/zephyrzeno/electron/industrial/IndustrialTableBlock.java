package top.lacrus.zephyrzeno.electron.industrial;

import com.mojang.serialization.MapCodec;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import net.minecraft.loot.context.*;

/** Six paid, linked cells form one three-by-two workshop; only its origin stores/drops inventory. */
public final class IndustrialTableBlock extends BlockWithEntity {
    public static final IntProperty PART=IntProperty.of("part",0,5);
    public static final DirectionProperty FACING=net.minecraft.state.property.Properties.HORIZONTAL_FACING;
    private static final Set<String> CHANGING=new HashSet<>();
    public IndustrialTableBlock(Settings s){super(s);setDefaultState(getDefaultState().with(PART,0).with(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return createCodec(IndustrialTableBlock::new);}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState>b){b.add(PART,FACING);}
    public static BlockPos offset(Direction facing,int part){var right=facing.rotateYClockwise();return BlockPos.ORIGIN.offset(right,part%3).offset(facing.getOpposite(),part/3);}
    public static BlockPos origin(BlockPos p,BlockState s){return p.subtract(offset(s.get(FACING),s.get(PART)));}
    @Override public BlockState getPlacementState(ItemPlacementContext c){
        var s=getDefaultState().with(FACING,c.getHorizontalPlayerFacing().getOpposite());
        for(int i=0;i<6;i++){var q=c.getBlockPos().add(offset(s.get(FACING),i));
            if(!c.getWorld().isChunkLoaded(q)||!c.getWorld().getBlockState(q).isReplaceable()||!c.getWorld().getWorldBorder().contains(q)||!Block.isShapeFullCube(c.getWorld().getBlockState(q.down()).getCollisionShape(c.getWorld(),q.down())))return null;
            if(c.getPlayer()!=null&&!c.getWorld().canPlayerModifyAt(c.getPlayer(),q))return null;
        }return s;
    }
    @Override public void onPlaced(World w,BlockPos p,BlockState s,LivingEntity user,ItemStack stack){super.onPlaced(w,p,s,user,stack);if(!w.isClient)for(int i=1;i<6;i++)w.setBlockState(p.add(offset(s.get(FACING),i)),s.with(PART,i),Block.NOTIFY_ALL);}
    @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s){return new IndustrialTableEntity(p,s);}
    @Override protected BlockRenderType getRenderType(BlockState s){return BlockRenderType.MODEL;}
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return VoxelShapes.union(Block.createCuboidShape(0,12,0,16,16,16),Block.createCuboidShape(2,0,2,14,12,14));}
    @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){if(!w.isClient&&w.getBlockEntity(origin(p,s)) instanceof IndustrialTableEntity e)player.openHandledScreen(e);return ActionResult.SUCCESS;}
    @Override protected List<ItemStack> getDroppedStacks(BlockState s,LootContextParameterSet.Builder b){return s.get(PART)==0?super.getDroppedStacks(s,b):List.of();}
    @Override protected void onStateReplaced(BlockState s,World w,BlockPos p,BlockState next,boolean moved){
        if(!next.isOf(this)&&!w.isClient){var base=origin(p,s);String key=w.getRegistryKey().getValue()+":"+base.asLong();
            if(s.get(PART)==0&&w.getBlockEntity(p) instanceof IndustrialTableEntity e)ItemScatterer.spawn(w,p,e);
            if(CHANGING.add(key))try{
                if(s.get(PART)!=0&&w.getBlockState(base).isOf(this))w.breakBlock(base,true);
                for(int i=1;i<6;i++){var q=base.add(offset(s.get(FACING),i));if(!q.equals(p)&&w.getBlockState(q).isOf(this))w.setBlockState(q,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);}
            }finally{CHANGING.remove(key);}
        }super.onStateReplaced(s,w,p,next,moved);
    }
}
