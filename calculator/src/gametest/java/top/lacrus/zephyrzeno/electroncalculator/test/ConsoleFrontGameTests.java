package top.lacrus.zephyrzeno.electroncalculator.test;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.*;

/** Measures the physical parser before attaching its autonomous clock and arithmetic machine. */
public final class ConsoleFrontGameTests implements FabricGameTest {
    @GameTest(templateName="electron_calculator:full_calculator_test",tickLimit=160000,batchId="console-front")
    public void physicalKeypadTransactions(TestContext ctx)throws Exception {
        CompactBlueprint plan=CompactBlueprint.read(Files.newInputStream(Path.of("../build/keypad.ebp")));KeypadCircuit k=KeypadCircuit.create();
        BlockPos origin=ctx.getAbsolutePos(new BlockPos(3,3,3));var world=ctx.getWorld();force(ctx,origin,plan,true);CompactGameTests.place(world,origin,plan);
        String[] keys={"CLEAR","1","PLUS","1","RUN","2","PLUS","2","RUN","CLEAR","9","9","3","BACK","MINUS","1","2","RUN","CLEAR","1","DIVIDE","6","RUN","CLEAR","9","9","MULTIPLY","9","9","RUN"};
        List<Map<String,Boolean>> expected=new ArrayList<>();Map<String,Boolean> value=new HashMap<>();for(String key:keys){value=k.step(value,Set.of(key));expected.add(value);}
        int[] keyIndex={0},phase={-2};long[] changed={0},began={0};
        ctx.runAtEveryTick(()->{
            long tick=ctx.getTick();int queued=world.getBlockTickScheduler().getTickCount();
            if(tick%1000==0)System.out.println("FRONT WAIT tick="+tick+" key="+keyIndex[0]+" phase="+phase[0]+" queued="+queued);
            if(tick-changed[0]<30||queued!=0)return;
            switch(phase[0]) {
                case -2->CompactGameTests.lever(world,origin,plan,"CLOCK5",true);
                case -1->CompactGameTests.lever(world,origin,plan,"CLOCK5",false);
                case 0->{if(keyIndex[0]==keys.length){force(ctx,origin,plan,false);ctx.complete();return;}
                    if(keyIndex[0]==0){
                        for(String key:KeypadCircuit.KEYS)System.out.println("INITIAL KEY "+key+" "+world.getBlockState(origin.add(VanillaCalculator.pos(plan.sources.get(k.key.get(key))))));
                        verifyGates(ctx,origin,plan,k);
                    }
                    began[0]=tick;ConsoleGameTests.press(world,origin.add(VanillaCalculator.pos(plan.inputs.get("KEY_"+keys[keyIndex[0]]))));}
                case 1->CompactGameTests.lever(world,origin,plan,"CLOCK1",true);
                case 2->CompactGameTests.lever(world,origin,plan,"CLOCK2",true);
                case 3->CompactGameTests.lever(world,origin,plan,"CLOCK3",true);
                case 4->{
                    CompactGameTests.lever(world,origin,plan,"CLOCK4",true);
                }
                case 5->{
                    verifyGates(ctx,origin,plan,k);
                    for(var e:k.state.entrySet()) {
                        var p=plan.sources.get(e.getValue());boolean actual=world.getBlockState(origin.add(VanillaCalculator.pos(p))).get(RedstoneWireBlock.POWER)>0;
                        ctx.assertEquals(actual,expected.get(keyIndex[0]).get(e.getKey()),"Key "+keys[keyIndex[0]]+" state "+e.getKey());
                    }
                    CompactGameTests.lever(world,origin,plan,"CLOCK5",true);
                }
                case 6->CompactGameTests.lever(world,origin,plan,"CLOCK6",true);
                case 7->{for(int i:new int[]{1,3,5})CompactGameTests.lever(world,origin,plan,"CLOCK"+i,false);}
                case 8->{for(int i:new int[]{2,4,6})CompactGameTests.lever(world,origin,plan,"CLOCK"+i,false);}
                case 9->{System.out.println("FRONT PASS key="+keys[keyIndex[0]]+" ticks="+(tick-began[0]));keyIndex[0]++;phase[0]=-1;}
                default->throw new IllegalStateException();
            }
            phase[0]++;changed[0]=tick;
        });
    }
    private static void verifyGates(TestContext ctx,BlockPos origin,CompactBlueprint plan,KeypadCircuit k) {
        Map<Integer,Boolean> memo=new HashMap<>();memo.put(0,false);memo.put(1,true);
        for(int id:plan.sources.keySet()) {
            var node=k.circuit.nodes.get(id);
            if(node.kind()!=DecimalCircuit.Kind.NOR)memo.put(id,ctx.getWorld().getBlockState(origin.add(VanillaCalculator.pos(plan.sources.get(id)))).get(RedstoneWireBlock.POWER)>0);
        }
        for(int id:plan.sources.keySet()) {
            if(k.circuit.nodes.get(id).kind()!=DecimalCircuit.Kind.NOR)continue;
            boolean expected=gate(id,k.circuit,memo),actual=ctx.getWorld().getBlockState(origin.add(VanillaCalculator.pos(plan.sources.get(id)))).get(RedstoneWireBlock.POWER)>0;
            if(expected!=actual)System.out.println("FRONT BAD GATE "+id+" "+k.circuit.nodes.get(id)+" source="+plan.sources.get(id));
            ctx.assertEquals(actual,expected,"Physical parser gate "+id);
        }
    }
    private static boolean gate(int id,DecimalCircuit circuit,Map<Integer,Boolean> memo) {
        if(memo.containsKey(id))return memo.get(id);var n=circuit.nodes.get(id);
        boolean result=!(gate(n.a(),circuit,memo)||gate(n.b(),circuit,memo));memo.put(id,result);return result;
    }
    public static void force(TestContext ctx,BlockPos origin,CompactBlueprint plan,boolean on) {
        for(int x=origin.getX()>>4;x<=(origin.getX()+plan.width)>>4;x++)for(int z=origin.getZ()>>4;z<=(origin.getZ()+plan.depth)>>4;z++)ctx.getWorld().setChunkForced(x,z,on);
    }
}
