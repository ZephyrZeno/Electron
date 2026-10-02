package top.lacrus.zephyrzeno.electron.test;
import top.lacrus.zephyrzeno.electron.item.CircuitToolItem;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class SurfaceAndTowerTests implements FabricGameTest {
    private static BlockPos place(TestContext t,int x,int y,int z,Block block){var p=t.getAbsolutePos(new BlockPos(x,y,z));t.getWorld().setBlockState(p,block instanceof ComponentBlock?block.getDefaultState().with(ComponentBlock.FACING,Direction.UP):block.getDefaultState());return p;}
    private static BlockPos tower(TestContext t,int x,int z){place(t,x,1,z,Blocks.SMOOTH_STONE);for(int y=2;y<5;y++)place(t,x,y,z,Foundry.TOWER_SECTION);return place(t,x,5,z,Foundry.TOWER_TERMINAL);}
    private static Wire link(TestContext t,BlockPos a,int ap,BlockPos b,int bp,boolean cable){
        var s=CircuitState.get(t.getWorld());var start=s.endpoints().get(node(a,ap));var end=s.endpoints().get(node(b,bp));boolean overhead=OverheadCable.connection(s,start,end,cable);
        var path=s.connectionRoute(t.getWorld(),start,end,0,cable);var w=new Wire(UUID.randomUUID(),start.id(),end.id(),0,path,overhead?OverheadCable.units(s,path):s.lengthUnits(path,start,end),cable,overhead);s.wires.put(w.id(),w);s.changed();return w;
    }
    @GameTest(templateName="electron:components_test",tickLimit=40)
    public void raisedComponentsOnStoneBenchFormARealCircuit(TestContext t){
        for(int x=0;x<9;x++)for(int z=0;z<9;z++)place(t,x,1,z,Blocks.SMOOTH_STONE);
        var source=place(t,1,2,2,Foundry.CRANK);var resistor=place(t,4,2,2,Foundry.RESISTOR_220);var led=place(t,7,2,5,Foundry.LED);
        ((ComponentEntity)t.getWorld().getBlockEntity(source)).energy=100;
        link(t,source,0,resistor,0,false);link(t,resistor,1,led,0,false);link(t,led,1,source,1,false);
        var s=CircuitState.get(t.getWorld());t.assertTrue(s.wires.values().stream().flatMap(w->w.path().stream()).anyMatch(p->p.host().getY()==source.getY()-1),"Wire rests on the tabletop between raised boards");
        t.runAtTick(15,()->{t.assertTrue(((ComponentEntity)t.getWorld().getBlockEntity(led)).current>.01,"Raised LED works through the real surface route");t.assertTrue(t.getWorld().getBlockState(source.down()).isOf(Blocks.SMOOTH_STONE),"Workbench remains under the component");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=60)
    public void overheadCarriesPowerAndDetachesWhenTowerIsBroken(TestContext t){
        for(int x=0;x<10;x++)for(int z=0;z<10;z++)place(t,x,1,z,Blocks.SMOOTH_STONE);
        var a=tower(t,2,5);var b=tower(t,7,5);var source=place(t,1,2,2,Foundry.GENERATOR);var lamp=place(t,7,2,8,Foundry.LAMP);
        ((ComponentEntity)t.getWorld().getBlockEntity(source)).energy=100;
        var forward=link(t,a,0,b,0,true);link(t,a,4,b,4,true);
        link(t,source,0,a,1,true);link(t,source,1,a,5,true);link(t,b,1,lamp,0,true);link(t,b,5,lamp,1,true);
        var s=CircuitState.get(t.getWorld());t.assertTrue(forward.overhead(),"Only cable between tower terminals is suspended");
        t.assertEquals(CircuitState.read(s.snapshot()).wires,s.wires,"Suspension mode and paid length survive saving");
        t.runAtTick(15,()->{t.assertTrue(((ComponentEntity)t.getWorld().getBlockEntity(lamp)).current>.07,"Power crosses the two suspended supply and return cables");t.getWorld().setBlockState(a.down(2),Blocks.AIR.getDefaultState());});
        t.runAtTick(45,()->{t.assertFalse(s.wires.containsKey(forward.id()),"Broken tower removes suspended span");t.assertTrue(((ComponentEntity)t.getWorld().getBlockEntity(lamp)).current<.001,"Broken span cuts load power");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=35)
    public void suspendedCableRefusesObstaclesAndDebitsSurvivalSpool(TestContext t){
        var a=tower(t,2,5);var b=tower(t,7,5);var s=CircuitState.get(t.getWorld());var start=s.endpoints().get(node(a,0));var end=s.endpoints().get(node(b,0));
        var curve=OverheadCable.curve(s.position(start.point()),s.position(end.point()));var obstacle=BlockPos.ofFloored(curve.get(curve.size()/2));
        var player=FoundryRegressionTests.survivalPlayer(t);var spool=CircuitToolItem.spool(512,0,true);
        CircuitInteractions.wire(t.getWorld(),player,spool,s,start.point(),s.position(start.point()));
        t.getWorld().setBlockState(obstacle,Blocks.STONE.getDefaultState());boolean refused=false;
        try{CircuitInteractions.wire(t.getWorld(),player,spool,s,end.point(),s.position(end.point()));}catch(CircuitError error){refused=true;}
        t.assertTrue(refused,"Obstacle intersects the sagging curve");t.assertEquals(CircuitToolItem.units(spool),512,"Failed connection costs nothing");
        t.getWorld().setBlockState(obstacle,Blocks.AIR.getDefaultState());
        CircuitInteractions.wire(t.getWorld(),player,spool,s,end.point(),s.position(end.point()));
        var wire=s.wires.values().stream().filter(w->w.overhead()&&w.a().equals(start.id())).findFirst().orElseThrow();
        t.assertEquals(CircuitToolItem.units(spool)+wire.units(),512,"Suspended length debited exactly once");t.assertTrue(wire.units()>80,"Sag consumes more than the five-block chord");
        t.getWorld().setBlockState(b.down(2),Blocks.AIR.getDefaultState());
        boolean missingTower=false;try{OverheadCable.route(t.getWorld(),s,start,end,0);}catch(CircuitError error){missingTower=true;}
        t.assertTrue(missingTower,"An incomplete tower cannot support a new cable");t.complete();
    }
}
