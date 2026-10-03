package top.lacrus.zephyrzeno.electron.pcb;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import top.lacrus.zephyrzeno.electron.Electron;
public final class PcbNetworking {
    public record Action(int sync,int revision,int op,int a,int b,int c,int d) implements CustomPayload {
        public static final Id<Action> ID=new Id<>(Electron.id("pcb_action"));
        public static final PacketCodec<RegistryByteBuf,Action> CODEC=new PacketCodec<>(){
            public Action decode(RegistryByteBuf b){return new Action(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt());}
            public void encode(RegistryByteBuf b,Action a){b.writeVarInt(a.sync);b.writeVarInt(a.revision);b.writeVarInt(a.op);b.writeVarInt(a.a);b.writeVarInt(a.b);b.writeVarInt(a.c);b.writeVarInt(a.d);}
        };
        public Id<? extends CustomPayload> getId(){return ID;}
    }
    public static void register(){PayloadTypeRegistry.playC2S().register(Action.ID,Action.CODEC);ServerPlayNetworking.registerGlobalReceiver(Action.ID,(a,c)->c.server().execute(()->{if(c.player().currentScreenHandler instanceof PcbScreenHandler h&&h.syncId==a.sync)h.action(c.player(),a);}));}
    private PcbNetworking(){}
}
