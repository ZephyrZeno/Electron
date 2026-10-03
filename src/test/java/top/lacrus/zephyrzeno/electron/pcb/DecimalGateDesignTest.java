package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electron.circuit.DcSolver;
import top.lacrus.zephyrzeno.electron.logic.WireLogic;

class DecimalGateDesignTest {
    @Test void allTwoHundredDecimalCasesUseFiniteSupplyAndActualPcbCopper(){
        var design=DecimalGateDesign.create();assertEquals(16,design.cells().stream().filter(DecimalGateDesign.Cell::pcb).count());
        var boards=new HashMap<String,PcbCircuit>();for(var c:design.cells())if(c.pcb())boards.put(c.id(),new PcbCircuit(PcbLayout.halfAdder()));
        for(int op=0;op<2;op++)for(int a=0;a<10;a++)for(int b=0;b<10;b++){
            var states=new HashMap<String,Integer>();for(var c:design.cells())states.put(c.id(),3);boards.values().forEach(PcbCircuit::reset);DcSolver.Result r=null;
            for(int tick=0;tick<64;tick++){
                var edges=new ArrayList<DcSolver.Branch>();edges.add(DcSolver.Branch.source("source","V","G",5,.1));
                for(int bit=0;bit<4;bit++)for(int operand=0;operand<2;operand++){String n=(operand==0?"A":"B")+bit;edges.add(DcSolver.Branch.resistor(n,n,(((operand==0?a:b)>>bit)&1)==1?"V":"G",100));}
                edges.add(DcSolver.Branch.resistor("switch","SUB",op==1?"V":"G",.02));
                for(var cell:design.cells()){
                    var p=cell.pins();if(cell.pcb())boards.get(cell.id()).stamp(cell.id(),p.toArray(String[]::new),edges,tick,false);
                    else{int state=states.get(cell.id());edges.add(DcSolver.Branch.resistor(cell.id(),"V","G",10000));for(int i=2;i<4;i++)edges.add(DcSolver.Branch.resistor(cell.id()+"in"+i,p.get(i),"G",1e7));edges.add(DcSolver.Branch.resistor(cell.id()+"out",p.get(4),state==1?"V":"G",state<2?100:1e12));}
                }
                r=DcSolver.solve(edges);double v=r.voltage("V")-r.voltage("G");assertTrue(v>4.5);var updates=new HashMap<String,Integer>();
                for(var cell:design.cells()){if(cell.pcb())boards.get(cell.id()).update(cell.id(),r,n->true,tick,false);else{var p=cell.pins();updates.put(cell.id(),WireLogic.evaluate(cell.gate(),WireLogic.input(r.voltage(p.get(2))-r.voltage("G"),v,true),WireLogic.input(r.voltage(p.get(3))-r.voltage("G"),v,true)));}}states.putAll(updates);
            }
            int units=0;for(int i=0;i<4;i++)units|=bit(r,design.units().get(i))<<i;int result=units+10*bit(r,design.tens());if(bit(r,design.negative())==1)result=-result;
            assertEquals(op==0?a+b:a-b,result,"A="+a+" B="+b+" subtract="+op);assertTrue(-r.current("source")>.01,"Logic gates consume supply energy");
        }
    }
    private static int bit(DcSolver.Result r,String n){double v=r.voltage(n)-r.voltage("G");assertTrue(v<1||v>4,"Output must settle to a valid logic level");return v>2.5?1:0;}
    @Test void halfAdderSixIndependentPadsMatchItsEditableCopper(){var l=PcbLayout.halfAdder();var nets=l.nets();for(int i=0;i<6;i++){assertEquals(nets[l.ports()[i]],nets[l.parts().getFirst().pin(i)]);for(int j=0;j<i;j++)assertNotEquals(nets[l.ports()[i]],nets[l.ports()[j]]);}}
    @Test void sparseNetworkSolvesMoreThanOld256NodeLimit(){var edges=new ArrayList<DcSolver.Branch>();edges.add(DcSolver.Branch.source("source","n0","G",5,.1));for(int i=0;i<600;i++)edges.add(DcSolver.Branch.resistor("r"+i,"n"+i,i==599?"G":"n"+(i+1),1));var r=DcSolver.solve(edges);assertEquals(5/600.1,r.current("r599"),1e-8);assertEquals(5*300/600.1,r.voltage("n300"),1e-7);}
}
