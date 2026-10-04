package top.lacrus.zephyrzeno.electron.pcb;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import top.lacrus.zephyrzeno.electron.foundry.*;

public final class PcbBlock extends ComponentBlock {
    public final boolean bench;
    public enum Role { DESIGN,ASSEMBLY,SOCKET }
    public final Role role;
    public PcbBlock(Role role){super(AbstractBlock.Settings.create().strength(3).sounds(BlockSoundGroup.METAL).nonOpaque().dynamicBounds().pistonBehavior(PistonBehavior.BLOCK),role==Role.SOCKET?Kind.PCB:Kind.MOTOR,24);this.role=role;this.bench=role!=Role.SOCKET;}
    @Override protected net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState s,net.minecraft.world.BlockView w,BlockPos p,ShapeContext context){return bench?WorkshopGeometry.shape(role==Role.DESIGN?"pcb_design_station":"precision_workbench",s.get(FACING),s.get(TURN)):super.getOutlineShape(s,w,p,context);}
    @Override public BlockState getPlacementState(ItemPlacementContext c){return bench&&c.getSide()!=Direction.UP?null:super.getPlacementState(c);}
    @Override public boolean rearPowerPorts(){return bench;}
    @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s){return bench?new PcbBenchEntity(p,s):new PcbSocketEntity(p,s);}
    @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){
        if(w.isClient)return ActionResult.SUCCESS;
        if(w.getBlockEntity(p) instanceof PcbBenchEntity e)player.openHandledScreen(e);
        else if(w.getBlockEntity(p) instanceof PcbSocketEntity e)return e.interact(player,hit);
        return ActionResult.CONSUME;
    }
    @Override protected void onStateReplaced(BlockState s,World w,BlockPos p,BlockState next,boolean moved){if(!next.isOf(this)&&!w.isClient&&w.getBlockEntity(p) instanceof PcbBenchEntity e){e.releaseMaterials();net.minecraft.util.ItemScatterer.spawn(w,p,e);}super.onStateReplaced(s,w,p,next,moved);}
}
