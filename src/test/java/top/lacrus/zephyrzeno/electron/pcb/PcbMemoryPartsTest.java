package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electron.circuit.DcSolver;

class PcbMemoryPartsTest {
    private static final class Fixture {
        PcbCircuit circuit;long tick;DcSolver.Result result;
        Fixture(PcbLayout.Type type){
            var part=new PcbLayout.Part(type,6,4,0);var groups=new LinkedHashMap<String,List<Integer>>();
            groups.put("V",List.of(part.pin(0),PcbLayout.ports(32,24)[0]));groups.put("G",List.of(part.pin(1),PcbLayout.ports(32,24)[1]));
            for(int pin=2;pin<type.pins();pin++)groups.put("p"+pin,List.of(part.pin(pin)));
            circuit=new PcbCircuit(PcbRouter.route(List.of(part),groups,32,24,2));
        }
        void drive(double supply,int... inputs){for(int i=0;i<6;i++)step(supply,false,inputs);}
        void step(double supply,boolean broken,int... inputs){
            var part=circuit.layout.parts().getFirst();var edges=new ArrayList<DcSolver.Branch>();var floating=new HashSet<String>();
            circuit.stamp("p",new String[]{"V","G","A","B","Y","Z"},edges,tick,broken);
            edges.add(DcSolver.Branch.source("supply","V","G",supply,.01));
            for(int i=0;i<inputs.length;i++){String node=circuit.net("p",part.pin(i+2));
                if(inputs[i]==3)floating.add(node);
                else if(inputs[i]==2)edges.add(DcSolver.Branch.source("input"+i,node,"G",supply*.5,100));
                else edges.add(DcSolver.Branch.resistor("input"+i,node,inputs[i]==1?"V":"G",100));
            }
            for(int out=0;out<part.type().outputs();out++)edges.add(DcSolver.Branch.resistor("load"+out,circuit.net("p",part.pin(part.type().outputPin(out))),"G",1000));
            result=DcSolver.solve(edges);circuit.update("p",result,n->!floating.contains(n),tick++,broken);
        }
        int output(){int value=0;for(int i=0;i<circuit.layout.parts().getFirst().type().outputs();i++){int state=circuit.outputState(0,i);if(state>1)return -state;value|=state<<i;}return value;}
        void reload(){var old=circuit;circuit=new PcbCircuit(old.layout);assertTrue(circuit.restore(old.snapshot()));}
    }

