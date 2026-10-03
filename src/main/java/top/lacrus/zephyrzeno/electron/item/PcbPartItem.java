package top.lacrus.zephyrzeno.electron.item;
import java.util.List;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import top.lacrus.zephyrzeno.electron.pcb.PcbLayout;
public final class PcbPartItem extends Item {
    private final PcbLayout.Type part;
    public PcbPartItem(PcbLayout.Type type){super(new Settings());part=type;}
    @Override public void appendTooltip(ItemStack stack,TooltipContext context,List<Text> lines,TooltipType type){lines.add(Text.translatable("pcb.electron.part_rating_"+part.name().toLowerCase(java.util.Locale.ROOT)).formatted(Formatting.GRAY));}
}
