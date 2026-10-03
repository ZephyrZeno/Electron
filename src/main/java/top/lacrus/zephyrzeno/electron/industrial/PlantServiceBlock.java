package top.lacrus.zephyrzeno.electron.industrial;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** A visible console or external intake; both expose the controller's one saved inventory. */
public final class PlantServiceBlock extends BlockWithEntity {
    public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
    public final boolean port;
    public PlantServiceBlock(Settings settings,boolean port){super(settings);this.port=port;setDefaultState(getDefaultState().with(FACING,Direction.SOUTH));}
    @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return createCodec(s->new PlantServiceBlock(s,port));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){b.add(FACING);}
    @Override public BlockState getPlacementState(ItemPlacementContext c){return getDefaultState().with(FACING,c.getHorizontalPlayerFacing().getOpposite());}
    @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s){return new PlantServiceEntity(p,s);}
    @Override protected BlockRenderType getRenderType(BlockState s){return BlockRenderType.MODEL;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World w,BlockState s,BlockEntityType<T> type){return w.isClient?null:validateTicker(type,Industrial.SERVICE_ENTITY,PlantServiceEntity::tick);}
    @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){
        if(!w.isClient&&w.getBlockEntity(p) instanceof PlantServiceEntity e){
            if(e.core()!=null)player.openHandledScreen(e);
            else player.sendMessage(Text.translatable("message.electron.service_unlinked"),true);
        }return ActionResult.SUCCESS;
    }
}
