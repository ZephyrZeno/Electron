package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.nbt.NbtCompound;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.item.*;

public final class Pcb {
    public static PcbBlock BENCH,ASSEMBLY,SOCKET;
    public static final int INPUT_SLOTS=3+PcbLayout.Type.values().length,OUTPUT_SLOT=INPUT_SLOTS,INVENTORY_SIZE=INPUT_SLOTS+1;
    public static Item BLANK,SOLDER,FOIL,BOARD;
    public static final EnumMap<PcbLayout.Type,Item> PARTS=new EnumMap<>(PcbLayout.Type.class);
    public static final List<Item> MATERIALS=new ArrayList<>();
    public static ScreenHandlerType<PcbScreenHandler> SCREEN;
    private static Item item(String id,Item item){Registry.register(Registries.ITEM,Electron.id(id),item);Foundry.ITEMS.put(id,item);return item;}
    private static PcbBlock block(String id,PcbBlock.Role role){var b=Registry.register(Registries.BLOCK,Electron.id(id),new PcbBlock(role));item(id,new ComponentItem(b,new Item.Settings()));Foundry.DEVICES.put(id,b);return b;}
    public static void register(){
        BLANK=item("blank_pcb",new Item(new Item.Settings()));SOLDER=item("solder_alloy",new Item(new Item.Settings()));FOIL=item("copper_foil",new Item(new Item.Settings()));BOARD=item("assembled_pcb",new PcbItem());
        Collections.addAll(MATERIALS,BLANK,SOLDER,FOIL);
        for(var type:PcbLayout.Type.values()){var part=item("smd_"+type.name().toLowerCase(Locale.ROOT),new PcbPartItem(type));PARTS.put(type,part);MATERIALS.add(part);}
        BENCH=block("pcb_design_station",PcbBlock.Role.DESIGN);ASSEMBLY=block("precision_workbench",PcbBlock.Role.ASSEMBLY);SOCKET=block("pcb_socket",PcbBlock.Role.SOCKET);
        SCREEN=Registry.register(Registries.SCREEN_HANDLER,Electron.id("pcb_workbench"),new ScreenHandlerType<>(PcbScreenHandler::new,FeatureFlags.VANILLA_FEATURES));
    }
    public static ItemStack board(PcbLayout layout){return designed(BOARD,layout);}
    public static ItemStack designed(Item item,PcbLayout layout){var stack=new ItemStack(item);var n=new NbtCompound();n.putString("layout",layout.encode());stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));return stack;}
    public static PcbLayout layout(ItemStack s){if(!PcbFabrication.carriesLayout(s))return PcbLayout.empty();try{return PcbLayout.decode(s.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt().getString("layout"));}catch(IllegalArgumentException e){return PcbLayout.empty();}}
    public static int[] costs(PcbLayout l){int[] costs=new int[INPUT_SLOTS];costs[0]=1;costs[1]=l.solderCost();for(var p:l.parts())costs[3+p.type().ordinal()]++;return costs;}
    private Pcb(){}
}
