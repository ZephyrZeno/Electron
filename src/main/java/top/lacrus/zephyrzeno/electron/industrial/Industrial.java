package top.lacrus.zephyrzeno.electron.industrial;

import java.util.*;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.sound.BlockSoundGroup;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.item.ComponentItem;

public final class Industrial {
    public static final Map<String,Block> BLOCKS=new LinkedHashMap<>();
    public static Block MAST,BOILER,TURBINE,ALTERNATOR,CONDENSER,CONTROL,REACTOR_VESSEL,CONTROL_RODS,SAFETY_VALVE;
    public static ComponentBlock PUMP,WIND_BASE,BREAKOUT;
    public static FluidBlock IRON_PIPE,GLASS_PIPE,VALVE,THERMAL_TANK,NUCLEAR_TANK;
    public static IndustrialTableBlock TABLE;
    public static BlockEntityType<FluidEntity> FLUID_ENTITY;
    public static BlockEntityType<IndustrialTableEntity> TABLE_ENTITY;
    public static ScreenHandlerType<IndustrialScreenHandler> SCREEN;
    private static Block.Settings settings(){return AbstractBlock.Settings.create().strength(4).sounds(BlockSoundGroup.METAL).nonOpaque().dynamicBounds().pistonBehavior(PistonBehavior.BLOCK);}
    private static <T extends Block>T block(String id,T b){Registry.register(Registries.BLOCK,Electron.id(id),b);var item=b instanceof ComponentBlock?new ComponentItem(b,new Item.Settings()):new BlockItem(b,new Item.Settings());Registry.register(Registries.ITEM,Electron.id(id),item);Foundry.ITEMS.put(id,item);BLOCKS.put(id,b);return b;}
    private static ComponentBlock device(String id,ComponentBlock.Kind kind){var b=block(id,new ComponentBlock(settings(),kind,.002){@Override public net.minecraft.block.BlockState getPlacementState(net.minecraft.item.ItemPlacementContext c){if(id.equals("wind_base"))return getDefaultState().with(FACING,c.getHorizontalPlayerFacing().getOpposite()).with(TURN,0);return super.getPlacementState(c);}});Foundry.DEVICES.put(id,b);return b;}
    public static void registerBlocks(){
        MAST=block("wind_mast",new Block(settings()));
        BOILER=block("boiler_module",new Block(settings()));TURBINE=block("turbine_module",new Block(settings()));ALTERNATOR=block("alternator_module",new Block(settings()));CONDENSER=block("condenser_module",new Block(settings()));CONTROL=block("plant_control_module",new Block(settings()));REACTOR_VESSEL=block("reactor_vessel_module",new Block(settings()));CONTROL_RODS=block("control_rod_drive",new Block(settings()));SAFETY_VALVE=block("safety_valve_module",new Block(settings()));
        PUMP=device("water_pump",ComponentBlock.Kind.MOTOR);WIND_BASE=device("wind_base",ComponentBlock.Kind.MAINS);BREAKOUT=device("cable_breakout",ComponentBlock.Kind.DISTRIBUTION);
        IRON_PIPE=block("iron_pipe",new FluidBlock(settings(),FluidBlock.Type.IRON));GLASS_PIPE=block("glass_pipe",new FluidBlock(settings(),FluidBlock.Type.GLASS));VALVE=block("pipe_valve",new FluidBlock(settings(),FluidBlock.Type.VALVE));
        THERMAL_TANK=block("boiler_water_tank",new FluidBlock(settings(),FluidBlock.Type.BOILER));NUCLEAR_TANK=block("reactor_coolant_tank",new FluidBlock(settings(),FluidBlock.Type.COOLANT));
        FLUID_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,Electron.id("fluid_component"),FabricBlockEntityTypeBuilder.create(FluidEntity::new,IRON_PIPE,GLASS_PIPE,VALVE,THERMAL_TANK,NUCLEAR_TANK).build());
        TABLE=block("industrial_assembly_table",new IndustrialTableBlock(settings()));
        TABLE_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,Electron.id("industrial_assembly_table"),FabricBlockEntityTypeBuilder.create(IndustrialTableEntity::new,TABLE).build());
        SCREEN=Registry.register(Registries.SCREEN_HANDLER,Electron.id("industrial_assembly"),new ScreenHandlerType<>(IndustrialScreenHandler::new,FeatureFlags.VANILLA_FEATURES));
    }
    public static boolean module(BlockState s){return List.of(MAST,BOILER,TURBINE,ALTERNATOR,CONDENSER,CONTROL,REACTOR_VESSEL,CONTROL_RODS,SAFETY_VALVE).contains(s.getBlock());}
    private Industrial(){}
}
