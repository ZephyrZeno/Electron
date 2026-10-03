package top.lacrus.zephyrzeno.electron.foundry;
import top.lacrus.zephyrzeno.electron.item.MagnifierItem;
import top.lacrus.zephyrzeno.electron.item.ComponentItem;
import top.lacrus.zephyrzeno.electron.item.CircuitToolItem;

import java.util.*;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.sound.BlockSoundGroup;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.logic.LogicFunction;

public final class Foundry {
    public static final Map<String,Item> ITEMS=new LinkedHashMap<>();
    public static final Map<String,ComponentBlock> DEVICES=new LinkedHashMap<>();
    public static final Map<LogicFunction,ComponentBlock> WIRE_GATES=new EnumMap<>(LogicFunction.class);
    public static BlockEntityType<ComponentEntity> COMPONENT_ENTITY;
    public static ComponentBlock CRANK,RESISTOR_100,RESISTOR_220,RESISTOR_1000,RESISTOR_10000,LED,SWITCH,DIODE,FUSE,BUTTON,CAPACITOR,BATTERY,TERMINAL_STRIP,REDSTONE_SWITCH;
    public static net.minecraft.component.ComponentType<Double> BATTERY_ENERGY;
    public static ComponentBlock MOSFET,RELAY,BOOST,BUCK,GENERATOR,DISTRIBUTION,BREAKER,LAMP,MOTOR;
    public static Item CABLE;
    public static final Item[] WIRE_COLORS=new Item[8],CABLE_COLORS=new Item[8];
    public static ComponentBlock BOOST_12,BUCK_3V3,BUCK_12;
    public static ComponentBlock TOWER_TERMINAL;
    public static Block TOWER_SECTION;
    public static Block SHAFT,TURNTABLE;
    public static Block PLANT_CASING,REACTOR_CASING,URANIUM_ORE;
    public static ComponentBlock THERMAL,HYDRO,WIND,LIGHTNING,NUCLEAR,SUBSTATION,MAINS;
    public static ComponentBlock STATION_6K6,STATION_11K,STATION_24K;
    public static Item RAW_URANIUM,URANIUM_PELLET,FUEL_ROD,SPENT_ROD;
    public static BlockEntityType<top.lacrus.zephyrzeno.electron.mechanical.MechanicalEntity> MECHANICAL_ENTITY;
    public static java.util.function.BooleanSupplier CREATIVE_READINGS=()->false;
    public static Block TABLE;
    public static Item SPOOL,CUTTER,METER,WRENCH,MAGNIFIER,TERMINAL,WALL_PORT,BRIDGE,HANDBOOK,SCRAP;
    private static Item item(String id,Item value){Registry.register(Registries.ITEM,Electron.id(id),value);ITEMS.put(id,value);return value;}
    private static <T extends Block> T block(String id,T block){Registry.register(Registries.BLOCK,Electron.id(id),block);item(id,block instanceof ComponentBlock component?new ComponentItem(block,new Item.Settings().maxCount(component.kind==ComponentBlock.Kind.BATTERY?1:64)):new BlockItem(block,new Item.Settings()));return block;}
    private static ComponentBlock device(String id,ComponentBlock.Kind kind,double r){var block=block(id,createDevice(id,kind,r));DEVICES.put(id,block);return block;}
    private static ComponentBlock createDevice(String id,ComponentBlock.Kind kind,double r){var settings=AbstractBlock.Settings.create().dynamicBounds().strength(2).sounds(BlockSoundGroup.METAL).nonOpaque().pistonBehavior(PistonBehavior.BLOCK).luminance(s->kind==ComponentBlock.Kind.LED||kind==ComponentBlock.Kind.LAMP?s.get(ComponentBlock.LEVEL):0);return top.lacrus.zephyrzeno.electron.power.GridDevices.handles(kind)||id.equals("tower_terminal")?new top.lacrus.zephyrzeno.electron.multiblock.AssemblyControllerBlock(settings,kind,r):new ComponentBlock(settings,kind,r);}
    public static void register() {
        top.lacrus.zephyrzeno.electron.multiblock.Assembly.register();
        ElectricalEffects.register();
        BATTERY_ENERGY=Registry.register(Registries.DATA_COMPONENT_TYPE,Electron.id("battery_energy"),net.minecraft.component.ComponentType.<Double>builder().codec(com.mojang.serialization.Codec.doubleRange(0,BatteryModel.CAPACITY)).packetCodec(net.minecraft.network.codec.PacketCodecs.DOUBLE).build());
        CRANK=device("hand_crank",ComponentBlock.Kind.CRANK,5);
        RESISTOR_100=device("resistor_100",ComponentBlock.Kind.RESISTOR,100);RESISTOR_220=device("resistor_220",ComponentBlock.Kind.RESISTOR,220);RESISTOR_1000=device("resistor_1000",ComponentBlock.Kind.RESISTOR,1000);
        LED=device("indicator_led",ComponentBlock.Kind.LED,20);SWITCH=device("circuit_switch",ComponentBlock.Kind.SWITCH,.02);
        DIODE=device("silicon_diode",ComponentBlock.Kind.DIODE,2);FUSE=device("fuse_25ma",ComponentBlock.Kind.FUSE,.2);
        BUTTON=device("momentary_button",ComponentBlock.Kind.BUTTON,.02);
        CAPACITOR=device("capacitor_100mf",ComponentBlock.Kind.CAPACITOR,CapacitorModel.ESR);
        RESISTOR_10000=device("resistor_10000",ComponentBlock.Kind.RESISTOR,10000);
        BATTERY=device("rechargeable_battery",ComponentBlock.Kind.BATTERY,5);
        TERMINAL_STRIP=device("terminal_strip",ComponentBlock.Kind.TERMINAL_STRIP,.002);
        REDSTONE_SWITCH=device("electrical_redstone_switch",ComponentBlock.Kind.REDSTONE_SWITCH,RedstoneSwitchModel.COIL_OHMS);
        MOSFET=device("power_mosfet",ComponentBlock.Kind.MOSFET,.08);RELAY=device("electrical_relay",ComponentBlock.Kind.RELAY,.02);
        BOOST=device("boost_converter",ComponentBlock.Kind.BOOST,24);BUCK=device("buck_converter",ComponentBlock.Kind.BUCK,5);
        BOOST_12=device("boost_converter_12",ComponentBlock.Kind.BOOST,12);BUCK_3V3=device("buck_converter_3v3",ComponentBlock.Kind.BUCK,3.3);BUCK_12=device("buck_converter_12",ComponentBlock.Kind.BUCK,12);
        GENERATOR=device("dc_generator",ComponentBlock.Kind.GENERATOR,4);DISTRIBUTION=device("distribution_box",ComponentBlock.Kind.DISTRIBUTION,.0005);
        BREAKER=device("dc_breaker",ComponentBlock.Kind.BREAKER,.02);LAMP=device("engineering_lamp",ComponentBlock.Kind.LAMP,240);MOTOR=device("dc_motor",ComponentBlock.Kind.MOTOR,12);
        TOWER_TERMINAL=device("tower_terminal",ComponentBlock.Kind.DISTRIBUTION,.0005);
        TOWER_SECTION=block("tower_section",new Block(AbstractBlock.Settings.create().strength(3).sounds(BlockSoundGroup.METAL).nonOpaque().pistonBehavior(PistonBehavior.BLOCK)));
        SHAFT=block("transmission_shaft",new top.lacrus.zephyrzeno.electron.mechanical.MechanicalBlock(AbstractBlock.Settings.create().strength(2).sounds(BlockSoundGroup.METAL).nonOpaque().pistonBehavior(PistonBehavior.BLOCK),false));
        TURNTABLE=block("rotating_platform",new top.lacrus.zephyrzeno.electron.mechanical.MechanicalBlock(AbstractBlock.Settings.create().strength(2).sounds(BlockSoundGroup.METAL).nonOpaque().pistonBehavior(PistonBehavior.BLOCK),true));
        MECHANICAL_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,Electron.id("mechanical"),FabricBlockEntityTypeBuilder.create(top.lacrus.zephyrzeno.electron.mechanical.MechanicalEntity::new,SHAFT,TURNTABLE).build());
        PLANT_CASING=block("plant_casing",new Block(AbstractBlock.Settings.create().strength(4).sounds(BlockSoundGroup.METAL).requiresTool()));
        REACTOR_CASING=block("reactor_casing",new Block(AbstractBlock.Settings.create().strength(6).sounds(BlockSoundGroup.METAL).requiresTool()));
        URANIUM_ORE=block("uranium_ore",new Block(AbstractBlock.Settings.create().strength(4).sounds(BlockSoundGroup.STONE).requiresTool()));
        RAW_URANIUM=item("raw_uranium",new Item(new Item.Settings()));URANIUM_PELLET=item("uranium_pellet",new Item(new Item.Settings()));FUEL_ROD=item("fuel_rod",new Item(new Item.Settings().maxCount(16)));SPENT_ROD=item("spent_fuel_rod",new Item(new Item.Settings().maxCount(16)));
        THERMAL=device("thermal_generator",ComponentBlock.Kind.THERMAL,2);HYDRO=device("hydro_generator",ComponentBlock.Kind.HYDRO,2);WIND=device("wind_generator",ComponentBlock.Kind.WIND,2);LIGHTNING=device("lightning_collector",ComponentBlock.Kind.LIGHTNING,2);NUCLEAR=device("nuclear_reactor",ComponentBlock.Kind.NUCLEAR,2);SUBSTATION=device("substation",ComponentBlock.Kind.SUBSTATION,.1);MAINS=device("mains_inlet",ComponentBlock.Kind.MAINS,.02);
        STATION_6K6=device("substation_6k6",ComponentBlock.Kind.SUBSTATION,6600);STATION_11K=device("substation_11k",ComponentBlock.Kind.SUBSTATION,11000);STATION_24K=device("substation_24k",ComponentBlock.Kind.SUBSTATION,24000);
        for(var function:LogicFunction.values()) {
            String id="wire_"+function.name().toLowerCase(Locale.ROOT)+"_gate";
            var gate=block(id,new ComponentBlock(AbstractBlock.Settings.create().strength(2).sounds(BlockSoundGroup.METAL).nonOpaque().pistonBehavior(PistonBehavior.BLOCK),ComponentBlock.Kind.GATE,10000,function));
            DEVICES.put(id,gate);WIRE_GATES.put(function,gate);
        }
        top.lacrus.zephyrzeno.electron.industrial.Industrial.registerBlocks();
        top.lacrus.zephyrzeno.electron.industrial.Processing.register();
        top.lacrus.zephyrzeno.electron.industrial.Lighting.register();
        top.lacrus.zephyrzeno.electron.industrial.Automation.register();
        top.lacrus.zephyrzeno.electron.pcb.Pcb.register();
        top.lacrus.zephyrzeno.electron.pcb.PcbFabrication.register();
        top.lacrus.zephyrzeno.electron.pcb.PcbNetworking.register();
        COMPONENT_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,Electron.id("component"),FabricBlockEntityTypeBuilder.<ComponentEntity>create((p,s)->s.getBlock() instanceof top.lacrus.zephyrzeno.electron.pcb.PcbBlock pcb?(pcb.bench?new top.lacrus.zephyrzeno.electron.pcb.PcbBenchEntity(p,s):new top.lacrus.zephyrzeno.electron.pcb.PcbSocketEntity(p,s)):s.getBlock() instanceof top.lacrus.zephyrzeno.electron.industrial.ProcessingBlock?new top.lacrus.zephyrzeno.electron.industrial.ProcessingEntity(p,s):new ComponentEntity(p,s),DEVICES.values().toArray(Block[]::new)).build());
        TABLE=block("assembly_table",new AssemblyTableBlock(AbstractBlock.Settings.create().strength(2.5f).sounds(BlockSoundGroup.WOOD).nonOpaque()));
        SPOOL=item("wire_spool",new CircuitToolItem(new Item.Settings().maxCount(1),CircuitToolItem.Tool.SPOOL));
        CABLE=item("cable_spool",new CircuitToolItem(new Item.Settings().maxCount(1),CircuitToolItem.Tool.SPOOL,-1,true));
        for(int color=0;color<8;color++){
            WIRE_COLORS[color]=item("wire_spool_"+CircuitToolItem.COLOR_NAMES[color],new CircuitToolItem(new Item.Settings().maxCount(1),CircuitToolItem.Tool.SPOOL,color,false));
            CABLE_COLORS[color]=item("cable_spool_"+CircuitToolItem.COLOR_NAMES[color],new CircuitToolItem(new Item.Settings().maxCount(1),CircuitToolItem.Tool.SPOOL,color,true));
        }
        CUTTER=item("wire_cutters",new CircuitToolItem(new Item.Settings().maxCount(1).maxDamage(512),CircuitToolItem.Tool.CUTTER));
        METER=item("multimeter",new CircuitToolItem(new Item.Settings().maxCount(1),CircuitToolItem.Tool.METER));
        WRENCH=item("calibration_wrench",new CircuitToolItem(new Item.Settings().maxCount(1),CircuitToolItem.Tool.WRENCH));
        MAGNIFIER=item("magnifying_visor",new MagnifierItem());
        HANDBOOK=item("engineering_handbook",new Item(new Item.Settings().maxCount(1)));
        TERMINAL=item("terminal",new Item(new Item.Settings()));WALL_PORT=item("wall_port_2",new Item(new Item.Settings()));BRIDGE=item("wire_bridge",new Item(new Item.Settings()));SCRAP=item("burnt_component",new Item(new Item.Settings()));
        CircuitNetworking.register();CircuitInteractions.register();CircuitState.register();MeterService.register();
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),net.minecraft.world.gen.GenerationStep.Feature.UNDERGROUND_ORES,RegistryKey.of(RegistryKeys.PLACED_FEATURE,Electron.id("uranium_ore")));
    }
}
