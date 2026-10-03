package top.lacrus.zephyrzeno.electron.item;

import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.appliance.*;
import top.lacrus.zephyrzeno.electron.foundry.*;

public final class PowerCordItem extends Item {
    public PowerCordItem(Settings settings){super(settings);}
    @Override public ActionResult useOnBlock(ItemUsageContext c){
        if(c.getWorld().isClient)return ActionResult.SUCCESS;
        var w=(ServerWorld)c.getWorld();var player=c.getPlayer();if(player==null||!player.canModifyBlocks()||!w.canPlayerModifyAt(player,c.getBlockPos()))return ActionResult.FAIL;
        var stack=c.getStack();var n=CircuitToolItem.data(stack);var s=CircuitState.get(w);var p=c.getBlockPos();
        if(player.isSneaking()){
            var lead=s.cords.values().stream().filter(a->a.load().equals(p)||a.source().equals(p)).min(java.util.Comparator.comparingDouble(a->Math.min(a.path().getFirst().squaredDistanceTo(c.getHitPos()),a.path().getLast().squaredDistanceTo(c.getHitPos())))).orElse(null);
            if(lead!=null&&w.canPlayerModifyAt(player,lead.source())&&w.canPlayerModifyAt(player,lead.load())){
                s.cords.remove(lead.id());s.cordHeat.remove(lead.id());s.changed();if(!player.isCreative())player.giveItemStack(new ItemStack(Appliances.CORD));
                ElectricalEffects.play(w,p,ElectricalEffects.CABLE_CONNECT,.35f,.8f);
            }n.remove("cord_source");CircuitToolItem.data(stack,n);return ActionResult.SUCCESS;
        }
        try{
            String dim=w.getRegistryKey().getValue().toString();
            if(!n.contains("cord_source")||!dim.equals(n.getString("cord_dimension"))){
                if(Appliances.outlets(w.getBlockState(p))==0)throw new CircuitError("error.electron.cord_source");
                n.putLong("cord_source",p.asLong());n.putString("cord_dimension",dim);CircuitToolItem.data(stack,n);player.sendMessage(Text.translatable("message.electron.cord_selected"),true);return ActionResult.SUCCESS;
            }
            var source=BlockPos.fromLong(n.getLong("cord_source"));if(!w.isChunkLoaded(source)||!w.canPlayerModifyAt(player,source))throw new CircuitError("error.electron.permission");
            var lead=CordNetwork.connect(w,s,source,p);for(var at:lead.path())if(!w.canPlayerModifyAt(player,BlockPos.ofFloored(at))||!w.getWorldBorder().contains(BlockPos.ofFloored(at)))throw new CircuitError("error.electron.permission");
            s.cords.put(lead.id(),lead);s.changed();n.remove("cord_source");CircuitToolItem.data(stack,n);
            if(!player.isCreative())stack.decrement(1);ElectricalEffects.play(w,lead.path().getLast(),ElectricalEffects.CABLE_CONNECT,.45f,1);
            player.sendMessage(Text.translatable("message.electron.cord_connected"),true);return ActionResult.SUCCESS;
        }catch(IllegalArgumentException error){player.sendMessage(CircuitError.text(error),true);return ActionResult.FAIL;}
    }
}
