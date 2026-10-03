package top.lacrus.zephyrzeno.electron.multiblock;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.*;

/** Retains only paid block states, never copies inventories or recreates materials for free. */
public final class AssemblyRepair {
    public static void remember(ServerWorld w,BlockPos p,ComponentEntity core){
        for(var offset:core.assemblyCells){
            var q=p.add(offset);if(!w.isChunkLoaded(q))continue;
            if(w.getBlockEntity(q) instanceof AssemblyPartEntity part&&part.origin.equals(p)&&!part.original.isAir())core.assemblyBlueprint.put(offset,part.original);
        }
        var state=w.getBlockState(p);
        if(!(state.getBlock() instanceof ComponentBlock)){core.markDirty();return;}
        for(var entry:PlantStructures.modules(p,state).entrySet()){
            var q=entry.getKey();if(w.isChunkLoaded(q)&&Assembly.original(w,q).isOf(entry.getValue()))core.assemblyBlueprint.put(q.subtract(p),Assembly.original(w,q));
        }
        if(state.isOf(Foundry.WIND))rememberAuxiliary(w,p,core,p.down(core.assemblyHeight+1),Industrial.WIND_BASE);
        if(state.isOf(Foundry.LIGHTNING))rememberAuxiliary(w,p,core,p.up(),Blocks.LIGHTNING_ROD);
        core.markDirty();
    }
    private static void rememberAuxiliary(ServerWorld w,BlockPos p,ComponentEntity core,BlockPos q,Block expected){
        if(w.isChunkLoaded(q)&&Assembly.original(w,q).isOf(expected))core.assemblyBlueprint.put(q.subtract(p),Assembly.original(w,q));
    }
    private static boolean allowed(ServerWorld w,BlockPos p,PlayerEntity player){return w.isChunkLoaded(p)&&player.canModifyBlocks()&&w.canPlayerModifyAt(player,p)&&w.getWorldBorder().contains(p);}
    private static int available(PlayerEntity p,Item item){int n=0;for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(item))n+=p.getInventory().getStack(i).getCount();return n;}
    public static String repair(ServerWorld w,BlockPos p,PlayerEntity player){
        if(!allowed(w,p,player)||!(w.getBlockEntity(p) instanceof ComponentEntity core))return "permission";
        var state=w.getBlockState(p);boolean broken=state.get(ComponentBlock.BROKEN);
        if(core.temperature>40)return "repair_hot";
        if(Assembly.formed(state))remember(w,p,core);
        for(var offset:core.assemblyCells)if(!allowed(w,p.add(offset),player))return "permission";
        var missing=new LinkedHashMap<BlockPos,BlockState>();var cost=new LinkedHashMap<Item,Integer>();
        for(var entry:core.assemblyBlueprint.entrySet()){
            var q=p.add(entry.getKey());if(!allowed(w,q,player))return "permission";
            var old=Assembly.original(w,q);if(old.isOf(entry.getValue().getBlock()))continue;
            if(!old.isAir()||w.getBlockEntity(q)!=null)return "repair_obstructed";
            var item=entry.getValue().getBlock().asItem();if(item==Items.AIR)return "repair_obstructed";
            missing.put(q,entry.getValue());cost.merge(item,1,Integer::sum);
        }
        if(broken)cost.merge(state.getBlock().asItem(),1,Integer::sum);
        if(!player.isCreative()){
            boolean enough=true;int shown=0;
            for(var entry:cost.entrySet()){int shortfall=entry.getValue()-available(player,entry.getKey());if(shortfall>0){enough=false;if(shown++<6)player.sendMessage(Text.translatable("message.electron.repair_material",shortfall,entry.getKey().getName()),false);}}
            if(!enough)return "repair_materials";
        }
        if(missing.isEmpty()&&!broken&&Assembly.formed(state))return "repair_intact";
        boolean rebuild=!Assembly.formed(state)||!missing.isEmpty();
        // All access, obstruction and material checks precede mutations.
        if(Assembly.formed(state)&&rebuild)Assembly.dismantle(w,p,null);
        core.manualAssemblyLock=true;
        for(var entry:missing.entrySet())w.setBlockState(entry.getKey(),entry.getValue(),Block.NOTIFY_ALL);
        if(broken)w.setBlockState(p,w.getBlockState(p).with(ComponentBlock.BROKEN,false).with(ComponentBlock.ENABLED,false),Block.NOTIFY_ALL);
        String status=rebuild?Assembly.form(w,p,player):"formed";
        if(!status.equals("formed")){
            for(var q:missing.keySet())w.setBlockState(q,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
            if(broken)w.setBlockState(p,w.getBlockState(p).with(ComponentBlock.BROKEN,true).with(ComponentBlock.ENABLED,state.get(ComponentBlock.ENABLED)),Block.NOTIFY_ALL);
            core.markDirty();return status;
        }
        if(!player.isCreative())for(var entry:cost.entrySet()){
            int left=entry.getValue();for(int i=0;i<player.getInventory().size()&&left>0;i++){
                var stack=player.getInventory().getStack(i);if(!stack.isOf(entry.getKey()))continue;
                int used=Math.min(left,stack.getCount());player.getInventory().removeStack(i,used);left-=used;
            }
        }
        if(broken){core.overload=0;core.energy=0;core.converterEnergy=0;}
        core.manualAssemblyLock=false;core.markDirty();player.getInventory().markDirty();
        ElectricalEffects.play(w,p,ElectricalEffects.WRENCH,.5f,1);
        return broken?"repair_controller_done":missing.isEmpty()?"formed":"repaired";
    }
    public static void write(ComponentEntity core,NbtCompound n){
        var list=new NbtList();core.assemblyBlueprint.forEach((p,state)->{var entry=new NbtCompound();entry.putLong("pos",p.asLong());entry.put("state",NbtHelper.fromBlockState(state));list.add(entry);});n.put("assembly_blueprint",list);
    }
    public static void read(ComponentEntity core,NbtCompound n){
        core.assemblyBlueprint.clear();for(var tag:n.getList("assembly_blueprint",NbtElement.COMPOUND_TYPE)){
            var entry=(NbtCompound)tag;var state=NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),entry.getCompound("state"));
            if(!state.isAir())core.assemblyBlueprint.put(BlockPos.fromLong(entry.getLong("pos")),state);
        }
    }
    private AssemblyRepair(){}
}
