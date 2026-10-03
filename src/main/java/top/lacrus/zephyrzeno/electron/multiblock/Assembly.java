package top.lacrus.zephyrzeno.electron.multiblock;

import java.util.*;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.power.PowerPlant;

public final class Assembly {
    public static final int GEOMETRY_REVISION=5;
    public static AssemblyPartBlock PART;
    public static BlockEntityType<AssemblyPartEntity> PART_ENTITY;
    private static final Set<String> CHANGING=new HashSet<>();
    public static void register(){
        PART=Registry.register(Registries.BLOCK,Electron.id("assembly_part"),new AssemblyPartBlock(AbstractBlock.Settings.create().strength(3).nonOpaque().dynamicBounds().pistonBehavior(PistonBehavior.BLOCK)));
        PART_ENTITY=Registry.register(Registries.BLOCK_ENTITY_TYPE,Electron.id("assembly_part"),FabricBlockEntityTypeBuilder.create(AssemblyPartEntity::new,PART).build());
    }
    public static boolean formed(BlockState s){return s.contains(AssemblyControllerBlock.FORMED)&&s.get(AssemblyControllerBlock.FORMED);}
    public static BlockState original(World w,BlockPos p){return w.getBlockEntity(p) instanceof AssemblyPartEntity e?e.original:w.getBlockState(p);}
    public static boolean air(World w,BlockPos p){return original(w,p).isAir();}
    public static boolean serviceSupport(World w,BlockPos p){return w.getBlockEntity(p) instanceof AssemblyPartEntity e&&e.original.isOf(Foundry.TOWER_SECTION);}
    public static boolean internalNeighbor(World w,BlockPos p,Direction face){
        if(!(w.getBlockEntity(p.offset(face)) instanceof AssemblyPartEntity part))return false;
        return part.original.isAir()&&(part.origin.equals(p)||w.getBlockEntity(p) instanceof AssemblyPartEntity host&&host.origin.equals(part.origin)&&host.original.isOf(Foundry.TOWER_SECTION));
    }
    public static int height(World w,BlockPos p){
        int h=0;while(h<64&&(original(w,p.down(h+1)).isOf(Foundry.TOWER_SECTION)||original(w,p.down(h+1)).isOf(top.lacrus.zephyrzeno.electron.industrial.Industrial.MAST)))h++;return h;
    }
    public static String form(ServerWorld w,BlockPos p,PlayerEntity player){
        var state=w.getBlockState(p);
        if(!(state.getBlock() instanceof AssemblyControllerBlock controller)||state.get(ComponentBlock.FACING)!=Direction.UP)return "upright";
        if(formed(state)){dismantle(w,p,null);return "dismantled";}
        boolean tower=state.isOf(Foundry.TOWER_TERMINAL);
        if(tower&&CircuitState.get(w).wires.values().stream().anyMatch(wire->wire.overhead()&&wire.hosts().contains(p)))return "attached";
        String reason=tower?(OverheadCable.tower(w,p)?"ready":"tower"):PowerPlant.structure(w,p,controller.kind);
        if(!reason.equals("ready"))return reason;
        int h=tower||controller.kind==ComponentBlock.Kind.WIND||controller.kind==ComponentBlock.Kind.LIGHTNING?height(w,p):0;
        if(h>0&&controller.kind!=ComponentBlock.Kind.WIND){
            double r=AssemblyGeometry.baseRadius(h);
            for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1}){
                var foot=p.add((int)Math.floor(.5+sx*r),-h-1,(int)Math.floor(.5+sz*r));
                if(!w.isChunkLoaded(foot))return "unloaded";
                if(!Block.isShapeFullCube(w.getBlockState(foot).getCollisionShape(w,foot)))return "tower_base";
            }
        }
        var model=AssemblyGeometry.model(controller.kind,h,tower,state.get(ComponentBlock.TURN));
        var replacements=new LinkedHashMap<BlockPos,BlockState>();
        var occupied=new LinkedHashSet<>(model.cells().keySet());
        top.lacrus.zephyrzeno.electron.industrial.PlantStructures.modules(p,state).keySet().forEach(q->occupied.add(q.subtract(p)));
        for(var offset:occupied){
            if(offset.equals(BlockPos.ORIGIN))continue;var q=p.add(offset);
            if(!w.isChunkLoaded(q))return "unloaded";
            if(player!=null&&(!player.canModifyBlocks()||!w.canPlayerModifyAt(player,q)||!w.getWorldBorder().contains(q)))return "permission";
            var old=w.getBlockState(q);
            if(old.getBlock() instanceof top.lacrus.zephyrzeno.electron.industrial.PlantServiceBlock||old.getBlock() instanceof top.lacrus.zephyrzeno.electron.industrial.FluidBlock||old.isOf(top.lacrus.zephyrzeno.electron.industrial.Industrial.WIND_BASE))continue;
            if(controller.kind==ComponentBlock.Kind.HYDRO&&q.equals(top.lacrus.zephyrzeno.electron.industrial.PlantStructures.at(p,state,0,0,-1))&&(old.isOf(Blocks.WATER)||old.isAir()))continue;
            if(old.isOf(Blocks.FURNACE)&&controller.kind==ComponentBlock.Kind.THERMAL)continue;
            if(old.isOf(Blocks.LIGHTNING_ROD)&&controller.kind==ComponentBlock.Kind.LIGHTNING)continue;
            if(!old.isAir()&&!old.isOf(Foundry.PLANT_CASING)&&!old.isOf(Foundry.REACTOR_CASING)&&!old.isOf(Foundry.TOWER_SECTION)&&!top.lacrus.zephyrzeno.electron.industrial.Industrial.module(old))return "space";
            if(old.hasBlockEntity()||CircuitState.get(w).hasAttachment(q))return "attached";
            replacements.put(q,old);
        }
        if(!(w.getBlockEntity(p) instanceof ComponentEntity core))return "unloaded";
        core.energy=0;core.converterEnergy=0;core.assemblyHeight=h;core.assemblyRevision=GEOMETRY_REVISION;core.assemblyCells=replacements.keySet().stream().map(q->q.subtract(p)).toList();core.markDirty();
        for(var entry:replacements.entrySet()){
            var q=entry.getKey();w.setBlockState(q,PART.getDefaultState(),Block.NOTIFY_LISTENERS);
            if(w.getBlockEntity(q) instanceof AssemblyPartEntity e){e.origin=p.toImmutable();e.original=entry.getValue();e.height=h;e.turn=state.get(ComponentBlock.TURN);e.kind=controller.kind;e.tower=tower;e.markDirty();w.updateListeners(q,e.getCachedState(),e.getCachedState(),Block.NOTIFY_LISTENERS);}
        }
        w.setBlockState(p,state.with(AssemblyControllerBlock.FORMED,true),Block.NOTIFY_ALL);
        w.updateListeners(p,state,w.getBlockState(p),Block.NOTIFY_LISTENERS);CircuitState.get(w).track(p,w.getBlockState(p));
        core.assemblyBlueprint.clear();AssemblyRepair.remember(w,p,core);
        if(controller.kind==ComponentBlock.Kind.THERMAL||controller.kind==ComponentBlock.Kind.NUCLEAR)top.lacrus.zephyrzeno.electron.industrial.PlantOperations.bind(w,core);
        ElectricalEffects.play(w,p,ElectricalEffects.FORM,.6f,1);return "formed";
    }
    /** Add newly detailed collision cells on upgrade, preserving energy, inventories and paid parts. */
    public static String refresh(ServerWorld w,BlockPos p,ComponentEntity core){
        var s=w.getBlockState(p);if(!formed(s))return "assembly_pending";
        var kind=((ComponentBlock)s.getBlock()).kind;boolean tower=s.isOf(Foundry.TOWER_TERMINAL);
        var offsets=new LinkedHashSet<>(core.assemblyCells);var additions=new ArrayList<BlockPos>();
        for(var offset:AssemblyGeometry.model(kind,core.assemblyHeight,tower,s.get(ComponentBlock.TURN)).cells().keySet()){
            if(offset.equals(BlockPos.ORIGIN)||offsets.contains(offset))continue;var q=p.add(offset);if(!w.isChunkLoaded(q))return "unloaded";var old=w.getBlockState(q);
            if(old.getBlock() instanceof top.lacrus.zephyrzeno.electron.industrial.PlantServiceBlock||old.getBlock() instanceof top.lacrus.zephyrzeno.electron.industrial.FluidBlock||old.isOf(top.lacrus.zephyrzeno.electron.industrial.Industrial.WIND_BASE))continue;
            if(kind==ComponentBlock.Kind.HYDRO&&q.equals(top.lacrus.zephyrzeno.electron.industrial.PlantStructures.at(p,s,0,0,-1)))continue;
            if(!old.isAir()||CircuitState.get(w).hasAttachment(q))return "space";additions.add(offset);
        }
        for(var offset:additions){var q=p.add(offset);var old=w.getBlockState(q);w.setBlockState(q,PART.getDefaultState(),Block.NOTIFY_LISTENERS);if(w.getBlockEntity(q) instanceof AssemblyPartEntity e){e.origin=p.toImmutable();e.original=old;e.height=core.assemblyHeight;e.turn=s.get(ComponentBlock.TURN);e.kind=kind;e.tower=tower;e.markDirty();w.updateListeners(q,e.getCachedState(),e.getCachedState(),Block.NOTIFY_LISTENERS);}offsets.add(offset);}
        core.assemblyCells=List.copyOf(offsets);core.assemblyRevision=GEOMETRY_REVISION;AssemblyRepair.remember(w,p,core);core.markDirty();return "ready";
    }
    public static void dismantle(ServerWorld w,BlockPos p,BlockPos removed){
        String key=w.getRegistryKey().getValue()+":"+p.asLong();if(!CHANGING.add(key))return;
        try{
            if(!(w.getBlockEntity(p) instanceof ComponentEntity core))return;
            AssemblyRepair.remember(w,p,core);
            if(removed!=null)core.manualAssemblyLock=true;
            var offsets=core.assemblyCells;core.assemblyCells=List.of();core.markDirty();
            var s=w.getBlockState(p);
            if(formed(s))w.setBlockState(p,s.with(AssemblyControllerBlock.FORMED,false).with(ComponentBlock.LEVEL,0),Block.NOTIFY_ALL);
            for(var offset:offsets){var q=p.add(offset);if(q.equals(removed))continue;
                // Never load a chunk from a block callback. A loaded orphan restores itself in tick().
                if(w.isChunkLoaded(q)&&w.getBlockEntity(q) instanceof AssemblyPartEntity part&&part.origin.equals(p))w.setBlockState(q,part.original,Block.NOTIFY_ALL);
            }
            core.energy=0;core.converterEnergy=0;
        }finally{CHANGING.remove(key);}
    }
    public static boolean interact(ServerWorld w,BlockPos p,PlayerEntity player){
        if(w.getBlockEntity(p) instanceof AssemblyPartEntity e)p=e.origin;
        if(!(w.getBlockState(p).getBlock() instanceof AssemblyControllerBlock))return false;
        if(!player.canModifyBlocks()||!w.isChunkLoaded(p)||!w.canPlayerModifyAt(player,p)){player.sendMessage(Text.translatable("plant.electron.permission"),true);return true;}
        if(!player.isSneaking()){
            String status=AssemblyRepair.repair(w,p,player);
            player.sendMessage(Text.translatable("message.electron.plant_status",Text.translatable("plant.electron."+status)),true);return true;
        }
        if(formed(w.getBlockState(p))){
            if(player!=null)for(var offset:((ComponentEntity)w.getBlockEntity(p)).assemblyCells){var q=p.add(offset);if(!w.isChunkLoaded(q)||!w.canPlayerModifyAt(player,q)){player.sendMessage(Text.translatable("plant.electron.permission"),true);return true;}}
        }
        if(w.getBlockEntity(p) instanceof ComponentEntity core){core.manualAssemblyLock=formed(w.getBlockState(p));core.markDirty();}
        String status=form(w,p,player);player.sendMessage(Text.translatable("message.electron.plant_status",Text.translatable("plant.electron."+status)),true);return true;
    }
    public static String integrity(ServerWorld w,BlockPos p,ComponentEntity e){
        if(!formed(w.getBlockState(p)))return "ready";
        for(var offset:e.assemblyCells){var q=p.add(offset);if(!w.isChunkLoaded(q))return "unloaded";if(!(w.getBlockEntity(q) instanceof AssemblyPartEntity part)||!part.origin.equals(p))return "casing";}
        return "ready";
    }
    private Assembly(){}
}
