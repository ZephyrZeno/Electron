package top.lacrus.zephyrzeno.electron.showcase;

import java.util.*;
import java.nio.file.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.item.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

/** Development-only map exporter. This source set is never included in the gameplay JAR. */
public final class AcademyBuilder implements ModInitializer {
    private ServerWorld world;private CircuitState circuit;private int age;private boolean ready;
    private final List<BlockPos> indicators=new ArrayList<>(),motors=new ArrayList<>();
    private final List<String> report=new ArrayList<>();private final List<BlockPos> sources=new ArrayList<>();
    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try{world=server.getOverworld();circuit=CircuitState.get(world);if(Boolean.getBoolean("electron.academy.verify"))load();else build(server);ready=true;}catch(Exception e){e.printStackTrace();server.stop(false);throw new IllegalStateException("Academy map build failed",e);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{if(ready&&++age==100){try{verify();server.save(false,true,true);report.add(Boolean.getBoolean("electron.academy.verify")?"reload_verified=true":"build_verified=true");Files.writeString(Path.of("academy-verification.txt"),String.join("\n",report)+"\n");}catch(Exception e){e.printStackTrace();throw new IllegalStateException("Academy validation failed",e);}finally{server.stop(false);}}});
    }
    private void set(int x,int y,int z,Block block){world.setBlockState(new BlockPos(x,y,z),block.getDefaultState(),Block.NOTIFY_ALL);}
    private void load() throws java.io.IOException {
        for(int x=-1;x<=3;x++)for(int z=-1;z<=4;z++)world.getChunk(x,z);
        report.addAll(Files.readAllLines(Path.of("academy-verification.txt")));report.add("reopened_from_saved_world=true");
        for(var p:new BlockPos[]{new BlockPos(8,65,11),new BlockPos(26,65,15),new BlockPos(8,65,29),new BlockPos(26,65,29),new BlockPos(8,65,63)})indicators.add(p);
        motors.add(new BlockPos(8,65,58));
    }
    private void build(MinecraftServer server){
        if(!circuit.devices.isEmpty()||!circuit.wires.isEmpty())throw new IllegalStateException("Export requires a fresh world. Run tools/build_academy.ps1 instead of rebuilding an existing map.");
        circuit.wires.clear();circuit.junctions.clear();circuit.bridges.clear();circuit.cores.clear();circuit.devices.clear();circuit.wireHeat.clear();
        server.setDefaultGameMode(GameMode.SURVIVAL);server.setDifficulty(Difficulty.PEACEFUL,true);
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,server);world.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,server);world.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,server);world.getGameRules().get(GameRules.KEEP_INVENTORY).set(true,server);world.getGameRules().get(GameRules.DO_FIRE_TICK).set(false,server);world.getGameRules().get(GameRules.SPAWN_RADIUS).set(0,server);world.setTimeOfDay(6000);
        for(int x=-4;x<=55;x++)for(int z=-8;z<=74;z++){
            for(int y=65;y<=70;y++)set(x,y,z,Blocks.AIR);
            set(x,63,z,Blocks.STONE_BRICKS);set(x,64,z,x%18==15||z%24==20?Blocks.POLISHED_DEEPSLATE:Blocks.SMOOTH_QUARTZ);
            if(x==-4||x==55||z==-8||z==74)set(x,65,z,Blocks.IRON_BARS);
            if(x%6==0&&z%12==0)set(x,64,z,Blocks.SEA_LANTERN);
        }
        world.setSpawnPos(new BlockPos(8,65,-4),0);
        sign(new BlockPos(8,65,-1),"welcome","welcome_1","welcome_2","welcome_3");chest(new BlockPos(5,65,-1),true);
        String[] names={"meter","battery","timing","mosfet","relay","conversion","distribution","fault","sandbox"};
        Block[] colors={Blocks.CYAN_CONCRETE,Blocks.GREEN_CONCRETE,Blocks.ORANGE_CONCRETE,Blocks.BLUE_CONCRETE,Blocks.PURPLE_CONCRETE,Blocks.YELLOW_CONCRETE,Blocks.RED_CONCRETE,Blocks.BLACK_CONCRETE,Blocks.LIME_CONCRETE};
        for(int i=0;i<9;i++){
            int x=(i%3)*18,z=(i/3)*24+3;var base=new BlockPos(x,65,z);
            for(int dx=0;dx<12;dx++)for(int dz=0;dz<17;dz++){set(x+dx,64,z+dz,colors[i]);set(x+dx,65,z+dz,Blocks.SMOOTH_STONE);}
            for(int dx=0;dx<12;dx++)set(x+dx,65,z+17,colors[i]);
            sign(base.add(2,1,17),names[i],i==2?"press":i==3||i==4?"switch":i==6?"load":i==7?"unpowered":i==8?"free_build":"crank","meter_help","read_book");
            chest(base.add(10,1,17),false);
            switch(i){case 0->first(base);case 1->battery(base);case 2->timing(base);case 3->mosfet(base);case 4->relay(base);case 5->conversion(base);case 6->distribution(base,false);case 7->distribution(base,true);case 8->sandbox(base);}
        }
        circuit.bridges.values().removeIf(b->circuit.wires.values().stream().noneMatch(w->w.path().stream().anyMatch(p->p.host().equals(b.host())&&p.layer()==1)));
        if(circuit.bridges.size()>CircuitState.MAX_BRIDGES)throw new IllegalStateException("Map exceeded normal bridge budget");
        circuit.changed();world.getPersistentStateManager().save();report.add("stations=9");report.add("devices="+circuit.devices.size());report.add("wires="+circuit.wires.size());report.add("bridges="+circuit.bridges.size());
    }
    private BlockPos device(BlockPos base,int x,int z,ComponentBlock block){var p=base.add(x,0,z);world.setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP),Block.NOTIFY_ALL);circuit.track(p,world.getBlockState(p));var bridge=new Bridge(UUID.randomUUID(),p,Direction.UP,net.minecraft.registry.Registries.BLOCK.getId(block).toString());circuit.bridges.put(bridge.id(),bridge);return p;}
    private ComponentEntity e(BlockPos p){return (ComponentEntity)world.getBlockEntity(p);}
    private BlockPos source(BlockPos base,int x,int z,boolean large,double energy){var p=device(base,x,z,large?Foundry.GENERATOR:Foundry.CRANK);e(p).energy=energy;e(p).markDirty();sources.add(p);return p;}
    private void wire(BlockPos base,BlockPos a,int ap,BlockPos b,int bp,int color,boolean cable){
        var start=circuit.endpoints().get(node(a,ap));var end=circuit.endpoints().get(node(b,bp));List<SurfacePoint> path;
        try{path=circuit.connectionRoute(world,start,end,0);}catch(IllegalArgumentException first){
            // Crossing supports only at existing conductors. Unused supports are removed after routing.
            Set<BlockPos> occupied=new HashSet<>();circuit.wires.values().forEach(w->w.path().forEach(p->occupied.add(p.host())));
            for(var p:occupied)if(p.getX()>=base.getX()&&p.getX()<base.getX()+12&&p.getZ()>=base.getZ()&&p.getZ()<base.getZ()+17&&!circuit.hasBridge(new SurfacePoint(p,Direction.UP,7,7,0))){
                try{circuit.validateBridgeSpace(new SurfacePoint(p,Direction.UP,7,7,0));var bridge=new Bridge(UUID.randomUUID(),p,Direction.UP,net.minecraft.registry.Registries.BLOCK.getId(world.getBlockState(p).getBlock()).toString());circuit.bridges.put(bridge.id(),bridge);}catch(IllegalArgumentException blocked){}
            }
            try{path=circuit.connectionRoute(world,start,end,0);}catch(IllegalArgumentException failure){throw new IllegalStateException("Cannot route "+a+":"+ap+" -> "+b+":"+bp,failure);}
        }
        circuit.validateCapacity(path);var w=new Wire(UUID.randomUUID(),start.id(),end.id(),color,path,circuit.lengthUnits(path,start,end),cable);circuit.wires.put(w.id(),w);circuit.changed();
    }
    private void first(BlockPos b){var s=source(b,1,3,false,100);var r=device(b,5,3,Foundry.RESISTOR_220);var l=device(b,8,8,Foundry.LED);wire(b,s,0,r,0,0,false);wire(b,r,1,l,0,2,false);wire(b,l,1,s,1,1,false);indicators.add(l);}
    private void battery(BlockPos b){var s=source(b,1,2,false,100);var m=device(b,5,2,Foundry.BATTERY);var bus=device(b,5,7,Foundry.TERMINAL_STRIP);var r=device(b,8,8,Foundry.RESISTOR_220);var l=device(b,8,12,Foundry.LED);e(m).energy=10;wire(b,s,0,m,0,0,false);wire(b,s,1,m,1,1,false);wire(b,m,2,bus,0,0,false);wire(b,m,3,bus,4,1,false);wire(b,bus,1,r,0,0,false);wire(b,r,1,l,0,2,false);wire(b,l,1,bus,5,1,false);indicators.add(l);}
    private void timing(BlockPos b){var s=source(b,1,2,false,100);var button=device(b,5,2,Foundry.BUTTON);var cap=device(b,5,7,Foundry.CAPACITOR);var r=device(b,8,7,Foundry.RESISTOR_220);var l=device(b,8,12,Foundry.LED);wire(b,s,0,button,0,0,false);wire(b,button,1,cap,0,2,false);wire(b,cap,1,s,1,1,false);wire(b,cap,0,r,0,2,false);wire(b,r,1,l,0,3,false);wire(b,l,1,cap,1,1,false);}
    private void mosfet(BlockPos b){var s=source(b,1,2,true,1000);var c=source(b,1,12,false,100);var sw=device(b,5,12,Foundry.SWITCH);var m=device(b,5,7,Foundry.MOSFET);var l=device(b,8,2,Foundry.LAMP);wire(b,s,0,l,0,0,true);wire(b,l,1,m,1,2,true);wire(b,m,2,s,1,1,true);wire(b,c,0,sw,0,3,false);wire(b,sw,1,m,0,3,false);wire(b,c,1,m,2,1,false);indicators.add(l);}
    private void relay(BlockPos b){var s=source(b,1,2,true,1000);var c=source(b,1,12,false,100);var sw=device(b,5,12,Foundry.SWITCH);var m=device(b,5,7,Foundry.RELAY);var l=device(b,8,2,Foundry.LAMP);wire(b,s,0,m,2,0,true);wire(b,m,3,l,0,2,true);wire(b,l,1,s,1,1,true);wire(b,c,0,sw,0,3,false);wire(b,sw,1,m,0,3,false);wire(b,m,1,c,1,1,false);indicators.add(l);}
    private void conversion(BlockPos b){var s=source(b,1,2,false,100);var boost=device(b,5,2,Foundry.BOOST_12);var load=device(b,8,2,Foundry.RESISTOR_10000);wire(b,s,0,boost,0,0,true);wire(b,s,1,boost,1,1,true);wire(b,boost,2,load,0,3,false);wire(b,boost,3,load,1,1,false);var large=source(b,1,10,true,1000);var buck=device(b,5,10,Foundry.BUCK);var coil=device(b,8,10,Foundry.RELAY);wire(b,large,0,buck,0,0,true);wire(b,large,1,buck,1,1,true);wire(b,buck,2,coil,0,3,false);wire(b,buck,3,coil,1,1,false);}
    private void distribution(BlockPos b,boolean fault){var s=source(b,1,2,true,fault?0:1000);var breaker=device(b,5,2,Foundry.BREAKER);var bus=device(b,5,7,Foundry.DISTRIBUTION);var motor=device(b,8,7,Foundry.MOTOR);var lamp=device(b,8,12,Foundry.LAMP);wire(b,s,0,breaker,0,0,true);wire(b,breaker,1,bus,0,0,true);wire(b,s,1,bus,4,1,true);wire(b,bus,1,motor,0,2,true);wire(b,bus,5,motor,1,1,true);wire(b,bus,2,lamp,0,3,true);wire(b,bus,6,lamp,1,1,true);if(fault)e(motor).loadMode=2;else{motors.add(motor);indicators.add(lamp);}}
    private void sandbox(BlockPos b){world.setBlockState(b.add(2,1,3),Foundry.TABLE.getDefaultState());world.setBlockState(b.add(5,1,3),Blocks.CRAFTING_TABLE.getDefaultState());chest(b.add(8,1,3),true);}
    private void sign(BlockPos p,String...keys){world.setBlockState(p,Blocks.OAK_SIGN.getDefaultState().with(SignBlock.ROTATION,0));var sign=(SignBlockEntity)world.getBlockEntity(p);var text=new SignText().withColor(DyeColor.WHITE).withGlowing(true);for(int i=0;i<Math.min(4,keys.length);i++)text=text.withMessage(i,Text.translatable("map.electron."+keys[i]));sign.setText(text,true);sign.setText(text,false);sign.setWaxed(true);sign.markDirty();}
    private void chest(BlockPos p,boolean all){world.setBlockState(p,Blocks.CHEST.getDefaultState().with(ChestBlock.FACING,Direction.SOUTH));var chest=(ChestBlockEntity)world.getBlockEntity(p);List<ItemStack> items=new ArrayList<>();for(var item:new Item[]{Foundry.HANDBOOK,Foundry.METER,Foundry.CUTTER,Foundry.WRENCH,Foundry.MAGNIFIER})items.add(new ItemStack(item));for(int color=0;color<4;color++){items.add(CircuitToolItem.spool(4096,color,false));items.add(CircuitToolItem.spool(4096,color,true));}items.add(new ItemStack(Foundry.BRIDGE,32));items.add(new ItemStack(Foundry.TERMINAL,32));items.add(new ItemStack(Foundry.WALL_PORT,8));for(var item:all?new Item[]{Foundry.CRANK.asItem(),Foundry.RESISTOR_220.asItem(),Foundry.LED.asItem(),Foundry.BATTERY.asItem(),Foundry.MOSFET.asItem(),Foundry.RELAY.asItem(),Foundry.BOOST.asItem(),Foundry.BUCK_3V3.asItem(),Foundry.BUCK_12.asItem(),Foundry.GENERATOR.asItem(),Foundry.MOTOR.asItem()}:new Item[]{Foundry.LED.asItem(),Foundry.RESISTOR_220.asItem(),Foundry.BREAKER.asItem(),Foundry.FUSE.asItem()})items.add(new ItemStack(item,item==Foundry.BATTERY.asItem()?1:4));for(int i=0;i<Math.min(27,items.size());i++)chest.setStack(i,items.get(i));chest.markDirty();}
    private void verify(){
        if(circuit.wires.size()!=50||circuit.devices.size()!=39)throw new IllegalStateException("Incomplete map: devices="+circuit.devices.size()+", wires="+circuit.wires.size());
        // Entity chunks load asynchronously; check after ticking, never discard at SERVER_STARTED.
        long droppedItems=0;for(var entity:world.iterateEntities())if(entity instanceof net.minecraft.entity.ItemEntity&&entity.isAlive())droppedItems++;
        if(droppedItems!=0)throw new IllegalStateException("Map contains "+droppedItems+" dropped item entities");
        for(var p:indicators)if(e(p).paused||e(p).current<.005||world.getBlockState(p).get(ComponentBlock.BROKEN))throw new IllegalStateException("Indicator not working at "+p+" V="+e(p).voltage+" I="+e(p).current);
        for(var p:motors)if(e(p).omega<150)throw new IllegalStateException("Motor failed to start at "+p);
        for(var p:circuit.devices.keySet())if(world.getBlockState(p).get(ComponentBlock.BROKEN))throw new IllegalStateException("Damaged example at "+p);
        report.add("dropped_item_entities="+droppedItems);report.add("powered_indicators_checked="+indicators.size());report.add("rotating_motors_checked="+motors.size());report.add("all_examples_intact=true");report.add("world_saved=true");
    }
}
