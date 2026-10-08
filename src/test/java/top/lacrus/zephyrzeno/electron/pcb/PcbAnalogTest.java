package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electron.circuit.DcSolver;

class PcbAnalogTest {
    private static final String[] P={"V","G","A","B","Y","Z"};
    private static final class Board {
        PcbCircuit circuit; long tick; DcSolver.Result result; double stress;
        Board(PcbLayout layout){circuit=new PcbCircuit(layout);}
        void step(double supply){
            var branches=new ArrayList<DcSolver.Branch>();circuit.stamp("p",P,branches,tick,false);
            branches.add(DcSolver.Branch.source("source","V","G",supply,.01));
            result=DcSolver.solve(branches);stress=circuit.update("p",result,n->true,tick++,false);
        }
        void run(double supply,int ticks){for(int j=0;j<ticks;j++)step(supply);}
        double voltage(int part,int pin){return result.voltage(circuit.net("p",circuit.layout.parts().get(part).pin(pin)))-result.voltage("G");}
    }
    @Test void analogIdsAndRotatedPadsSurviveSerialization(){
        assertEquals(26,PcbLayout.Type.R220.ordinal());assertEquals(34,PcbLayout.Type.BUCK5.ordinal());
        for(var type:PcbLayout.Type.values())if(type.analog())for(int turn=0;turn<4;turn++){
            var l=PcbLayout.empty().place(type,6,6,turn);var p=l.parts().getFirst();var pins=new HashSet<Integer>();
            for(int j=0;j<type.pins();j++)assertTrue(pins.add(p.pin(j)),type+" distinct pin "+j);
            assertEquals(l,PcbLayout.decode(l.encode()));
        }
    }
    @Test void resistorsAndDiodeHaveFiniteRatingsAndPolarity(){
        for(var type:List.of(PcbLayout.Type.R220,PcbLayout.Type.R100K,PcbLayout.Type.DIODE)){
            var part=new PcbAnalog(type);var edges=new ArrayList<DcSolver.Branch>();part.stamp("part",P,edges,0,0,false);
            edges.add(DcSolver.Branch.source("source","V","G",5,100));var result=DcSolver.solve(edges);
            assertTrue(result.current("part")>0);assertTrue(part.update("part",P,result,n->true,false)<1);
            if(type==PcbLayout.Type.DIODE){edges.set(edges.size()-1,DcSolver.Branch.source("source","V","G",-5,100));assertTrue(Math.abs(DcSolver.solve(edges).current("part"))<1e-8);}
        }
    }
    @Test void potentiometerDividesPaidInputAndSagsWithItsLoad(){
        var part=new PcbAnalog(PcbLayout.Type.POT10K);var edges=new ArrayList<DcSolver.Branch>();part.stamp("pot",P,edges,5,0,false);
        edges.add(DcSolver.Branch.source("source","V","G",5,.01));var r=DcSolver.solve(edges);
        assertEquals(2.5,r.voltage("A")-r.voltage("G"),1e-4);
        edges.add(DcSolver.Branch.resistor("load","A","G",1000));r=DcSolver.solve(edges);
        assertEquals(5d/7,r.voltage("A")-r.voltage("G"),1e-4);
        assertTrue(-5*r.current("source")>Math.pow(r.voltage("A")-r.voltage("G"),2)/1000);
    }
    @Test void capacitorStoresFiniteEnergyDischargesAndRejectsCorruptState(){
        var cap=new PcbAnalog(PcbLayout.Type.CAP100UF);
        for(int j=0;j<100;j++){
            var edges=new ArrayList<DcSolver.Branch>();cap.stamp("cap",P,edges,0,0,false);
            edges.add(DcSolver.Branch.source("source","V","G",5,10000));cap.update("cap",P,DcSolver.solve(edges),n->true,false);
        }
        assertTrue(cap.stored>4.9&&cap.stored<5);
        var saved=cap.snapshot();var restored=new PcbAnalog(PcbLayout.Type.CAP100UF);assertTrue(restored.restore(saved));
        var bad=saved.clone();bad[0]=Double.doubleToLongBits(Double.NaN);assertFalse(restored.restore(bad));assertArrayEquals(saved,restored.snapshot());
        double budget=.5*PcbAnalog.CAPACITANCE*cap.stored*cap.stored,spent=0;
        for(int j=0;j<180;j++){
            var edges=new ArrayList<DcSolver.Branch>();restored.stamp("cap",P,edges,0,0,false);edges.add(DcSolver.Branch.resistor("load","V","G",10000));
            var r=DcSolver.solve(edges);spent+=Math.pow(r.current("load"),2)*10000*.05;restored.update("cap",P,r,n->true,false);
        }
        assertTrue(restored.stored<.002);assertTrue(spent>0&&spent<=budget);
    }
    private static double logicStep(PcbAnalog part,double plus,double minus,boolean driven){
        var edges=new ArrayList<DcSolver.Branch>();part.stamp("ic",P,edges,0,part.level,false);
        edges.add(DcSolver.Branch.source("supply","V","G",5,.01));edges.add(DcSolver.Branch.source("plus","A","G",plus,.01));
        edges.add(DcSolver.Branch.source("minus","B","G",minus,.01));edges.add(DcSolver.Branch.resistor("load","Y","G",1000));
        var r=DcSolver.solve(edges);part.update("ic",P,r,n->driven,false);return r.current("load");
    }
    @Test void comparatorAndSchmittUseDifferentialAndHystereticThresholds(){
        var comparator=new PcbAnalog(PcbLayout.Type.COMPARATOR);logicStep(comparator,3,2,true);assertEquals(1,comparator.level);
        assertTrue(logicStep(comparator,3,2,true)>.004);logicStep(comparator,1,2,true);assertEquals(0,comparator.level);
        logicStep(comparator,4,2,false);assertEquals(2,comparator.level,"Floating input cannot become a valid rail");
        var schmitt=new PcbAnalog(PcbLayout.Type.SCHMITT);logicStep(schmitt,1,0,true);assertEquals(0,schmitt.level);
        logicStep(schmitt,2.5,0,true);assertEquals(0,schmitt.level);logicStep(schmitt,4,0,true);assertEquals(1,schmitt.level);
        logicStep(schmitt,2.5,0,true);assertEquals(1,schmitt.level);logicStep(schmitt,1,0,true);assertEquals(0,schmitt.level);
    }
    @Test void routedTimerChargesBeforeLightingAndDiodeSpeedsReset(){
        var f=new Board(PcbExamples.timer());assertEquals(1,f.circuit.layout.tilesX()*f.circuit.layout.tilesY());f.run(5,20);assertFalse(f.circuit.leds.get(PcbExamples.TIMER_LED));
        f.circuit.controls[PcbExamples.TIMER_SWITCH]=1;f.run(5,8);assertFalse(f.circuit.leds.get(PcbExamples.TIMER_LED),"RC delay must not turn into an instant switch");
        f.run(5,22);assertTrue(f.circuit.leds.get(PcbExamples.TIMER_LED));assertTrue(f.stress<1);
        var saved=f.circuit.snapshot();var copy=new PcbCircuit(f.circuit.layout);assertTrue(copy.restore(saved));assertArrayEquals(saved,copy.snapshot());f.circuit=copy;
        f.circuit.controls[PcbExamples.TIMER_SWITCH]=0;f.run(5,10);assertFalse(f.circuit.leds.get(PcbExamples.TIMER_LED));
    }
    @Test void buckAndMosfetDriveAnActualBoardWithoutCreatingEnergy(){
        var f=new Board(PcbExamples.regulated());assertEquals(30,f.circuit.maxSupplyVoltage());f.run(24,80);
        assertEquals(5,f.voltage(0,2),.03);assertFalse(f.circuit.leds.get(PcbExamples.REGULATED_LED));
        f.circuit.controls[PcbExamples.REGULATED_POT]=10;double input=0,output=0;
        for(int j=0;j<200;j++){f.step(24);input+=Math.max(0,-24*f.result.current("source"))*.05;output+=Math.max(0,-5*f.result.current("p:c0"))*.05;}
        assertTrue(f.circuit.leds.get(PcbExamples.REGULATED_LED));assertTrue(output>0&&output<=input*.85+.5);assertTrue(f.stress<1);
        f.circuit.controls[PcbExamples.REGULATED_POT]=0;f.run(24,10);assertFalse(f.circuit.leds.get(PcbExamples.REGULATED_LED));
        f.circuit.controls[PcbExamples.REGULATED_POT]=10;f.run(0,500);assertFalse(f.circuit.leds.get(PcbExamples.REGULATED_LED));assertTrue(f.voltage(0,2)<.5);
        var cold=new Board(PcbExamples.regulated());cold.circuit.controls[PcbExamples.REGULATED_POT]=10;cold.run(0,40);assertFalse(cold.circuit.leds.get(PcbExamples.REGULATED_LED));
    }
    @Test void olderDigitalSnapshotsRemainCompatibleAndAnalogCorruptionIsAtomic(){
        var digital=new PcbCircuit(PcbLayout.inverter());var current=digital.snapshot();var legacy=new long[3+digital.layout.parts().size()*24];
        System.arraycopy(current,0,legacy,0,3);legacy[0]=1;for(int j=0;j<digital.layout.parts().size();j++)System.arraycopy(current,3+j*27,legacy,3+j*24,24);
        assertTrue(digital.restore(legacy));var analog=new PcbCircuit(PcbExamples.timer());var valid=analog.snapshot();var bad=valid.clone();bad[3+2*27+24]=Double.doubleToLongBits(Double.POSITIVE_INFINITY);
        assertFalse(analog.restore(bad));assertArrayEquals(valid,analog.snapshot());
    }
}
