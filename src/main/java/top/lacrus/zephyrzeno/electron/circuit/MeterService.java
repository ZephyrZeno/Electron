package top.lacrus.zephyrzeno.electron.circuit;

import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import top.lacrus.zephyrzeno.electron.foundry.*;

/** Only a held meter requests live electrical readings. Values are sent to its owner, never broadcast. */
public final class MeterService {
    public static void register(){ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getTicks()%4!=0)return;for(var player:server.getPlayerManager().getPlayerList()){var stack=player.getMainHandStack().isOf(Foundry.METER)?player.getMainHandStack():player.getOffHandStack();if(stack.isOf(Foundry.METER))send(player,stack);}});}
    public static List<Text> reading(ServerWorld world,ItemStack stack){
        var n=CircuitToolItem.data(stack);var s=CircuitState.get(world);List<Text> lines=new ArrayList<>();
        if(!world.getRegistryKey().getValue().toString().equals(n.getString("dimension")))return List.of(Text.translatable("meter.electron.ready"));
        if(n.contains("meter_device")){
            var p=BlockPos.fromLong(n.getLong("meter_device"));if(!world.isChunkLoaded(p)||!(world.getBlockEntity(p) instanceof ComponentEntity e))return List.of(Text.translatable("meter.electron.unavailable"));
            lines.add(world.getBlockState(p).getBlock().getName());lines.add(e.reading());
            if(!e.paused&&world.getBlockState(p).getBlock() instanceof ComponentBlock b&&b.kind==ComponentBlock.Kind.RESISTOR)try{var r=ResistanceModel.measure(s.lastBranches,CircuitParts.node(p,0),CircuitParts.node(p,1));lines.add(resistance(r));}catch(IllegalStateException unavailable){lines.add(Text.translatable("meter.electron.unavailable"));}
            return lines;
        }
        if(n.containsUuid("meter_wire")){
            var w=s.wires.get(n.getUuid("meter_wire"));if(w==null||s.paused.containsKey(w.a()))return List.of(Text.translatable("meter.electron.unavailable"));
            return List.of(Text.translatable(w.cable()?"item.electron.cable_spool":"item.electron.wire_spool"),Text.translatable("meter.electron.current",number(s.readings.current("w:"+w.id())*1000)),Text.translatable("meter.electron.voltage",number(s.readings.voltage(w.a())-s.readings.voltage(w.b()))));
        }
        var endpoints=s.endpoints();var a=endpoints.get(n.getString("start"));var b=endpoints.get(n.getString("end"));
        if(a==null)return List.of(Text.translatable("meter.electron.ready"));
        lines.add(Text.translatable("meter.electron.com",a.name()));
        if(b==null){lines.add(Text.translatable("meter.electron.second_probe"));return lines;}
        lines.add(Text.translatable("meter.electron.probe",b.name()));
        if(s.paused.containsKey(a.id())||s.paused.containsKey(b.id()))lines.add(Text.translatable("meter.electron.unavailable"));
        else if(n.getInt("meter_mode")==1)try{lines.add(resistance(ResistanceModel.measure(s.lastBranches,a.id(),b.id())));}catch(IllegalStateException unavailable){lines.add(Text.translatable("meter.electron.unavailable"));}
        else if(!s.readings.commonReference(a.id(),b.id()))lines.add(Text.translatable("message.electron.meter_reference"));
        else lines.add(Text.translatable("meter.electron.voltage",number(s.readings.voltage(b.id())-s.readings.voltage(a.id()))));
        return lines;
    }
    private static Text resistance(ResistanceModel.Reading r){return r.state().equals("value")?Text.translatable("meter.electron.resistance",number(r.ohms())):Text.translatable("meter.electron.resistance_"+r.state());}
    private static String number(double value){return String.format(Locale.ROOT,"%.3f",value);}
    public static void send(ServerPlayerEntity player,ItemStack stack){
        if(!ServerPlayNetworking.canSend(player,CircuitNetworking.Meter.ID))return;
        var n=new NbtCompound();var lines=new NbtList();for(var text:reading(player.getServerWorld(),stack))lines.add(NbtString.of(Text.Serialization.toJsonString(text,player.getRegistryManager())));n.put("lines",lines);n.putInt("mode",CircuitToolItem.data(stack).getInt("meter_mode"));ServerPlayNetworking.send(player,new CircuitNetworking.Meter(n));
    }
    private MeterService(){}
}
