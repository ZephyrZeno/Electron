package top.lacrus.zephyrzeno.electron;

import org.junit.jupiter.api.Test;
import java.util.List;
import top.lacrus.zephyrzeno.electron.circuit.DcSolver;
import top.lacrus.zephyrzeno.electron.power.GridModel;
import static org.junit.jupiter.api.Assertions.*;

final class GridModelTest {
    @Test void emptyAndDisabledSourcesCannotSupplyPower(){
        for(var branch:List.of(GridModel.source("s","a","b",0,true),GridModel.source("s","a","b",4000,false))){var r=DcSolver.solve(List.of(branch,DcSolver.Branch.resistor("load","a","b",240)));assertEquals(0,r.current("load"),1e-9);}
    }
    @Test void sourceLimitsEnergyWithinOneTick(){
        var r=DcSolver.solve(List.of(GridModel.source("s","a","b",.1,true),DcSolver.Branch.resistor("short","a","b",.01)));
        assertTrue(-r.current("s")*240*.05<=.100001);assertEquals(0,GridModel.consume(.1,r.current("s")),1e-8);
    }
    @Test void stationCannotCreateEnergyAndOutputHasCurrentLimit(){
        double energy=10,spent=0;
        for(int i=0;i<100;i++){
            var r=DcSolver.solve(List.of(GridModel.stationOutput("s","a","b",energy,true),DcSolver.Branch.resistor("load","a","b",.1)));
            assertTrue(Math.abs(r.current("s"))<=4.00001);spent+=Math.max(0,-r.current("s"))*24*.05;energy=GridModel.stationEnergy(energy,0,0,r.current("s"));
        }
        assertTrue(spent<=10.00001);assertEquals(0,energy,1e-8);
    }
    @Test void stationEfficiencyDebitsMeasuredInput(){assertEquals(10+240*.2*.92*.05-24*.5*.05,GridModel.stationEnergy(10,240,.2,-.5),1e-9);}
    @Test void stationWaitsForRealStartupEnergy(){
        assertEquals(0,GridModel.stationOutput("s","a","b",1e-8,true).emf());
        assertEquals(24,GridModel.stationOutput("s","a","b",.001,true).emf());
    }
    @Test void windAlwaysRespectsNameplate(){for(long t=0;t<200000;t+=100)assertTrue(GridModel.wind(t,300,true)>0&&GridModel.wind(t,300,true)<=1);}
}
