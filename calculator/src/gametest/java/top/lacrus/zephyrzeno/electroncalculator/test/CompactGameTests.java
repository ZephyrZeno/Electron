package top.lacrus.zephyrzeno.electroncalculator.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.*;

public class CompactGameTests implements FabricGameTest {
    @GameTest(templateName="electron_calculator:compact_test",tickLimit=3000,batchId="modules")
    public void connectedFunctionalModules(TestContext ctx) {
        CompactBlueprint plan=ModularLayout.create(DecimalCircuit.smoke());
        BlockPos origin=ctx.getAbsolutePos(new BlockPos(2,2,2));ServerWorld world=ctx.getWorld();place(world,origin,plan);
        for(int bits=0;bits<4;bits++) {
            int state=bits;long tick=100+bits*400L;
            ctx.runAtTick(tick,()->{lever(world,origin,plan,"A",(state&1)!=0);lever(world,origin,plan,"B",(state&2)!=0);});
            ctx.runAtTick(tick+350,()->{
                ctx.assertEquals(output(world,origin,plan,"R0"),state==3,"Connected AND "+state);
                ctx.assertEquals(output(world,origin,plan,"R1"),state==1||state==2,"Connected XOR "+state);
                ctx.assertEquals(output(world,origin,plan,"R2"),state==3,"Connected memory "+state);
                ctx.assertEquals(output(world,origin,plan,"SEG0_0"),state==3,"Connected segment "+state);
            });
        }
        ctx.runAtTick(1800,()->lever(world,origin,plan,"HOLD",true));
        ctx.runAtTick(2100,()->lever(world,origin,plan,"A",false));
        ctx.runAtTick(2400,()->{ctx.assertTrue(output(world,origin,plan,"R2"),"Connected memory holds");lever(world,origin,plan,"HOLD",false);});
        ctx.runAtTick(2700,()->{ctx.assertFalse(output(world,origin,plan,"R2"),"Connected memory releases");ctx.complete();});
    }
    @GameTest(templateName="electron_calculator:compact_test",tickLimit=2000,batchId="modules")
    public void moduleGatesAndLatch(TestContext ctx) {
        var types=new PhysicalNetlist.Type[]{PhysicalNetlist.Type.NOR,PhysicalNetlist.Type.AND,PhysicalNetlist.Type.OR,PhysicalNetlist.Type.REGISTER};
        CompactBlueprint[] plans=new CompactBlueprint[4];BlockPos[] origins=new BlockPos[4];ServerWorld world=ctx.getWorld();
        for(int i=0;i<4;i++){plans[i]=ModularLayout.moduleFixture(types[i]);origins[i]=ctx.getAbsolutePos(new BlockPos(3+i*20,3,3));place(world,origins[i],plans[i]);}
        for(int bits=0;bits<4;bits++) {
            int state=bits;long tick=100+bits*200L;
            ctx.runAtTick(tick,()->{for(int i=0;i<3;i++){lever(world,origins[i],plans[i],"A",(state&1)!=0);lever(world,origins[i],plans[i],"B",(state&2)!=0);}});
            ctx.runAtTick(tick+100,()->{
                ctx.assertEquals(output(world,origins[0],plans[0],"Q"),state==0,"NOR module "+state);
                ctx.assertEquals(output(world,origins[1],plans[1],"Q"),state==3,"AND module "+state);
                ctx.assertEquals(output(world,origins[2],plans[2],"Q"),state!=0,"OR module "+state);
            });
        }
        ctx.runAtTick(900,()->lever(world,origins[3],plans[3],"A",true));
        ctx.runAtTick(1000,()->{ctx.assertTrue(output(world,origins[3],plans[3],"Q"),"Latch open");lever(world,origins[3],plans[3],"B",true);});
        ctx.runAtTick(1100,()->lever(world,origins[3],plans[3],"A",false));
        ctx.runAtTick(1200,()->{ctx.assertTrue(output(world,origins[3],plans[3],"Q"),"Latch holds");lever(world,origins[3],plans[3],"B",false);});
        ctx.runAtTick(1300,()->{ctx.assertFalse(output(world,origins[3],plans[3],"Q"),"Latch releases");ctx.complete();});
    }
    @GameTest(templateName="electron_calculator:compact_test",tickLimit=3000,batchId="modules")
    public void functionalFullAdder(TestContext ctx) {
        CompactBlueprint plan=VerticalFullAdder.fixture();
        BlockPos origin=ctx.getAbsolutePos(new BlockPos(2,2,2));ServerWorld world=ctx.getWorld();place(world,origin,plan);
        int[] cases={0,1,7,2,6,4,3,5,0};
        List<String> failures=new ArrayList<>();
        for(int i=0;i<cases.length;i++) {
            int bits=cases[i],expected=Integer.bitCount(bits);long tick=100+i*200L;
            ctx.runAtTick(tick,()->{lever(world,origin,plan,"A",(bits&1)!=0);lever(world,origin,plan,"B",(bits&2)!=0);lever(world,origin,plan,"CIN",(bits&4)!=0);});
            ctx.runAtTick(tick+190,()->{
                boolean sum=output(world,origin,plan,"SUM"),carry=output(world,origin,plan,"COUT");
                System.out.println("ADDER "+bits+" => sum="+sum+" carry="+carry);
                if(sum!=((expected&1)!=0)||carry!=(expected>=2))failures.add(bits+":"+sum+","+carry);
            });
        }
        ctx.runAtTick(1950,()->{ctx.assertTrue(failures.isEmpty(),"Full-adder truth table "+failures);ctx.complete();});
    }
    @GameTest(templateName="electron_calculator:compact_test",tickLimit=40000,batchId="modules")
    public void fourBitRippleCarry(TestContext ctx) {
        CompactBlueprint plan=adderWithControls(4);
        BlockPos origin=ctx.getAbsolutePos(new BlockPos(2,2,2));ServerWorld world=ctx.getWorld();place(world,origin,plan);
        System.out.println("RIPPLE4 blocks="+plan.blocks.size()+" size="+plan.width+"x"+plan.height+"x"+plan.depth);
        // Exhaustive operands and carry-in, including long carry chains and falling transitions.
        for(int i=0;i<512;i++) {
            int a=i&15,b=(i>>4)&15,carry=i>>8,expected=a+b+carry;long tick=100+i*70L;
            ctx.runAtTick(tick,()->{
                for(int bit=0;bit<4;bit++){lever(world,origin,plan,"A"+bit,(a&(1<<bit))!=0);lever(world,origin,plan,"B"+bit,(b&(1<<bit))!=0);}
                lever(world,origin,plan,"CIN",carry!=0);
            });
            ctx.runAtTick(tick+65,()->{
                int actual=output(world,origin,plan,"COUT")?16:0;
                for(int bit=0;bit<4;bit++)if(output(world,origin,plan,"R"+bit))actual|=1<<bit;
                ctx.assertEquals(actual,expected,"Four-bit ripple "+a+"+"+b+"+"+carry);
            });
        }
        ctx.runAtTick(36100,ctx::complete);
    }
    private static CompactBlueprint adderWithControls(int bits) {
        CompactBlueprint plan=VerticalFullAdder.template(bits);
        for(var entry:plan.inputs.entrySet()) {
            var pin=entry.getValue();boolean b=entry.getKey().startsWith("B");var lever=pin.add(b?1:-1,0,0);
            plan.blocks.put(lever.add(0,-1,0),new CompactBlueprint.Tile(CompactBlueprint.Part.WOOL,DecimalCircuit.Module.INPUT,0,-1));
            plan.blocks.put(lever,new CompactBlueprint.Tile(CompactBlueprint.Part.LEVER,DecimalCircuit.Module.INPUT,0,-1));entry.setValue(lever);
        }
        for(var entry:plan.outputs.entrySet()) {
            var lamp=entry.getValue().add(0,-1,0);plan.blocks.put(lamp,new CompactBlueprint.Tile(CompactBlueprint.Part.LAMP,DecimalCircuit.Module.DISPLAY,0,-1));entry.setValue(lamp);
        }
        return plan;
    }
    @GameTest(templateName="electron_calculator:compact_test",tickLimit=4000,batchId="modules")
    public void fourteenBitCarryBoundaries(TestContext ctx) {
        CompactBlueprint plan=adderWithControls(14);
        BlockPos origin=ctx.getAbsolutePos(new BlockPos(2,2,2));ServerWorld world=ctx.getWorld();place(world,origin,plan);
        int[][] cases={{0,0,0},{16383,0,0},{16383,0,1},{0,0,0},{8191,1,0},{16383,16383,1},{0,0,1},{5461,10922,1},{9801,99,0},{0,0,0}};
        for(int i=0;i<cases.length;i++) {
            int a=cases[i][0],b=cases[i][1],carry=cases[i][2],expected=a+b+carry;long tick=100+i*300L;
            ctx.runAtTick(tick,()->{
                for(int bit=0;bit<14;bit++){lever(world,origin,plan,"A"+bit,(a&(1<<bit))!=0);lever(world,origin,plan,"B"+bit,(b&(1<<bit))!=0);}
                lever(world,origin,plan,"CIN",carry!=0);
            });
            ctx.runAtTick(tick+290,()->{
                int actual=output(world,origin,plan,"COUT")?16384:0;
                for(int bit=0;bit<14;bit++)if(output(world,origin,plan,"R"+bit))actual|=1<<bit;
                ctx.assertEquals(actual,expected,"Fourteen-bit ripple "+a+"+"+b+"+"+carry);
            });
        }
        ctx.runAtTick(3200,ctx::complete);
    }
    public static void place(ServerWorld world,BlockPos origin,CompactBlueprint plan) {
        var entries=VanillaCalculator.blocks(plan).entrySet().stream().sorted(Comparator.comparingInt(e->e.getKey().getY())).toList();
        entries.forEach(e->world.setBlockState(origin.add(e.getKey()),e.getValue(),Block.NOTIFY_LISTENERS|Block.FORCE_STATE));
        entries.forEach(e->{BlockPos p=origin.add(e.getKey());world.setBlockState(p,Block.postProcessState(world.getBlockState(p),world,p),Block.NOTIFY_ALL);world.updateNeighborsAlways(p,e.getValue().getBlock());});
    }
    public static void lever(ServerWorld world,BlockPos origin,CompactBlueprint plan,String name,boolean on) {
        BlockPos p=origin.add(VanillaCalculator.pos(plan.inputs.get(name)));
        world.setBlockState(p,world.getBlockState(p).with(LeverBlock.POWERED,on),Block.NOTIFY_ALL);world.updateNeighborsAlways(p.down(),Blocks.LEVER);
    }
    public static boolean output(ServerWorld world,BlockPos origin,CompactBlueprint plan,String name) {
        return world.getBlockState(origin.add(VanillaCalculator.pos(plan.outputs.get(name)))).get(RedstoneLampBlock.LIT);
    }
    @GameTest(templateName="electron_calculator:compact_test",tickLimit=10000,batchId="compact")
    public void routedGatesAndLockedRegister(TestContext ctx) {
        DecimalCircuit circuit=DecimalCircuit.smoke();
        CompactBlueprint plan=CompactBlueprint.create(circuit,System.out::println);
        System.out.println("SMOKE "+plan.width+"x"+plan.height+"x"+plan.depth+" blocks="+plan.blocks.size());
        BlockPos origin=ctx.getAbsolutePos(new BlockPos(2,2,2));ServerWorld world=ctx.getWorld();place(world,origin,plan);
        for(int state=0;state<4;state++) {
            int bits=state;long tick=1000+state*1000L;
            ctx.runAtTick(tick,()->{lever(world,origin,plan,"A",(bits&1)!=0);lever(world,origin,plan,"B",(bits&2)!=0);});
            ctx.runAtTick(tick+990,()->{
                debugNodes(ctx,circuit,plan,origin,bits);
                if(output(world,origin,plan,"R1")!=(bits==1||bits==2)) {
                    int signal=circuit.outputs.get("R1");
                    plan.blocks.forEach((p,t)->{if(t.net()==signal)System.out.println("XOR OUT "+p+" "+world.getBlockState(origin.add(VanillaCalculator.pos(p))));});
                }
                ctx.assertEquals(output(world,origin,plan,"R0"),bits==3,"Routed AND "+bits);
                ctx.assertEquals(output(world,origin,plan,"R1"),bits==1||bits==2,"Routed XOR "+bits);
                ctx.assertEquals(output(world,origin,plan,"R2"),bits==3,"Open register "+bits);
                ctx.assertEquals(output(world,origin,plan,"SEG0_0"),bits==3,"Display segment "+bits);
            });
        }
        ctx.runAtTick(5100,()->lever(world,origin,plan,"HOLD",true));
        ctx.runAtTick(6100,()->lever(world,origin,plan,"A",false));
        ctx.runAtTick(7100,()->{
            if(!output(world,origin,plan,"R2"))plan.blocks.forEach((p,t)->{if(t.net()==circuit.inputs.get("HOLD")||t.net()==circuit.outputs.get("R2"))System.out.println("MEMORY "+p+" "+world.getBlockState(origin.add(VanillaCalculator.pos(p))));});
            ctx.assertTrue(output(world,origin,plan,"R2"),"Register retains one while locked");
            lever(world,origin,plan,"HOLD",false);
        });
        ctx.runAtTick(8100,()->{ctx.assertFalse(output(world,origin,plan,"R2"),"Register updates after unlock");ctx.complete();});
    }

