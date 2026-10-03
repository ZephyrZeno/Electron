package top.lacrus.zephyrzeno.electron.item;

import java.util.List;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import top.lacrus.zephyrzeno.electron.pcb.*;

public final class PcbItem extends Item {
    public PcbItem(){super(new Settings().maxCount(1));}
    @Override public ActionResult useOnBlock(ItemUsageContext c){
        if(!(c.getWorld().getBlockEntity(c.getBlockPos()) instanceof PcbSocketEntity e))return ActionResult.PASS;
        if(!e.plantItems.get(0).isEmpty()||e.getCachedState().get(top.lacrus.zephyrzeno.electron.foundry.ComponentBlock.BROKEN))return ActionResult.FAIL;
        if(!c.getWorld().isClient){e.plantItems.set(0,c.getStack().copyWithCount(1));if(c.getPlayer()==null||!c.getPlayer().isCreative())c.getStack().decrement(1);e.boardChanged();}
        return ActionResult.SUCCESS;
    }
    @Override public void appendTooltip(ItemStack stack,TooltipContext context,List<Text> lines,TooltipType type){var l=Pcb.layout(stack);lines.add(Text.translatable("pcb.electron.board_rating",l.parts().size(),l.tracks().size()).formatted(Formatting.AQUA));lines.add(Text.translatable("pcb.electron.remove").formatted(Formatting.GRAY));}
}
