package top.lacrus.zephyrzeno.electron.test;
import top.lacrus.zephyrzeno.electron.item.CircuitToolItem;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class FoundryRegressionTests implements FabricGameTest {
    static net.minecraft.server.network.ServerPlayerEntity survivalPlayer(TestContext t){
        var world=t.getWorld();var server=world.getServer();var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"wire-material-test");
        var data=net.minecraft.server.network.ConnectedClientData.createDefault(profile,false);
        var player=new net.minecraft.server.network.ServerPlayerEntity(server,world,profile,data.syncedOptions());
        var connection=new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);server.getPlayerManager().onPlayerConnect(connection,player,data);player.changeGameMode(GameMode.SURVIVAL);return player;
    }
    private static BlockPos surface(TestContext t){var p=t.getAbsolutePos(new BlockPos(3,2,3));t.getWorld().setBlockState(p,Blocks.STONE.getDefaultState());return p;}
    private static SurfacePoint point(BlockPos host,int u,int v,int layer){return new SurfacePoint(host,Direction.UP,u,v,layer);}
    private static Wire connect(TestContext t,CircuitState s,Endpoint a,Endpoint b,int color){var path=s.connectionRoute(t.getWorld(),a,b,0);s.validateCapacity(path);var w=new Wire(UUID.randomUUID(),a.id(),b.id(),color,path,s.lengthUnits(path,a,b));s.wires.put(w.id(),w);s.changed();return w;}
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void uniformPanelsAndPinPickingInEveryDevicePose(TestContext t){
        var world=t.getWorld();var s=CircuitState.get(world);var pos=t.getAbsolutePos(new BlockPos(3,3,3));
        var player=t.createMockPlayer(GameMode.CREATIVE);var item=new ItemStack(Foundry.RESISTOR_220);
        for(var facing:Direction.Type.HORIZONTAL){player.setYaw(facing.asRotation());
            for(var mount:new Direction[]{Direction.UP,Direction.DOWN}){
                var support=pos.offset(mount.getOpposite());var hit=new BlockHitResult(Vec3d.ofCenter(support).add(Vec3d.of(mount.getVector()).multiply(.5)),mount,support,false);
                var placement=Foundry.RESISTOR_220.getPlacementState(new ItemPlacementContext(player,Hand.MAIN_HAND,item,hit));
                t.assertEquals(placement.get(ComponentBlock.TURN),(facing.getHorizontal()+(mount==Direction.DOWN?2:0))&3,"Ceiling text faces the observer instead of facing away");
                var uv=ComponentGeometry.rotate(8,16,placement.get(ComponentBlock.TURN));
                var top=ComponentGeometry.local(mount,0,uv[0],uv[1],13).subtract(ComponentGeometry.local(mount,0,8,8,13));
                double alignment=top.dotProduct(Vec3d.of(facing.getVector()));t.assertTrue(mount==Direction.UP?alignment>0:alignment<0,"Readable label top agrees with the viewing plane");
            }
        }
        for(var block:Foundry.DEVICES.values())for(var face:Direction.values())for(int turn=0;turn<4;turn++){
            var state=block.getDefaultState().with(ComponentBlock.FACING,face).with(ComponentBlock.TURN,turn);
            world.setBlockState(pos,state);s.track(pos,state);t.assertEquals(ComponentGeometry.inset(block.kind),block.kind.large()?1:13,"Small panels stay 3/16; power enclosures use their full block depth");
            for(var pin:s.endpoints().values())if(pin.point().host().equals(pos)){
                var testFace=block.rearPowerPorts()?pin.point().face():face;
                var hit=s.position(pin.point()).add(Vec3d.of(testFace.getVector()).multiply(.12));
                var selected=CircuitPicking.pick(world,s,new BlockHitResult(hit,block.rearPowerPorts()?testFace:testFace.getOpposite(),pos,false),0);
                t.assertEquals(selected,pin.point(),"A raised part's side still selects the visible pin: "+block.kind+" "+face+" "+turn);
            }
        }
        var before=world.getBlockState(pos);CircuitInteractions.rotate(world,pos,false);
        t.assertEquals(world.getBlockState(pos).get(ComponentBlock.TURN),(before.get(ComponentBlock.TURN)+1)&3,"Wrench rotates pins and model together");
        var end=s.endpoints().get(node(pos,0));var attachment=new Junction(UUID.randomUUID(),end.point(),"electron:wire_buffer_gate",true);s.junctions.put(attachment.id(),attachment);
        var saved=world.getBlockState(pos);boolean refused=false;try{CircuitInteractions.rotate(world,pos,false);}catch(IllegalArgumentException expected){refused=true;}
        t.assertTrue(refused,"Attached device refuses rotation");t.assertEquals(world.getBlockState(pos),saved,"Refused rotation preserves state");
        s.junctions.remove(attachment.id());t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void directRoutingDetoursAndFreeNodes(TestContext t){
        var s=CircuitState.get(t.getWorld());var p=surface(t);var a=s.joinAt(t.getWorld(),point(p,1,7,0));var b=s.joinAt(t.getWorld(),point(p,15,7,0));
        var direct=s.connectionRoute(t.getWorld(),a,b,0);t.assertEquals(direct.size(),8,"Unblocked aligned endpoints use the shortest straight segment");
        var obstacle=s.joinAt(t.getWorld(),point(p,7,7,0));
        var detour=s.connectionRoute(t.getWorld(),a,b,0);t.assertFalse(detour.contains(obstacle.point()),"Aligned route detours around an unrelated node");t.assertTrue(detour.size()>8,"Detour is an actual routed path");
        var c=s.joinAt(t.getWorld(),point(p,11,15,0));var diagonal=s.connectionRoute(t.getWorld(),a,c,0);
        t.assertEquals(diagonal.getLast(),c.point(),"A freely chosen diagonal target is reachable");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void tappingAnyColorPreservesMaterialAndConnectivity(TestContext t){
        var s=CircuitState.get(t.getWorld());var p=surface(t);var a=s.joinAt(t.getWorld(),point(p,1,7,0));var b=s.joinAt(t.getWorld(),point(p,15,7,0));
        var wire=connect(t,s,a,b,2);int oldUnits=wire.units();var tap=s.joinAt(t.getWorld(),point(p,7,7,0));
        var split=s.wires.values().stream().filter(w->w.a().equals(tap.id())||w.b().equals(tap.id())).toList();
        t.assertEquals(split.size(),2,"Wire can be tapped between old endpoints");t.assertEquals(split.stream().mapToInt(Wire::units).sum(),oldUnits,"Splitting neither creates nor loses paid wire");
        t.assertTrue(split.stream().allMatch(w->w.color()==2),"Original segments retain their color");
        var c=s.joinAt(t.getWorld(),point(p,7,15,0));connect(t,s,c,tap,0);s.simulate(t.getWorld());
        t.assertTrue(s.readings.commonReference(a.id(),c.id()),"Red branch explicitly joins cyan conductor");
        var loaded=CircuitState.read(s.snapshot());t.assertEquals(loaded.wires,s.wires,"Tap routes and paid lengths persist");t.assertEquals(loaded.junctions,s.junctions,"Solder node layer persists");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void failedClickLeavesNoSplitAndChargesNothing(TestContext t){
        var s=CircuitState.get(t.getWorld());var p=surface(t);var player=survivalPlayer(t);
        var stack=CircuitToolItem.spool(128,0);player.setStackInHand(Hand.MAIN_HAND,stack);
        var first=point(p,1,1,0);CircuitInteractions.wire(t.getWorld(),player,stack,s,first,s.position(first));t.assertEquals(CircuitToolItem.units(stack),127,"First free node charges 1/16");
        var last=point(p,15,15,0);CircuitInteractions.wire(t.getWorld(),player,stack,s,last,s.position(last));
        var run=s.wires.values().stream().filter(w->w.path().contains(first)).findFirst().orElseThrow();var target=run.path().get(run.path().size()/2);
        var n=CircuitToolItem.data(stack);n.putInt("units",0);CircuitToolItem.data(stack,n);var wires=new LinkedHashMap<>(s.wires);var junctions=new LinkedHashMap<>(s.junctions);
        boolean refused=false;try{CircuitInteractions.wire(t.getWorld(),player,stack,s,target,s.position(target));}catch(IllegalArgumentException expected){refused=true;}
        t.assertTrue(refused,"Insufficient material refuses a new tap");t.assertEquals(s.wires,wires,"Rejected transaction does not split original wire");t.assertEquals(s.junctions,junctions,"Rejected transaction adds no nodes");t.assertEquals(CircuitToolItem.units(stack),0,"Rejected transaction does not consume material");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=30)
    public void automaticBridgeAndLayerSpecificTapsStayIsolated(TestContext t){
        var s=CircuitState.get(t.getWorld());var p=surface(t);var a=s.joinAt(t.getWorld(),point(p,1,7,0));var b=s.joinAt(t.getWorld(),point(p,15,7,0));var low=connect(t,s,a,b,0);
        var lowTap=s.joinAt(t.getWorld(),point(p,7,7,0));var bridge=new Bridge(UUID.randomUUID(),p,Direction.UP,"minecraft:stone");s.bridges.put(bridge.id(),bridge);
        var c=s.joinAt(t.getWorld(),point(p,7,1,0));var d=s.joinAt(t.getWorld(),point(p,7,15,0));var upper=connect(t,s,c,d,2);
        t.assertTrue(upper.path().contains(point(p,7,7,1)),"Crossing automatically uses the bridge");t.assertEquals(upper.units(),18,"Automatic overpass charges both 1/8 height changes");
        var upperTap=s.joinAt(t.getWorld(),point(p,7,7,1));t.assertFalse(upperTap.id().equals(lowTap.id()),"Clicking raised wire never shorts the lower node");
        s.simulate(t.getWorld());t.assertFalse(s.readings.commonReference(low.a(),upper.a()),"Crossing remains electrically isolated after both layers are tapped");
        t.assertEquals(CircuitState.read(s.snapshot()).junctions,s.junctions,"Layer isolation survives save/reload");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=45)
    public void fuseOpensBeforeAnUnprotectedLedBurns(TestContext t){
        var world=t.getWorld();var s=CircuitState.get(world);for(int x=0;x<8;x++)for(int z=0;z<6;z++)world.setBlockState(t.getAbsolutePos(new BlockPos(x,2,z)),Blocks.STONE.getDefaultState());
        BlockPos source=t.getAbsolutePos(new BlockPos(1,2,2)),fuse=source.east(2),led=source.east(4);
        world.setBlockState(source,Foundry.CRANK.getDefaultState().with(ComponentBlock.FACING,Direction.UP));world.setBlockState(fuse,Foundry.FUSE.getDefaultState().with(ComponentBlock.FACING,Direction.UP));world.setBlockState(led,Foundry.LED.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
        ((ComponentEntity)world.getBlockEntity(source)).energy=100;var ends=s.endpoints();connect(t,s,ends.get(node(source,0)),ends.get(node(fuse,0)),0);connect(t,s,ends.get(node(fuse,1)),ends.get(node(led,0)),0);connect(t,s,ends.get(node(led,1)),ends.get(node(source,1)),1);
        t.runAtTick(15,()->{t.assertTrue(world.getBlockState(fuse).get(ComponentBlock.BROKEN),"25 mA fuse opens under direct-LED overload");t.assertFalse(world.getBlockState(led).get(ComponentBlock.BROKEN),"Fuse trips before LED destruction");t.assertEquals(world.getBlockState(led).get(ComponentBlock.LEVEL),0,"Open fuse extinguishes LED");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=20)
    public void magnifierUsesHeadSlotAndEquips(TestContext t){
        var player=t.createMockCreativeServerPlayerInWorld();var stack=new ItemStack(Foundry.MAGNIFIER);player.setStackInHand(Hand.MAIN_HAND,stack);
        t.assertEquals(player.getPreferredEquipmentSlot(stack),EquipmentSlot.HEAD,"Magnifier is a head-slot tool");Foundry.MAGNIFIER.use(t.getWorld(),player,Hand.MAIN_HAND);
        t.assertTrue(player.getEquippedStack(EquipmentSlot.HEAD).isOf(Foundry.MAGNIFIER),"Right click equips the magnifier");t.complete();
    }
}
