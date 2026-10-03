package top.lacrus.zephyrzeno.electron.industrial;

import net.minecraft.block.*;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.Item;
import net.minecraft.registry.*;
import net.minecraft.sound.BlockSoundGroup;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.item.ComponentItem;

/** Two useful luminaire forms, supplied by the same physical 24 V network. */
public final class Lighting {
    public static ComponentBlock WORKSHOP,YARD;
    private static ComponentBlock lamp(String id,double watts){
        var block=Registry.register(Registries.BLOCK,Electron.id(id),new ComponentBlock(AbstractBlock.Settings.create().strength(2).sounds(BlockSoundGroup.METAL).nonOpaque().dynamicBounds().pistonBehavior(PistonBehavior.BLOCK).luminance(s->s.get(ComponentBlock.LEVEL)),ComponentBlock.Kind.LAMP,576/watts));
        var item=Registry.register(Registries.ITEM,Electron.id(id),new ComponentItem(block,new Item.Settings()));Foundry.DEVICES.put(id,block);Foundry.ITEMS.put(id,item);return block;
    }
    public static void register(){WORKSHOP=lamp("workshop_lamp",2.4);YARD=lamp("yard_lamp",6);}
    public static boolean fixture(ComponentBlock block){return block==WORKSHOP||block==YARD;}
    private Lighting(){}
}
