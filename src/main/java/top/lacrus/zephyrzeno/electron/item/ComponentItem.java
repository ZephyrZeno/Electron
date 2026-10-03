package top.lacrus.zephyrzeno.electron.item;

import top.lacrus.zephyrzeno.electron.foundry.*;

import java.util.List;
import java.util.Locale;
import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import top.lacrus.zephyrzeno.electron.block.LogicGateBlock;

/** Ratings describe the simulation shipped with the item, not real-world component guarantees. */
public final class ComponentItem extends BlockItem {
    public ComponentItem(Block block,Settings settings){super(block,settings);}
    private boolean battery(){return getBlock() instanceof ComponentBlock c&&c.kind==ComponentBlock.Kind.BATTERY;}
    @Override public boolean isItemBarVisible(ItemStack stack){return battery()?Foundry.CREATIVE_READINGS.getAsBoolean():super.isItemBarVisible(stack);}
    @Override public int getItemBarStep(ItemStack stack){return battery()?(int)Math.round(13*top.lacrus.zephyrzeno.electron.circuit.BatteryModel.clampEnergy(stack.getOrDefault(Foundry.BATTERY_ENERGY,0d))/200):super.getItemBarStep(stack);}
    @Override public int getItemBarColor(ItemStack stack){return battery()?getItemBarStep(stack)>2?0x5ac99c:0xdf695b:super.getItemBarColor(stack);}
    @Override public void appendTooltip(ItemStack stack,TooltipContext context,List<Text> lines,TooltipType type){
        super.appendTooltip(stack,context,lines,type);
        if(getBlock() instanceof ComponentBlock block){
            lines.add(Text.translatable(block.kind.large()?"tooltip.electron.large_component":"tooltip.electron.small_component").formatted(Formatting.AQUA));
            if(block instanceof top.lacrus.zephyrzeno.electron.industrial.ProcessingBlock machine){line(lines,"processing_rating",(int)machine.watts);return;}
            if(block==top.lacrus.zephyrzeno.electron.industrial.Industrial.PUMP){line(lines,"pump");return;}
            if(block==top.lacrus.zephyrzeno.electron.industrial.Industrial.BREAKOUT){line(lines,"breakout");return;}
            if(block==top.lacrus.zephyrzeno.electron.industrial.Industrial.LOW_SOCKET){line(lines,"low_socket");return;}
            switch(block.kind){
                case CRANK -> {line(lines,"crank_voltage");line(lines,"crank_energy");}
                case RESISTOR -> {line(lines,"resistor_rating",(int)block.resistance);line(lines,"resistor_voltage",String.format(Locale.ROOT,"%.2f",Math.sqrt(.25*block.resistance)));}
                case LED -> {line(lines,"led_voltage");line(lines,"led_current");}
                case SWITCH -> line(lines,"switch_rating");
                case BUTTON -> {line(lines,"button_pulse");line(lines,"switch_rating");}
                case CAPACITOR -> {line(lines,"capacitor_rating");line(lines,"capacitor_polarity");line(lines,"capacitor_storage");}
                case BATTERY -> {if(Foundry.CREATIVE_READINGS.getAsBoolean())line(lines,"battery_energy",String.format(Locale.ROOT,"%.2f",stack.getOrDefault(Foundry.BATTERY_ENERGY,0d)));else line(lines,"battery_capacity");line(lines,"battery_input");line(lines,"battery_output");}
                case TERMINAL_STRIP -> {line(lines,"terminal_strip_buses");line(lines,"terminal_strip_resistance");}
                case REDSTONE_SWITCH -> {line(lines,"redstone_switch_coil");line(lines,"redstone_switch_contacts");}
                case GATE -> {line(lines,"gate_voltage");line(lines,"gate_current");line(lines,"gate_threshold");}
                case DIODE -> {line(lines,"diode_voltage");line(lines,"diode_current");}
                case FUSE -> {line(lines,"fuse_rating");}
                case MOSFET -> {line(lines,"mosfet_rating");}
                case RELAY -> {line(lines,"relay_rating");line(lines,"relay_isolation");}
                case BOOST,BUCK -> {var spec=top.lacrus.zephyrzeno.electron.circuit.ConverterSpec.of(block.kind==ComponentBlock.Kind.BOOST,block.resistance);lines.add(Text.translatable("tooltip.electron.fixed_converter_rating",spec.minInput,spec.maxInput,spec.output,(int)(spec.maxCurrent*1000)).formatted(Formatting.GRAY));line(lines,"converter_power");}
                case GENERATOR -> line(lines,"generator_rating");
                case DISTRIBUTION -> {line(lines,"terminal_strip_buses");line(lines,"distribution_rating");}
                case BREAKER -> line(lines,"breaker_rating");
                case LAMP -> line(lines,"fixture_rating",String.format(Locale.ROOT,"%.1f",576/block.resistance));
                case THERMAL,HYDRO,WIND,LIGHTNING,NUCLEAR -> {line(lines,"source_"+block.kind.name().toLowerCase(Locale.ROOT));}
                case SUBSTATION -> {var spec=top.lacrus.zephyrzeno.electron.power.StationSpec.of(block.resistance);line(lines,"station_rating",(int)spec.min(),(int)spec.max(),(int)spec.output());}
                case MAINS -> line(lines,"mains_inlet");
                case MOTOR -> {line(lines,"motor_rating");}
            }

        }else if(getBlock() instanceof LogicGateBlock){
            lines.add(Text.translatable("tooltip.electron.redstone_component").formatted(Formatting.RED));line(lines,"redstone_rating");
        }
    }
    private static void line(List<Text> lines,String key,Object... args){lines.add(Text.translatable("tooltip.electron."+key,args).formatted(Formatting.GRAY));}
}
