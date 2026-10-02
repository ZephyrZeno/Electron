package top.lacrus.zephyrzeno.electron.circuit;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import top.lacrus.zephyrzeno.electron.Electron;

public final class CircuitNetworking {
    public record Snapshot(NbtCompound data) implements CustomPayload {
        public static final Id<Snapshot> ID=new Id<>(Electron.id("circuit_snapshot"));
        public static final PacketCodec<RegistryByteBuf,Snapshot> CODEC=PacketCodec.tuple(PacketCodecs.NBT_COMPOUND,Snapshot::data,Snapshot::new);
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Preview(NbtCompound data) implements CustomPayload {
        public static final Id<Preview> ID=new Id<>(Electron.id("wire_preview"));
        public static final PacketCodec<RegistryByteBuf,Preview> CODEC=PacketCodec.tuple(PacketCodecs.NBT_COMPOUND,Preview::data,Preview::new);
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public record Meter(NbtCompound data) implements CustomPayload {
        public static final Id<Meter> ID=new Id<>(Electron.id("meter_reading"));
        public static final PacketCodec<RegistryByteBuf,Meter> CODEC=PacketCodec.tuple(PacketCodecs.NBT_COMPOUND,Meter::data,Meter::new);
        @Override public Id<? extends CustomPayload> getId(){return ID;}
    }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(Snapshot.ID,Snapshot.CODEC);PayloadTypeRegistry.playS2C().register(Preview.ID,Preview.CODEC);PayloadTypeRegistry.playS2C().register(Meter.ID,Meter.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->send(handler.player,CircuitState.get(handler.player.getServerWorld())));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,from,to)->send(player,CircuitState.get(to)));
    }
    public static void send(ServerPlayerEntity player,CircuitState state){if(ServerPlayNetworking.canSend(player,Snapshot.ID)){var n=state.snapshot();n.putString("dimension",player.getWorld().getRegistryKey().getValue().toString());for(var part:CircuitSnapshots.split(n))ServerPlayNetworking.send(player,new Snapshot(part));}}
    public static void broadcast(ServerWorld world,CircuitState state){if(world.getPlayers().isEmpty())return;var n=state.snapshot();n.putString("dimension",world.getRegistryKey().getValue().toString());var parts=CircuitSnapshots.split(n);for(var player:world.getPlayers())if(ServerPlayNetworking.canSend(player,Snapshot.ID))for(var part:parts)ServerPlayNetworking.send(player,new Snapshot(part));}
    public static void preview(ServerPlayerEntity player,java.util.List<SurfacePoint> points){var n=new NbtCompound();var list=new net.minecraft.nbt.NbtList();points.forEach(p->list.add(p.nbt()));n.put("points",list);ServerPlayNetworking.send(player,new Preview(n));}
}
