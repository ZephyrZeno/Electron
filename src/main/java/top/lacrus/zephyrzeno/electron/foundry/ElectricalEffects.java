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
    private static SoundEvent sound(String name){var id=Electron.id(name);return Registry.register(Registries.SOUND_EVENT,id,SoundEvent.of(id));}
    public static void register(){}
    public static void play(ServerWorld world,BlockPos pos,SoundEvent sound,float volume,float pitch){world.playSound(null,pos,sound,SoundCategory.BLOCKS,volume,pitch);}
    public static Vec3d front(BlockPos pos,BlockState state){var b=(ComponentBlock)state.getBlock();return Vec3d.of(pos).add(ComponentGeometry.local(state.get(ComponentBlock.FACING),state.get(ComponentBlock.TURN),7,10,ComponentGeometry.inset(b.kind)-1.5));}
    public static void overload(ServerWorld world,BlockPos pos,BlockState state){
        Vec3d p=front(pos,state);world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,p.x,p.y,p.z,2,.045,.045,.045,.015);
        world.spawnParticles(ParticleTypes.SMOKE,p.x,p.y,p.z,1,.02,.02,.02,.005);
        play(world,pos,ARC,.18f,1.15f+world.random.nextFloat()*.15f);
    }
    public static void burn(ServerWorld world,BlockPos pos,BlockState state,boolean burst){
        Vec3d p=front(pos,state);world.spawnParticles(ParticleTypes.SMOKE,p.x,p.y,p.z,12,.12,.12,.12,.025);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,p.x,p.y,p.z,burst?18:7,.11,.11,.11,.065);
        if(burst)world.spawnParticles(ParticleTypes.EXPLOSION,p.x,p.y,p.z,1,0,0,0,0);
        play(world,pos,burst?POP:FAIL,burst?.55f:.4f,1.15f);
        world.addSyncedBlockEvent(pos,state.getBlock(),ComponentEntity.EVENT_FAILURE,burst?1:0);
    }
    private ElectricalEffects(){}
}
