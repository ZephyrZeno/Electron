package top.lacrus.zephyrzeno.electron.power;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.ElectricalEffects;

public final class FaultEffects {
    public static final int ARC=0,OVERLOAD=1,MELT=2,BURST=3,TRIP=4,SHOCK=5;
    public static final Block FLASH=Registry.register(Registries.BLOCK,Electron.id("arc_flash"),new ArcFlashBlock(AbstractBlock.Settings.create().noCollision().nonOpaque().replaceable().dropsNothing().luminance(s->15).strength(-1)));
    public record Event(NbtCompound data) implements CustomPayload {
        public static final Id<Event> ID=new Id<>(Electron.id("fault_effect"));
        public static final PacketCodec<RegistryByteBuf,Event> CODEC=PacketCodec.tuple(PacketCodecs.NBT_COMPOUND,Event::data,Event::new);
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static final Map<ServerWorld,Map<Long,Long>> SOUNDS=new WeakHashMap<>();
    private static final Map<ServerWorld,long[]> BUDGET=new WeakHashMap<>();
    public static void register(){PayloadTypeRegistry.playS2C().register(Event.ID,Event.CODEC);net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s->{SOUNDS.clear();BUDGET.clear();});}
    public static NbtCompound packet(ServerWorld w,Vec3d a,Vec3d b,int kind,double power){
        var n=new NbtCompound();n.putString("dimension",w.getRegistryKey().getValue().toString());
        n.putDouble("x",a.x);n.putDouble("y",a.y);n.putDouble("z",a.z);n.putDouble("X",b.x);n.putDouble("Y",b.y);n.putDouble("Z",b.z);
        n.putInt("kind",kind);n.putFloat("strength",(float)Math.clamp(Math.log1p(Math.max(0,power))/7,.15,1));n.putLong("seed",w.random.nextLong());return n;
    }
    public static void emit(ServerWorld w,Vec3d a,Vec3d b,int kind,double power){
        emit(w,a,b,kind,power,0);
    }
    public static boolean hasArc(double voltage,double gap){return Double.isFinite(voltage)&&Math.abs(voltage)>=1000&&gap<=CableFaults.clearance(Math.abs(voltage));}
    public static void emit(ServerWorld w,Vec3d a,Vec3d b,int kind,double power,double voltage){
        if(!Double.isFinite(power)||!w.isChunkLoaded(BlockPos.ofFloored(a))||!w.isChunkLoaded(BlockPos.ofFloored(b)))return;
        var budget=BUDGET.computeIfAbsent(w,k->new long[]{-1,0});if(budget[0]!=w.getTime()){budget[0]=w.getTime();budget[1]=0;}if(budget[1]++>=32)return;
        var data=packet(w,a,b,kind,power);boolean arc=hasArc(voltage,a.distanceTo(b));data.putBoolean("arc",arc);var packet=new Event(data);
        for(var player:w.getPlayers())if(player.squaredDistanceTo(ArcGeometry.closest(player.getPos(),a,b))<96*96&&ServerPlayNetworking.canSend(player,Event.ID))ServerPlayNetworking.send(player,packet);
        // Mono Ogg + exact double coordinates: the impact, never the listener or the host-block centre.
        var position=b;long key=BlockPos.ofFloored(position).asLong();var times=SOUNDS.computeIfAbsent(w,k->new HashMap<>());times.entrySet().removeIf(e->w.getTime()-e.getValue()>40);
        if(w.getTime()-times.getOrDefault(key,-100L)>=8){times.put(key,w.getTime());var sound=switch(kind){case MELT->ElectricalEffects.WIRE_MELT;case BURST->ElectricalEffects.POP;case TRIP->ElectricalEffects.TRIP;case OVERLOAD->ElectricalEffects.SIZZLE;default->arc?ElectricalEffects.ARC:ElectricalEffects.SPARK;};ElectricalEffects.play(w,position,sound,kind==BURST?.9f:.55f,.9f+w.random.nextFloat()*.2f);}
        if(kind==MELT||kind==BURST)w.spawnParticles(ParticleTypes.SMOKE,b.x,b.y,b.z,12,.08,.08,.08,.02);
        if(power>=1)pulse(w,b);
    }
    public static void pulse(ServerWorld w,Vec3d at){
        var p=BlockPos.ofFloored(at);for(var q:List.of(p,p.up(),p.west(),p.east()))if(w.isChunkLoaded(q)&&w.getBlockState(q).isAir()&&w.getFluidState(q).isEmpty()){
            w.setBlockState(q,FLASH.getDefaultState(),Block.NOTIFY_ALL);return;
        }
    }
    private FaultEffects(){}
}
