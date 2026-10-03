package top.lacrus.zephyrzeno.electron.power;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.*;
import top.lacrus.zephyrzeno.electron.multiblock.Assembly;

/** Player-built structures and finite fuel. No energy is provided by redstone or a UI setting. */
public final class PowerPlant {
    private static BlockState original(ServerWorld w,BlockPos p){return top.lacrus.zephyrzeno.electron.multiblock.Assembly.original(w,p);}
    private static boolean air(ServerWorld w,BlockPos p){return top.lacrus.zephyrzeno.electron.multiblock.Assembly.air(w,p);}
    public static boolean source(ComponentBlock.Kind k){return switch(k){case THERMAL,HYDRO,WIND,LIGHTNING,NUCLEAR->true;default->false;};}
    public static String structure(ServerWorld w,BlockPos p,ComponentBlock.Kind k){
        var s=w.getBlockState(p);if(!(s.getBlock() instanceof ComponentBlock)||s.get(ComponentBlock.FACING)!=Direction.UP)return "upright";
        if(k==ComponentBlock.Kind.WIND||k==ComponentBlock.Kind.THERMAL||k==ComponentBlock.Kind.NUCLEAR||k==ComponentBlock.Kind.HYDRO)return PlantStructures.structure(w,p,s);
        if(k==ComponentBlock.Kind.LIGHTNING){int h=Assembly.height(w,p);if(h<3||h>=64||!Block.isShapeFullCube(w.getBlockState(p.down(h+1)).getCollisionShape(w,p.down(h+1))))return "tower";return w.getBlockState(p.up()).isOf(Blocks.LIGHTNING_ROD)?"ready":"rod";}
        if(k==ComponentBlock.Kind.SUBSTATION){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var q=p.add(x,-1,z);if(!w.isChunkLoaded(q))return "unloaded";if(!w.getBlockState(q).isOf(Blocks.SMOOTH_STONE))return "foundation";}for(var d:Direction.Type.HORIZONTAL)if(!original(w,p.offset(d)).isOf(Foundry.PLANT_CASING))return "casing";}
        return "ready";
    }
    public static String requirement(ServerWorld w,BlockPos p,ComponentBlock.Kind k){
        if(w.getBlockEntity(p) instanceof ComponentEntity e){String integrity=Assembly.integrity(w,p,e);if(!integrity.equals("ready"))return integrity;}
        String structure=structure(w,p,k);if(!structure.equals("ready"))return structure;
        if(k==ComponentBlock.Kind.HYDRO&&!PlantStructures.flowingWater(w,p,w.getBlockState(p)))return "flowing_water";
        if(k==ComponentBlock.Kind.THERMAL||k==ComponentBlock.Kind.NUCLEAR){var s=w.getBlockState(p);var tank=FluidNetwork.tank(w,PlantStructures.tankPos(p,s),k==ComponentBlock.Kind.NUCLEAR?Industrial.NUCLEAR_TANK:Industrial.THERMAL_TANK);if(tank==null||tank.amount<(k==ComponentBlock.Kind.NUCLEAR?1000:250))return "coolant_tank";}
        return "ready";
    }
    public static double voltage(BlockState state){if(!top.lacrus.zephyrzeno.electron.multiblock.Assembly.formed(state))return 240;return switch(((ComponentBlock)state.getBlock()).kind){case THERMAL->11000;case HYDRO->6600;case NUCLEAR->24000;default->240;};}
    private static boolean safetyHot(double temperature){return temperature>30;}
    private static boolean water(ServerWorld w,BlockPos p){return w.isChunkLoaded(p)&&w.getFluidState(p).isIn(FluidTags.WATER);}
    public static void prepare(ServerWorld w,BlockPos p,BlockState state,ComponentEntity e){
        var kind=((ComponentBlock)state.getBlock()).kind;
        if(kind==ComponentBlock.Kind.THERMAL||kind==ComponentBlock.Kind.NUCLEAR)PlantOperations.feed(w,e);
        e.plantStatus=requirement(w,p,kind);e.plantReady=e.plantStatus.equals("ready")&&Assembly.formed(state);
        if(!Assembly.formed(state)&&e.plantStatus.equals("ready"))e.plantStatus=e.assemblyStatus.equals("formed")?"assembly_pending":e.assemblyStatus;
        boolean enabled=state.get(ComponentBlock.ENABLED)&&!state.get(ComponentBlock.BROKEN);
        if(kind==ComponentBlock.Kind.NUCLEAR||kind==ComponentBlock.Kind.THERMAL){
            boolean nuclear=kind==ComponentBlock.Kind.NUCLEAR;
            var tank=FluidNetwork.tank(w,PlantStructures.tankPos(p,state),nuclear?Industrial.NUCLEAR_TANK:Industrial.THERMAL_TANK);
            boolean cooling=tank!=null&&tank.amount>=(nuclear?20:4);
            boolean burning=enabled&&e.plantReady&&e.fuelEnergy>0;
            e.decayHeat=burning?Math.min(1,e.decayHeat+.003):e.decayHeat*(nuclear?.9998:.997);
            double heating=burning?(nuclear?.14:.10):e.decayHeat*(nuclear?.11:.07);
            e.temperature=Math.clamp(e.temperature+heating-(cooling?.0025*Math.max(0,e.temperature-20):.0003),20,1000);
            if(cooling&&(burning||e.temperature>25))tank.change(-(nuclear?20:4));
            if(!cooling&&e.temperature>35){enabled=false;e.energy=0;e.plantReady=false;e.plantStatus="scram";if(state.get(ComponentBlock.ENABLED))w.setBlockState(p,state.with(ComponentBlock.ENABLED,false),Block.NOTIFY_ALL);}
            if(!cooling&&e.temperature>45&&w.getTime()%60==0)ElectricalEffects.play(w,p,ElectricalEffects.ALARM,.65f,1);
            if(e.temperature>180&&!state.get(ComponentBlock.BROKEN)){
                e.energy=e.fuelEnergy=e.decayHeat=0;e.plantReady=false;e.plantStatus="plant_failure";
                w.setBlockState(p,w.getBlockState(p).with(ComponentBlock.BROKEN,true).with(ComponentBlock.ENABLED,false),Block.NOTIFY_ALL);
                ElectricalEffects.play(w,p,ElectricalEffects.PRESSURE_BURST,1,1);
                // A bounded industrial steam/pressure accident; no nuclear-weapon simulation.
                w.createExplosion(null,p.getX()+.5,p.getY()+1,p.getZ()+.5,nuclear?5:3,net.minecraft.world.World.ExplosionSourceType.BLOCK);e.markDirty();return;
            }
        }
        if(!enabled||!e.plantReady){if(!e.plantStatus.equals("unloaded"))e.energy=0;e.markDirty();return;}
        double watts=switch(kind){case THERMAL->120;case HYDRO->160;case WIND->92*GridModel.wind(w.getTime(),p.getY(),w.isRaining());case NUCLEAR->400;default->0;};
        if(kind==ComponentBlock.Kind.LIGHTNING){
            for(var bolt:w.getEntitiesByClass(LightningEntity.class,new Box(p.up()).expand(1.5),b->true))if(top.lacrus.zephyrzeno.electron.circuit.CircuitState.get(w).claimLightning(bolt.getUuid())){
                e.lastPeakVoltage=300_000_000;e.lastPeakCurrent=30_000;e.lastCapturedEnergy=Math.min(GridModel.CAPACITY-e.energy,3000);e.energy+=e.lastCapturedEnergy;e.lastStrike=bolt.getUuidAsString();e.markDirty();ElectricalEffects.play(w,p,ElectricalEffects.ARC,.8f,.7f);
            }
        }
        double added=Math.min(watts*GridModel.DT,GridModel.CAPACITY-e.energy);
        if(kind==ComponentBlock.Kind.THERMAL||kind==ComponentBlock.Kind.NUCLEAR){double efficiency=kind==ComponentBlock.Kind.THERMAL?.35:.3;double burned=Math.min(watts*GridModel.DT/efficiency,e.fuelEnergy);added=Math.min(added,burned*efficiency);e.fuelEnergy=Math.max(0,e.fuelEnergy-burned);if(e.fuelEnergy<=1e-8)e.plantStatus="fuel";}
        e.energy+=added;e.markDirty();
        if(added>0&&kind==ComponentBlock.Kind.THERMAL&&w.getTime()%20==0){var chimney=PlantStructures.at(p,state,0,7,-5);w.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE,chimney.getX()+.5,chimney.getY()+.5,chimney.getZ()+.5,3,.12,.1,.12,.02);}
    }
    public static void interact(ServerWorld w,BlockPos p,BlockState s,ComponentEntity e,PlayerEntity player,ItemStack held){
        var kind=((ComponentBlock)s.getBlock()).kind;
        if(player.isSneaking()){w.setBlockState(p,s.cycle(ComponentBlock.ENABLED),Block.NOTIFY_ALL);player.sendMessage(Text.translatable(s.get(ComponentBlock.ENABLED)?"message.electron.source_off":"message.electron.source_on"),true);return;}
        if(WiringCabinet.present(s)){WiringCabinet.toggle(w,e);player.sendMessage(Text.translatable(e.cabinetOpen?"message.electron.cabinet_open":"message.electron.cabinet_closed"),true);return;}
        if(kind==ComponentBlock.Kind.WIND){player.sendMessage(Text.translatable("message.electron.wind_base_output"),true);return;}
        String reason=requirement(w,p,kind);if(reason.equals("ready")&&(kind==ComponentBlock.Kind.THERMAL||kind==ComponentBlock.Kind.NUCLEAR)&&e.fuelEnergy<=1e-8)reason="fuel";
        if(reason.equals("ready")&&s.getBlock() instanceof top.lacrus.zephyrzeno.electron.multiblock.AssemblyControllerBlock&&!Assembly.formed(s))reason=e.assemblyStatus.equals("formed")?"assembly_pending":e.assemblyStatus;
        player.sendMessage(Text.translatable("message.electron.plant_status",Text.translatable("plant.electron."+reason)),true);
    }
    private PowerPlant(){}
}
