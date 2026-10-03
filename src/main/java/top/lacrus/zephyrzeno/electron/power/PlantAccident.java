package top.lacrus.zephyrzeno.electron.power;

import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.PlantStructures;

/** Once-per-failure event. Cloud dimensions do not change the bounded physical blast radius. */
public final class PlantAccident {
    public static final int MACHINE=0,THERMAL=1,NUCLEAR=2;
    public record Event(NbtCompound data) implements CustomPayload {
        public static final Id<Event> ID=new Id<>(Electron.id("plant_accident"));
        public static final PacketCodec<RegistryByteBuf,Event> CODEC=PacketCodec.tuple(PacketCodecs.NBT_COMPOUND,Event::data,Event::new);
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    private static final Map<ServerWorld,Map<BlockPos,Long>> RECENT=new WeakHashMap<>();
    public static void register(){PayloadTypeRegistry.playS2C().register(Event.ID,Event.CODEC);net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s->RECENT.clear());}
    public static Vec3d origin(BlockPos controller,BlockState state){var k=((ComponentBlock)state.getBlock()).kind;var p=k==ComponentBlock.Kind.NUCLEAR?PlantStructures.at(controller,state,-3,0,-20):k==ComponentBlock.Kind.THERMAL?PlantStructures.at(controller,state,0,0,-4):controller;return Vec3d.ofCenter(p).add(0,k==ComponentBlock.Kind.NUCLEAR?3:k==ComponentBlock.Kind.THERMAL?1.5:.3,0);}
    public static NbtCompound packet(ServerWorld w,Vec3d origin,int kind){return packet(w,origin,kind,origin.y);}
    public static NbtCompound packet(ServerWorld w,Vec3d origin,int kind,double groundY){var n=new NbtCompound();n.putString("dimension",w.getRegistryKey().getValue().toString());n.putDouble("x",origin.x);n.putDouble("y",origin.y);n.putDouble("z",origin.z);n.putInt("kind",kind);n.putDouble("ground_y",groundY);n.putLong("seed",w.random.nextLong());return n;}
    public static boolean emit(ServerWorld w,BlockPos controller,BlockState state,int kind){var times=RECENT.computeIfAbsent(w,k->new HashMap<>());times.entrySet().removeIf(e->w.getTime()-e.getValue()>400);if(w.getTime()-times.getOrDefault(controller,-1000L)<200)return false;times.put(controller.toImmutable(),w.getTime());var at=origin(controller,state);var packet=new Event(packet(w,at,kind,controller.getY()+.12));
        for(var player:w.getPlayers())if(player.squaredDistanceTo(at)<192*192&&ServerPlayNetworking.canSend(player,Event.ID))ServerPlayNetworking.send(player,packet);
        ElectricalEffects.play(w,at,kind==NUCLEAR?ElectricalEffects.REACTOR_BURST:kind==THERMAL?ElectricalEffects.BOILER_BURST:ElectricalEffects.MACHINE_BURST,1,1);
        FaultEffects.pulse(w,at);return true;
    }
    private PlantAccident(){}
}
