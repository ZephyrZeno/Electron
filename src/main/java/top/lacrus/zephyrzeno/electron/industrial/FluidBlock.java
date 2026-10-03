package top.lacrus.zephyrzeno.electron.industrial;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import net.minecraft.server.world.ServerWorld;
import top.lacrus.zephyrzeno.electron.foundry.ElectricalEffects;
import top.lacrus.zephyrzeno.electron.multiblock.SealedAssembly;

public final class FluidBlock extends BlockWithEntity implements SealedAssembly {
    public enum Type { IRON,GLASS,VALVE,BOILER,COOLANT }
    public final Type type;
    public static final BooleanProperty OPEN=BooleanProperty.of("open");
    public static final BooleanProperty[] LINKS={Properties.DOWN,Properties.UP,Properties.NORTH,Properties.SOUTH,Properties.WEST,Properties.EAST};
    public FluidBlock(Settings s,Type type){super(s);this.type=type;var state=getStateManager().getDefaultState().with(OPEN,true);for(var p:LINKS)state=state.with(p,false);setDefaultState(state);}
    public boolean tank(){return type==Type.BOILER||type==Type.COOLANT;}
    public int capacity(){return type==Type.BOILER?16000:type==Type.COOLANT?256000:250;}
    @Override protected MapCodec<? extends BlockWithEntity> getCodec(){return createCodec(s->new FluidBlock(s,type));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState>b){b.add(OPEN);for(var p:LINKS)b.add(p);}
    private boolean link(WorldAccess w,BlockPos p){return w.getBlockState(p).getBlock() instanceof FluidBlock||w.getBlockState(p).isOf(Industrial.PUMP);}
    @Override public BlockState getPlacementState(ItemPlacementContext c){var s=getDefaultState();for(var d:Direction.values())s=s.with(LINKS[d.getId()],link(c.getWorld(),c.getBlockPos().offset(d)));return s;}
    @Override protected BlockState getStateForNeighborUpdate(BlockState s,Direction d,BlockState n,WorldAccess w,BlockPos p,BlockPos q){return s.with(LINKS[d.getId()],link(w,q));}
    @Override protected BlockRenderType getRenderType(BlockState s){return BlockRenderType.MODEL;}
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
        if(tank())return Block.createCuboidShape(1,0,1,15,16,15);
        var shape=Block.createCuboidShape(5,5,5,11,11,11);
        for(var d:Direction.values())if(s.get(LINKS[d.getId()])){var v=Vec3d.of(d.getVector()).multiply(.5);shape=VoxelShapes.union(shape,VoxelShapes.cuboid(new Box(.5,.5,.5,.5+v.x,.5+v.y,.5+v.z).expand(.16)));}return shape;
    }
    @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s){return new FluidEntity(p,s);}
    @Override protected ItemActionResult onUseWithItem(ItemStack held,BlockState s,World w,BlockPos p,PlayerEntity player,Hand hand,BlockHitResult hit){
        if(!(w.getBlockEntity(p) instanceof FluidEntity e))return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(held.isOf(Items.WATER_BUCKET)||held.isOf(Items.BUCKET)){
            if(!tank())return ItemActionResult.FAIL;
            boolean fill=held.isOf(Items.WATER_BUCKET);if(fill?e.amount+1000>capacity():e.amount<1000)return ItemActionResult.FAIL;
            if(w instanceof ServerWorld server){e.change(fill?1000:-1000);player.setStackInHand(hand,ItemUsage.exchangeStack(held,player,new ItemStack(fill?Items.BUCKET:Items.WATER_BUCKET)));ElectricalEffects.play(server,p,ElectricalEffects.WATER_FILL,.55f,1);}
            return ItemActionResult.SUCCESS;
        }return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){
        if(w instanceof ServerWorld server){
            if(type==Type.VALVE){w.setBlockState(p,s.cycle(OPEN),Block.NOTIFY_ALL);ElectricalEffects.play(server,p,ElectricalEffects.VALVE,.4f,1);}
            else if(w.getBlockEntity(p) instanceof FluidEntity e)player.sendMessage(Text.translatable("message.electron.water_tank",e.amount,capacity()),true);
        }return ActionResult.SUCCESS;
    }
}
