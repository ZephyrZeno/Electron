package top.lacrus.zephyrzeno.electron.item;

import top.lacrus.zephyrzeno.electron.foundry.*;

import java.util.List;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** A head-slot optical tool. Vanilla head-item rendering uses the model's head transform. */
public final class MagnifierItem extends Item implements Equipment {
    public MagnifierItem(){super(new Item.Settings().maxCount(1));}
    @Override public EquipmentSlot getSlotType(){return EquipmentSlot.HEAD;}
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){return equipAndSwap(this,world,player,hand);}
    @Override public void appendTooltip(ItemStack stack,TooltipContext context,List<Text> tooltip,TooltipType type){tooltip.add(Text.translatable("tooltip.electron.magnifier"));}
}
