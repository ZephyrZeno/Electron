package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electron.circuit.DcSolver;

class PcbSequentialTest {
    private static final class Fixture {
        final PcbCircuit c;int tick;DcSolver.Result result;
        Fixture(PcbLayout.Type type){var p=new PcbLayout.Part(type,6,4,0);var nets=new LinkedHashMap<String,List<Integer>>();nets.put("V",List.of(p.pin(0),PcbLayout.ports(32,24)[0]));nets.put("G",List.of(p.pin(1),PcbLayout.ports(32,24)[1]));for(int i=2;i<type.pins();i++)nets.put("n"+i,List.of(p.pin(i)));c=new PcbCircuit(PcbRouter.route(List.of(p),nets,32,24,2));}
        void drive(double voltage,int... inputs){for(int step=0;step<10;step++){var edges=new ArrayList<DcSolver.Branch>();c.stamp("p",new String[]{"V","G","A","B","Y","Z"},edges,tick,false);edges.add(DcSolver.Branch.source("s","V","G",voltage,.01));var p=c.layout.parts().getFirst();for(int i=0;i<inputs.length;i++)edges.add(DcSolver.Branch.resistor("i"+i,c.net("p",p.pin(i+2)),inputs[i]==1?"V":"G",100));if(p.type().outputs()>0)edges.add(DcSolver.Branch.resistor("load",c.net("p",p.pin(p.type()==PcbLayout.Type.MUX?5:4)),"G",1000));result=DcSolver.solve(edges);c.update("p",result,n->true,tick++,false);}}
        boolean high(){return result.current("load")>.003;}
    }
    @Test void flipFlopSamplesEdgesAndHoldsData(){var f=new Fixture(PcbLayout.Type.DFF);f.drive(5,1,0);assertFalse(f.high());f.drive(5,1,1);assertTrue(f.high());f.drive(5,0,1);assertTrue(f.high(),"Holding CLK high must not make DFF transparent");f.drive(5,0,0);assertTrue(f.high());f.drive(5,0,1);assertFalse(f.high());f.drive(0,1,1);assertEquals(0,f.result.current("load"),1e-8);}
    @Test void triStateDisconnectsAndMuxSelectsTheCorrectPaidInput(){var tri=new Fixture(PcbLayout.Type.TRISTATE);tri.drive(5,1,1);assertTrue(tri.high());tri.drive(5,1,0);assertEquals(0,tri.result.current("load"),1e-8);var mux=new Fixture(PcbLayout.Type.MUX);mux.drive(5,0,1,0);assertFalse(mux.high());mux.drive(5,0,1,1);assertTrue(mux.high());mux.drive(0,0,1,1);assertEquals(0,mux.result.current("load"),1e-8);}
    @Test void rgbChannelsAreIndependentAndPayForBrightness(){var f=new Fixture(PcbLayout.Type.RGB);f.drive(5,1,0,1);assertEquals(5,f.c.displays[0]);double two=-f.result.current("s");f.drive(5,1,1,1);assertEquals(7,f.c.displays[0]);assertTrue(-f.result.current("s")>two+.002);f.drive(0,1,1,1);assertEquals(-1,f.c.displays[0]);}
    @Test void copperMeshPreservesEverySurfaceEdge(){var l=SingleBoardCalculator.layout();var edges=new HashSet<PcbLayout.Track>();for(var r:PcbCopperMesh.compile(l)){for(int x=r.x0();x<r.x1();x++)edges.add(new PcbLayout.Track(PcbLayout.point(x,r.y0()),PcbLayout.point(x+1,r.y0())));for(int y=r.y0();y<r.y1();y++)edges.add(new PcbLayout.Track(PcbLayout.point(r.x0(),y),PcbLayout.point(r.x0(),y+1)));}assertEquals(new HashSet<>(l.tracks().stream().filter(t->PcbLayout.layer(t.a())==0).toList()),edges);assertTrue(PcbCopperMesh.compile(l).size()<edges.size()/2);}
}
