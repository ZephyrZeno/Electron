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
import top.lacrus.zephyrzeno.electron.industrial.*;
import top.lacrus.zephyrzeno.electron.pcb.*;
import top.lacrus.zephyrzeno.electron.appliance.*;
import top.lacrus.zephyrzeno.electron.multiblock.Assembly;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

/** Development-only map exporter. This source set is never included in the gameplay JAR. */
public final class AcademyBuilder implements ModInitializer {
    private ServerWorld world;private CircuitState circuit;private int age;private boolean ready;
    private final List<BlockPos> indicators=new ArrayList<>(),motors=new ArrayList<>();
    private final List<String> report=new ArrayList<>();private final List<BlockPos> sources=new ArrayList<>();
    private final Set<BlockPos> liveSupplies=new HashSet<>();
    @Override public void onInitialize(){
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try{world=server.getOverworld();circuit=CircuitState.get(world);if(Boolean.getBoolean("electron.academy.verify"))load();else build(server);for(int cx=-1;cx<=8;cx++)for(int cz=-1;cz<=11;cz++)world.setChunkForced(cx,cz,true);ready=true;}catch(Exception e){e.printStackTrace();server.stop(false);throw new IllegalStateException("Academy map build failed",e);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(!ready)return;
            age++;
            try{
                DecimalCircuitLesson.tickPixels(world,age);
                if(age==300)DecimalCircuitLesson.inputs(world,2,7,true);
                if(age==450)DecimalCircuitLesson.verify(world,-5);
                if(age==500)DecimalCircuitLesson.inputs(world,9,9,false);
                if(age==650)DecimalCircuitLesson.verify(world,18);
                if(age==700)DecimalCircuitLesson.inputs(world,0,0,false);
                if(age==850){DecimalCircuitLesson.verify(world,0);report.add("decimal_input_changes_verified=true");}
                if(age==900)DecimalCircuitLesson.inputs(world,9,7,false);
                if(age==1100)arcSupply(true);
                if(age==1140){
                    boolean discharge=circuit.wires.values().stream().filter(Wire::vertical)
                            .anyMatch(w->Math.abs(circuit.readings.current("ground:"+w.id()))>=.002);
                    arcSupply(false);
                    if(!discharge)throw new IllegalStateException("The teaching arc did not conduct when switched on");
                    report.add("arc_demo_powered_test=true");
                }
                if(age==1200){
                    verify();
                    for(int cx=-1;cx<=8;cx++)for(int cz=-1;cz<=11;cz++)world.setChunkForced(cx,cz,false);
                    server.save(false,true,true);
                    report.add(Boolean.getBoolean("electron.academy.verify")?"reload_verified=true":"build_verified=true");
                    Files.writeString(Path.of("academy-verification.txt"),String.join("\n",report)+"\n");
                    server.stop(false);
                }
            }catch(Exception e){ready=false;server.stop(false);throw new IllegalStateException("Academy validation failed",e);}
        });
    }
    private void arcSupply(boolean enabled){
        var p=new BlockPos(60,65,51);
        world.setBlockState(p,world.getBlockState(p).with(ComponentBlock.ENABLED,enabled));
        circuit.changed();
    }
    private void formed(BlockPos p){String status=top.lacrus.zephyrzeno.electron.multiblock.Assembly.form(world,p,null);if(!status.equals("formed"))throw new IllegalStateException("Cannot form "+p+": "+status);}
    private void foundation(BlockPos p){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)set(p.getX()+x,64,p.getZ()+z,Blocks.SMOOTH_STONE);}
    private void set(int x,int y,int z,Block block){world.setBlockState(new BlockPos(x,y,z),block.getDefaultState(),Block.NOTIFY_ALL);}
    private void load() throws java.io.IOException {
        for(int x=-1;x<=8;x++)for(int z=-1;z<=11;z++)world.getChunk(x,z);
        report.addAll(Files.readAllLines(Path.of("academy-verification.txt")));report.add("reopened_from_saved_world=true");
        for(var p:new BlockPos[]{new BlockPos(8,66,11),new BlockPos(26,66,15),new BlockPos(8,66,29),new BlockPos(26,66,29),new BlockPos(8,66,63)})indicators.add(p);
        motors.add(new BlockPos(8,66,58));
        indicators.add(new BlockPos(80,66,14));indicators.add(new BlockPos(80,66,36));indicators.add(new BlockPos(115,66,62));
        indicators.add(new BlockPos(53,66,3));motors.add(new BlockPos(50,66,6));
        indicators.add(new BlockPos(61,66,87));indicators.add(new BlockPos(64,66,89));indicators.add(new BlockPos(122,66,106));indicators.add(new BlockPos(9,66,131));indicators.add(new BlockPos(23,66,131));
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
        towers();gridWorkshop();industrialWorkshop();processingWorkshop();pcbWorkshop();plugWorkshop();decimalWorkshop();
        circuit.bridges.values().removeIf(b->circuit.wires.values().stream().noneMatch(w->w.path().stream().anyMatch(p->p.host().equals(b.host())&&p.layer()==1)));
        if(circuit.bridges.size()>CircuitState.MAX_BRIDGES)throw new IllegalStateException("Map exceeded normal bridge budget");
        for(var pos:circuit.devices.keySet())if(world.getBlockState(pos).getBlock() instanceof top.lacrus.zephyrzeno.electron.multiblock.AssemblyControllerBlock&&!top.lacrus.zephyrzeno.electron.multiblock.Assembly.formed(world.getBlockState(pos)))throw new IllegalStateException("Construction overlapped a formed machine at "+pos);
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
        if((liveSupplies.contains(a)||world.getBlockState(a).isOf(Industrial.WIND_BASE))&&ap<2)ap+=2;
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
        for(int x:new int[]{14,46}){
            foundation(new BlockPos(x,74,-4));for(int y=65;y<74;y++)set(x,y,-4,Foundry.TOWER_SECTION);
            var head=new BlockPos(x,74,-4);world.setBlockState(head,Foundry.TOWER_TERMINAL.getDefaultState().with(ComponentBlock.FACING,Direction.UP).with(ComponentBlock.TURN,1));circuit.track(head,world.getBlockState(head));formed(head);heads.add(head);
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
        foundation(p);var base=new BlockPos(p.getX(),65,p.getZ());
        world.setBlockState(base,Industrial.WIND_BASE.getDefaultState().with(ComponentBlock.FACING,Direction.SOUTH));circuit.track(base,world.getBlockState(base));
        for(int y=66;y<p.getY();y++)world.setBlockState(new BlockPos(p.getX(),y,p.getZ()),Industrial.MAST.getDefaultState());
        world.setBlockState(p,Foundry.WIND.getDefaultState().with(ComponentBlock.FACING,Direction.UP));circuit.track(p,world.getBlockState(p));formed(p);return base;
    }
    private void stationStructure(BlockPos p){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.setBlockState(p.add(x,-1,z),Blocks.SMOOTH_STONE.getDefaultState());
        for(var d:Direction.Type.HORIZONTAL)world.setBlockState(p.offset(d),Foundry.PLANT_CASING.getDefaultState());
        formed(p);
    }
    private void basin(BlockPos water){
        world.setBlockState(water.down(),Blocks.SMOOTH_STONE.getDefaultState());
        for(var d:Direction.Type.HORIZONTAL)if(world.isAir(water.offset(d)))world.setBlockState(water.offset(d),Blocks.GLASS.getDefaultState());
        world.setBlockState(water,Blocks.WATER.getDefaultState());
    }
    private void gridWorkshop(){
        for(int x=56;x<=124;x++)for(int z=-8;z<=74;z++){set(x,63,z,Blocks.STONE_BRICKS);set(x,64,z,Blocks.SMOOTH_QUARTZ);}
        for(int z=-5;z<=10;z++)for(int x=49;x<=55;x++)set(x,65,z,Blocks.SMOOTH_STONE);
        for(int z=-1;z<=10;z++)set(55,65,z,Blocks.SMOOTH_STONE);
        var base=new BlockPos(49,65,-5);var wind=windTower(new BlockPos(6,72,-4));
        var a=new BlockPos(14,74,-4);var b=new BlockPos(46,74,-4);
        wire(base,wind,0,a,1,0,true);wire(base,wind,1,a,5,1,true);
        var inlet=device(base,1,6,Foundry.MAINS);formed(inlet);var station=device(base,3,1,Foundry.SUBSTATION);stationStructure(station);
        wire(base,b,1,inlet,0,0,true);wire(base,b,5,inlet,1,1,true);wire(base,inlet,2,station,0,0,true);wire(base,inlet,3,station,1,1,true);
        var lamp=device(base,4,8,Foundry.LAMP);var motor=device(base,1,11,Foundry.MOTOR);
        wire(base,station,2,lamp,0,0,true);wire(base,station,3,lamp,1,1,true);wire(base,station,2,motor,0,0,true);wire(base,station,3,motor,1,1,true);
        world.setBlockState(motor.up(),Foundry.SHAFT.getDefaultState());world.setBlockState(motor.up(2),Foundry.TURNTABLE.getDefaultState());
        var table=(top.lacrus.zephyrzeno.electron.mechanical.MechanicalEntity)world.getBlockEntity(motor.up(2));table.mounted=new ItemStack(Blocks.OAK_LOG);table.markDirty();indicators.add(lamp);motors.add(motor);
        sign(new BlockPos(57,65,1),"grid","grid_1","grid_2","grid_3");
        int index=0;
        for(var block:new ComponentBlock[]{Foundry.THERMAL,Foundry.HYDRO,Foundry.NUCLEAR}){
            var p=block==Foundry.NUCLEAR?new BlockPos(103,66,58):new BlockPos(68,66,block==Foundry.THERMAL?10:32);index++;
            if(block==Foundry.THERMAL||block==Foundry.NUCLEAR){
                boolean nuclear=block==Foundry.NUCLEAR;for(int x=nuclear?-16:-4;x<=(nuclear?22:4);x++)for(int z=nuclear?-36:-7;z<=1;z++)set(p.getX()+x,65,p.getZ()+z,Blocks.SMOOTH_STONE);
                world.setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
                PlantStructures.modules(p,world.getBlockState(p)).forEach((q,bk)->world.setBlockState(q,bk.getDefaultState()));
                var tank=(FluidEntity)world.getBlockEntity(PlantStructures.tankPos(p,world.getBlockState(p)));tank.change(nuclear?200000:15000);e(p).fuelEnergy=nuclear?200000:64000;e(p).fuelLoaded=nuclear;
            }else{
                for(int x=-4;x<=4;x++)for(int z=-2;z<=2;z++)set(p.getX()+x,65,p.getZ()+z,Blocks.SMOOTH_STONE);
                world.setBlockState(p,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
                for(int x=-4;x<=4;x++){set(p.getX()+x,66,p.getZ()-2,Blocks.GLASS);if(x!=0)set(p.getX()+x,66,p.getZ(),Blocks.GLASS);}
                set(p.getX()-4,66,p.getZ()-1,Blocks.GLASS);set(p.getX()+4,66,p.getZ()-1,Blocks.GLASS);
                for(int x=-3;x<=3;x++)world.setBlockState(p.add(x,0,-1),Blocks.WATER.getDefaultState().with(net.minecraft.block.FluidBlock.LEVEL,x==-3?0:x+3));
            }
            circuit.track(p,world.getBlockState(p));e(p).markDirty();formed(p);
            if(block==Foundry.THERMAL||block==Foundry.NUCLEAR){
                var intake=PlantStructures.fuelPos(p,world.getBlockState(p));var boxPos=intake.offset(block==Foundry.NUCLEAR?Direction.EAST:Direction.WEST);
                world.setBlockState(boxPos.down(),Blocks.SMOOTH_STONE.getDefaultState());world.setBlockState(boxPos,Blocks.CHEST.getDefaultState());
                var fuelChest=(ChestBlockEntity)world.getBlockEntity(boxPos);fuelChest.setStack(0,new ItemStack(block==Foundry.NUCLEAR?Foundry.FUEL_ROD:Items.COAL,block==Foundry.NUCLEAR?4:32));fuelChest.markDirty();
                var output=PlantStructures.outputPos(p,world.getBlockState(p)).offset(block==Foundry.NUCLEAR?Direction.EAST:Direction.WEST);
                world.setBlockState(output.down(),Blocks.SMOOTH_STONE.getDefaultState());world.setBlockState(output,Blocks.CHEST.getDefaultState());
            }
            for(int x=-7;x<=14;x++)for(int z=1;z<=7;z++)world.setBlockState(p.add(x,-1,z),Blocks.SMOOTH_STONE.getDefaultState());
            var stageBase=p.down();var hv=device(stageBase,5,4,block==Foundry.THERMAL?Foundry.STATION_11K:block==Foundry.HYDRO?Foundry.STATION_6K6:Foundry.STATION_24K);stationStructure(hv);
            var lv=device(stageBase,9,4,Foundry.SUBSTATION);stationStructure(lv);var load=device(stageBase,12,4,Foundry.LAMP);
            wire(stageBase,p,0,hv,0,0,true);wire(stageBase,p,1,hv,1,1,true);wire(stageBase,hv,2,lv,0,0,true);wire(stageBase,hv,3,lv,1,1,true);wire(stageBase,lv,2,load,0,0,true);wire(stageBase,lv,3,load,1,1,true);indicators.add(load);
            if(block!=Foundry.HYDRO)coolingDemo(p,lv,block==Foundry.NUCLEAR);
        }
        var lightning=new BlockPos(66,72,60);foundation(lightning);for(int y=65;y<lightning.getY();y++)set(lightning.getX(),y,lightning.getZ(),Foundry.TOWER_SECTION);world.setBlockState(lightning,Foundry.LIGHTNING.getDefaultState().with(ComponentBlock.FACING,Direction.UP));world.setBlockState(lightning.up(),Blocks.LIGHTNING_ROD.getDefaultState());circuit.track(lightning,world.getBlockState(lightning));formed(lightning);
        var box=new BlockPos(60,65,3);world.setBlockState(box,Blocks.CHEST.getDefaultState());var chest=(ChestBlockEntity)world.getBlockEntity(box);int slot=0;
        for(var item:new ItemConvertible[]{Foundry.THERMAL,Foundry.HYDRO,Foundry.NUCLEAR,Foundry.WIND,Foundry.LIGHTNING,Foundry.MAINS,Foundry.SUBSTATION,Foundry.PLANT_CASING,Foundry.REACTOR_CASING,Foundry.SHAFT,Foundry.TURNTABLE,Foundry.FUEL_ROD,Items.COAL,Items.WATER_BUCKET,Blocks.LIGHTNING_ROD})chest.setStack(slot++,new ItemStack(item,item==Items.WATER_BUCKET?1:4));
        for(int color=0;color<8;color++)chest.setStack(slot++,CircuitToolItem.spool(2048,color,true));for(var hv:new ComponentBlock[]{Foundry.STATION_6K6,Foundry.STATION_11K,Foundry.STATION_24K})chest.setStack(slot++,new ItemStack(hv));chest.markDirty();
    }
    private void coolingDemo(BlockPos core,BlockPos lv,boolean nuclear){
        var tank=PlantStructures.waterPos(core,world.getBlockState(core));
        var direction=nuclear?Direction.NORTH:Direction.EAST;
        int distance=2;
        var pump=tank.offset(direction.getOpposite(),distance);var supply=pump.offset(direction.getOpposite());
        for(var q:BlockPos.iterate(core.add(nuclear?-16:-8,-1,-4),core.add(3,-1,7)))world.setBlockState(q,Blocks.SMOOTH_STONE.getDefaultState());
        for(int i=1;i<distance;i++)world.setBlockState(tank.offset(direction.getOpposite(),i),Industrial.GLASS_PIPE.getDefaultState());
        if(nuclear){world.setBlockState(supply,Industrial.NUCLEAR_TANK.getDefaultState());((FluidEntity)world.getBlockEntity(supply)).change(200000);}
        else{
            // A raised glass pond keeps the pump terminals dry and the layer withdrawal visible.
            for(int x=58;x<=62;x++)for(int z=5;z<=9;z++)set(x,65,z,Blocks.SMOOTH_STONE);
            for(int y=66;y<=66;y++)for(int x=57;x<=63;x++)for(int z=4;z<=10;z++){
                if(x>=58&&x<=62&&z>=5&&z<=9)world.setBlockState(new BlockPos(x,y,z),Blocks.WATER.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
                else if(!(x==63&&z==9&&y==66))set(x,y,z,Blocks.GLASS);
            }
            sign(new BlockPos(57,66,11),"lake","lake_1","lake_2","read_book");
        }
        world.setBlockState(pump,Industrial.PUMP.getDefaultState().with(ComponentBlock.FACING,Direction.UP).with(ComponentBlock.TURN,direction.getHorizontal()));circuit.track(pump,world.getBlockState(pump));
        var stageBase=core.down();var tap=device(stageBase,0,4,Industrial.BREAKOUT);
        wire(stageBase,lv,2,tap,0,0,true);wire(stageBase,lv,3,tap,4,1,true);wire(stageBase,tap,1,pump,0,2,true);wire(stageBase,tap,5,pump,1,1,true);
    }
    private void plugWorkshop(){
        var b=new BlockPos(112,65,100);
        for(int x=110;x<=126;x++)for(int z=98;z<=117;z++){set(x,63,z,Blocks.STONE_BRICKS);set(x,64,z,Blocks.SMOOTH_QUARTZ);if(x>=114)set(x,65,z,Blocks.SMOOTH_STONE);}
        // Leave the paid wind base on its foundation instead of burying it in the bench.
        set(112,65,103,Blocks.AIR);
        var wind=windTower(new BlockPos(112,72,103));
        var outlet=device(b,3,3,Appliances.WALL);var adapter=device(b,6,3,Appliances.ADAPTER);var strip=device(b,6,6,Appliances.STRIP);
        var lamp=device(b,10,6,Lighting.WORKSHOP);var bench=device(b,10,10,Pcb.BENCH);
        wire(b,wind,0,outlet,0,0,true);wire(b,wind,1,outlet,1,1,true);
        for(var pair:List.of(new BlockPos[]{outlet,adapter},new BlockPos[]{adapter,strip},new BlockPos[]{strip,lamp},new BlockPos[]{strip,bench})){
            var cord=CordNetwork.connect(world,circuit,pair[0],pair[1]);circuit.cords.put(cord.id(),cord);
        }
        var table=(PcbBenchEntity)e(bench);table.draft=PcbLayout.inverter();table.setStack(0,new ItemStack(PcbFabrication.FILM,4));table.setStack(2,new ItemStack(Items.BLACK_DYE,4));table.edit(5,0,0,0,0);
        indicators.add(lamp);sign(b.add(3,1,14),"plug_power","plug_power_1","plug_power_2","read_book");
        var p=b.add(9,1,14);world.setBlockState(p,Blocks.CHEST.getDefaultState());var chest=(ChestBlockEntity)world.getBlockEntity(p);int slot=0;
        for(var item:new ItemConvertible[]{Appliances.WALL,Appliances.STRIP,Appliances.ADAPTER,Appliances.CORD,Lighting.WORKSHOP,Pcb.BENCH,Foundry.WRENCH,Foundry.METER})chest.setStack(slot++,new ItemStack(item,Math.min(4,item.asItem().getMaxCount())));chest.markDirty();
    }
    private void industrialWorkshop(){
        for(int x=57;x<=64;x++)for(int z=11;z<=19;z++)set(x,65,z,Blocks.SMOOTH_STONE);
        var table=new BlockPos(59,66,12);for(int i=0;i<6;i++)world.setBlockState(table.add(IndustrialTableBlock.offset(Direction.NORTH,i)),Industrial.TABLE.getDefaultState().with(IndustrialTableBlock.PART,i).with(IndustrialTableBlock.FACING,Direction.NORTH));
        if(!IndustrialTableBlock.form(world,table))throw new IllegalStateException("Six paid teaching table modules did not form");
        var inventory=(IndustrialTableEntity)world.getBlockEntity(table);int slot=0;for(var ingredient:AssemblyRecipes.recipes().getFirst().inputs())inventory.setStack(slot++,new ItemStack(ingredient.item(),ingredient.count()));
        var workshopItems=new ArrayList<ItemStack>();
        for(var block:Industrial.BLOCKS.values())workshopItems.add(new ItemStack(block,4));
        for(var item:new ItemConvertible[]{Items.IRON_BLOCK,Items.COPPER_BLOCK,Items.WATER_BUCKET,Foundry.RELAY,Foundry.MOTOR,Foundry.MOSFET,Foundry.CAPACITOR})workshopItems.add(new ItemStack(item,item==Items.WATER_BUCKET?1:16));
        for(int page=0;page*27<workshopItems.size();page++){var box=new BlockPos(60+page*2,66,17);world.setBlockState(box,Blocks.CHEST.getDefaultState());var chest=(ChestBlockEntity)world.getBlockEntity(box);
            for(int i=0;i<27&&page*27+i<workshopItems.size();i++)chest.setStack(i,workshopItems.get(page*27+i));chest.markDirty();}
        sign(new BlockPos(117,66,61),"service","service_1","service_2","service_3");
        for(int x=58;x<=62;x++){set(x,65,60,Blocks.SMOOTH_STONE);set(x,73,60,Blocks.SMOOTH_STONE);}
        for(int y=66;y<73;y++){set(58,y,60,Blocks.IRON_BARS);set(62,y,60,Blocks.IRON_BARS);}
        var high=new BlockPos(60,74,60);var low=new BlockPos(60,66,60);for(var q:List.of(high,low)){world.setBlockState(q,Industrial.BREAKOUT.getDefaultState().with(ComponentBlock.FACING,Direction.SOUTH));circuit.track(q,world.getBlockState(q));}
        for(int pin:new int[]{0,4}){var a=circuit.endpoints().get(node(high,pin));var b=circuit.endpoints().get(node(low,pin));var path=circuit.connectionRoute(world,a,b,0,true);var wire=new Wire(UUID.randomUUID(),a.id(),b.id(),pin==0?0:1,path,VerticalWire.units(circuit,path),true,false,true);circuit.wires.put(wire.id(),wire);}
        var supply=windTower(new BlockPos(60,73,51));world.setBlockState(supply,world.getBlockState(supply).with(ComponentBlock.ENABLED,false));
        wire(new BlockPos(56,64,48),supply,0,low,0,0,true);wire(new BlockPos(56,64,48),supply,1,low,4,1,true);
        set(59,69,61,Blocks.IRON_BLOCK);set(59,68,61,Blocks.SMOOTH_STONE);set(59,67,61,Blocks.SMOOTH_STONE);set(59,66,61,Blocks.SMOOTH_STONE);set(59,65,61,Blocks.SMOOTH_STONE);
        for(int x=57;x<=64;x++)set(x,65,64,Blocks.IRON_BARS);
        sign(new BlockPos(60,65,66),"arc_demo","arc_demo_1","arc_demo_2","arc_demo_3");
        device(new BlockPos(36,65,51),5,6,Industrial.LOW_SOCKET);
    }
    private void processingWorkshop(){
        for(int x=-4;x<=71;x++)for(int z=75;z<=94;z++){set(x,63,z,Blocks.STONE_BRICKS);set(x,64,z,Blocks.SMOOTH_QUARTZ);}
        for(int x=5;x<=10;x++)set(x,65,74,Blocks.AIR);
        var machines=List.of(Processing.CRUSHER,Processing.CENTRIFUGE,Processing.KILN,Processing.FABRICATOR);
        var ingredients=List.of(Foundry.RAW_URANIUM,Processing.CRUSHED_URANIUM,Processing.CONCENTRATE,Foundry.URANIUM_PELLET);
        for(int i=0;i<4;i++){
            var b=new BlockPos(i*18,65,80);for(int x=0;x<12;x++)for(int z=0;z<13;z++)set(b.getX()+x,65,b.getZ()+z,Blocks.SMOOTH_STONE);
            var source=source(b,1,3,true,1000);var machine=device(b,8,3,machines.get(i));e(machine).plantItems.set(0,new ItemStack(ingredients.get(i),64));
            if(i==3)e(machine).plantItems.set(1,new ItemStack(Processing.CLADDING,16));e(machine).markDirty();
            wire(b,source,0,machine,0,0,true);wire(b,source,1,machine,1,1,true);
            if(i==3){var indoor=device(b,7,7,Lighting.WORKSHOP);var yard=device(b,10,9,Lighting.YARD);for(var light:List.of(indoor,yard)){wire(b,source,0,light,0,2,true);wire(b,source,1,light,1,1,true);indicators.add(light);}}
        }
        sign(new BlockPos(7,65,78),"processing","processing_1","processing_2","processing_3");
        var p=new BlockPos(10,66,92);world.setBlockState(p,Blocks.CHEST.getDefaultState().with(ChestBlock.FACING,Direction.SOUTH));var chest=(ChestBlockEntity)world.getBlockEntity(p);int slot=0;
        for(var item:new ItemConvertible[]{Processing.CRUSHER,Processing.CENTRIFUGE,Processing.KILN,Processing.FABRICATOR,Processing.CRUSHED_URANIUM,Processing.CONCENTRATE,Processing.TAILINGS,Processing.IRON_DUST,Processing.COPPER_DUST,Processing.CLADDING,Processing.IRON_PLATE,Processing.COPPER_COIL,Lighting.WORKSHOP,Lighting.YARD,Foundry.RAW_URANIUM,Items.RAW_IRON,Items.RAW_COPPER,Items.IRON_INGOT,Items.COPPER_INGOT,Foundry.HANDBOOK,Foundry.METER,Foundry.WRENCH})chest.setStack(slot++,new ItemStack(item,Math.min(16,item.asItem().getMaxCount())));chest.markDirty();
    }
    private void pcbWorkshop(){
        for(int x=-4;x<=107;x++)for(int z=95;z<=138;z++){set(x,63,z,Blocks.STONE_BRICKS);set(x,64,z,Blocks.SMOOTH_QUARTZ);}
        var design=PcbLayout.inverter();
        for(int i=0;i<6;i++){
            var b=new BlockPos(i*18,65,100);for(int x=0;x<12;x++)for(int z=0;z<15;z++)set(b.getX()+x,65,b.getZ()+z,Blocks.SMOOTH_STONE);
            var supply=source(b,1,3,true,1000);var machine=device(b,8,3,i==5?Pcb.BENCH:PcbFabrication.STAGES.get(i).machine());
            wire(b,supply,0,machine,0,0,true);wire(b,supply,1,machine,1,1,true);
            if(i<5){var stage=PcbFabrication.STAGES.get(i);e(machine).plantItems.set(0,i==0?new ItemStack(Pcb.BLANK,16):Pcb.designed(stage.input(),design));e(machine).plantItems.set(1,i==0?Pcb.designed(PcbFabrication.MASK,design):new ItemStack(stage.reagent(),16));}
            else{var table=(PcbBenchEntity)e(machine);table.draft=design;table.setStack(0,new ItemStack(PcbFabrication.FILM,16));table.setStack(2,new ItemStack(Items.BLACK_DYE,16));table.edit(5,0,0,0,0);}
            e(machine).markDirty();sign(b.add(7,1,14),"pcb_step_"+i,"pcb_flow","pcb_book","pcb_tools");
        }
        var b=new BlockPos(0,65,120);for(int x=0;x<12;x++)for(int z=0;z<17;z++)set(x,65,120+z,Blocks.SMOOTH_STONE);
        var supply=source(b,1,3,false,100);var socket=device(b,5,3,Pcb.SOCKET);var sw=device(b,1,9,Foundry.SWITCH);world.setBlockState(sw,world.getBlockState(sw).with(ComponentBlock.ENABLED,false));
        var pull=device(b,5,9,Foundry.RESISTOR_10000);var resistor=device(b,9,3,Foundry.RESISTOR_220);var led=device(b,9,11,Foundry.LED);
        // Reserve crossing clearances before laying the return loop. Retrofitting
        // feet after a trace already occupies a corner must correctly be refused.
        for(int x=4;x<=6;x++)for(int z=122;z<=130;z++){
            var support=new BlockPos(x,65,z);
            if(!world.isAir(support.up()))continue;
            var bridge=new Bridge(UUID.randomUUID(),support,Direction.UP,"minecraft:smooth_stone");
            circuit.bridges.put(bridge.id(),bridge);
        }
        var board=(PcbSocketEntity)e(socket);board.plantItems.set(0,Pcb.board(design));board.boardChanged();
        wire(b,supply,0,socket,0,0,false);wire(b,supply,1,socket,1,1,false);wire(b,supply,0,sw,0,0,false);wire(b,sw,1,socket,2,3,false);wire(b,socket,2,pull,0,3,false);wire(b,pull,1,supply,1,1,false);
        wire(b,socket,4,resistor,0,2,false);wire(b,resistor,1,led,0,2,false);wire(b,led,1,supply,1,1,false);indicators.add(led);sign(b.add(3,1,16),"pcb_control","pcb_control_1","pcb_control_2","pcb_book");
        b=new BlockPos(18,65,120);for(int x=0;x<12;x++)for(int z=0;z<17;z++)set(18+x,65,120+z,Blocks.SMOOTH_STONE);
        var main=source(b,1,3,true,1000);var buck=device(b,5,3,Foundry.BUCK);var sensor=device(b,8,4,Automation.WATER);var emergency=device(b,8,9,Automation.EMERGENCY);var light=device(b,5,11,Lighting.WORKSHOP);
        var bus=device(b,4,7,Industrial.LOW_SOCKET);
        var tank=sensor.north();world.setBlockState(tank,Industrial.THERMAL_TANK.getDefaultState());((FluidEntity)world.getBlockEntity(tank)).change(1000);
        for(int x=20;x<=25;x++)for(int z=125;z<=133;z++){
            var support=new BlockPos(x,65,z);if(!world.isAir(support.up()))continue;
            try{circuit.validateBridgeSpace(new SurfacePoint(support,Direction.UP,7,7,0));var bridge=new Bridge(UUID.randomUUID(),support,Direction.UP,"minecraft:smooth_stone");circuit.bridges.put(bridge.id(),bridge);}catch(IllegalArgumentException occupied){}
        }
        wire(b,main,0,bus,0,0,true);wire(b,main,1,bus,4,1,true);
        wire(b,bus,1,buck,0,0,true);wire(b,bus,5,buck,1,1,true);wire(b,buck,2,sensor,0,3,false);wire(b,buck,3,sensor,1,1,false);
        wire(b,bus,2,sensor,2,0,true);wire(b,sensor,3,light,0,2,true);wire(b,light,1,bus,6,1,true);wire(b,bus,3,emergency,0,0,true);wire(b,bus,7,emergency,1,1,true);indicators.add(light);sign(b.add(3,1,16),"pcb_automation","pcb_automation_1","pcb_automation_2","pcb_book");
        // Keep the two established processing boxes and their old slot order. Timing
        // packages live beside the playable counter in the existing digital toolbox.
        var timingParts=Arrays.stream(PcbLayout.Type.values()).filter(type->type.ordinal()>=PcbLayout.Type.TFF.ordinal()).map(Pcb.PARTS::get).toList();
        var items=new ArrayList<>(PcbFabrication.items().stream().filter(item->!timingParts.contains(item)).toList());Collections.addAll(items,Pcb.BENCH.asItem(),Pcb.ASSEMBLY.asItem(),Pcb.SOCKET.asItem(),Pcb.EXTENSION.asItem(),top.lacrus.zephyrzeno.electron.display.Displays.PIXEL.asItem(),Automation.WATER.asItem(),Automation.TEMPERATURE.asItem(),Automation.EMERGENCY.asItem(),Items.BLACK_DYE);
        for(int box=0;box<(items.size()+26)/27;box++){var p=new BlockPos(40+box*3,65,126);world.setBlockState(p,Blocks.CHEST.getDefaultState().with(ChestBlock.FACING,Direction.SOUTH));var chest=(ChestBlockEntity)world.getBlockEntity(p);
            for(int slot=0;slot<27&&box*27+slot<items.size();slot++){var item=items.get(box*27+slot);var stack=new ItemStack(item,Math.min(16,item.getMaxCount()));if(PcbFabrication.carriesLayout(stack)){stack=Pcb.designed(item,design);stack.setCount(Math.min(16,item.getMaxCount()));}chest.setStack(slot,stack);}chest.markDirty();}
    }
    private void decimalWorkshop(){
        var base=DecimalCircuitLesson.ORIGIN;
        for(int x=-4;x<30;x++)for(int z=-3;z<36;z++){set(base.getX()+x,63,base.getZ()+z,Blocks.STONE_BRICKS);set(base.getX()+x,64,base.getZ()+z,Blocks.SMOOTH_QUARTZ);}
        // The supply bench is separate so constructing the circuit cannot bury its cabinet.
        var supplyBase=base.add(-10,0,0);for(int x=0;x<10;x++)for(int z=0;z<16;z++)set(supplyBase.getX()+x,65,supplyBase.getZ()+z,Blocks.SMOOTH_STONE);
        var supply=source(supplyBase,1,3,true,1000);new DecimalCircuitLesson(world,circuit).build(supply);
        var assembly=device(supplyBase,7,10,Pcb.ASSEMBLY);wire(supplyBase,supply,0,assembly,0,0,true);wire(supplyBase,supply,1,assembly,1,1,true);
        var station=(PcbBenchEntity)e(assembly);station.setStack(0,Pcb.designed(PcbFabrication.GOLD,PcbLayout.halfAdder()));var costs=Pcb.costs(PcbLayout.halfAdder());for(int i=1;i<Pcb.INPUT_SLOTS;i++)if(costs[i]>0)station.setStack(i,new ItemStack(Pcb.MATERIALS.get(i),costs[i]));station.edit(5,0,0,0,0);
        sign(base.add(8,1,1),"decimal_single","decimal_input","decimal_input_back","decimal_manual");sign(base.add(11,1,1),"decimal_operation","decimal_add","decimal_subtract","decimal_manual");sign(base.add(8,1,9),"pixel_demo","pixel_controls","pixel_latch","pixel_clear");sign(base.add(8,1,16),"decimal_result","decimal_range","decimal_real","decimal_manual");
        var box=base.add(13,1,16);world.setBlockState(box,Blocks.CHEST.getDefaultState().with(ChestBlock.FACING,Direction.SOUTH));var chest=(ChestBlockEntity)world.getBlockEntity(box);int slot=0;for(var block:List.of(Pcb.BENCH,Pcb.ASSEMBLY,Pcb.SOCKET,top.lacrus.zephyrzeno.electron.display.Displays.DIAL,top.lacrus.zephyrzeno.electron.display.Displays.SMALL,top.lacrus.zephyrzeno.electron.display.Displays.PANEL))chest.setStack(slot++,new ItemStack(block,8));chest.setStack(slot++,Pcb.board(SingleBoardCalculator.layout()));chest.setStack(slot++,Pcb.designed(PcbFabrication.GOLD,SingleBoardCalculator.layout()));chest.setStack(slot++,new ItemStack(Pcb.PARTS.get(PcbLayout.Type.HALF_ADDER),16));chest.setStack(slot++,new ItemStack(Pcb.PARTS.get(PcbLayout.Type.DISPLAY),16));chest.setStack(slot++,new ItemStack(Pcb.EXTENSION,32));chest.setStack(slot++,new ItemStack(top.lacrus.zephyrzeno.electron.display.Displays.PIXEL,8));chest.setStack(slot++,Pcb.board(PcbExamples.pixel()));
        chest.setStack(slot++,Pcb.board(PcbExamples.counter()));chest.setStack(slot++,Pcb.designed(PcbFabrication.GOLD,PcbExamples.counter()));
        for(var type:List.of(PcbLayout.Type.TFF,PcbLayout.Type.SR_LATCH,PcbLayout.Type.COUNTER4,PcbLayout.Type.SHIFT4))chest.setStack(slot++,new ItemStack(Pcb.PARTS.get(type),16));
        if(slot>chest.size())throw new IllegalStateException("Digital toolbox exceeds its existing chest");chest.markDirty();
        box=base.add(17,1,16);world.setBlockState(box.down(),Blocks.SMOOTH_STONE.getDefaultState());world.setBlockState(box,Blocks.CHEST.getDefaultState().with(ChestBlock.FACING,Direction.SOUTH));chest=(ChestBlockEntity)world.getBlockEntity(box);slot=0;
        for(var type:PcbLayout.Type.values())if(type.analog())chest.setStack(slot++,new ItemStack(Pcb.PARTS.get(type),16));
        chest.setStack(slot++,new ItemStack(Automation.LIGHT,8));chest.setStack(slot++,new ItemStack(Automation.RAIN,8));
        for(var sample:List.of(PcbExamples.timer(),PcbExamples.regulated())){chest.setStack(slot++,Pcb.board(sample));chest.setStack(slot++,Pcb.designed(PcbFabrication.GOLD,sample));}chest.markDirty();
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
        DecimalCircuitLesson.verify(world);report.add("decimal_gate_calculator=true");report.add("single_board_calculator=true");report.add("pixel_stream_verified=true");
        if(circuit.wires.size()!=186||circuit.devices.size()!=149||circuit.cords.size()!=4)throw new IllegalStateException("Incomplete map: devices="+circuit.devices.size()+", wires="+circuit.wires.size()+", cords="+circuit.cords.size());
        var solder=(PcbBenchEntity)e(new BlockPos(43,66,152));if(!solder.getStack(Pcb.OUTPUT_SLOT).isOf(Pcb.BOARD)||!Pcb.layout(solder.getStack(Pcb.OUTPUT_SLOT)).equals(PcbLayout.halfAdder()))throw new IllegalStateException("Powered solder assembly did not finish the half adder");report.add("pcb_assembly_working=true");
        if(e(new BlockPos(122,66,106)).current<.005||!((PcbBenchEntity)e(new BlockPos(122,66,110))).getStack(Pcb.OUTPUT_SLOT).isOf(PcbFabrication.MASK))throw new IllegalStateException("Plug appliance demonstration failed");
        report.add("plug_appliances_working=true");
        for(int i=0;i<4;i++){var p=new BlockPos(i*18+8,66,83);if(!(e(p) instanceof ProcessingEntity)||e(p).plantItems.get(2).isEmpty())throw new IllegalStateException("Powered processing did not produce output at "+p);}
        report.add("processing_machines_working=4");
        for(int i=0;i<5;i++){var p=new BlockPos(i*18+8,66,103);if(e(p).plantItems.get(2).isEmpty())throw new IllegalStateException("PCB stage failed at "+p);if(!Pcb.layout(e(p).plantItems.get(2)).equals(PcbLayout.inverter()))throw new IllegalStateException("PCB design changed in stage "+p);}
        var pcbBench=(PcbBenchEntity)e(new BlockPos(98,66,103));if(!pcbBench.getStack(Pcb.OUTPUT_SLOT).isOf(PcbFabrication.MASK))throw new IllegalStateException("Photoplotter failed");
        if(!(e(new BlockPos(5,66,123)) instanceof PcbSocketEntity pcb)||pcb.current<.005)throw new IllegalStateException("PCB control example has no paid output");
        if(e(new BlockPos(26,66,124)).contactClosed==false||e(new BlockPos(26,66,129)).energy<1)throw new IllegalStateException("Level sensor or emergency battery failed");
        report.add("pcb_fabrication_stages=5");report.add("pcb_control_working=true");report.add("emergency_lamp_charged=true");
        for(var p:circuit.devices.keySet()){
            if(world.getBlockState(p).isOf(Foundry.TOWER_TERMINAL)){if(!OverheadCable.tower(world,p))throw new IllegalStateException("Invalid demonstration tower");}
            else if(world.getBlockState(p).isOf(Foundry.WIND)||world.getBlockState(p).isOf(Foundry.LIGHTNING)){if(!top.lacrus.zephyrzeno.electron.multiblock.Assembly.original(world,p.down()).isOf(world.getBlockState(p).isOf(Foundry.WIND)?Industrial.MAST:Foundry.TOWER_SECTION))throw new IllegalStateException("Missing plant tower: "+p);}
            else if(!world.getBlockState(p.down()).isOf(Blocks.SMOOTH_STONE))throw new IllegalStateException("Component is not on the smooth-stone bench: "+p);
        }
        if(circuit.wires.values().stream().filter(Wire::overhead).count()!=2)throw new IllegalStateException("Missing overhead example");
        for(var p:circuit.devices.keySet())if(world.getBlockState(p).getBlock() instanceof top.lacrus.zephyrzeno.electron.multiblock.AssemblyControllerBlock&&!top.lacrus.zephyrzeno.electron.multiblock.Assembly.formed(world.getBlockState(p)))throw new IllegalStateException("Unformed showcase machine: "+p);
        for(var q:circuit.devices.keySet())if(world.getBlockState(q).isOf(Industrial.PUMP)&&world.getBlockState(q).get(ComponentBlock.LEVEL)==0)throw new IllegalStateException("Unpowered teaching pump: "+q);
        if(circuit.wires.values().stream().filter(Wire::vertical).count()!=2)throw new IllegalStateException("Missing hanging cable lesson");
        if(world.getBlockState(new BlockPos(60,65,51)).get(ComponentBlock.ENABLED))throw new IllegalStateException("Arc lesson must be switched off before export");
        report.add("arc_demo_default_off=true");
        for(var p:java.util.List.of(new BlockPos(68,66,10),new BlockPos(103,66,58))){
            var console=world.getBlockEntity(PlantStructures.consolePos(p,world.getBlockState(p)));
            var port=world.getBlockEntity(PlantStructures.fuelPos(p,world.getBlockState(p)));
            if(!(console instanceof PlantServiceEntity c)||!(port instanceof PlantServiceEntity f)||c.core()!=e(p)||f.core()!=e(p)||e(p).plantItems.get(0).isEmpty())throw new IllegalStateException("Unlinked or empty teaching fuel intake at "+p);
            if(!(world.getBlockEntity(PlantStructures.outputPos(p,world.getBlockState(p))) instanceof PlantServiceEntity o)||o.core()!=e(p))throw new IllegalStateException("Unlinked teaching output at "+p);
            if(!(world.getBlockEntity(PlantStructures.waterPos(p,world.getBlockState(p))) instanceof FluidEntity water)||water.plant()!=e(p)||water.amount<=0)throw new IllegalStateException("Dry or unlinked water inlet at "+p);
        }
        if(!IndustrialTableBlock.complete(world,new BlockPos(59,66,12),world.getBlockState(new BlockPos(59,66,12))))throw new IllegalStateException("Teaching workbench missing modules");
        report.add("fuel_services_working=true");report.add("six_module_workshop=true");
        report.add("pumps_working=true");report.add("vertical_drops=2");report.add("all_multiblocks_formed=true");
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
