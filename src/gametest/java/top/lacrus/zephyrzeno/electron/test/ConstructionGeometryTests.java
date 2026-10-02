package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.world.GameMode;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class ConstructionGeometryTests implements FabricGameTest {
    @GameTest(templateName="electron:components_test",tickLimit=20)
    public void tableFacesPlayerAndDoesNotHideNeighborFaces(TestContext t){
        var world=t.getWorld();var pos=t.getAbsolutePos(new BlockPos(4,4,4));var player=t.createMockPlayer(GameMode.CREATIVE);var stack=new ItemStack(Foundry.TABLE);
        for(var facing:Direction.Type.HORIZONTAL){
            player.setYaw(facing.asRotation());var hit=new BlockHitResult(Vec3d.ofCenter(pos.down()).add(0,.5,0),Direction.UP,pos.down(),false);
            var state=Foundry.TABLE.getPlacementState(new ItemPlacementContext(player,Hand.MAIN_HAND,stack,hit));world.setBlockState(pos,state);
            t.assertEquals(state.get(AssemblyTableBlock.FACING),facing.getOpposite(),"Drawer front faces the placing player");t.assertFalse(state.isOpaque(),"Open bench does not use full opaque culling");
            t.assertFalse(Block.isShapeFullCube(state.getCollisionShape(world,pos)),"Collision follows open furniture geometry");
            for(var side:Direction.Type.HORIZONTAL){var neighbor=pos.offset(side);world.setBlockState(neighbor,Blocks.STONE.getDefaultState());t.assertTrue(Block.shouldDrawSide(Blocks.STONE.getDefaultState(),world,neighbor,side.getOpposite(),pos),"Stone side beside open bench is rendered");world.setBlockState(neighbor,Blocks.AIR.getDefaultState());}
            CircuitInteractions.rotate(world,pos,false);t.assertEquals(world.getBlockState(pos).get(AssemblyTableBlock.FACING),facing.getOpposite().rotateYClockwise(),"Wrench rotates drawer front");
        }
        var north=Foundry.TABLE.getDefaultState();var shape=north.getCollisionShape(world,pos);
        t.assertFalse(VoxelShapes.matchesAnywhere(shape,VoxelShapes.cuboid(.1,.3,.4,.15,.35,.45),BooleanBiFunction.AND),"Space beside drawers remains open");
        t.assertTrue(VoxelShapes.matchesAnywhere(shape,VoxelShapes.cuboid(.49,.4,.49,.51,.42,.51),BooleanBiFunction.AND),"Centered cabinet has collision");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=20)
    public void wiresAvoidBridgeFeetOnBothLayersAndEveryMount(TestContext t){
        var pos=t.getAbsolutePos(new BlockPos(4,4,4));t.getWorld().setBlockState(pos,Blocks.STONE.getDefaultState());for(var face:Direction.values())t.getWorld().setBlockState(pos.offset(face),Blocks.AIR.getDefaultState());
        for(var face:Direction.values())for(int layer=0;layer<2;layer++){
            var s=new CircuitState();var bridge=new Bridge(UUID.randomUUID(),pos,face,"minecraft:stone");s.bridges.put(bridge.id(),bridge);
            t.assertTrue(CircuitState.supports(t.getWorld(),pos,face),"Fixture has an exposed supporting face: "+face);
            var a=s.joinAt(t.getWorld(),new SurfacePoint(pos,face,3,1,layer));var b=s.joinAt(t.getWorld(),new SurfacePoint(pos,face,1,3,layer));var route=s.connectionRoute(t.getWorld(),a,b,layer);
            t.assertTrue(route.stream().noneMatch(BridgeGeometry::reserved),"Route turns around the bridge foot clearance");
            boolean rejected=false;try{s.joinAt(t.getWorld(),new SurfacePoint(pos,face,1,1,layer));}catch(CircuitError expected){rejected=true;}t.assertTrue(rejected,"Free nodes cannot occupy reserved feet");
            var parts=BridgeGeometry.parts(s,bridge);t.assertEquals(parts.size(),16,"Four detailed feet; no decorative parallel rails");
            for(var side:new Direction[]{a.point().uDirection(),a.point().vDirection()}){var adjacent=BridgeGeometry.parts(s,new Bridge(UUID.randomUUID(),pos.offset(side),face,"minecraft:stone"));t.assertTrue(parts.stream().noneMatch(part->adjacent.stream().anyMatch(other->part.bounds().intersects(other.bounds()))),"Bridges on adjacent blocks leave a gap between feet");}
            for(int u=1;u<16;u+=2)for(int v=1;v<16;v+=2){var p=new SurfacePoint(pos,face,u,v,layer);for(int[] step:new int[][]{{2,0},{0,2}}){if(u+step[0]>15||v+step[1]>15)continue;var segment=new Box(s.position(p),s.position(p.step(step[0],step[1]))).expand(.010);t.assertTrue(parts.stream().noneMatch(part->part.bounds().intersects(segment)),"Rendered feet also clear saved legacy traces at corners");}}
        }
        t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=20)
    public void bridgePlacementRejectsOccupiedFeetWithoutSpendingItem(TestContext t){
        var world=t.getWorld();var s=CircuitState.get(world);var pos=t.getAbsolutePos(new BlockPos(4,4,4));world.setBlockState(pos,Blocks.STONE.getDefaultState());
        s.joinAt(world,new SurfacePoint(pos,Direction.UP,1,1,0));var player=FoundryRegressionTests.survivalPlayer(t);var stack=new ItemStack(Foundry.BRIDGE,2);player.setStackInHand(Hand.MAIN_HAND,stack);
        var hit=new BlockHitResult(Vec3d.ofCenter(pos).add(0,.5,0),Direction.UP,pos,false);
        // Other concurrent test fixtures deliberately prefill entire floors with bridges.
        // Scope their budget out of this synchronous interaction, then restore before the next tick.
        var otherBridges=new LinkedHashMap<>(s.bridges);s.bridges.clear();
        try{
            var result=UseBlockCallback.EVENT.invoker().interact(player,world,Hand.MAIN_HAND,hit);t.assertEquals(result,ActionResult.FAIL,"Occupied foot refuses installation");t.assertEquals(stack.getCount(),2,"Rejected bridge costs nothing");t.assertFalse(s.hasBridge(new SurfacePoint(pos,Direction.UP,7,7,0)),"Rejection leaves no bridge");
            s.junctions.values().removeIf(j->j.point().host().equals(pos));result=UseBlockCallback.EVENT.invoker().interact(player,world,Hand.MAIN_HAND,hit);t.assertEquals(result,ActionResult.CONSUME,"Clear feet allow installation");t.assertEquals(stack.getCount(),1,"One successful bridge costs one item");
        }finally{s.bridges.putAll(otherBridges);}
        t.complete();
    }
}
