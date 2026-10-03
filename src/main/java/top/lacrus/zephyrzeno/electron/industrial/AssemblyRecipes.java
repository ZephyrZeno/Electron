package top.lacrus.zephyrzeno.electron.industrial;

import java.util.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import top.lacrus.zephyrzeno.electron.foundry.Foundry;

/** Workshop recipes consume real components; the output slot is checked before any input changes. */
public final class AssemblyRecipes {
    public record Ingredient(Item item,int count){}
    public record Recipe(String id,Item output,List<Ingredient> inputs){}
    private static Ingredient of(ItemConvertible item,int count){return new Ingredient(item.asItem(),count);}
    public static List<Recipe> recipes(){return List.of(
        new Recipe("boiler",Industrial.BOILER.asItem(),List.of(of(Items.IRON_BLOCK,4),of(Items.COPPER_BLOCK,2),of(Foundry.RELAY,2),of(Foundry.RESISTOR_1000,2),of(Processing.IRON_PLATE,8))),
        new Recipe("turbine",Industrial.TURBINE.asItem(),List.of(of(Items.IRON_BLOCK,3),of(Processing.IRON_PLATE,6),of(Foundry.SHAFT,2),of(Foundry.MOTOR,1))),
        new Recipe("alternator",Industrial.ALTERNATOR.asItem(),List.of(of(Items.COPPER_BLOCK,4),of(Items.IRON_BLOCK,2),of(Foundry.MOTOR,2),of(Foundry.DIODE,4),of(Processing.COPPER_COIL,8))),
        new Recipe("condenser",Industrial.CONDENSER.asItem(),List.of(of(Items.COPPER_BLOCK,3),of(Industrial.IRON_PIPE,8),of(Foundry.CAPACITOR,2),of(Foundry.RELAY,1),of(Processing.IRON_PLATE,4))),
        new Recipe("control",Industrial.CONTROL.asItem(),List.of(of(Foundry.MOSFET,4),of(Foundry.RELAY,4),of(Foundry.RESISTOR_1000,4),of(Foundry.LED,4),of(Processing.IRON_PLATE,4),of(Processing.COPPER_COIL,2))),
        new Recipe("vessel",Industrial.REACTOR_VESSEL.asItem(),List.of(of(Foundry.REACTOR_CASING,16),of(Items.IRON_BLOCK,8),of(Industrial.CONDENSER,2),of(Industrial.CONTROL,1))),
        new Recipe("control_rods",Industrial.CONTROL_RODS.asItem(),List.of(of(Items.IRON_BLOCK,4),of(Foundry.MOTOR,4),of(Foundry.RELAY,4),of(Industrial.CONTROL,1))),
        new Recipe("safety_valve",Industrial.SAFETY_VALVE.asItem(),List.of(of(Items.IRON_INGOT,8),of(Foundry.RELAY,2),of(Foundry.RESISTOR_100,2),of(Industrial.IRON_PIPE,2)))
    );}
    public static boolean craft(Inventory inv,int index){
        if(index<0||index>=recipes().size()||inv.size()<28)return false;var r=recipes().get(index);var out=inv.getStack(27);
        if(!out.isEmpty()&&(!out.isOf(r.output)||out.getCount()>=out.getMaxCount()))return false;
        for(var ingredient:r.inputs){int total=0;for(int i=0;i<27;i++)if(inv.getStack(i).isOf(ingredient.item))total+=inv.getStack(i).getCount();if(total<ingredient.count)return false;}
        for(var ingredient:r.inputs){int left=ingredient.count;for(int i=0;i<27&&left>0;i++)if(inv.getStack(i).isOf(ingredient.item)){int n=Math.min(left,inv.getStack(i).getCount());inv.removeStack(i,n);left-=n;}}
        if(out.isEmpty())inv.setStack(27,new ItemStack(r.output));else out.increment(1);inv.markDirty();return true;
    }
    private AssemblyRecipes(){}
}
