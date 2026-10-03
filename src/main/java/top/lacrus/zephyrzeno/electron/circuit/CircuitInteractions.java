package top.lacrus.zephyrzeno.electron.circuit;
import top.lacrus.zephyrzeno.electron.item.CircuitToolItem;

import java.util.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.block.LogicGateBlock;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class CircuitInteractions {
    public static void register() {
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            ItemStack stack=player.getStackInHand(hand);Item item=stack.getItem();
            if(item==Foundry.HANDBOOK)return world.isClient?ActionResult.PASS:ActionResult.SUCCESS;
            if(!(item instanceof CircuitToolItem)&&item!=Foundry.TERMINAL&&item!=Foundry.WALL_PORT&&item!=Foundry.BRIDGE)return ActionResult.PASS;
            if(world.isClient)return ActionResult.SUCCESS;
            var server=(ServerWorld)world;var user=(ServerPlayerEntity)player;
            if(!mayEdit(server,player,hit.getBlockPos()))return fail(player,"error.electron.permission");
            CircuitState data=CircuitState.get(server);int layer=item instanceof CircuitToolItem?CircuitToolItem.data(stack).getInt("layer")&1:0;
            var point=CircuitPicking.pick(world,data,hit,layer);
            try {
                if(item instanceof CircuitToolItem tool)switch(tool.tool) {
                    case SPOOL -> {if(player.isSneaking()){var n=CircuitToolItem.data(stack);n.putInt("layer",layer^1);CircuitToolItem.data(stack,n);say(player,"message.electron.layer",layer^1);}else wire(server,user,stack,data,point,hit.getPos());}
                    case CUTTER -> cut(server,user,stack,data,point,hit.getPos(),hand==net.minecraft.util.Hand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND);
                    case METER -> {
                        var measured=point;
                        if(player.isSneaking()&&world.getBlockEntity(hit.getBlockPos()) instanceof top.lacrus.zephyrzeno.electron.multiblock.AssemblyPartEntity part){
                            var contact=data.endpoints().get(node(part.origin,0));if(contact!=null)measured=contact.point();
                        }
                        meter(server,user,stack,data,measured,hit.getPos());
                    }
                    case WRENCH -> {if(!top.lacrus.zephyrzeno.electron.multiblock.Assembly.interact(server,hit.getBlockPos(),player)){rotate(server,hit.getBlockPos(),player.isSneaking());say(player,"message.electron.rotated");}}
                }
                else install(server,user,stack,data,point);
                return ActionResult.CONSUME;
            } catch(IllegalArgumentException error){player.sendMessage(CircuitError.text(error),true);return ActionResult.FAIL;}
        });
    }
    private static boolean mayEdit(ServerWorld world,PlayerEntity p,BlockPos pos){return p.canModifyBlocks()&&world.canPlayerModifyAt(p,pos)&&world.getWorldBorder().contains(pos)&&world.isChunkLoaded(pos);}
    private static ActionResult fail(PlayerEntity p,String key){p.sendMessage(Text.translatable(key),true);return ActionResult.FAIL;}
    private static void say(PlayerEntity p,String key,Object... args){p.sendMessage(Text.translatable(key,args),true);}
    public static void rotate(ServerWorld world,BlockPos pos,boolean reverse){
        var state=world.getBlockState(pos);var data=CircuitState.get(world);
        if(state.getBlock() instanceof ComponentBlock){
            if(data.hasAttachment(pos))throw new CircuitError("error.electron.rotation_wired");
            var next=state.with(ComponentBlock.TURN,(state.get(ComponentBlock.TURN)+(reverse?3:1))&3);world.setBlockState(pos,next,Block.NOTIFY_ALL);data.track(pos,next);
        }else if(state.getBlock() instanceof LogicGateBlock){
            var direction=state.get(LogicGateBlock.FACING);var next=state.with(LogicGateBlock.FACING,reverse?direction.rotateYCounterclockwise():direction.rotateYClockwise());
            world.setBlockState(pos,next,Block.NOTIFY_ALL);world.updateNeighborsAlways(pos,state.getBlock());world.scheduleBlockTick(pos,state.getBlock(),2);
        }else if(state.getBlock() instanceof AssemblyTableBlock){
            world.setBlockState(pos,state.rotate(reverse?net.minecraft.util.BlockRotation.COUNTERCLOCKWISE_90:net.minecraft.util.BlockRotation.CLOCKWISE_90),Block.NOTIFY_ALL);
        }else throw new CircuitError("error.electron.wrench_target");
        ElectricalEffects.play(world,pos,ElectricalEffects.WRENCH,.35f,1.05f);
    }
    private static Endpoint pin(CircuitState s,SurfacePoint p,Vec3d hit){return s.contactAt(p).orElseThrow(()->new CircuitError("error.electron.select_pin"));}
    public static void wire(ServerWorld world,ServerPlayerEntity player,ItemStack stack,CircuitState s,SurfacePoint p,Vec3d hit) {
        NbtCompound n=CircuitToolItem.data(stack);String dimension=world.getRegistryKey().getValue().toString();
        if(!mayEdit(world,player,p.host()))throw new CircuitError("error.electron.edit_permission");
        for(var old:s.wires.values())if(old.path().contains(p)&&old.hosts().stream().anyMatch(pos->!mayEdit(world,player,pos)))throw new CircuitError("error.electron.tap_permission");
        if(!top.lacrus.zephyrzeno.electron.power.WiringCabinet.accessible(world,p.host()))throw new CircuitError("error.electron.cabinet_closed");
        var copy=s.wiringCopy();Endpoint end=copy.joinAt(world,p,CircuitToolItem.cable(stack));
        Endpoint start=dimension.equals(n.getString("dimension"))?copy.endpoints().get(n.getString("start")):null;
        if(start!=null&&!top.lacrus.zephyrzeno.electron.power.WiringCabinet.accessible(world,start.point().host()))throw new CircuitError("error.electron.cabinet_closed");
        int nodeCost=copy.junctions.size()-s.junctions.size();
        if(start==null){
            if(!player.isCreative()&&CircuitToolItem.units(stack)<nodeCost)throw new CircuitError("error.electron.node_material");
            s.commitWiring(copy);
            if(!player.isCreative())n.putInt("units",CircuitToolItem.units(stack)-nodeCost);
            n.putString("start",end.id());n.putString("dimension",dimension);n.remove("end");CircuitToolItem.data(stack,n);
            say(player,"message.electron.wire_start");return;
        }
        if(start.id().equals(end.id())){n.remove("start");n.remove("end");CircuitToolItem.data(stack,n);say(player,"message.electron.wire_end");return;}
        boolean cable=CircuitToolItem.cable(stack),overhead=OverheadCable.connection(copy,start,end,cable);
        var path=copy.connectionRoute(world,start,end,n.getInt("layer")&1,cable);copy.validateCapacity(path);boolean vertical=VerticalWire.connection(copy,start,end);int units=vertical?VerticalWire.units(copy,path):overhead?OverheadCable.units(copy,path):copy.lengthUnits(path,start,end);
        if(vertical)for(var cell:VerticalWire.cells(copy,path))if(!mayEdit(world,player,cell))throw new CircuitError("error.electron.path_permission");
        for(var point:path)if(!mayEdit(world,player,point.host()))throw new CircuitError("error.electron.path_permission");
        if(overhead)for(var cell:OverheadCable.cells(OverheadCable.vertices(copy,path)))if(!mayEdit(world,player,cell))throw new CircuitError("error.electron.path_permission");
        if(!player.isCreative()&&CircuitToolItem.units(stack)<units+nodeCost)throw new CircuitError("error.electron.material",units+nodeCost);
        // All geometry, permissions and material checks complete before any mutation.
        var wire=new Wire(UUID.randomUUID(),start.id(),end.id(),CircuitToolItem.color(stack),path,units,cable,overhead,vertical);copy.wires.put(wire.id(),wire);s.commitWiring(copy);
        if(!player.isCreative())n.putInt("units",CircuitToolItem.units(stack)-units-nodeCost);
        if(end.id().startsWith("c:"))n.remove("start");else n.putString("start",end.id());
        n.remove("end");CircuitToolItem.data(stack,n);CircuitNetworking.preview(player,List.of());
        ElectricalEffects.play(world,p.host(),cable?ElectricalEffects.CABLE_CONNECT:ElectricalEffects.WIRE_CONNECT,.45f,1);say(player,"message.electron.connected",units,nodeCost);
    }
    private static void install(ServerWorld world,ServerPlayerEntity player,ItemStack stack,CircuitState s,SurfacePoint point) {
        BlockPos pos=point.host();BlockState host=world.getBlockState(pos);String id=Registries.BLOCK.getId(host.getBlock()).toString();
        if(!CircuitState.supports(world,pos,point.face()))throw new CircuitError("error.electron.support");
        if(stack.isOf(Foundry.TERMINAL)) {
            if(s.junctions.size()>=CircuitState.MAX_JUNCTIONS)throw new CircuitError("error.electron.terminal_budget");
            if(!s.traceClear(point)||!s.assemblyClear(world,point))throw new CircuitError("error.electron.component_body");
            if(s.endpoints().values().stream().anyMatch(e->e.point().equals(point))||s.wires.values().stream().anyMatch(w->w.path().contains(point)))throw new CircuitError("error.electron.occupied");
            var j=new Junction(UUID.randomUUID(),point,id);s.junctions.put(j.id(),j);
        } else if(stack.isOf(Foundry.BRIDGE)) {
            if(s.bridges.size()>=CircuitState.MAX_BRIDGES||s.hasBridge(point))throw new CircuitError("error.electron.bridge_budget");
            s.validateBridgeSpace(point);
            var b=new Bridge(UUID.randomUUID(),pos,point.face(),id);s.bridges.put(b.id(),b);
        } else {
            if(host.hasBlockEntity()||host.isOf(Blocks.BEDROCK)||!Block.isShapeFullCube(host.getCollisionShape(world,pos)))throw new CircuitError("error.electron.drill_host");
            if(s.cores.size()>=CircuitState.MAX_CORES)throw new CircuitError("error.electron.core_budget");
            int slot=(point.u()>=8?1:0)|(point.v()>=8?2:0);var core=new WallCore(UUID.randomUUID(),pos,point.face(),slot,id);
            if(s.cores.values().stream().anyMatch(c->c.host().equals(pos)&&c.volume().intersects(core.volume())))throw new CircuitError("error.electron.core_overlap");
            for(var e:core.endpoints())if(s.endpoints().values().stream().anyMatch(x->x.point().equals(e.point()))||s.wires.values().stream().anyMatch(w->w.path().contains(e.point())))throw new CircuitError("error.electron.port_occupied");
            s.validateCoreCapacity(pos);s.cores.put(core.id(),core);
        }
        boolean bridge=stack.isOf(Foundry.BRIDGE);if(!player.isCreative())stack.decrement(1);s.changed();ElectricalEffects.play(world,pos,ElectricalEffects.TERMINAL,.35f,1);say(player,bridge?"message.electron.bridge_installed":"message.electron.installed");
    }
    private static void cut(ServerWorld world,ServerPlayerEntity player,ItemStack stack,CircuitState s,SurfacePoint p,Vec3d hit,EquipmentSlot slot) {
        if(player.isSneaking()) {
            var core=s.cores.values().stream().filter(c->c.host().equals(p.host())).min(Comparator.comparingDouble(c->c.endpoints().stream().filter(e->e.point().face()==p.face()).mapToDouble(e->e.point().position().squaredDistanceTo(hit)).min().orElse(100))).filter(c->c.endpoints().stream().anyMatch(e->e.point().face()==p.face()&&e.point().position().squaredDistanceTo(hit)<.13)).orElse(null);
            if(core!=null){s.cores.remove(core.id());give(player,new ItemStack(Foundry.WALL_PORT));s.changed();return;}
            var junction=s.junctions.values().stream().filter(j->j.point().host().equals(p.host())&&j.point().face()==p.face()&&s.position(j.point()).squaredDistanceTo(hit)<.025).min(Comparator.comparingDouble(j->s.position(j.point()).squaredDistanceTo(hit))).orElse(null);
            if(junction!=null){s.junctions.remove(junction.id());give(player,junction.wireNode()?CircuitToolItem.spool(1,0,junction.cable()):new ItemStack(Foundry.TERMINAL));s.changed();return;}
            var bridge=s.bridges.values().stream().filter(b->b.host().equals(p.host())&&b.face()==p.face()).findFirst().orElse(null);
            if(bridge!=null){s.bridges.remove(bridge.id());give(player,new ItemStack(Foundry.BRIDGE));s.changed();return;}
            throw new CircuitError("error.electron.no_attachment");
        }
        int layer=CircuitToolItem.data(stack).getInt("layer")&1;
        Wire selected=s.wires.values().stream().min(Comparator.comparingDouble(w->distance(s,w,p,hit))).filter(w->distance(s,w,p,hit)<.025).orElseThrow(()->new CircuitError("error.electron.no_wire",layer));
        if(selected.hosts().stream().anyMatch(pos->!mayEdit(world,player,pos))||(selected.overhead()&&OverheadCable.cells(s.overheadVertices(selected)).stream().anyMatch(pos->!mayEdit(world,player,pos))))throw new CircuitError("error.electron.cut_permission");
        if(selected.vertical()&&VerticalWire.cells(s,selected.path()).stream().anyMatch(pos->!mayEdit(world,player,pos)))throw new CircuitError("error.electron.cut_permission");
        ElectricalEffects.play(world,p.host(),ElectricalEffects.WIRE_CUT,.4f,1);s.wires.remove(selected.id());s.changed();give(player,CircuitToolItem.spool(selected.units(),selected.color(),selected.cable()));stack.damage(1,player,slot);say(player,"message.electron.recovered",selected.units());
    }
    private static double distance(CircuitState state,Wire wire,SurfacePoint target,Vec3d hit){return wire.path().stream().filter(p->p.host().equals(target.host())&&p.face()==target.face()&&p.layer()==target.layer()).mapToDouble(p->state.position(p).squaredDistanceTo(state.position(target))).min().orElse(100);}
    private static void give(ServerPlayerEntity player,ItemStack stack){if(!player.getInventory().insertStack(stack))player.dropItem(stack,false);}
    private static void meter(ServerWorld world,ServerPlayerEntity player,ItemStack stack,CircuitState s,SurfacePoint p,Vec3d hit) {
        var n=CircuitToolItem.data(stack);String dim=world.getRegistryKey().getValue().toString();
        if(player.isSneaking()){
            n.remove("start");n.remove("end");n.remove("meter_wire");n.remove("meter_device");
            if(world.getBlockEntity(p.host()) instanceof ComponentEntity)n.putLong("meter_device",p.host().asLong());
            else {var wire=s.wires.values().stream().filter(w->distance(s,w,p,hit)<.025).findFirst().orElseThrow(()->new CircuitError("error.electron.no_wire",p.layer()));n.putUuid("meter_wire",wire.id());}
        }else{Endpoint point=pin(s,p,hit);n.remove("meter_device");n.remove("meter_wire");if(!n.contains("start")||n.contains("end")||!dim.equals(n.getString("dimension"))){n.putString("start",point.id());n.remove("end");}else n.putString("end",point.id());}
        n.putString("dimension",dim);CircuitToolItem.data(stack,n);MeterService.send(player,stack);
    }
}
