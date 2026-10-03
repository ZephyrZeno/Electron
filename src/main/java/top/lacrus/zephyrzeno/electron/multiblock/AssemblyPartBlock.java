package top.lacrus.zephyrzeno.electron.multiblock;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;

public final class AssemblyPartBlock extends BlockWithEntity implements SealedAssembly {
    public AssemblyPartBlock(Settings settings){super(settings);}
    @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return createCodec(AssemblyPartBlock::new);}
    @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s){return new AssemblyPartEntity(p,s);}
    @Override public <T extends BlockEntity> net.minecraft.block.entity.BlockEntityTicker<T> getTicker(World w,BlockState s,net.minecraft.block.entity.BlockEntityType<T> type){
        return w.isClient?null:validateTicker(type,Assembly.PART_ENTITY,(world,pos,state,e)->{
            if(world.getTime()%20==0&&world.isChunkLoaded(e.origin)&&!Assembly.formed(world.getBlockState(e.origin)))world.setBlockState(pos,e.original,Block.NOTIFY_ALL);
        });
    }
    @Override protected BlockRenderType getRenderType(BlockState s){return BlockRenderType.INVISIBLE;}
    @Override protected net.minecraft.util.ActionResult onUse(BlockState s,World w,BlockPos p,net.minecraft.entity.player.PlayerEntity player,net.minecraft.util.hit.BlockHitResult hit){
        if(!w.isClient)player.sendMessage(net.minecraft.text.Text.translatable("message.electron.plant_body"),true);
        return net.minecraft.util.ActionResult.SUCCESS;
    }
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return w.getBlockEntity(p) instanceof AssemblyPartEntity e?e.shape():VoxelShapes.empty();}
    @Override public ItemStack getPickStack(WorldView w,BlockPos p,BlockState s){return w.getBlockEntity(p) instanceof AssemblyPartEntity e?new ItemStack(e.original.getBlock()):ItemStack.EMPTY;}
    @Override protected List<ItemStack> getDroppedStacks(BlockState s,LootContextParameterSet.Builder builder){
        if(builder.getOptional(LootContextParameters.BLOCK_ENTITY) instanceof AssemblyPartEntity e&&!e.original.isAir())return e.original.getDroppedStacks(builder);
        return List.of();
    }
    @Override protected void onStateReplaced(BlockState s,World w,BlockPos p,BlockState next,boolean moved){
        if(!next.isOf(this)&&w instanceof net.minecraft.server.world.ServerWorld server&&w.getBlockEntity(p) instanceof AssemblyPartEntity e)Assembly.dismantle(server,e.origin,p);
        super.onStateReplaced(s,w,p,next,moved);
    }
}
