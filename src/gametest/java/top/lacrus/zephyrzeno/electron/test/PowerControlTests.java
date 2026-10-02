package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

public final class PowerControlTests implements FabricGameTest {
    private static void floor(TestContext t){var s=CircuitState.get(t.getWorld());for(int x=0;x<10;x++)for(int z=0;z<18;z++){var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p,Blocks.STONE.getDefaultState());var b=new Bridge(UUID.randomUUID(),p,Direction.UP,"minecraft:stone");s.bridges.put(b.id(),b);}}
    private static BlockPos device(TestContext t,int x,int z,ComponentBlock block){var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));return p;}
    private static ComponentEntity entity(TestContext t,BlockPos p){return (ComponentEntity)t.getWorld().getBlockEntity(p);}
    private static Wire wire(TestContext t,BlockPos a,int ap,BlockPos b,int bp){var s=CircuitState.get(t.getWorld());var ends=s.endpoints();var start=ends.get(node(a,ap));var end=ends.get(node(b,bp));var path=s.connectionRoute(t.getWorld(),start,end,0);s.validateCapacity(path);var w=new Wire(UUID.randomUUID(),start.id(),end.id(),s.wires.size()%8,path,s.lengthUnits(path,start,end),true);s.wires.put(w.id(),w);s.changed();return w;}
    @GameTest(templateName="electron:components_test",tickLimit=60)
    public void fixedConvertersAcceptInputRangesAndPersistStoredEnergy(TestContext t){
        floor(t);var modules=List.of(Foundry.BOOST_12,Foundry.BOOST,Foundry.BUCK_3V3,Foundry.BUCK,Foundry.BUCK_12);List<BlockPos> converters=new ArrayList<>();
        for(int i=0;i<modules.size();i++){int z=2+i*3;var m=modules.get(i);var source=device(t,1,z,i<2?Foundry.CRANK:Foundry.GENERATOR);var module=device(t,4,z,m);var load=device(t,7,z,Foundry.RESISTOR_10000);entity(t,source).energy=100;wire(t,source,0,module,0);wire(t,source,1,module,1);wire(t,module,2,load,0);wire(t,module,3,load,1);converters.add(module);}
        t.runAtTick(35,()->{for(int i=0;i<modules.size();i++){var e=entity(t,converters.get(i));t.assertTrue(Math.abs(e.voltage-modules.get(i).resistance)<.1,"Fixed output regulated for "+modules.get(i).resistance+": "+e.voltage);t.assertTrue(e.converterEnergy>0,"Energy came from actual input");var saved=new ComponentEntity(e.getPos(),e.getCachedState());saved.read(e.createNbt(t.getWorld().getRegistryManager()),t.getWorld().getRegistryManager());t.assertTrue(Math.abs(saved.converterEnergy-e.converterEnergy)<1e-9,"Buffer survives world reload");}t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=70)
    public void mosfetControlsLampWithoutPuttingLoadCurrentThroughControlSource(TestContext t){
        floor(t);var power=device(t,1,3,Foundry.GENERATOR);var control=device(t,1,9,Foundry.CRANK);var m=device(t,4,6,Foundry.MOSFET);var lamp=device(t,7,3,Foundry.LAMP);entity(t,power).energy=100;
        wire(t,power,0,lamp,0);wire(t,lamp,1,m,1);wire(t,m,2,power,1);wire(t,control,0,m,0);wire(t,control,1,m,2);
        t.runAtTick(8,()->{t.assertTrue(entity(t,lamp).current<1e-6,"Gate low keeps lamp off");entity(t,control).energy=100;});
        t.runAtTick(20,()->{t.assertTrue(entity(t,lamp).current>.09,"Separate power supply lights lamp");t.assertTrue(Math.abs(entity(t,control).current)<.00001,"Gate uses microamps rather than load current");t.assertTrue(t.getWorld().getBlockState(lamp).get(ComponentBlock.LEVEL)>10,"Lamp brightness follows load power");entity(t,control).energy=0;});
        t.runAtTick(32,()->{t.assertTrue(entity(t,lamp).current<1e-6,"Removing control turns lamp off");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=65)
    public void relaySeparatesCoilAndLoadReferences(TestContext t){
        floor(t);var power=device(t,1,3,Foundry.GENERATOR);var control=device(t,1,9,Foundry.CRANK);var relay=device(t,4,6,Foundry.RELAY);var lamp=device(t,7,3,Foundry.LAMP);entity(t,power).energy=100;entity(t,control).energy=100;
        wire(t,power,0,relay,2);wire(t,relay,3,lamp,0);wire(t,lamp,1,power,1);wire(t,control,0,relay,0);wire(t,control,1,relay,1);
        t.runAtTick(18,()->{var s=CircuitState.get(t.getWorld());t.assertTrue(entity(t,relay).contactClosed,"Coil closes relay");t.assertTrue(entity(t,lamp).current>.09,"Isolated contacts carry lamp current");t.assertFalse(s.readings.commonReference(node(relay,0),node(relay,2)),"Coil never electrically joins the power circuit");entity(t,control).energy=0;});
        t.runAtTick(34,()->{t.assertFalse(entity(t,relay).contactClosed,"Relay releases after coil power is removed");t.assertTrue(entity(t,lamp).current<1e-6,"Open contacts disconnect lamp");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=100)
    public void distributionStartsMotorAndBreakerTripsStall(TestContext t){
        floor(t);var power=device(t,1,3,Foundry.GENERATOR);var breaker=device(t,4,3,Foundry.BREAKER);var bus=device(t,4,7,Foundry.DISTRIBUTION);var motor=device(t,7,7,Foundry.MOTOR);var lamp=device(t,7,12,Foundry.LAMP);entity(t,power).energy=300;
        wire(t,power,0,breaker,0);wire(t,breaker,1,bus,0);wire(t,power,1,bus,4);wire(t,bus,1,motor,0);wire(t,bus,5,motor,1);wire(t,bus,2,lamp,0);wire(t,bus,6,lamp,1);
        t.runAtTick(30,()->{t.assertTrue(entity(t,motor).omega>150,"Motor starts and accelerates");t.assertTrue(t.getWorld().getBlockState(breaker).get(ComponentBlock.ENABLED),"Normal startup does not trip breaker");t.assertTrue(entity(t,lamp).current>.07,"Second parallel load remains lit");entity(t,motor).loadMode=2;});
        t.runAtTick(75,()->{t.assertFalse(t.getWorld().getBlockState(breaker).get(ComponentBlock.ENABLED),"Sustained locked rotor trips breaker");t.assertTrue(Math.abs(entity(t,motor).current)<1e-6,"Breaker cuts fault current");t.assertFalse(t.getWorld().getBlockState(motor).get(ComponentBlock.BROKEN),"Protection acts before damage");t.complete();});
    }
    @GameTest(templateName="electron:components_test",tickLimit=35)
    public void liveBatteryValuesRequireMeterInSurvival(TestContext t){
        floor(t);var p=device(t,2,3,Foundry.BATTERY);entity(t,p).energy=37.125;var item=new ItemStack(Foundry.BATTERY);item.set(Foundry.BATTERY_ENERGY,37.125);
        List<net.minecraft.text.Text> lines=new ArrayList<>();((ComponentItem)item.getItem()).appendTooltip(item,Item.TooltipContext.DEFAULT,lines,net.minecraft.item.tooltip.TooltipType.BASIC);
        t.assertFalse(item.getItem().isItemBarVisible(item),"Survival battery inventory hides charge bar");t.assertFalse(lines.stream().anyMatch(v->v.getString().contains("37.13")),"Tooltip never exposes the live stored charge");
        var meter=new ItemStack(Foundry.METER);var data=CircuitToolItem.data(meter);data.putString("dimension",t.getWorld().getRegistryKey().getValue().toString());data.putLong("meter_device",p.asLong());CircuitToolItem.data(meter,data);
        t.assertTrue(MeterService.reading(t.getWorld(),meter).stream().anyMatch(v->v.getString().contains("37.13")),"Selected meter reads actual battery energy");t.getWorld().setBlockState(p,Blocks.AIR.getDefaultState());t.assertTrue(MeterService.reading(t.getWorld(),meter).getFirst().getContent() instanceof net.minecraft.text.TranslatableTextContent c&&c.getKey().equals("meter.electron.unavailable"),"Removed target clears stale values");t.complete();
    }
    @GameTest(templateName="electron:components_test",tickLimit=25)
    public void snapshotsRoundTripMoreThan128WiresAndCableMaterial(TestContext t){
        var s=new CircuitState();var p=t.getAbsolutePos(new BlockPos(2,2,2));t.getWorld().setBlockState(p,Blocks.STONE.getDefaultState());
        for(int i=0;i<130;i++){var q=p.add(0,0,100+i);var w=new Wire(UUID.randomUUID(),"a"+i,"b"+i,i%8,List.of(new SurfacePoint(q,Direction.UP,1,1,0),new SurfacePoint(q,Direction.UP,3,1,0)),2,i%2==0);s.wires.put(w.id(),w);}
        var player=t.createMockCreativeServerPlayerInWorld();var stack=CircuitToolItem.spool(512,0,true);CircuitInteractions.wire(t.getWorld(),player,stack,s,new SurfacePoint(p,Direction.UP,1,7,0),p.toCenterPos());CircuitInteractions.wire(t.getWorld(),player,stack,s,new SurfacePoint(p,Direction.UP,15,7,0),p.toCenterPos());t.assertEquals(s.wires.size(),131,"Interactive wire can exceed old 128-run cap");
        var chunks=CircuitSnapshots.split(s.snapshot());t.assertTrue(chunks.size()>=3,"Large snapshot uses bounded chunks");var receiver=new CircuitSnapshots.Receiver();net.minecraft.nbt.NbtCompound whole=null;for(int i=0;i<chunks.size();i++){var result=receiver.accept(chunks.get(i));if(i+1<chunks.size())t.assertTrue(result.isEmpty(),"Partial snapshot stays invisible");else whole=result.orElseThrow();}
        var loaded=CircuitState.read(whole);t.assertEquals(loaded.wires,s.wires,"Wire paths and cable identity survive batched synchronization");var cable=loaded.wires.values().stream().filter(Wire::cable).findFirst().orElseThrow();t.assertEquals(cable.rating(),5d,"Cable keeps power rating");receiver.accept(chunks.getFirst());t.assertTrue(receiver.accept(chunks.getLast()).isEmpty(),"Missing middle packet is never applied");t.complete();
    }
}
