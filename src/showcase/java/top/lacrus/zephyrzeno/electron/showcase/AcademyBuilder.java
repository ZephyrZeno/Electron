package top.lacrus.zephyrzeno.electron.showcase;
import top.lacrus.zephyrzeno.electron.item.CircuitToolItem;

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
    private final Set<BlockPos> liveSupplies=new HashSet<>();
    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try{world=server.getOverworld();circuit=CircuitState.get(world);if(Boolean.getBoolean("electron.academy.verify"))load();else build(server);for(int cx=-1;cx<=5;cx++)for(int cz=-1;cz<=4;cz++)world.setChunkForced(cx,cz,true);ready=true;}catch(Exception e){e.printStackTrace();server.stop(false);throw new IllegalStateException("Academy map build failed",e);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{if(ready&&++age==1200){try{verify();for(int cx=-1;cx<=5;cx++)for(int cz=-1;cz<=4;cz++)world.setChunkForced(cx,cz,false);server.save(false,true,true);report.add(Boolean.getBoolean("electron.academy.verify")?"reload_verified=true":"build_verified=true");Files.writeString(Path.of("academy-verification.txt"),String.join("\n",report)+"\n");}catch(Exception e){e.printStackTrace();throw new IllegalStateException("Academy validation failed",e);}finally{server.stop(false);}}});
    }
    private void set(int x,int y,int z,Block block){world.setBlockState(new BlockPos(x,y,z),block.getDefaultState(),Block.NOTIFY_ALL);}
    private void load() throws java.io.IOException {
        for(int x=-1;x<=5;x++)for(int z=-1;z<=4;z++)world.getChunk(x,z);
        report.addAll(Files.readAllLines(Path.of("academy-verification.txt")));report.add("reopened_from_saved_world=true");
        for(var p:new BlockPos[]{new BlockPos(8,66,11),new BlockPos(26,66,15),new BlockPos(8,66,29),new BlockPos(26,66,29),new BlockPos(8,66,63)})indicators.add(p);
        motors.add(new BlockPos(8,66,58));
        indicators.add(new BlockPos(53,66,3));motors.add(new BlockPos(50,66,6));
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
        towers();gridWorkshop();
        circuit.bridges.values().removeIf(b->circuit.wires.values().stream().noneMatch(w->w.path().stream().anyMatch(p->p.host().equals(b.host())&&p.layer()==1)));
        if(circuit.bridges.size()>CircuitState.MAX_BRIDGES)throw new IllegalStateException("Map exceeded normal bridge budget");
        circuit.changed();world.getPersistentStateManager().save();report.add("stations=9");report.add("devices="+circuit.devices.size());report.add("wires="+circuit.wires.size());report.add("bridges="+circuit.bridges.size());
    }
    private BlockPos device(BlockPos base,int x,int z,ComponentBlock block){var p=base.add(x,1,z);world.setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP),Block.NOTIFY_ALL);circuit.track(p,world.getBlockState(p));var bridge=new Bridge(UUID.randomUUID(),p,Direction.UP,net.minecraft.registry.Registries.BLOCK.getId(block).toString());circuit.bridges.put(bridge.id(),bridge);return p;}
    private ComponentEntity e(BlockPos p){return (ComponentEntity)world.getBlockEntity(p);}
    private BlockPos source(BlockPos base,int x,int z,boolean large,double energy){
        if(large&&energy>0){
            var p=device(base,x,z,Foundry.SUBSTATION);stationStructure(p);
            var wind=windTower(new BlockPos(base.getX()-2,72,base.getZ()+z));
            wire(base,wind,0,p,0,0,true);wire(base,wind,1,p,1,1,true);liveSupplies.add(p);return p;
        }
        var p=device(base,x,z,large?Foundry.GENERATOR:Foundry.CRANK);e(p).energy=energy;e(p).markDirty();sources.add(p);return p;
    }
    private void wire(BlockPos base,BlockPos a,int ap,BlockPos b,int bp,int color,boolean cable){
        // Existing lesson loads connect to a substation's OUT terminals; its feeds use the IN terminals.
        if(liveSupplies.contains(a)&&ap<2)ap+=2;
        if(liveSupplies.contains(b)&&bp<2)bp+=2;
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
    private void towers(){
        List<BlockPos> heads=new ArrayList<>();
        for(int x:new int[]{14,48}){
            set(x,64,-4,Blocks.SMOOTH_STONE);for(int y=65;y<74;y++)set(x,y,-4,Foundry.TOWER_SECTION);
            var head=new BlockPos(x,74,-4);world.setBlockState(head,Foundry.TOWER_TERMINAL.getDefaultState().with(ComponentBlock.FACING,Direction.UP));circuit.track(head,world.getBlockState(head));heads.add(head);
        }
        for(int pin:new int[]{0,6}){
            var a=circuit.endpoints().get(node(heads.get(0),pin));var b=circuit.endpoints().get(node(heads.get(1),pin));var path=circuit.connectionRoute(world,a,b,0,true);
            var w=new Wire(UUID.randomUUID(),a.id(),b.id(),pin==0?0:1,path,OverheadCable.units(circuit,path),true,true);circuit.wires.put(w.id(),w);
        }
        sign(new BlockPos(17,65,-4),"towers","towers_1","towers_2","towers_3");
        var box=new BlockPos(19,65,-4);world.setBlockState(box,Blocks.CHEST.getDefaultState());var chest=(ChestBlockEntity)world.getBlockEntity(box);
        chest.setStack(0,new ItemStack(Foundry.TOWER_SECTION,32));chest.setStack(1,new ItemStack(Foundry.TOWER_TERMINAL,4));chest.setStack(2,CircuitToolItem.spool(2048,0,true));chest.markDirty();
    }
    private BlockPos windTower(BlockPos p){
        world.setBlockState(new BlockPos(p.getX(),64,p.getZ()),Blocks.SMOOTH_STONE.getDefaultState());
        for(int y=65;y<p.getY();y++)world.setBlockState(new BlockPos(p.getX(),y,p.getZ()),Foundry.TOWER_SECTION.getDefaultState());
        world.setBlockState(p,Foundry.WIND.getDefaultState().with(ComponentBlock.FACING,Direction.UP));circuit.track(p,world.getBlockState(p));return p;
    }
    private void stationStructure(BlockPos p){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.setBlockState(p.add(x,-1,z),Blocks.SMOOTH_STONE.getDefaultState());
        for(var d:Direction.Type.HORIZONTAL)world.setBlockState(p.offset(d),Foundry.PLANT_CASING.getDefaultState());
    }
    private void basin(BlockPos water){
        world.setBlockState(water.down(),Blocks.SMOOTH_STONE.getDefaultState());
        for(var d:Direction.Type.HORIZONTAL)if(world.isAir(water.offset(d)))world.setBlockState(water.offset(d),Blocks.GLASS.getDefaultState());
        world.setBlockState(water,Blocks.WATER.getDefaultState());
    }
    private void gridWorkshop(){
        for(int x=56;x<=80;x++)for(int z=-8;z<=74;z++){set(x,63,z,Blocks.STONE_BRICKS);set(x,64,z,Blocks.SMOOTH_QUARTZ);}
        for(int z=-5;z<=10;z++)for(int x=49;x<=55;x++)set(x,65,z,Blocks.SMOOTH_STONE);
        for(int z=-1;z<=10;z++)set(55,65,z,Blocks.SMOOTH_STONE);
        var base=new BlockPos(49,65,-5);var wind=windTower(new BlockPos(11,72,-4));
        var a=new BlockPos(14,74,-4);var b=new BlockPos(48,74,-4);
        wire(base,wind,0,a,1,0,true);wire(base,wind,1,a,5,1,true);
        var inlet=device(base,1,6,Foundry.MAINS);var station=device(base,3,1,Foundry.SUBSTATION);stationStructure(station);
        wire(base,b,1,inlet,0,0,true);wire(base,b,5,inlet,1,1,true);wire(base,inlet,2,station,0,0,true);wire(base,inlet,3,station,1,1,true);
        var lamp=device(base,4,8,Foundry.LAMP);var motor=device(base,1,11,Foundry.MOTOR);
        wire(base,station,2,lamp,0,0,true);wire(base,station,3,lamp,1,1,true);wire(base,station,2,motor,0,0,true);wire(base,station,3,motor,1,1,true);
        world.setBlockState(motor.up(),Foundry.SHAFT.getDefaultState());world.setBlockState(motor.up(2),Foundry.TURNTABLE.getDefaultState());
        var table=(top.lacrus.zephyrzeno.electron.mechanical.MechanicalEntity)world.getBlockEntity(motor.up(2));table.mounted=new ItemStack(Blocks.OAK_LOG);table.markDirty();indicators.add(lamp);motors.add(motor);
        sign(new BlockPos(57,65,1),"grid","grid_1","grid_2","grid_3");
        int index=0;
        for(var block:new ComponentBlock[]{Foundry.THERMAL,Foundry.HYDRO,Foundry.NUCLEAR}){
            var p=new BlockPos(66,block==Foundry.HYDRO?69:66,10+index*16);index++;
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=65;y<p.getY();y++)set(p.getX()+x,y,p.getZ()+z,Blocks.SMOOTH_STONE);
            world.setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));circuit.track(p,world.getBlockState(p));
            if(block==Foundry.THERMAL){for(var d:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST})world.setBlockState(p.offset(d),Foundry.PLANT_CASING.getDefaultState());world.setBlockState(p.east(),Blocks.FURNACE.getDefaultState());e(p).fuelEnergy=16000;}
            if(block==Foundry.HYDRO){for(var d:new Direction[]{Direction.NORTH,Direction.SOUTH})world.setBlockState(p.offset(d),Foundry.PLANT_CASING.getDefaultState());basin(p.east());basin(p.west(2).down(2));world.setBlockState(p.west(2),Blocks.AIR.getDefaultState());world.setBlockState(p.west(2).down(),Blocks.AIR.getDefaultState());}
            if(block==Foundry.NUCLEAR){for(var d:Direction.Type.HORIZONTAL)world.setBlockState(p.offset(d),Foundry.REACTOR_CASING.getDefaultState());basin(p.east(2));basin(p.west(2));e(p).fuelEnergy=200000;e(p).fuelLoaded=true;}
            e(p).markDirty();
        }
        var lightning=new BlockPos(66,72,60);windTower(lightning);world.setBlockState(lightning,Foundry.LIGHTNING.getDefaultState().with(ComponentBlock.FACING,Direction.UP));world.setBlockState(lightning.up(),Blocks.LIGHTNING_ROD.getDefaultState());circuit.track(lightning,world.getBlockState(lightning));
        var box=new BlockPos(60,65,3);world.setBlockState(box,Blocks.CHEST.getDefaultState());var chest=(ChestBlockEntity)world.getBlockEntity(box);int slot=0;
        for(var item:new ItemConvertible[]{Foundry.THERMAL,Foundry.HYDRO,Foundry.NUCLEAR,Foundry.WIND,Foundry.LIGHTNING,Foundry.MAINS,Foundry.SUBSTATION,Foundry.PLANT_CASING,Foundry.REACTOR_CASING,Foundry.SHAFT,Foundry.TURNTABLE,Foundry.FUEL_ROD,Items.COAL,Items.WATER_BUCKET,Blocks.LIGHTNING_ROD})chest.setStack(slot++,new ItemStack(item,item==Items.WATER_BUCKET?1:4));
        for(int color=0;color<8;color++)chest.setStack(slot++,CircuitToolItem.spool(2048,color,true));chest.markDirty();
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
        if(circuit.wires.size()!=70||circuit.devices.size()!=54)throw new IllegalStateException("Incomplete map: devices="+circuit.devices.size()+", wires="+circuit.wires.size());
        for(var p:circuit.devices.keySet()){
            if(world.getBlockState(p).isOf(Foundry.TOWER_TERMINAL)){if(!OverheadCable.tower(world,p))throw new IllegalStateException("Invalid demonstration tower");}
            else if(world.getBlockState(p).isOf(Foundry.WIND)||world.getBlockState(p).isOf(Foundry.LIGHTNING)){if(!world.getBlockState(p.down()).isOf(Foundry.TOWER_SECTION))throw new IllegalStateException("Missing plant tower: "+p);}
            else if(!world.getBlockState(p.down()).isOf(Blocks.SMOOTH_STONE))throw new IllegalStateException("Component is not on the smooth-stone bench: "+p);
        }
        if(circuit.wires.values().stream().filter(Wire::overhead).count()!=2)throw new IllegalStateException("Missing overhead example");
        report.add("components_above_bench=true");
        // Entity chunks load asynchronously; check after ticking, never discard at SERVER_STARTED.
        long droppedItems=0;for(var entity:world.iterateEntities())if(entity instanceof net.minecraft.entity.ItemEntity&&entity.isAlive())droppedItems++;
        if(droppedItems!=0)throw new IllegalStateException("Map contains "+droppedItems+" dropped item entities");
        for(var p:indicators)if(e(p).paused||e(p).current<.005||world.getBlockState(p).get(ComponentBlock.BROKEN))throw new IllegalStateException("Indicator not working at "+p+" V="+e(p).voltage+" I="+e(p).current);
        for(var p:motors)if(e(p).omega<150)throw new IllegalStateException("Motor failed to start at "+p);
        for(var p:circuit.devices.keySet()){
            var kind=((ComponentBlock)world.getBlockState(p).getBlock()).kind;
            if(kind==ComponentBlock.Kind.SUBSTATION&&(e(p).paused||e(p).voltage<23))throw new IllegalStateException("Station failed at "+p+" Vin="+e(p).inputVoltage+" Iin="+e(p).inputCurrent+" Vout="+e(p).voltage+" Iout="+e(p).current+" E="+e(p).converterEnergy+" paused="+circuit.paused);
            if(top.lacrus.zephyrzeno.electron.power.PowerPlant.source(kind)&&kind!=ComponentBlock.Kind.LIGHTNING&&(!e(p).plantReady||e(p).energy<=0))throw new IllegalStateException("Plant failed at "+p+" status="+e(p).plantStatus+" V="+e(p).voltage+" I="+e(p).current+" E="+e(p).energy);
        }
        for(var p:circuit.devices.keySet())if(world.getBlockState(p).get(ComponentBlock.BROKEN))throw new IllegalStateException("Damaged example at "+p);
        report.add("dropped_item_entities="+droppedItems);report.add("powered_indicators_checked="+indicators.size());report.add("rotating_motors_checked="+motors.size());report.add("all_examples_intact=true");report.add("world_saved=true");
    }
}