    private void debugNodes(TestContext ctx,DecimalCircuit circuit,CompactBlueprint plan,BlockPos origin,int bits) {
        boolean[] expected=new boolean[circuit.nodes.size()];
        for(int id=0;id<expected.length;id++) {
            var node=circuit.nodes.get(id);
            expected[id]=switch(node.kind()) {
                case INPUT->node.name().equals("A")?(bits&1)!=0:node.name().equals("B")&&(bits&2)!=0;
                case NOR->!(expected[node.a()]||expected[node.b()]);
                case REGISTER->expected[node.a()];
            };
            if(node.kind()!=DecimalCircuit.Kind.NOR)continue;
            var p=plan.sources.get(id).add(0,-3,-5);
            BlockState state=ctx.getWorld().getBlockState(origin.add(VanillaCalculator.pos(p)));
            boolean actual=state.get(RedstoneTorchBlock.LIT);
            if(actual!=expected[id]) {
                System.out.println("BAD GATE "+id+" "+node+" expected="+expected[id]+" actual="+actual);
                int gate=id;
                plan.blocks.forEach((pos,tile)->{
                    if((tile.net()==node.a()||tile.net()==node.b()||tile.net()==gate)&&(tile.part()==CompactBlueprint.Part.WIRE||tile.part()==CompactBlueprint.Part.REPEATER))
                        System.out.println("net="+tile.net()+" "+pos+" "+ctx.getWorld().getBlockState(origin.add(VanillaCalculator.pos(pos))));
                });
                ctx.assertEquals(actual,expected[id],"Gate "+id+" bits "+bits);
            }
        }
    }
}
