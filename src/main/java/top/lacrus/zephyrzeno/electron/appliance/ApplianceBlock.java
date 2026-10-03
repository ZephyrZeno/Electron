package top.lacrus.zephyrzeno.electron.appliance;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import net.minecraft.world.*;
import top.lacrus.zephyrzeno.electron.foundry.*;

public final class ApplianceBlock extends ComponentBlock {
    public enum Type { WALL, STRIP, ADAPTER }
    public final Type type;
    public ApplianceBlock(Settings settings,Type type){super(settings,Kind.MAINS,.02);this.type=type;}
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
        return top.lacrus.zephyrzeno.electron.power.WiringCabinet.shape(s,false);
    }
    @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity user,BlockHitResult hit){
        if(!w.isClient&&user.isSneaking())w.setBlockState(p,s.cycle(ENABLED),Block.NOTIFY_ALL);
        else if(!w.isClient)user.sendMessage(net.minecraft.text.Text.translatable("message.electron.outlet_use"),true);
        return ActionResult.SUCCESS;
    }
}
