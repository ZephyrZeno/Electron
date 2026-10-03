package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electron.circuit.DcSolver;

class PcbCircuitTest {
    @Test void editableInverterHasFourDistinctNetsAndWorksUnderLoad(){
        var l=PcbLayout.inverter();var n=l.nets();var p=l.parts().getFirst();
        for(int i=0;i<4;i++)for(int j=i+1;j<4;j++)assertNotEquals(n[p.pin(new int[]{0,1,2,4}[i])],n[p.pin(new int[]{0,1,2,4}[j])],"Reference design must not cross-connect copper");
        assertEquals(n[l.ports()[0]],n[p.pin(0)]);assertEquals(n[l.ports()[1]],n[p.pin(1)]);assertEquals(n[l.ports()[2]],n[p.pin(2)]);assertEquals(n[l.ports()[4]],n[p.pin(4)]);
        for(int input=0;input<2;input++){
            var c=new PcbCircuit(l);var r=run(c,5,input*5,1000,20);
            assertEquals(input==0?5*1000d/1100:0,r.voltage("Y")-r.voltage("G"),.035);
            double paid=-r.current("supply")*5-r.current("input")*input*5;
            assertTrue(paid>=Math.pow(r.voltage("Y")-r.voltage("G"),2)/1000-1e-7,"Gate output must load the real supply");
        }
    }
    private DcSolver.Result run(PcbCircuit circuit,double supply,double input,double load,int ticks){
        DcSolver.Result r=null;
        for(int t=0;t<ticks;t++){var b=new ArrayList<DcSolver.Branch>();circuit.stamp("pcb",new String[]{"V","G","A","B","Y","Z"},b,t,false);b.add(DcSolver.Branch.source("supply","V","G",supply,.01));b.add(DcSolver.Branch.source("input","A","G",input,.01));b.add(DcSolver.Branch.resistor("load","Y","G",load));r=DcSolver.solve(b);circuit.update("pcb",r,node->true,t,false);}return r;
    }
    @Test void noSupplyProducesNoLogicEnergy(){var c=new PcbCircuit(PcbLayout.inverter());var r=run(c,0,0,1000,20);assertEquals(0,r.current("load"),1e-9);}
    @Test void shortedOutputDrawsRealCurrentAndReportsOverload(){
        var c=new PcbCircuit(PcbLayout.inverter());var r=run(c,5,0,.01,20);
        assertTrue(-r.current("supply")>.04);assertTrue(c.update("pcb",r,n->true,21,false)>1);
    }
    @Test void unknownFloatingInputsDoNotDrive(){
        var c=new PcbCircuit(PcbLayout.inverter());DcSolver.Result r=null;
        for(int t=0;t<15;t++){var b=new ArrayList<DcSolver.Branch>();c.stamp("pcb",new String[]{"V","G","A","B","Y","Z"},b,t,false);b.add(DcSolver.Branch.source("supply","V","G",5,.01));b.add(DcSolver.Branch.resistor("load","Y","G",1000));r=DcSolver.solve(b);c.update("pcb",r,n->false,t,false);}
        assertEquals(0,r.current("load"),1e-8);
    }
    @Test void outlineHolesRotationAndCopperRoundTrip(){
        var l=PcbLayout.empty().resize(12,10).place(PcbLayout.Type.LED,3,3,1).cut(10,0).cut(9,5).route(PcbLayout.point(1,0),PcbLayout.point(7,2),false);
        assertEquals(l,PcbLayout.decode(l.encode()));assertFalse(l.contains(9,5));assertEquals(PcbLayout.point(4,3),l.parts().getFirst().pin(0));
    }
    @Test void millingCannotRemovePinsPackagesCopperOrSplitBoard(){
        assertThrows(IllegalArgumentException.class,()->PcbLayout.empty().cut(0,1));
        var l=PcbLayout.empty().place(PcbLayout.Type.AND,4,3,0);assertThrows(IllegalArgumentException.class,()->l.cut(5,4));
        var t=PcbLayout.empty().route(PcbLayout.point(3,2),PcbLayout.point(8,2),false);assertThrows(IllegalArgumentException.class,()->t.cut(6,2));
        var holes=new HashSet<Integer>();for(int y=0;y<12;y++)holes.add(PcbLayout.point(6,y));assertThrows(IllegalArgumentException.class,()->new PcbLayout(List.of(),List.of(),16,12,holes));
    }
    @Test void crossingsConnectAndEraseBreaksThem(){
        var l=PcbLayout.empty().route(PcbLayout.point(2,5),PcbLayout.point(10,5),false).route(PcbLayout.point(6,2),PcbLayout.point(6,8),true);
        assertEquals(l.nets()[PcbLayout.point(2,5)],l.nets()[PcbLayout.point(6,8)]);
        var erased=l.erase(6,5);assertNotEquals(erased.nets()[PcbLayout.point(2,5)],erased.nets()[PcbLayout.point(6,8)]);
    }
    @Test void movingPackagePreservesCopperAndCannotOverlap(){
        var l=PcbLayout.empty().place(PcbLayout.Type.R1K,2,2,0).place(PcbLayout.Type.LED,9,6,0).route(0,5,false);
        assertEquals(l.tracks(),l.move(0,3,5,2).tracks());assertThrows(IllegalArgumentException.class,()->l.move(0,9,6,0));
    }
    @Test void invalidAndOversizedSerializedInputsAreRejected(){
        for(String text:List.of("", "9||","2|100,2,2,0;||16,12|","2||0,191;|16,12|","2|||99,99|","x".repeat(8001)))assertThrows(IllegalArgumentException.class,()->PcbLayout.decode(text));
    }
    @Test void shapeSpecificPortsStayOnCopper(){for(int[] d:new int[][]{{10,8},{12,8},{14,10},{16,12}}){var l=PcbLayout.empty().resize(d[0],d[1]);assertEquals(6,Arrays.stream(l.ports()).distinct().count());for(int at:l.ports())assertTrue(l.contains(PcbLayout.x(at),PcbLayout.y(at)));}}
}
