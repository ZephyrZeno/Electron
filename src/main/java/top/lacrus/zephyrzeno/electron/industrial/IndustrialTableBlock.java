package top.lacrus.zephyrzeno.electron.industrial;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

/** One paid module per placement. Six adjacent modules form one workshop without creating blocks. */
public final class IndustrialTableBlock extends BlockWithEntity {
    public static final IntProperty PART=IntProperty.of("part",0,5);
    public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
    public static final BooleanProperty FORMED=BooleanProperty.of("formed");
    private static boolean changing;
    public IndustrialTableBlock(Settings s){super(s);setDefaultState(getDefaultState().with(PART,0).with(FACING,Direction.NORTH).with(FORMED,false));}
    @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return createCodec(IndustrialTableBlock::new);}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState>b){b.add(PART,FACING,FORMED);}
    public static BlockPos offset(Direction f,int part){return BlockPos.ORIGIN.offset(f.rotateYClockwise(),part%3).offset(f.getOpposite(),part/3);}
    public static BlockPos origin(BlockPos p,BlockState s){return s.get(FORMED)?p.subtract(offset(s.get(FACING),s.get(PART))):p;}
    private static boolean supported(WorldView w,BlockPos p){return w.isChunkLoaded(p)&&Block.isShapeFullCube(w.getBlockState(p.down()).getCollisionShape(w,p.down()));}
    @Override public BlockState getPlacementState(ItemPlacementContext c){
        if(!supported(c.getWorld(),c.getBlockPos()))return null;
        var facing=c.getHorizontalPlayerFacing().getOpposite();
        for(var d:Direction.Type.HORIZONTAL){var s=c.getWorld().getBlockState(c.getBlockPos().offset(d));if(s.isOf(this)&&!s.get(FORMED)){facing=s.get(FACING);break;}}
        return getDefaultState().with(FACING,facing);
    }
    @Override protected void onBlockAdded(BlockState s,World w,BlockPos p,BlockState old,boolean notify){super.onBlockAdded(s,w,p,old,notify);if(!w.isClient&&!old.isOf(this))w.scheduleBlockTick(p,this,2);}
    public static boolean complete(World w,BlockPos base,BlockState state){
        for(int i=0;i<6;i++){var p=base.add(offset(state.get(FACING),i));if(!w.isChunkLoaded(p))return false;var s=w.getBlockState(p);
            if(!s.isOf(Industrial.TABLE)||!s.get(FORMED)||s.get(PART)!=i||s.get(FACING)!=state.get(FACING)||!supported(w,p))return false;
        }return true;
    }
    public static boolean form(World w,BlockPos p){
        var state=w.getBlockState(p);if(!state.isOf(Industrial.TABLE))return false;
        if(state.get(FORMED))return complete(w,origin(p,state),state);
        for(int anchor=0;anchor<6;anchor++){
            var base=p.subtract(offset(state.get(FACING),anchor));boolean valid=true;
            for(int i=0;i<6;i++){
                var q=base.add(offset(state.get(FACING),i));if(!w.isChunkLoaded(q)){valid=false;break;}var s=w.getBlockState(q);
                if(!s.isOf(Industrial.TABLE)||s.get(FORMED)||s.get(FACING)!=state.get(FACING)||!supported(w,q)){valid=false;break;}
                if(i!=0&&w.getBlockEntity(q) instanceof IndustrialTableEntity e&&!e.isEmpty()){valid=false;break;}
            }
            if(valid){for(int i=0;i<6;i++){var q=base.add(offset(state.get(FACING),i));w.setBlockState(q,state.with(PART,i).with(FORMED,true),Block.NOTIFY_ALL);}return true;}
        }return false;
    }
    private static void dismantle(World w,BlockPos base,BlockState state){
        if(changing)return;changing=true;
        try{
            if(w.getBlockEntity(base) instanceof IndustrialTableEntity inv){ItemScatterer.spawn(w,base,inv);inv.clear();}
            for(int i=0;i<6;i++){var q=base.add(offset(state.get(FACING),i));if(!w.isChunkLoaded(q))continue;var s=w.getBlockState(q);
                if(s.isOf(Industrial.TABLE)&&s.get(FORMED)&&origin(q,s).equals(base))w.setBlockState(q,s.with(PART,0).with(FORMED,false),Block.NOTIFY_ALL);
            }
        }finally{changing=false;}
    }
    @Override protected void scheduledTick(BlockState s,ServerWorld w,BlockPos p,net.minecraft.util.math.random.Random r){
        if(s.get(FORMED)){
            var base=origin(p,s);boolean loaded=true;
            for(int i=0;i<6;i++)if(!w.isChunkLoaded(base.add(offset(s.get(FACING),i)))){loaded=false;break;}
            // A partly unloaded workshop is paused, not dismantled with its inventory scattered.
            if(loaded&&!complete(w,base,s))dismantle(w,base,s);
        }else form(w,p);
        if(w.getBlockState(p).isOf(this))w.scheduleBlockTick(p,this,20);
    }
    @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s){return new IndustrialTableEntity(p,s);}
    @Override protected BlockRenderType getRenderType(BlockState s){return BlockRenderType.MODEL;}
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return VoxelShapes.union(Block.createCuboidShape(0,12,0,16,15,16),Block.createCuboidShape(1,0,1,15,12,15));}
    @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){
        if(!w.isClient){if(s.get(FORMED)&&complete(w,origin(p,s),s)&&w.getBlockEntity(origin(p,s)) instanceof IndustrialTableEntity e)player.openHandledScreen(e);
            else player.sendMessage(Text.translatable("message.electron.table_six"),true);}
        return ActionResult.SUCCESS;
    }
    @Override protected void onStateReplaced(BlockState s,World w,BlockPos p,BlockState next,boolean moved){
        if(!next.isOf(this)&&!w.isClient){
            if(s.get(FORMED))dismantle(w,origin(p,s),s);
            else if(w.getBlockEntity(p) instanceof IndustrialTableEntity e){ItemScatterer.spawn(w,p,e);e.clear();}
        }super.onStateReplaced(s,w,p,next,moved);
    }
}
