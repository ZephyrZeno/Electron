package top.lacrus.zephyrzeno.electron.industrial;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;

/** Shared, saved withdrawal ledger. One source block is 1000 mB; a layer is removed atomically. */
public final class WaterBodies extends PersistentState {
    public static final int RENEWABLE_AREA=1024, SEARCH_LIMIT=32768;
    private static final Type<WaterBodies> TYPE=new Type<>(WaterBodies::new,(n,r)->read(n),null);
    private final Map<String,Long> drawn=new HashMap<>();
    private final Map<BlockPos,Body> cached=new HashMap<>();
    private final Map<BlockPos,Long> retryAfter=new HashMap<>();
    private record Body(Set<BlockPos> cells,List<BlockPos> surface,String key,boolean renewable,long until) {}
    public static WaterBodies get(ServerWorld w){return w.getPersistentStateManager().getOrCreate(TYPE,"electron_water_bodies");}
    private static boolean water(ServerWorld w,BlockPos p){return w.getBlockState(p).isOf(Blocks.WATER);}
    private Body discover(ServerWorld w,BlockPos intake){
        var old=cached.get(intake);if(old!=null&&w.getTime()<old.until&&water(w,intake))return old;
        cached.entrySet().removeIf(e->e.getValue().until<=w.getTime());
        if(!w.isChunkLoaded(intake)||!water(w,intake))return null;
        // Start at the intake column's surface. A deep ocean must not exhaust the 3D search
        // budget before we have even reached its renewable surface layer.
        var topCell=intake;while(w.isInBuildLimit(topCell.up())){if(!w.isChunkLoaded(topCell.up()))return null;if(!water(w,topCell.up()))break;topCell=topCell.up();}
        var surfaceQueue=new ArrayDeque<BlockPos>();var surfaceSeen=new HashSet<BlockPos>();var verifiedSurface=new HashSet<BlockPos>();surfaceQueue.add(topCell);surfaceSeen.add(topCell);
        while(!surfaceQueue.isEmpty()){
            var p=surfaceQueue.removeFirst();if(!w.isChunkLoaded(p))return null;if(!water(w,p))continue;verifiedSurface.add(p);
            if(verifiedSurface.size()>=RENEWABLE_AREA){var body=new Body(verifiedSurface,List.copyOf(verifiedSurface),"",true,w.getTime()+40);verifiedSurface.forEach(q->cached.put(q,body));cached.put(intake,body);return body;}
            for(var d:Direction.Type.HORIZONTAL){var q=p.offset(d);if(surfaceSeen.add(q))surfaceQueue.addLast(q);}
        }
        Set<BlockPos> cells=new HashSet<>(),seen=new HashSet<>();var queue=new ArrayDeque<BlockPos>();queue.add(intake);seen.add(intake);
        int top=intake.getY();List<BlockPos> surface=new ArrayList<>();
        while(!queue.isEmpty()){
            var p=queue.removeFirst();if(!w.isChunkLoaded(p))return null; // Never drain only the loaded half of a lake.
            if(!water(w,p))continue;cells.add(p);
            if(p.getY()>top){top=p.getY();surface.clear();}if(p.getY()==top)surface.add(p);
            // Only a verified, continuous horizontal area qualifies as an inexhaustible intake.
            if(surface.size()>=RENEWABLE_AREA){var body=new Body(cells,List.copyOf(surface),"",true,w.getTime()+40);cells.forEach(q->cached.put(q,body));return body;}
            if(cells.size()>=SEARCH_LIMIT)return null;
            for(var d:Direction.values()){var q=p.offset(d);if(w.isInBuildLimit(q)&&seen.add(q))queue.addLast(q);}
        }
        surface.sort(Comparator.comparingLong(BlockPos::asLong));long hash=1;for(var p:surface)hash=31*hash+p.asLong();
        String key=surface.getFirst().asLong()+":"+surface.size()+":"+hash;
        var body=new Body(cells,List.copyOf(surface),key,false,w.getTime()+40);cells.forEach(q->cached.put(q,body));return body;
    }
    public int withdraw(ServerWorld w,BlockPos intake,int requested){
        if(requested<=0||retryAfter.getOrDefault(intake,-1L)>w.getTime())return 0;Body body=discover(w,intake);if(body==null){retryAfter.entrySet().removeIf(e->e.getValue()<=w.getTime());retryAfter.put(intake.toImmutable(),w.getTime()+20);return 0;}
        if(body.renewable)return requested;
        // A changed bank invalidates the cached volume before any water is charged.
        if(body.surface.stream().anyMatch(p->!w.isChunkLoaded(p)||!water(w,p))){cached.clear();return 0;}
        long capacity=body.surface.size()*1000L,previous=drawn.getOrDefault(body.key,0L);
        int paid=(int)Math.min(requested,Math.max(0,capacity-previous));if(paid==0)return 0;
        long total=previous+paid;
        if(total>=capacity){
            // Suppress intermediate neighbor updates: removing cell-by-cell otherwise renews the layer.
            for(var p:body.surface)w.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
            for(var p:body.surface)w.updateNeighbors(p,Blocks.WATER);
            drawn.remove(body.key);cached.clear();
        }else drawn.put(body.key,total);
        markDirty();return paid;
    }
    public static WaterBodies read(NbtCompound n){var state=new WaterBodies();for(var value:n.getList("withdrawals",NbtElement.COMPOUND_TYPE)){var v=(NbtCompound)value;long amount=v.getLong("mb");if(amount>0&&amount<SEARCH_LIMIT*1000L)state.drawn.put(v.getString("body"),amount);}return state;}
    @Override public NbtCompound writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){var list=new NbtList();drawn.forEach((key,amount)->{var v=new NbtCompound();v.putString("body",key);v.putLong("mb",amount);list.add(v);});n.put("withdrawals",list);return n;}
}
