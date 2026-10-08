package top.lacrus.zephyrzeno.electron.test;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import top.lacrus.zephyrzeno.electron.circuit.CircuitState;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.pcb.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

/** Runs the distributed teaching board with real world supply, copper and controls. */
public final class PcbCounterExampleTests implements FabricGameTest {
    private static ComponentEntity device(TestContext t,ComponentBlock block,int x,int z){
        var pos=t.getAbsolutePos(new BlockPos(x,2,z));
        t.getWorld().setBlockState(pos.down(),Blocks.SMOOTH_STONE.getDefaultState());
        t.getWorld().setBlockState(pos,block.getDefaultState().with(ComponentBlock.FACING,Direction.UP));
        return (ComponentEntity)t.getWorld().getBlockEntity(pos);
    }
    private static void wire(TestContext t,ComponentEntity a,int ap,ComponentEntity b,int bp){
        var circuit=CircuitState.get(t.getWorld());var endpoints=circuit.endpoints();
        var from=endpoints.get(node(a.getPos(),ap));var to=endpoints.get(node(b.getPos(),bp));
        var path=circuit.connectionRoute(t.getWorld(),from,to,0);circuit.validateCapacity(path);
        var wire=new Wire(UUID.randomUUID(),from.id(),to.id(),circuit.wires.size()%8,path,circuit.lengthUnits(path,from,to),false);
        circuit.wires.put(wire.id(),wire);circuit.changed();
    }
    @GameTest(templateName="electron:components_test",tickLimit=130)
    public void counterLessonRequiresResetCountsFromPaidClockAndStopsWithoutPower(TestContext t){
        var network=CircuitState.get(t.getWorld());
        for(int x=0;x<10;x++)for(int z=0;z<12;z++){
            var pos=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(pos,Blocks.SMOOTH_STONE.getDefaultState());
            var bridge=new Bridge(UUID.randomUUID(),pos,Direction.UP,"minecraft:smooth_stone");network.bridges.put(bridge.id(),bridge);
        }
        var source=device(t,Foundry.CRANK,1,4);source.energy=100;
        var board=(PcbSocketEntity)device(t,Pcb.SOCKET,7,4);var layout=PcbExamples.counter();
        t.assertEquals(layout.tilesX()*layout.tilesY(),1,"Lesson fits a single physical socket");
        t.assertTrue(!layout.tracks().isEmpty()&&!layout.vias().isEmpty(),"Every net uses real copper and plated vias");
        board.plantItems.set(0,Pcb.board(layout));board.boardChanged();
        wire(t,source,0,board,0);wire(t,source,1,board,1);
        t.runAtTick(15,()->{
            for(int bit=0;bit<4;bit++)t.assertEquals(board.circuit().outputState(PcbExamples.COUNTER_REGISTER,bit),2,"Power-up memory remains unknown until RESET");
            board.setControl(PcbExamples.COUNTER_RESET,1);
        });
        t.runAtTick(30,()->{
            t.assertEquals(board.displays[PcbExamples.COUNTER_DISPLAY],0,"Held reset clears the four real output rails");
            t.assertTrue(source.energy<100,"Clock, register and display spend source energy");
            board.setControl(PcbExamples.COUNTER_RESET,0);
        });
        t.runAtTick(90,()->{
            int value=board.displays[PcbExamples.COUNTER_DISPLAY];
            t.assertTrue(value>0&&value<8,"Releasing RESET lets the routed clock increment the display");
            t.assertTrue(board.current>.005,"Teaching board is an electrical load");
            source.energy=0;
        });
        t.runAtTick(115,()->{
            t.assertEquals(board.displays[PcbExamples.COUNTER_DISPLAY],-1,"Loss of power blanks the display");
            t.assertTrue(board.leds.isEmpty(),"Indicator cannot glow for free");
            for(int bit=0;bit<4;bit++)t.assertEquals(board.circuit().outputState(PcbExamples.COUNTER_REGISTER,bit),3,"Unpowered outputs are disconnected");
            t.complete();
        });
    }
}
