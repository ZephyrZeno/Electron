package top.lacrus.zephyrzeno.electron.foundry;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.*;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.BlockView;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/** The P0 bench uses the normal recipe book and 3x3 crafting grid. */
public final class AssemblyTableBlock extends Block {
    public static final MapCodec<AssemblyTableBlock> CODEC=createCodec(AssemblyTableBlock::new);
    public static final DirectionProperty FACING=Properties.HORIZONTAL_FACING;
    private static final VoxelShape[] SHAPES=shapes();
    public AssemblyTableBlock(Settings settings){super(settings);setDefaultState(getStateManager().getDefaultState().with(FACING,Direction.NORTH));}
    @Override protected MapCodec<? extends Block> getCodec(){return CODEC;}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> builder){builder.add(FACING);}
    @Override public BlockState getPlacementState(ItemPlacementContext context){return getDefaultState().with(FACING,context.getHorizontalPlayerFacing().getOpposite());}
    @Override protected BlockState rotate(BlockState state,BlockRotation rotation){return state.with(FACING,rotation.rotate(state.get(FACING)));}
    @Override protected BlockState mirror(BlockState state,BlockMirror mirror){return state.rotate(mirror.getRotation(state.get(FACING)));}
    @Override protected VoxelShape getOutlineShape(BlockState state,BlockView world,BlockPos pos,ShapeContext context){return SHAPES[(state.get(FACING).getHorizontal()+2)&3];}
    private static VoxelShape[] shapes(){
        VoxelShape shape=VoxelShapes.union(createCuboidShape(0,11,0,16,13,16),createCuboidShape(4,4,2,12,11,14),createCuboidShape(4.2,5,1.2,11.8,10.5,2),createCuboidShape(1,13,14,15,16,16),createCuboidShape(12,13,5,15,15.5,10));
        for(int x:new int[]{1,13})for(int z:new int[]{1,13})shape=VoxelShapes.union(shape,createCuboidShape(x,0,z,x+2,11,z+2));
        var result=new VoxelShape[4];result[0]=shape;
        for(int turn=1;turn<4;turn++){var boxes=result[turn-1].getBoundingBoxes();var rotated=VoxelShapes.empty();for(var b:boxes)rotated=VoxelShapes.union(rotated,VoxelShapes.cuboid(1-b.maxZ,b.minY,b.minX,1-b.minZ,b.maxY,b.maxX));result[turn]=rotated;}
        return result;
    }
    @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit) {
        if(!world.isClient)player.openHandledScreen(new SimpleNamedScreenHandlerFactory((sync,inventory,p)->new CraftingScreenHandler(sync,inventory,ScreenHandlerContext.create(world,pos)) {
            @Override public boolean canUse(PlayerEntity user){return world.getBlockState(pos).isOf(AssemblyTableBlock.this)&&user.squaredDistanceTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
        },Text.translatable("block.electron.assembly_table")));
        return ActionResult.SUCCESS;
    }
}
