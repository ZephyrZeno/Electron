package top.lacrus.zephyrzeno.electron.foundry;

import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.Electron;

/** Small electrical failures vent locally; they do not run a terrain-damaging TNT explosion. */
public final class ElectricalEffects {
    public static final SoundEvent CRANK=sound("hand_crank"),SWITCH=sound("switch_click"),LED=sound("led_on"),ARC=sound("electrical_arc"),FAIL=sound("component_fail"),POP=sound("component_pop"),WRENCH=sound("wrench_turn");
    public static final SoundEvent WIRE_CONNECT=sound("wire_connect"),CABLE_CONNECT=sound("cable_connect"),WIRE_CUT=sound("wire_cut"),TERMINAL=sound("terminal_attach"),ASSEMBLE=sound("machine_assemble"),FORM=sound("machine_form"),PUMP=sound("pump_run"),VALVE=sound("valve_turn"),WATER_FILL=sound("water_fill"),MOTOR=sound("motor_run"),WIND=sound("wind_rotor"),HYDRO=sound("hydro_run"),TURBINE=sound("turbine_run"),ALARM=sound("plant_alarm"),PRESSURE_BURST=sound("pressure_burst");
    private static SoundEvent sound(String name){var id=Electron.id(name);float range=switch(name){case "machine_burst"->32;case "boiler_burst"->64;case "reactor_burst"->128;default->0;};return Registry.register(Registries.SOUND_EVENT,id,range>0?SoundEvent.of(id,range):SoundEvent.of(id));}
    public static void register(){}
    public static final SoundEvent SIZZLE=sound("insulation_sizzle"),WIRE_MELT=sound("wire_melt"),TRIP=sound("breaker_trip");
    public static final SoundEvent CABINET_OPEN=sound("cabinet_open"),CABINET_CLOSE=sound("cabinet_close"),SPARK=sound("contact_spark");
    public static final SoundEvent REACTOR=sound("reactor_cooling"),TRANSFORMER=sound("transformer_hum"),CRUSHER=sound("crusher_run"),CENTRIFUGE=sound("centrifuge_run"),KILN=sound("kiln_run"),FABRICATOR=sound("fabricator_run"),DRILL=sound("pcb_drill_run"),BATH=sound("process_bath");
    public static final SoundEvent MACHINE_BURST=sound("machine_burst"),BOILER_BURST=sound("boiler_burst"),REACTOR_BURST=sound("reactor_burst");
    public static void play(ServerWorld world,BlockPos pos,SoundEvent sound,float volume,float pitch){world.playSound(null,pos,sound,SoundCategory.BLOCKS,volume,pitch);}
    public static void play(ServerWorld world,Vec3d pos,SoundEvent sound,float volume,float pitch){world.playSound(null,pos.x,pos.y,pos.z,sound,SoundCategory.BLOCKS,volume,pitch);}
    public static Vec3d front(BlockPos pos,BlockState state){var b=(ComponentBlock)state.getBlock();return Vec3d.of(pos).add(ComponentGeometry.local(state.get(ComponentBlock.FACING),state.get(ComponentBlock.TURN),7,10,ComponentGeometry.inset(b.kind)-1.5));}
    public static void overload(ServerWorld world,BlockPos pos,BlockState state){
        Vec3d p=front(pos,state);world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,p.x,p.y,p.z,2,.045,.045,.045,.015);
        world.spawnParticles(ParticleTypes.SMOKE,p.x,p.y,p.z,1,.02,.02,.02,.005);
        top.lacrus.zephyrzeno.electron.power.FaultEffects.emit(world,p.add(-.08,0,0),p,1,6);
    }
    public static void burn(ServerWorld world,BlockPos pos,BlockState state,boolean burst){
        if(burst&&state.getBlock() instanceof ComponentBlock b&&b.kind.large())top.lacrus.zephyrzeno.electron.power.PlantAccident.emit(world,pos,state,top.lacrus.zephyrzeno.electron.power.PlantAccident.MACHINE);
        Vec3d p=front(pos,state);world.spawnParticles(ParticleTypes.SMOKE,p.x,p.y,p.z,12,.12,.12,.12,.025);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,p.x,p.y,p.z,burst?18:7,.11,.11,.11,.065);
        if(burst)world.spawnParticles(ParticleTypes.EXPLOSION,p.x,p.y,p.z,1,0,0,0,0);
        top.lacrus.zephyrzeno.electron.power.FaultEffects.emit(world,p.add(-.12,0,0),p,burst?3:2,burst?120:12);
        world.addSyncedBlockEvent(pos,state.getBlock(),ComponentEntity.EVENT_FAILURE,burst?1:0);
    }
    private ElectricalEffects(){}
}
