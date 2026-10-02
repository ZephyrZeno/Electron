package top.lacrus.zephyrzeno.electron;

import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import top.lacrus.zephyrzeno.electron.block.*;
import top.lacrus.zephyrzeno.electron.logic.LogicFunction;
import top.lacrus.zephyrzeno.electron.foundry.Foundry;
import top.lacrus.zephyrzeno.electron.foundry.ComponentItem;

public final class Electron implements ModInitializer {
    public static final String MOD_ID = "electron";
    public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    public static final Map<LogicFunction, LogicGateBlock> GATES = new java.util.EnumMap<>(LogicFunction.class);
    public static BinaryMemoryBlock MEMORY;

    public static Identifier id(String path) { return Identifier.of(MOD_ID, path); }

    private static AbstractBlock.Settings settings() {
        return AbstractBlock.Settings.create().strength(1.5f).sounds(BlockSoundGroup.METAL)
                .nonOpaque().solidBlock((s, w, p) -> false)
                .luminance(state -> state.get(LogicGateBlock.POWERED) ? 7 : 0);
    }

    private static <T extends Block> T register(String name, T block) {
        Registry.register(Registries.BLOCK, id(name), block);
        Registry.register(Registries.ITEM, id(name), new ComponentItem(block, new Item.Settings()));
        BLOCKS.put(name, block);
        return block;
    }

    @Override public void onInitialize() {
        for (LogicFunction function : LogicFunction.values()) {
            GATES.put(function, register(function.name().toLowerCase(java.util.Locale.ROOT) + "_gate", new LogicGateBlock(settings(), function)));
        }
        MEMORY = register("binary_memory", new BinaryMemoryBlock(settings()));
        Foundry.register();
        Registry.register(Registries.ITEM_GROUP, id("components"), FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.electron.components"))
                .icon(() -> new ItemStack(Foundry.LED))
                .entries((context, entries) -> {Foundry.DEVICES.values().stream().filter(b->!b.kind.large()).forEach(entries::add);for(var item:new Item[]{Foundry.SPOOL,Foundry.TERMINAL,Foundry.WALL_PORT,Foundry.BRIDGE})entries.add(item);}).build());
        Registry.register(Registries.ITEM_GROUP,id("power"),FabricItemGroup.builder().displayName(Text.translatable("itemGroup.electron.power")).icon(()->new ItemStack(Foundry.MOTOR)).entries((context,entries)->{Foundry.DEVICES.values().stream().filter(b->b.kind.large()).forEach(entries::add);entries.add(Foundry.CABLE);entries.add(Foundry.TOWER_SECTION);}).build());
        Registry.register(Registries.ITEM_GROUP,id("redstone"),FabricItemGroup.builder().displayName(Text.translatable("itemGroup.electron.redstone"))
                .icon(()->new ItemStack(MEMORY)).entries((context,entries)->BLOCKS.values().forEach(entries::add)).build());
        Registry.register(Registries.ITEM_GROUP,id("workshop"),FabricItemGroup.builder().displayName(Text.translatable("itemGroup.electron.workshop"))
                .icon(()->new ItemStack(Foundry.METER)).entries((context,entries)->{entries.add(Foundry.TABLE);for(var item:new Item[]{Foundry.HANDBOOK,Foundry.METER,Foundry.CUTTER,Foundry.WRENCH,Foundry.MAGNIFIER,Foundry.SCRAP})entries.add(item);}).build());
    }
}
