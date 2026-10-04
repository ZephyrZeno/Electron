package top.lacrus.zephyrzeno.electron.pcb;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.*;

/** A paid carrier tile, not a second PCB or a second electrical device. */
public final class PcbExtensionBlock extends Block {
    private static final VoxelShape SHAPE=Block.createCuboidShape(0,0,0,16,3,16);
    public PcbExtensionBlock(){super(Settings.create().strength(2).nonOpaque().sounds(net.minecraft.sound.BlockSoundGroup.METAL));}
    @Override public BlockState getPlacementState(ItemPlacementContext c){return c.getSide()==Direction.UP?getDefaultState():null;}
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){return SHAPE;}
    @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){
        if(!player.getMainHandStack().isEmpty())return ActionResult.PASS;
        for(var at:BlockPos.iterate(p.add(-3,0,-3),p.add(3,0,3)))if(w.getBlockEntity(at) instanceof PcbSocketEntity socket&&socket.supportPositions(socket.circuit().layout).contains(p))return socket.interact(player,hit);
        return ActionResult.PASS;
    }
}