    @Test void eightPinPackagesKeepAllPadsDistinctAcrossRotationsAndSerialization(){
        assertEquals(22,PcbLayout.Type.TFF.ordinal());assertEquals(25,PcbLayout.Type.SHIFT4.ordinal());
        for(var type:List.of(PcbLayout.Type.COUNTER4,PcbLayout.Type.SHIFT4))for(int turn=0;turn<4;turn++){
            var layout=PcbLayout.empty().place(type,4,4,turn);var part=layout.parts().getFirst();var pads=new HashSet<Integer>();
            for(int pin=0;pin<8;pin++){int at=part.pin(pin);assertTrue(pads.add(at));assertTrue(PcbLayout.x(at)==4||PcbLayout.x(at)==6||PcbLayout.y(at)==4||PcbLayout.y(at)==6);}
            assertEquals(layout,PcbLayout.decode(layout.encode()));for(int out=0;out<4;out++)assertEquals(out+4,type.outputPin(out));
        }
        assertEquals(5,PcbLayout.Type.TFF.outputPin(0));assertEquals(6,PcbLayout.Type.TFF.pins());
    }
    @Test void counterNeedsResetCountsOnlyRisingEdgesAndWrapsAtSixteen(){
        var f=new Fixture(PcbLayout.Type.COUNTER4);f.drive(5,1,0);assertEquals(-2,f.output(),"Already-high clock cannot initialize unknown memory");
        f.drive(5,1,1);assertEquals(0,f.output());f.drive(5,1,0);assertEquals(0,f.output(),"Releasing reset is not an edge");
        for(int value=1;value<=17;value++){f.drive(5,0,0);f.drive(5,1,0);assertEquals(value&15,f.output());f.drive(5,1,0);assertEquals(value&15,f.output());}
        assertTrue(f.result.current("load0")>.004);assertTrue(-f.result.current("supply")>=f.result.current("load0"),"Output draws energy from paid rails");
    }
    @Test void tffEnableResetAndUnknownInputsHaveDistinctEffects(){
        var f=new Fixture(PcbLayout.Type.TFF);f.drive(5,1,0,1);assertEquals(0,f.output());
        f.drive(5,1,0,0);f.drive(5,1,1,0);assertEquals(1,f.output());f.drive(5,1,1,0);assertEquals(1,f.output());
        f.drive(5,0,0,0);f.drive(5,0,1,0);assertEquals(1,f.output(),"T low disables toggling");
        f.drive(5,3,0,0);f.drive(5,3,1,0);assertEquals(-2,f.output(),"Floating T corrupts the sampled state");
        f.drive(5,3,1,1);assertEquals(0,f.output(),"Asynchronous reset resolves unknown memory");
    }
    @Test void srForbiddenStateStaysUnknownUntilAnExplicitSetOrReset(){
        var f=new Fixture(PcbLayout.Type.SR_LATCH);f.drive(5,0,1);assertEquals(0,f.output());f.drive(5,1,0);assertEquals(1,f.output());
        f.drive(5,0,0);assertEquals(1,f.output());f.drive(5,1,1);assertEquals(-2,f.output());f.drive(5,0,0);assertEquals(-2,f.output());
        f.drive(5,0,1);assertEquals(0,f.output());f.drive(5,1,3);assertEquals(-2,f.output());
    }
    @Test void shiftRegisterRecoversOneKnownBitPerEdgeAndDrivesFourIndependentLoads(){
        var f=new Fixture(PcbLayout.Type.SHIFT4);f.drive(5,0,0);assertEquals(-2,f.output());
        for(int bit:new int[]{1,0,1,1}){f.drive(5,bit,0);f.drive(5,bit,1);}
        assertEquals(11,f.output());assertTrue(f.result.current("load0")>.004);assertTrue(f.result.current("load1")>.004);assertEquals(0,f.result.current("load2"),1e-8);assertTrue(f.result.current("load3")>.004);
        f.drive(5,0,1);assertEquals(11,f.output(),"Changing D while clock stays high does not shift");
        f.drive(5,3,0);f.drive(5,3,1);assertEquals(2,f.circuit.outputState(0,0));assertEquals(1,f.circuit.outputState(0,1));assertEquals(1,f.circuit.outputState(0,2));assertEquals(0,f.circuit.outputState(0,3));
    }
    @Test void lossBrownoutAndBrokenSupplyReleaseOutputsAndEraseVolatileMemory(){
        var f=new Fixture(PcbLayout.Type.TFF);f.drive(5,1,0,1);f.drive(5,1,0,0);f.drive(5,1,1,0);assertEquals(1,f.output());
        f.drive(0,1,1,0);assertEquals(-3,f.output());assertEquals(0,f.result.current("load0"),1e-9);
        f.drive(5,1,1,0);assertEquals(-2,f.output(),"Restoring power with CLK high cannot replay an edge");
        f.drive(5,1,1,1);assertEquals(0,f.output());f.drive(4,1,1,0);assertEquals(-2,f.output());
        f.drive(5,1,0,1);for(int i=0;i<6;i++)f.step(5,true,1,1,0);assertEquals(-3,f.output());assertTrue(Math.abs(f.result.current("load0"))<1e-8);
    }
    @Test void indeterminateClockCannotSilentlyProduceAnApparentlyValidCount(){
        var f=new Fixture(PcbLayout.Type.COUNTER4);f.drive(5,0,1);f.drive(5,0,0);assertEquals(0,f.output());f.drive(5,2,0);assertEquals(-2,f.output());
        f.drive(5,0,1);assertEquals(0,f.output());f.drive(5,3,0);assertEquals(-2,f.output());
    }
    @Test void dffRetainsUnknownSamplesInsteadOfSilentlyResettingThemToZero(){
        var f=new Fixture(PcbLayout.Type.DFF);f.drive(5,1,0);f.drive(5,1,1);assertEquals(1,f.output());
        f.drive(5,3,0);f.drive(5,3,1);assertEquals(-2,f.output());f.drive(5,0,1);assertEquals(-2,f.output());
        f.drive(5,0,0);f.drive(5,0,1);assertEquals(0,f.output());
    }
    @Test void savedCounterMemoryAndHighClockResumeWithoutInventingAnEdge(){
        var f=new Fixture(PcbLayout.Type.COUNTER4);f.drive(5,0,1);f.drive(5,0,0);f.drive(5,1,0);assertEquals(1,f.output());
        f.reload();f.drive(5,1,0);assertEquals(1,f.output());f.drive(5,0,0);f.drive(5,1,0);assertEquals(2,f.output());
        var state=f.circuit.snapshot();var bad=state.clone();bad[15]=Integer.MAX_VALUE;assertFalse(f.circuit.restore(bad));assertArrayEquals(state,f.circuit.snapshot(),"Invalid state must not partially overwrite live registers");
        var different=new PcbCircuit(f.circuit.layout.move(0,10,10,1));assertFalse(different.restore(state));
    }
}
