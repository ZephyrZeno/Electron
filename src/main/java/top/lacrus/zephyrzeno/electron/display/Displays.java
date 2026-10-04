package top.lacrus.zephyrzeno.electron.display;

import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.Foundry;
import top.lacrus.zephyrzeno.electron.item.ComponentItem;

public final class Displays {
    public static DisplayBlock DIAL,SMALL,PANEL,PIXEL;
    private static DisplayBlock block(String id,DisplayBlock.Role role){var b=Registry.register(Registries.BLOCK,Electron.id(id),new DisplayBlock(role));var item=Registry.register(Registries.ITEM,Electron.id(id),new ComponentItem(b,new Item.Settings()));Foundry.DEVICES.put(id,b);Foundry.ITEMS.put(id,item);return b;}
    public static void register(){DIAL=block("decimal_input",DisplayBlock.Role.DIAL);SMALL=block("numeric_display",DisplayBlock.Role.SMALL);PANEL=block("display_panel",DisplayBlock.Role.PANEL);PIXEL=block("pixel_display_panel",DisplayBlock.Role.PIXEL);}
    private Displays(){}
}
