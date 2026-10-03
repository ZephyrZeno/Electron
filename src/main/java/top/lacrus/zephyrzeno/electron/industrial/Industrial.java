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
    public static ComponentBlock PUMP,WIND_BASE,BREAKOUT,LOW_SOCKET;
    public static FluidBlock IRON_PIPE,GLASS_PIPE,VALVE,THERMAL_TANK,NUCLEAR_TANK,WATER_INLET;
    public static IndustrialTableBlock TABLE;
    public static PlantServiceBlock FUEL_PORT,ROD_PORT,ASH_PORT,SPENT_PORT;
    public static Item ASH;
    public static BlockEntityType<PlantServiceEntity> SERVICE_ENTITY;
    public static ScreenHandlerType<PlantScreenHandler> PLANT_SCREEN;
    public static BlockEntityType<FluidEntity> FLUID_ENTITY;
    public static BlockEntityType<IndustrialTableEntity> TABLE_ENTITY;
    public static ScreenHandlerType<IndustrialScreenHandler> SCREEN;
    private static Block.Settings settings(){return AbstractBlock.Settings.create().strength(4).sounds(BlockSoundGroup.METAL).nonOpaque().dynamicBounds().pistonBehavior(PistonBehavior.BLOCK);}
    private static <T extends Block>T block(String id,T b){Registry.register(Registries.BLOCK,Electron.id(id),b);var item=b instanceof ComponentBlock?new ComponentItem(b,new Item.Settings()):new BlockItem(b,new Item.Settings());Registry.register(Registries.ITEM,Electron.id(id),item);Foundry.ITEMS.put(id,item);BLOCKS.put(id,b);return b;}
    private static ComponentBlock device(String id,ComponentBlock.Kind kind){var b=block(id,new ComponentBlock(settings(),kind,.002){
        @Override public net.minecraft.block.BlockState getPlacementState(net.minecraft.item.ItemPlacementContext c){if(id.equals("wind_base"))return getDefaultState().with(FACING,c.getHorizontalPlayerFacing().getOpposite()).with(TURN,0);return super.getPlacementState(c);}
        @Override protected BlockRenderType getRenderType(BlockState s){return id.equals("wind_base")?BlockRenderType.INVISIBLE:BlockRenderType.MODEL;}
        @Override protected net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState s,net.minecraft.world.BlockView w,net.minecraft.util.math.BlockPos p,ShapeContext c){
            if(!id.equals("wind_base"))return super.getOutlineShape(s,w,p,c);
            var shape=Block.createCuboidShape(0,0,0,16,1.3,16);
            for(int z=0;z<16;z++){double dz=(z+.5)/16-.5,r=Math.sqrt(Math.max(0,.48*.48-dz*dz));if(r>0)shape=net.minecraft.util.shape.VoxelShapes.union(shape,Block.createCuboidShape((.5-r)*16,1.3,z,(.5+r)*16,16,z+1));}
            var cavity=top.lacrus.zephyrzeno.electron.power.WiringCabinet.orient(new net.minecraft.util.math.Box(0,.61,0,1,1,1),s);
            shape=net.minecraft.util.shape.VoxelShapes.combineAndSimplify(shape,net.minecraft.util.shape.VoxelShapes.cuboid(cavity),net.minecraft.util.function.BooleanBiFunction.ONLY_FIRST);
            return net.minecraft.util.shape.VoxelShapes.union(shape,top.lacrus.zephyrzeno.electron.power.WiringCabinet.shape(s,w.getBlockEntity(p) instanceof ComponentEntity e&&e.cabinetOpen));
        }
    });Foundry.DEVICES.put(id,b);return b;}
    public static void registerBlocks(){
        MAST=block("wind_mast",new Block(settings()));
        BOILER=block("boiler_module",new Block(settings()));TURBINE=block("turbine_module",new Block(settings()));ALTERNATOR=block("alternator_module",new Block(settings()));CONDENSER=block("condenser_module",new Block(settings()));CONTROL=block("plant_control_module",new PlantServiceBlock(settings(),PlantServiceBlock.Role.CONTROL));REACTOR_VESSEL=block("reactor_vessel_module",new Block(settings()));CONTROL_RODS=block("control_rod_drive",new Block(settings()));SAFETY_VALVE=block("safety_valve_module",new Block(settings()));
        FUEL_PORT=block("plant_fuel_port",new PlantServiceBlock(settings(),PlantServiceBlock.Role.SOLID_FUEL));
        ROD_PORT=block("reactor_fuel_intake",new PlantServiceBlock(settings(),PlantServiceBlock.Role.FUEL_ROD));
        ASH_PORT=block("plant_ash_outlet",new PlantServiceBlock(settings(),PlantServiceBlock.Role.ASH_OUTPUT));
        SPENT_PORT=block("spent_rod_outlet",new PlantServiceBlock(settings(),PlantServiceBlock.Role.SPENT_OUTPUT));
        ASH=Registry.register(Registries.ITEM,Electron.id("combustion_ash"),new Item(new Item.Settings()));Foundry.ITEMS.put("combustion_ash",ASH);
        SERVICE_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,Electron.id("plant_service"),FabricBlockEntityTypeBuilder.create(PlantServiceEntity::new,CONTROL,FUEL_PORT,ROD_PORT,ASH_PORT,SPENT_PORT).build());
        PLANT_SCREEN=Registry.register(Registries.SCREEN_HANDLER,Electron.id("plant_service"),new ScreenHandlerType<>(PlantScreenHandler::new,FeatureFlags.VANILLA_FEATURES));
        PUMP=device("water_pump",ComponentBlock.Kind.MOTOR);WIND_BASE=device("wind_base",ComponentBlock.Kind.MAINS);BREAKOUT=device("cable_breakout",ComponentBlock.Kind.DISTRIBUTION);
        LOW_SOCKET=device("low_profile_socket",ComponentBlock.Kind.TERMINAL_STRIP);
        IRON_PIPE=block("iron_pipe",new FluidBlock(settings(),FluidBlock.Type.IRON));GLASS_PIPE=block("glass_pipe",new FluidBlock(settings(),FluidBlock.Type.GLASS));VALVE=block("pipe_valve",new FluidBlock(settings(),FluidBlock.Type.VALVE));
        THERMAL_TANK=block("boiler_water_tank",new FluidBlock(settings(),FluidBlock.Type.BOILER));NUCLEAR_TANK=block("reactor_coolant_tank",new FluidBlock(settings(),FluidBlock.Type.COOLANT));
        WATER_INLET=block("plant_water_inlet",new FluidBlock(settings(),FluidBlock.Type.INLET));
        FLUID_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,Electron.id("fluid_component"),FabricBlockEntityTypeBuilder.create(FluidEntity::new,IRON_PIPE,GLASS_PIPE,VALVE,THERMAL_TANK,NUCLEAR_TANK,WATER_INLET).build());
        TABLE=block("industrial_assembly_table",new IndustrialTableBlock(settings()));
        TABLE_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,Electron.id("industrial_assembly_table"),FabricBlockEntityTypeBuilder.create(IndustrialTableEntity::new,TABLE).build());
        SCREEN=Registry.register(Registries.SCREEN_HANDLER,Electron.id("industrial_assembly"),new ScreenHandlerType<>(IndustrialScreenHandler::new,FeatureFlags.VANILLA_FEATURES));
    }
    public static boolean module(BlockState s){return List.of(MAST,BOILER,TURBINE,ALTERNATOR,CONDENSER,CONTROL,REACTOR_VESSEL,CONTROL_RODS,SAFETY_VALVE).contains(s.getBlock());}
    private Industrial(){}
}
