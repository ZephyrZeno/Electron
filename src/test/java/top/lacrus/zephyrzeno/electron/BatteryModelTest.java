package top.lacrus.zephyrzeno.electron;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import static top.lacrus.zephyrzeno.electron.circuit.DcSolver.Branch.*;

class BatteryModelTest {
    @Test void outputFollowsOhmsLawAndDebitsConversionLoss(){
        var r=DcSolver.solve(List.of(BatteryModel.output("b","p","n",10,true),resistor("r","p","n",100)));
        assertEquals(5d/105,r.current("r"),1e-9);assertEquals(5d/105*100,r.voltage("p"),1e-9);
        assertEquals(10-5*(5d/105)*.05/.9,BatteryModel.nextEnergy(10,0,0,r.current("b")),1e-9);
    }
    @Test void shortCircuitLimitsCurrentAndSagsVoltage(){
        var r=DcSolver.solve(List.of(BatteryModel.output("b","p","n",10,true),resistor("r","p","n",.01)));
        assertEquals(.1,r.current("r"),1e-8);assertEquals(.001,r.voltage("p"),1e-8);
        assertEquals(-r.current("b"),r.current("r"),1e-9);
    }
    @Test void finalTickCannotSpendMoreStoredEnergyThanAvailable(){
        double energy=.001;
        var r=DcSolver.solve(List.of(BatteryModel.output("b","p","n",energy,true),source("hostile","n","p",12,.01)));
        assertTrue(-r.current("b")<=energy*.9/(5*.05)+1e-8);
        assertEquals(0,BatteryModel.nextEnergy(energy,0,0,r.current("b")),1e-8);
    }
    @Test void emptyAndDisabledBatteriesDoNotPowerLoads(){
        for(var output:List.of(BatteryModel.output("b","p","n",0,true),BatteryModel.output("b","p","n",100,false))){
            var r=DcSolver.solve(List.of(output,resistor("r","p","n",220)));assertEquals(0,r.current("r"),1e-10);
        }
    }
    @Test void outputBlocksBackfeedAndCannotBeChargedThroughWrongPort(){
        var r=DcSolver.solve(List.of(BatteryModel.output("b","p","n",10,true),source("external","p","n",12,5)));
        assertTrue(Math.abs(r.current("b"))<1e-8);assertEquals(10,BatteryModel.nextEnergy(10,0,0,r.current("b")),1e-9);
    }
    @Test void chargingUsesActualTerminalPowerAndStopsWhenFull(){
        var r=DcSolver.solve(List.of(source("s","p","n",5,5),BatteryModel.input("charge","p","n",0)));
        assertEquals(.25,r.current("charge"),1e-8);assertEquals(3.75,r.voltage("p"),1e-8);
        assertEquals(.0375,BatteryModel.nextEnergy(0,r.voltage("p"),r.current("charge"),0),1e-8);
        var full=DcSolver.solve(List.of(source("s","p","n",5,5),BatteryModel.input("charge","p","n",200)));
        assertTrue(full.current("charge")<1e-8);
        var near=DcSolver.solve(List.of(source("s","p","n",6,1),BatteryModel.input("charge","p","n",199.999)));
        assertTrue(BatteryModel.nextEnergy(199.999,near.voltage("p"),near.current("charge"),0)<=200);
    }
    @Test void reverseInputDoesNotStoreEnergy(){
        var r=DcSolver.solve(List.of(source("s","n","p",5,5),BatteryModel.input("charge","p","n",10)));
        assertTrue(Math.abs(r.current("charge"))<1e-8);
        assertEquals(10,BatteryModel.nextEnergy(10,r.voltage("p"),r.current("charge"),0),1e-9);
    }
    @Test void connectingOutputBackToInputAlwaysLosesEnergy(){
        double energy=20;
        for(int tick=0;tick<1200;tick++){
            var r=DcSolver.solve(List.of(BatteryModel.output("out","p","n",energy,true),BatteryModel.input("in","p","n",energy)));
            double next=BatteryModel.nextEnergy(energy,r.voltage("p"),r.current("in"),r.current("out"));
            assertTrue(next<=energy+1e-8,"Closed charging loop must not create energy");energy=next;
        }
        assertTrue(energy<1);
    }
}
