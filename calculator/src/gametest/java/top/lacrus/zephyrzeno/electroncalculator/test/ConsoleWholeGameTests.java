package top.lacrus.zephyrzeno.electroncalculator.test;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.*;

/** Exercises real buttons, autonomous vanilla clocks, the existing ALU and every screen lamp. */
public final class ConsoleWholeGameTests implements FabricGameTest {
    @GameTest(templateName="electron_calculator:console_calculator_test",tickLimit=2000000,batchId="console-whole")
    public void buttonsToStandingScreen(TestContext ctx)throws Exception {
        CompactBlueprint plan=CompactBlueprint.read(Files.newInputStream(Path.of("../build/console-calculator.ebp")));
        boolean autoFixture=Boolean.getBoolean("electron.autoFixture");
        boolean repairFixture=Boolean.getBoolean("electron.repairFixture");
        if(autoFixture) {
            Set<CompactBlueprint.Pos> selected=new HashSet<>();
            plan.blocks.forEach((p,t)->{if(p.x()>=ConsoleLayout.OFFSET_X||t.net()>=ConsoleCompiler.FRONT_NET)selected.add(p);});
            for(var p:new ArrayList<>(selected))if(plan.blocks.containsKey(p.add(0,-1,0)))selected.add(p.add(0,-1,0));
            plan.blocks.keySet().retainAll(selected);
            System.out.println("AUTO FIXTURE blocks="+plan.blocks.size());
        }
        KeypadCircuit k=KeypadCircuit.create();ConsoleLayout layout=ConsoleLayout.create(k);DecimalCircuit alu=DecimalCircuit.create();
        BlockPos origin=ctx.getAbsolutePos(new BlockPos(3,3,3));var world=ctx.getWorld();
        for(long packed:world.getForcedChunks().toLongArray()) {
            var chunk=new net.minecraft.util.math.ChunkPos(packed);
            world.getBlockTickScheduler().clearNextTicks(new net.minecraft.util.math.BlockBox(chunk.getStartX(),world.getBottomY(),chunk.getStartZ(),chunk.getEndX(),world.getTopY()-1,chunk.getEndZ()));
            world.setChunkForced(chunk.x,chunk.z,false);
        }
        world.getServer().getTickManager().startSprint(2000000);
        ConsoleFrontGameTests.force(ctx,origin,plan,true);
        BlockPos[] foreign={null};boolean[] validatedRepair={!repairFixture};
        BlockPos extra=origin.add(1,10,1);
        boolean[] checkedExtra={!repairFixture};
        if(autoFixture)CompactGameTests.place(world,origin,plan);
        else {
            try(var resource=CalculatorCompiler.class.getResourceAsStream("/data/electron_calculator/calculator.ebp")) {
                ctx.assertTrue(resource!=null&&Arrays.equals(resource.readAllBytes(),Files.readAllBytes(Path.of("../build/console-calculator.ebp"))),"Packaged calculator matches the verified blueprint");
            }
            if(repairFixture)try(var old=CalculatorCompiler.class.getResourceAsStream("/data/electron_calculator/calculator-legacy.ebp")) {
                ctx.assertTrue(old!=null,"Legacy repair reference exists");
                CompactBlueprint legacy=CompactBlueprint.read(old);
                CompactGameTests.place(world,origin,legacy);
                var extraRelative=new CompactBlueprint.Pos(1,10,1);
                ctx.assertFalse(legacy.blocks.containsKey(extraRelative)||plan.blocks.containsKey(extraRelative),"Unrelated tick fixture is outside calculator cells");
                world.setBlockState(extra,Blocks.STONE.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
                world.scheduleBlockTick(extra,Blocks.STONE,1000000);
                var lamp=legacy.blocks.entrySet().stream().filter(e->e.getKey().x()<ConsoleLayout.OFFSET_X&&e.getValue().part()==CompactBlueprint.Part.LAMP).findFirst().orElseThrow().getKey();
                foreign[0]=origin.add(VanillaCalculator.pos(lamp));
                world.setBlockState(foreign[0],Blocks.DIAMOND_BLOCK.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
            }
            world.getServer().getCommandManager().executeWithPrefix(world.getServer().getCommandSource().withWorld(world),"electron calculator "+(repairFixture?"repair":"build")+" "+origin.getX()+" "+origin.getY()+" "+origin.getZ());
            ctx.assertTrue(building(),"Actual calculator generation command starts");
        }
        boolean capture=Boolean.getBoolean("electron.captureStartup");
        String[] keys=capture?new String[]{"CLEAR","BACK"}:repairFixture?new String[]{"1","PLUS","1","RUN"}:new String[]{"1","PLUS","1","RUN","1","2","MULTIPLY","3","RUN","9","MINUS","1","2","RUN","1","DIVIDE","6","RUN","9","9","MULTIPLY","9","9","RUN","9","9","DIVIDE","0","RUN","CLEAR","RUN"};
        List<Map<String,Boolean>> expected=new ArrayList<>();Map<String,Boolean> value=new HashMap<>();
        for(String key:keys){value=k.step(value,Set.of(key));expected.add(value);}
        int[] index={-1},lastExecution={-1};long[] changed={0};
        long[] idleSince={-1};
        Runnable[] poll=new Runnable[1];
        poll[0]=()->{
            // runAtEveryTick eagerly registers tickLimit callbacks. Keep only the next
            // callback scheduled so a long physical delay does not make the test quadratic.
            ctx.runAtTick(ctx.getTick()+1,new Runnable(){@Override public void run(){poll[0].run();}});
            long tick=ctx.getTick();int queued=world.getBlockTickScheduler().getTickCount();
            if(!autoFixture&&building()) {
                if(tick%1000==0)System.out.println("CONSOLE GENERATION tick="+tick);
                changed[0]=tick;return;
            }
            if(!validatedRepair[0]) {
                ctx.assertTrue(world.getBlockState(foreign[0]).isOf(Blocks.DIAMOND_BLOCK),"Repair refuses to overwrite foreign block");
                System.out.println("REPAIR FOREIGN BLOCK REJECTED PASS");
                world.setBlockState(foreign[0],Blocks.REDSTONE_LAMP.getDefaultState(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE);
                validatedRepair[0]=true;
                world.getServer().getCommandManager().executeWithPrefix(world.getServer().getCommandSource().withWorld(world),"electron calculator repair "+origin.getX()+" "+origin.getY()+" "+origin.getZ());
                ctx.assertTrue(building(),"Repair starts after foreign block removed");changed[0]=tick;return;
            }
            if(!checkedExtra[0]) {
                ctx.assertTrue(world.getBlockState(extra).isOf(Blocks.STONE),"Repair preserves unrelated block between modules");
                ctx.assertTrue(world.getBlockTickScheduler().isQueued(extra,Blocks.STONE),"Repair preserves unrelated scheduled tick");
                world.getBlockTickScheduler().clearNextTicks(new net.minecraft.util.math.BlockBox(extra));
                checkedExtra[0]=true;
                System.out.println("REPAIR UNRELATED BLOCK AND TICK PRESERVED PASS");
            }
            BlockPos cell=origin.add(ConsoleLayout.OFFSET_X+240,1,42);
            int execution=(world.getBlockState(cell).get(BulbBlock.LIT)?1:0)|(world.getBlockState(cell.north()).get(RepeaterBlock.POWERED)?2:0)|(world.getBlockState(cell.south()).get(RepeaterBlock.POWERED)?4:0);
            if(execution!=lastExecution[0]){System.out.println("EXECUTION tick="+tick+" key="+index[0]+" bulb/start/end="+execution);lastExecution[0]=execution;}
            if(tick%1000==0)System.out.println("CONSOLE WAIT tick="+tick+" key="+index[0]+" queued="+queued);
            boolean stalled=tick-changed[0]>50000;
            if(!stalled&&(tick-changed[0]<30||queued!=0))return;
            if(index[0]<0||index[0]==0||stalled) {
                plan.blocks.forEach((p,t)->{
                    if(stalled&&world.getBlockTickScheduler().isQueued(origin.add(VanillaCalculator.pos(p)),VanillaCalculator.state(t).getBlock()))System.out.println("QUEUED BLOCK "+p+" "+t+" "+world.getBlockState(origin.add(VanillaCalculator.pos(p))));
                    if(t.net()==ConsoleCompiler.TIMER_NET&&t.part()==CompactBlueprint.Part.REPEATER) {
                        var at=origin.add(VanillaCalculator.pos(p));var block=world.getBlockState(at);
                        if(block.get(RepeaterBlock.LOCKED)||(!block.get(RepeaterBlock.POWERED)&&world.isReceivingRedstonePower(at)))System.out.println("TIMER STOP "+p+" "+block);
                    }
                });
                for(String name:List.of("REQUEST","BUSY","READY","GO","RESULT_HOLD"))System.out.println("CONTROL "+name+" "+world.getBlockState(origin.add(VanillaCalculator.pos(plan.sources.get(ConsoleCompiler.FRONT_NET+k.circuit.outputs.get(name))))));
                for(var e:k.circuit.inputs.entrySet())if(!e.getKey().equals("ZERO"))System.out.println("CONTROL INPUT "+e.getKey()+" "+world.getBlockState(origin.add(VanillaCalculator.pos(plan.sources.get(ConsoleCompiler.FRONT_NET+e.getValue())))));
                for(int net:new int[]{ConsoleCompiler.TIMER_NET+2,ConsoleCompiler.TIMER_NET+3})System.out.println("EXECUTION SOURCE "+net+" "+world.getBlockState(origin.add(VanillaCalculator.pos(plan.sources.get(net)))));
            }
            ctx.assertFalse(stalled,"Autonomous transaction exceeded 50000 ticks, queued="+queued);
            ctx.assertTrue(CompactGameTests.output(world,origin,plan,"READY"),"Console ready before pressing next key");
            if(index[0]<0&&!capture) {
                if(repairFixture&&idleSince[0]<0)try(var old=CalculatorCompiler.class.getResourceAsStream("/data/electron_calculator/calculator-legacy.ebp")) {
                    CompactBlueprint legacy=CompactBlueprint.read(old);
                    legacy.blocks.forEach((p,t)->{if(!plan.blocks.containsKey(p))ctx.assertTrue(world.getBlockState(origin.add(VanillaCalculator.pos(p))).isAir(),"Repair clears obsolete part "+p);});
                }catch(java.io.IOException error){throw new IllegalStateException(error);}
                for(var e:k.state.entrySet()) {
                    var p=plan.sources.get(ConsoleCompiler.FRONT_NET+e.getValue());
                    ctx.assertEquals(world.getBlockState(origin.add(VanillaCalculator.pos(p))).get(RedstoneWireBlock.POWER),0,"Cold boot without any input must clear "+e.getKey());
                }
                ctx.assertFalse(CompactGameTests.output(world,origin,plan,"INPUT_ERROR"),"Cold boot must not report missing input");
                ctx.assertFalse(CompactGameTests.output(world,origin,plan,"BUSY"),"Cold boot must finish before accepting input");
                if(idleSince[0]<0)idleSince[0]=tick;
                if(tick-idleSince[0]<200)return;
                System.out.println("COLD BOOT PASS: no user input, all editing and execution registers are zero");
                if(Boolean.getBoolean("electron.coldOnly")){ConsoleFrontGameTests.force(ctx,origin,plan,false);ctx.complete();return;}
            }
            if(index[0]>=0) {
                var state=expected.get(index[0]);
                for(var e:k.state.entrySet()) {
                    var p=plan.sources.get(ConsoleCompiler.FRONT_NET+e.getValue());
                    boolean actual=world.getBlockState(origin.add(VanillaCalculator.pos(p))).get(RedstoneWireBlock.POWER)>0;
                    ctx.assertEquals(actual,state.get(e.getKey()),"Key "+keys[index[0]]+" state "+e.getKey());
                }
                Map<String,Boolean> inputs=new HashMap<>();
                for(String p:List.of("AT","AU","BT","BU"))for(int bit=0;bit<4;bit++)inputs.put(p+bit,state.get("X"+p+bit));
                inputs.put("OP0",state.get("XOP0"));inputs.put("OP1",state.get("XOP1"));inputs.put("HOLD",false);
                var result=alu.evaluate(inputs);
                if(!autoFixture)layout.resultPins.forEach((name,pins)->{
                    for(var pin:pins) {
                        BlockPos lamp=origin.add(VanillaCalculator.pos(pin.add(ConsoleLayout.OFFSET_X,0,-2)));
                        if(world.getBlockState(lamp).get(RedstoneLampBlock.LIT)!=result.get(name)) {
                            System.out.println("BAD SCREEN "+name+" source="+world.getBlockState(origin.add(VanillaCalculator.pos(plan.sources.get(alu.outputs.get(name))))));
                            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=2;dz++) {
                                BlockPos at=lamp.add(dx,dy,dz);System.out.println("SCREEN NEIGHBOR "+at.subtract(origin)+" "+world.getBlockState(at));
                            }
                        }
                        ctx.assertEquals(world.getBlockState(lamp).get(RedstoneLampBlock.LIT),result.get(name),"Screen "+name+" after "+keys[index[0]]);
                    }
                });
                if(!autoFixture) {
                    int[] patterns={0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f};
                    Map<Integer,Boolean> lamps=new HashMap<>();
                    for(String prefix:List.of("AT","AU","BT","BU")) {
                        int digit=0;for(int bit=0;bit<4;bit++)if(state.get(prefix+bit))digit|=1<<bit;
                        for(int segment=0;segment<7;segment++)lamps.put(k.circuit.outputs.get("INPUT_"+prefix+"_"+segment),(patterns[digit]&(1<<segment))!=0);
                    }
                    int operation=(state.get("OP0")?1:0)+(state.get("OP1")?2:0),op=0;
                    for(String name:List.of("PLUS","MINUS","MULTIPLY","DIVIDE"))lamps.put(k.circuit.outputs.get("SELECT_"+name),state.get("OP_SET")&&operation==op++);
                    lamps.put(k.circuit.outputs.get("INPUT_ERROR"),state.get("ERROR"));
                    layout.fixed.blocks.forEach((p,t)->{
                        if(t.part()==CompactBlueprint.Part.LAMP&&lamps.containsKey(t.net()))ctx.assertEquals(world.getBlockState(origin.add(VanillaCalculator.pos(p.add(ConsoleLayout.OFFSET_X,0,-1)))).get(RedstoneLampBlock.LIT),lamps.get(t.net()),"Input display "+p+" after "+keys[index[0]]);
                    });
                }
                ctx.assertTrue(CompactGameTests.output(world,origin,plan,"READY"),"Console ready after transaction");
                ctx.assertFalse(CompactGameTests.output(world,origin,plan,"BUSY"),"Busy cleared after transaction");
                for(String key:KeypadCircuit.KEYS) {
                    var p=plan.sources.get(ConsoleCompiler.FRONT_NET+k.key.get(key));
                    ctx.assertEquals(world.getBlockState(origin.add(VanillaCalculator.pos(p))).get(RedstoneWireBlock.POWER),0,"Request cleared "+key);
                }
                System.out.println("CONSOLE PASS key="+keys[index[0]]+" settleTicks="+(tick-changed[0]));
            }
            if(++index[0]==keys.length){
                if(capture)try{CalculatorStartup.capture(world,origin,plan,Path.of("../build/calculator.startup"));}catch(java.io.IOException error){throw new IllegalStateException(error);}
                ConsoleFrontGameTests.force(ctx,origin,plan,false);ctx.complete();return;
            }
            ConsoleGameTests.press(world,origin.add(VanillaCalculator.pos(plan.inputs.get("KEY_"+keys[index[0]]))));changed[0]=tick;
        };
        ctx.runAtTick(1,poll[0]);
    }
    private static boolean building() {
        try{var field=DecimalCalculatorCommands.class.getDeclaredField("active");field.setAccessible(true);return field.get(null)!=null;}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
}
