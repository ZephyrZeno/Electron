package top.lacrus.zephyrzeno.electroncalculator.test;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.*;

/** Opt-in integration suite for the complete vanilla machine. */
public final class FullCalculatorGameTests implements FabricGameTest {
    @GameTest(templateName="electron_calculator:full_calculator_test",tickLimit=450000,batchId="full")
    public void physicalDecimalArithmetic(TestContext ctx)throws Exception {
        CompactBlueprint plan=CompactBlueprint.read(Files.newInputStream(Path.of("../build/modular-calculator.ebp")));
        DecimalCircuit circuit=DecimalCircuit.create();
        BlockPos origin=ctx.getAbsolutePos(new BlockPos(3,3,3));
        System.out.println("FULL PLACE "+plan.blocks.size()+" "+plan.width+"x"+plan.height+"x"+plan.depth);
        for(int x=origin.getX()>>4;x<=(origin.getX()+plan.width)>>4;x++)
            for(int z=origin.getZ()>>4;z<=(origin.getZ()+plan.depth)>>4;z++)ctx.getWorld().setChunkForced(x,z,true);
        CompactGameTests.place(ctx.getWorld(),origin,plan);
        System.out.println("FULL PLACED");
        int[][] cases={{0,0,0,0},{99,99,0,0},{12,34,1,0},{34,12,1,0},{99,99,2,0},{1,6,3,0},{99,0,3,0},{99,1,3,0},{99,1,3,1},{0,0,0,1},{0,0,0,0},{100,0,0,0},{0,150,0,0},{0,0,0,0}};
        Map<String,Boolean> inputs=new HashMap<>();int[] index={-1};long[] changed={0};
        boolean[][] previous={new boolean[circuit.nodes.size()]};
        ctx.runAtEveryTick(()->{
            long tick=ctx.getTick();int pending=ctx.getWorld().getBlockTickScheduler().getTickCount();
            if(tick%1000==0)System.out.println("FULL WAIT tick="+tick+" queued="+pending+" case="+index[0]);
            if(tick-changed[0]<30||pending!=0)return;
            if(index[0]>=0) {
                try{previous[0]=verify(ctx,circuit,plan,origin,inputs,previous[0]);}
                catch(RuntimeException failure){unforce(ctx,origin,plan);throw failure;}
                System.out.println("FULL PASS "+Arrays.toString(cases[index[0]])+" settleTicks="+(tick-changed[0]));
            }
            if(++index[0]==cases.length){unforce(ctx,origin,plan);ctx.complete();return;}
            int[] scenario=cases[index[0]];int a=scenario[0],b=scenario[1],op=scenario[2];
            inputs.clear();
            for(var e:Map.of("AT",a/10,"AU",a%10,"BT",b/10,"BU",b%10).entrySet())
                for(int bit=0;bit<4;bit++)inputs.put(e.getKey()+bit,(e.getValue()&(1<<bit))!=0);
            inputs.put("OP0",(op&1)!=0);inputs.put("OP1",(op&2)!=0);inputs.put("HOLD",scenario[3]!=0);
            inputs.forEach((name,on)->CompactGameTests.lever(ctx.getWorld(),origin,plan,name,on));
            System.out.println("FULL INPUT "+Arrays.toString(scenario));changed[0]=tick;
        });
    }
    private static void unforce(TestContext ctx,BlockPos origin,CompactBlueprint plan) {
        for(int x=origin.getX()>>4;x<=(origin.getX()+plan.width)>>4;x++)
            for(int z=origin.getZ()>>4;z<=(origin.getZ()+plan.depth)>>4;z++)ctx.getWorld().setChunkForced(x,z,false);
    }
    private static boolean[] verify(TestContext ctx,DecimalCircuit circuit,CompactBlueprint plan,BlockPos origin,Map<String,Boolean> inputs,boolean[] previous) {
        boolean[] expected=new boolean[circuit.nodes.size()];
        for(int id=0;id<expected.length;id++) {
            var n=circuit.nodes.get(id);
            expected[id]=switch(n.kind()) {case INPUT->inputs.getOrDefault(n.name(),false);case NOR->!(expected[n.a()]||expected[n.b()]);case REGISTER->inputs.getOrDefault("HOLD",false)?previous[id]:expected[n.a()];};
            if(!plan.sources.containsKey(id))continue;
            var relative=plan.sources.get(id);
            BlockState state=ctx.getWorld().getBlockState(origin.add(VanillaCalculator.pos(relative)));
            boolean actual=state.get(RedstoneWireBlock.POWER)>0;
            if(actual!=expected[id]) {
                System.out.println("FULL BAD NODE "+id+" "+n+" at "+relative+" state="+state);
                for(int x=-4;x<=4;x++)for(int z=-4;z<=2;z++) {
                    var p=relative.add(x,0,z);var tile=plan.blocks.get(p);
                    if(tile!=null)System.out.println(p+" "+tile+" "+ctx.getWorld().getBlockState(origin.add(VanillaCalculator.pos(p))));
                }
                ctx.assertEquals(actual,expected[id],"Module output "+id+" inputs "+inputs);
            }
        }
        plan.outputs.forEach((name,p)->ctx.assertEquals(CompactGameTests.output(ctx.getWorld(),origin,plan,name),expected[circuit.outputs.get(name)],"Output "+name+" inputs "+inputs));
        return expected;
    }
}
