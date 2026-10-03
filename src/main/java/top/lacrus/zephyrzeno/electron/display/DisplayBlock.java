package top.lacrus.zephyrzeno.electron.display;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import top.lacrus.zephyrzeno.electron.foundry.*;

public final class DisplayBlock extends ComponentBlock {
    public enum Role { DIAL,SMALL,PANEL }
    public final Role role;
    public DisplayBlock(Role role){super(AbstractBlock.Settings.create().strength(2).sounds(BlockSoundGroup.METAL).nonOpaque().dynamicBounds().pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK),Kind.PCB,1000);this.role=role;}
    @Override public BlockEntity createBlockEntity(BlockPos p,BlockState s){return new DisplayEntity(p,s);}
    @Override protected net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){if(role!=Role.PANEL)return super.getOutlineShape(s,w,p,c);var a=ComponentGeometry.local(s.get(FACING),s.get(TURN),0,0,13).multiply(16);var b=ComponentGeometry.local(s.get(FACING),s.get(TURN),16,16,16).multiply(16);return Block.createCuboidShape(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z),Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z));}
    @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){if(w.isClient)return ActionResult.SUCCESS;if(w.getBlockEntity(p) instanceof DisplayEntity e&&!s.get(BROKEN)){if(role==Role.DIAL){e.setting=Math.floorMod(e.setting+(player.isSneaking()?-1:1),10);player.sendMessage(Text.translatable("display.electron.input",e.setting),true);}else{e.toggleMode();player.sendMessage(Text.translatable(e.signMode?"display.electron.sign":"display.electron.hex"),true);}e.sync();ElectricalEffects.play((net.minecraft.server.world.ServerWorld)w,p,ElectricalEffects.SWITCH,.25f,1.2f);}return ActionResult.CONSUME;}
}
