package top.lacrus.zephyrzeno.electron.circuit;

import java.util.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import top.lacrus.zephyrzeno.electron.foundry.Foundry;

public final class CircuitToolItem extends Item {
    public enum Tool { SPOOL,CUTTER,METER,WRENCH }
    public static final int[] COLORS={0xe3555c,0x292e3c,0x42c6cc,0xf2cc60,0x73c685,0xb990e6,0xe6ebed,0x8794a2};
    public static final String[] COLOR_NAMES={"red","black","cyan","yellow","green","purple","white","gray"};
    public static Text colorName(int color){return Text.translatable("color.electron."+COLOR_NAMES[color&7]);}
    public final Tool tool;
    public CircuitToolItem(Settings s,Tool tool){super(s);this.tool=tool;}
    public static NbtCompound data(ItemStack s){return s.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();}
    public static boolean wireSpool(ItemStack stack){return stack.isOf(Foundry.SPOOL)||stack.isOf(Foundry.CABLE);}
    public static void data(ItemStack s,NbtCompound n){s.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));if(wireSpool(s))s.set(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelDataComponent(n.getInt("color")&7));}
    public static int units(ItemStack s){var n=data(s);return n.contains("units")?Math.clamp(n.getInt("units"),0,16384):256;}
    public static ItemStack spool(int units,int color){return spool(units,color,false);}
    public static ItemStack spool(int units,int color,boolean cable){ItemStack s=new ItemStack(cable?Foundry.CABLE:Foundry.SPOOL);var n=new NbtCompound();n.putInt("units",units);n.putInt("color",color&7);data(s,n);return s;}
    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand) {
        ItemStack s=player.getStackInHand(hand);if(!world.isClient) {
            var n=data(s);n.remove("start");n.remove("end");n.remove("meter_device");n.remove("meter_wire");
            if(tool==Tool.SPOOL) {if(player.isSneaking())n.putInt("layer",1-n.getInt("layer"));else n.putInt("color",(n.getInt("color")+1)&7);player.sendMessage(Text.translatable("message.electron.spool_mode",colorName(n.getInt("color")),n.getInt("layer")),true);}
            else if(tool==Tool.CUTTER){n.putInt("layer",1-n.getInt("layer"));player.sendMessage(Text.translatable("message.electron.cutter_mode",n.getInt("layer")),true);}
            else if(tool==Tool.WRENCH)player.sendMessage(Text.translatable("message.electron.wrench_help"),true);
            else {if(player.isSneaking())n.putInt("meter_mode",1-n.getInt("meter_mode"));player.sendMessage(Text.translatable(n.getInt("meter_mode")==1?"message.electron.meter_ohms":"message.electron.meter_reset"),true);}
            data(s,n);
            if(tool==Tool.SPOOL&&player instanceof net.minecraft.server.network.ServerPlayerEntity server)CircuitNetworking.preview(server,List.of());
        }
        return TypedActionResult.success(s,world.isClient);
    }
    @Override public void appendTooltip(ItemStack stack,TooltipContext context,List<Text> lines,TooltipType type) {
        var n=data(stack);
        if(tool==Tool.SPOOL){lines.add(Text.translatable(stack.isOf(Foundry.CABLE)?"tooltip.electron.cable_rating":"tooltip.electron.wire_rating"));lines.add(Text.translatable("tooltip.electron.spool_balance",String.format(Locale.ROOT,"%.4f",units(stack)/16d),colorName(n.getInt("color")),n.getInt("layer")).formatted(Formatting.AQUA));lines.add(Text.translatable("tooltip.electron.spool_route"));lines.add(Text.translatable("tooltip.electron.spool_tap"));lines.add(Text.translatable("tooltip.electron.spool_controls"));}
        else if(tool==Tool.CUTTER){lines.add(Text.translatable("tooltip.electron.cut_run"));lines.add(Text.translatable("tooltip.electron.cut_controls"));}
        else if(tool==Tool.WRENCH){lines.add(Text.translatable("tooltip.electron.wrench_turn"));lines.add(Text.translatable("tooltip.electron.wrench_wired"));}
        else {lines.add(Text.translatable("tooltip.electron.meter_measure"));lines.add(Text.translatable("tooltip.electron.meter_network"));}
    }
}
