package top.lacrus.zephyrzeno.electron.pcb;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import top.lacrus.zephyrzeno.electron.circuit.DcSolver;
import static org.junit.jupiter.api.Assertions.*;

class PcbCounterExampleTest {
    @Test void editableCopperExampleCountsAllSixteenValuesAndConsumesSupply(){
        var layout=PcbExamples.counter();var circuit=new PcbCircuit(layout);
        assertEquals(layout,PcbLayout.decode(layout.encode()));
        assertEquals(1,layout.tilesX()*layout.tilesY());
        assertFalse(layout.supplyShort());
        assertTrue(layout.layers()>1&&!layout.vias().isEmpty()&&!layout.tracks().isEmpty());
        for(int tick=0;tick<390;tick++){
            circuit.controls[PcbExamples.COUNTER_RESET]=tick>=15&&tick<30?1:0;
            var branches=new ArrayList<DcSolver.Branch>();
            circuit.stamp("p",new String[]{"V","G","A","B","Y","Z"},branches,tick,false);
            branches.add(DcSolver.Branch.source("s","V","G",tick>=360?0:5,.1));
            var result=DcSolver.solve(branches);
            circuit.update("p",result,n->true,tick,false);
            if(tick==14)for(int bit=0;bit<4;bit++)assertEquals(2,circuit.outputState(PcbExamples.COUNTER_REGISTER,bit),"Initial memory is unknown");
            if(tick==29)assertEquals(0,circuit.displays[PcbExamples.COUNTER_DISPLAY],"Player reset establishes a known state");
            if(tick>=49&&tick<360&&tick%20==9){
                assertEquals(((tick-49)/20+1)&15,circuit.displays[PcbExamples.COUNTER_DISPLAY],"One increment per physical rising edge, wrapping after 15");
                assertTrue(-result.current("s")>.005,"Clock, memory and decoder spend electrical energy");
            }
            if(tick==389){
                assertEquals(-1,circuit.displays[PcbExamples.COUNTER_DISPLAY]);
                assertEquals(0,result.current("s"),1e-9);
                assertTrue(circuit.leds.isEmpty());
            }
        }
    }
}
