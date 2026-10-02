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

/** Player-built structures and finite fuel. No energy is provided by redstone or a UI setting. */
public final class PowerPlant {
    public static boolean source(ComponentBlock.Kind k){return switch(k){case THERMAL,HYDRO,WIND,LIGHTNING,NUCLEAR->true;default->false;};}
    public static String requirement(ServerWorld w,BlockPos p,ComponentBlock.Kind k){
        for(var q:BlockPos.iterate(p.add(-2,0,-2),p.add(2,0,2)))if(!w.isChunkLoaded(q))return "unloaded";
        if(w.getBlockState(p).get(ComponentBlock.FACING)!=Direction.UP)return "upright";
        if(k==ComponentBlock.Kind.WIND||k==ComponentBlock.Kind.LIGHTNING){
            var q=p.down();int height=0;while(height<64&&w.getBlockState(q).isOf(Foundry.TOWER_SECTION)){height++;q=q.down();}
            if(height<3||height>=64||!Block.isShapeFullCube(w.getBlockState(q).getCollisionShape(w,q)))return "tower";
            if(k==ComponentBlock.Kind.LIGHTNING)return w.getBlockState(p.up()).isOf(Blocks.LIGHTNING_ROD)?"ready":"rod";
            for(var check:BlockPos.iterate(p.add(-2,0,-2),p.add(2,4,2)))if(!check.equals(p)&&!w.isAir(check))return "wind_clearance";
            return w.isSkyVisible(p.up(5))?"ready":"sky";
        }
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(!w.getBlockState(p.add(x,-1,z)).isOf(Blocks.SMOOTH_STONE))return "foundation";
        if(k==ComponentBlock.Kind.SUBSTATION){
            for(var d:Direction.Type.HORIZONTAL)if(!w.getBlockState(p.offset(d)).isOf(Foundry.PLANT_CASING))return "casing";
            return "ready";
        }
        if(k==ComponentBlock.Kind.THERMAL){
            if(!w.getBlockState(p.east()).isOf(Blocks.FURNACE))return "furnace";
            for(var q:List.of(p.north(),p.south(),p.west()))if(!w.getBlockState(q).isOf(Foundry.PLANT_CASING))return "casing";
            return w.isAir(p.up())?"ready":"vent";
        }
        if(k==ComponentBlock.Kind.HYDRO){
            for(var q:List.of(p.north(),p.south()))if(!w.getBlockState(q).isOf(Foundry.PLANT_CASING))return "casing";
            return water(w,p.east())&&water(w,p.west(2).down(2))&&w.isAir(p.west(2))&&w.isAir(p.west(2).down())?"ready":"water_head";
        }
        if(k==ComponentBlock.Kind.NUCLEAR){
            for(var d:Direction.Type.HORIZONTAL)if(!w.getBlockState(p.offset(d)).isOf(Foundry.REACTOR_CASING))return "shielding";
            return water(w,p.east(2))&&water(w,p.west(2))?"ready":"coolant";
        }
        return "ready";
    }
    private static boolean safetyHot(double temperature){return temperature>30;}
    private static boolean water(ServerWorld w,BlockPos p){return w.isChunkLoaded(p)&&w.getFluidState(p).isIn(FluidTags.WATER);}
    public static void prepare(ServerWorld w,BlockPos p,BlockState state,ComponentEntity e){
        var kind=((ComponentBlock)state.getBlock()).kind;
        // Check structures every tick: dismantling a station must never create a last free power tick.
        e.plantStatus=requirement(w,p,kind);e.plantReady=e.plantStatus.equals("ready");
        boolean enabled=state.get(ComponentBlock.ENABLED)&&!state.get(ComponentBlock.BROKEN);
        if(kind==ComponentBlock.Kind.NUCLEAR){
            boolean cooling=water(w,p.east(2))&&water(w,p.west(2));
            e.temperature=Math.clamp(e.temperature+(enabled&&e.plantReady&&e.fuelEnergy>0?.08:0)-(cooling?.001*Math.max(0,e.temperature-20):.002),20,150);
            if(!cooling&&safetyHot(e.temperature)){w.setBlockState(p,state.with(ComponentBlock.ENABLED,false),Block.NOTIFY_ALL);enabled=false;e.energy=0;e.plantStatus="scram";}
        }
        if(!enabled)return;
        if(!e.plantReady){if(!e.plantStatus.equals("unloaded"))e.energy=0;return;}
        double watts=switch(kind){case THERMAL->120;case HYDRO->160;case WIND->100*GridModel.wind(w.getTime(),p.getY(),w.isRaining());case NUCLEAR->400;default->0;};
        if(kind==ComponentBlock.Kind.LIGHTNING){
            for(var bolt:w.getEntitiesByClass(LightningEntity.class,new Box(p.up()).expand(1.5),b->true)){
                if(!bolt.getUuidAsString().equals(e.lastStrike)){e.energy=Math.min(GridModel.CAPACITY,e.energy+3000);e.lastStrike=bolt.getUuidAsString();e.markDirty();}
            }
        }
        double added=Math.min(watts*GridModel.DT,GridModel.CAPACITY-e.energy);
        if(kind==ComponentBlock.Kind.THERMAL||kind==ComponentBlock.Kind.NUCLEAR){
            double efficiency=kind==ComponentBlock.Kind.THERMAL?.35:.3;
            added=Math.min(added,e.fuelEnergy*efficiency);e.fuelEnergy=Math.max(0,e.fuelEnergy-added/efficiency);
            if(e.fuelEnergy<=1e-8)e.plantStatus="fuel";
        }
        e.energy+=added;e.markDirty();
        if(added>0&&kind==ComponentBlock.Kind.THERMAL&&w.getTime()%20==0)w.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE,p.getX()+.5,p.getY()+1.1,p.getZ()+.5,2,.08,.1,.08,.015);
    }
    public static void interact(ServerWorld w,BlockPos p,BlockState s,ComponentEntity e,PlayerEntity player,ItemStack held){
        var kind=((ComponentBlock)s.getBlock()).kind;
        if(player.isSneaking()){w.setBlockState(p,s.cycle(ComponentBlock.ENABLED),Block.NOTIFY_ALL);player.sendMessage(Text.translatable(s.get(ComponentBlock.ENABLED)?"message.electron.source_off":"message.electron.source_on"),true);return;}
        boolean coal=kind==ComponentBlock.Kind.THERMAL&&(held.isOf(Items.COAL)||held.isOf(Items.CHARCOAL));
        boolean nuclear=kind==ComponentBlock.Kind.NUCLEAR&&held.isOf(Foundry.FUEL_ROD);
        if(coal&&e.fuelEnergy<=184000){e.fuelEnergy+=16000;if(!player.isCreative())held.decrement(1);e.markDirty();}
        else if(nuclear&&e.fuelEnergy<=1e-8&&!e.fuelLoaded){e.fuelEnergy=200000;e.fuelLoaded=true;if(!player.isCreative())held.decrement(1);e.markDirty();}
        else if(kind==ComponentBlock.Kind.NUCLEAR&&e.fuelLoaded&&e.fuelEnergy<=1e-8){var spent=new ItemStack(Foundry.SPENT_ROD);if(!player.getInventory().insertStack(spent))player.dropItem(spent,false);e.fuelLoaded=false;e.markDirty();}
        String reason=requirement(w,p,kind);if(reason.equals("ready")&&(kind==ComponentBlock.Kind.THERMAL||kind==ComponentBlock.Kind.NUCLEAR)&&e.fuelEnergy<=1e-8)reason="fuel";
        player.sendMessage(Text.translatable("message.electron.plant_status",Text.translatable("plant.electron."+reason)),true);
    }
    private PowerPlant(){}
}
