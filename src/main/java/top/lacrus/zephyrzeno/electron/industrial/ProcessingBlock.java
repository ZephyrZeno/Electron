package top.lacrus.zephyrzeno.electron.industrial;

import net.minecraft.block.*;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import top.lacrus.zephyrzeno.electron.foundry.*;

public class ProcessingBlock extends ComponentBlock {
    public final String machine;public final double watts;
    public ProcessingBlock(String name,double watts){super(AbstractBlock.Settings.create().strength(4).sounds(BlockSoundGroup.METAL).nonOpaque().dynamicBounds().pistonBehavior(PistonBehavior.BLOCK),Kind.MOTOR,576/watts);this.machine=name;this.watts=watts;}
    @Override public boolean rearPowerPorts(){return top.lacrus.zephyrzeno.electron.pcb.PcbFabrication.STAGES.stream().anyMatch(s->s.machine()==this);}
    @Override protected net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState s,net.minecraft.world.BlockView w,BlockPos p,ShapeContext context){return rearPowerPorts()?top.lacrus.zephyrzeno.electron.pcb.WorkshopGeometry.shape(machine,s.get(FACING),s.get(TURN)):super.getOutlineShape(s,w,p,context);}
    @Override public BlockState getPlacementState(ItemPlacementContext c){return c.getSide()==Direction.UP?super.getPlacementState(c):null;}
    @Override public net.minecraft.block.entity.BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new ProcessingEntity(pos,state);}
    @Override protected ActionResult onUse(BlockState state,World world,BlockPos pos,PlayerEntity player,BlockHitResult hit){
        if(!world.isClient&&world.getBlockEntity(pos) instanceof ComponentEntity e)player.openHandledScreen(new ProcessingInventory(e));return ActionResult.SUCCESS;
    }
}
