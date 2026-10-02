package top.lacrus.zephyrzeno.electron;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import static top.lacrus.zephyrzeno.electron.circuit.DcSolver.Branch.*;

class CapacitorModelTest {
    @Test void rcChargeMatchesTimeConstantAndApproachesSupply(){
        double v=0;
        for(int tick=1;tick<=1000;tick++){
            var r=DcSolver.solve(List.of(source("s","p","n",5,5),resistor("r","p","c",100),CapacitorModel.branch("c","c","n",v)));
            double next=CapacitorModel.nextVoltage(v,r.current("c"));assertTrue(next>v&&next<5);v=next;
            if(tick==212)assertEquals(5*(1-Math.exp(-1)),v,.007); // t = (100 + 5 + ESR) C
        }
        assertTrue(v>4.94);
    }
    @Test void disconnectingSupplyDischargesThroughLoadWithNoCreatedEnergy(){
        double v=5,delivered=0;
        for(int tick=0;tick<1000;tick++){
            var r=DcSolver.solve(List.of(CapacitorModel.branch("c","p","n",v),resistor("r","p","n",100)));
            assertTrue(r.current("c")<0);double next=CapacitorModel.nextVoltage(v,r.current("c"));
            assertTrue(next>=0&&next<v);
            delivered+=r.current("r")*r.current("r")*100*CapacitorModel.STEP_SECONDS;v=next;
        }
        assertTrue(v<.04);assertTrue(delivered>1.2&&delivered+CapacitorModel.joules(v)<CapacitorModel.joules(5));
    }
    @Test void noLoadOnlyLeaksSlowlyAndZeroChargeCannotCreatePower(){
        var empty=DcSolver.solve(List.of(CapacitorModel.branch("c","p","n",0),resistor("load","p","n",220)));
        assertEquals(0,empty.current("load"),0);
        double v=5;
        for(int tick=0;tick<1200;tick++){
            var r=DcSolver.solve(List.of(CapacitorModel.branch("c","p","n",v)));
            assertEquals(0,r.current("c"),1e-10);v=CapacitorModel.nextVoltage(v,r.current("c"));
        }
        assertTrue(v<5&&v>4.99);
    }
    @Test void storedChargeKeepsLedAliveAfterButtonOpens(){
        double v=4,first=0,last=0;
        for(int tick=0;tick<1000;tick++){
            var r=DcSolver.solve(List.of(CapacitorModel.branch("c","p","n",v),resistor("r","p","a",220),led("d","a","n")));
            if(tick==0)first=r.current("d");last=r.current("d");v=CapacitorModel.nextVoltage(v,r.current("c"));
        }
        assertTrue(first>.008);assertTrue(last>0&&last<first/5);assertTrue(v>2&&v<2.3);
    }
    @Test void capacitorSeriesAndParallelHaveDifferentEffectiveCapacity(){
        double seriesA=0,seriesB=0,parallelA=0,parallelB=0;
        for(int tick=0;tick<100;tick++){
            var s=DcSolver.solve(List.of(source("s","p","n",5,100),CapacitorModel.branch("a","p","m",seriesA),CapacitorModel.branch("b","m","n",seriesB)));
            var p=DcSolver.solve(List.of(source("s","p","n",5,100),CapacitorModel.branch("a","p","n",parallelA),CapacitorModel.branch("b","p","n",parallelB)));
            seriesA=CapacitorModel.nextVoltage(seriesA,s.current("a"));seriesB=CapacitorModel.nextVoltage(seriesB,s.current("b"));
            parallelA=CapacitorModel.nextVoltage(parallelA,p.current("a"));parallelB=CapacitorModel.nextVoltage(parallelB,p.current("b"));
        }
        assertEquals(seriesA,seriesB,1e-9);assertEquals(parallelA,parallelB,1e-9);
        assertEquals(5*(1-Math.exp(-5/(102*.05))),seriesA+seriesB,.012);
        assertEquals(5*(1-Math.exp(-5/(100.5*.2))),parallelA,.003);
        assertTrue(seriesA+seriesB>2*parallelA);
    }
    @Test void polarityOvervoltageAndRippleHaveEnforcedRatings(){
        assertTrue(CapacitorModel.overloadRatio(5,.02)<1);
        assertTrue(CapacitorModel.overloadRatio(12,0)>1);
        assertTrue(CapacitorModel.overloadRatio(-2,0)>1);
        assertTrue(CapacitorModel.overloadRatio(5,.5)>1);
    }
}
