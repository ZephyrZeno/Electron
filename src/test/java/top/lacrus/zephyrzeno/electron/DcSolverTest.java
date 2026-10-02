package top.lacrus.zephyrzeno.electron;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electron.circuit.DcSolver;
import static top.lacrus.zephyrzeno.electron.circuit.DcSolver.Branch.*;

class DcSolverTest {
    @Test void seriesDividesVoltageAndKeepsOneCurrent(){
        var r=DcSolver.solve(List.of(source("s","p","n",5,5),resistor("r1","p","m",100),resistor("r2","m","n",220)));
        double i=5d/325;
        assertEquals(i,r.current("r1"),1e-10);assertEquals(i,r.current("r2"),1e-10);
        assertEquals(100d/220,(r.voltage("p")-r.voltage("m"))/r.voltage("m"),1e-10);
        assertEquals(5,5*i+r.voltage("p"),1e-10);
    }
    @Test void parallelSharesVoltageAndAddsUnequalBranchCurrents(){
        var r=DcSolver.solve(List.of(source("s","p","n",5,5),resistor("r1","p","n",100),resistor("r2","p","n",220)));
        double equivalent=1/(1d/100+1d/220),total=5/(5+equivalent);
        assertEquals(total,-r.current("s"),1e-10);
        assertEquals(total,r.current("r1")+r.current("r2"),1e-10);
        assertEquals(2.2,r.current("r1")/r.current("r2"),1e-10);
        assertEquals(r.current("r1")*100,r.current("r2")*220,1e-10);
    }
    @Test void mixedCircuitAndBranchOpeningRedistributeCurrent(){
        double parallel=1/(1d/220+1d/1000),total=5/(5+100+parallel);
        var closed=DcSolver.solve(List.of(source("s","p","n",5,5),resistor("common","p","m",100),resistor("a","m","n",220),resistor("b","m","n",1000)));
        assertEquals(total,closed.current("common"),1e-10);
        assertEquals(total,closed.current("a")+closed.current("b"),1e-10);
        assertEquals(parallel*total,closed.voltage("m"),1e-10);
        var opened=DcSolver.solve(List.of(source("s","p","n",5,5),resistor("common","p","m",100),resistor("a","m","n",220),resistor("b","m","n",1e12)));
        assertTrue(opened.current("common")<closed.current("common"));
        assertTrue(opened.current("a")>closed.current("a"));
        assertEquals(5d/325,opened.current("a"),1e-10);
    }
    @Test void independentLedBranchesDoNotExtinguishEachOther(){
        var both=DcSolver.solve(List.of(source("s","p","n",5,5),resistor("r1","p","a",220),led("d1","a","n"),resistor("r2","p","b",220),led("d2","b","n")));
        assertEquals(.012,both.current("d1"),1e-9);assertEquals(.012,both.current("d2"),1e-9);
        var open=DcSolver.solve(List.of(source("s","p","n",5,5),resistor("r1","p","a",220),led("d1","a","open"),resistor("r2","p","b",220),led("d2","b","n")));
        assertEquals(0,open.current("d1"),1e-9);assertTrue(open.current("d2")>.012);
    }
    @Test void siliconDiodeDropAndReverseIsolation(){
        var forward=DcSolver.solve(List.of(source("s","+","-",5,5),resistor("r","+","a",220),diode("d","a","-")));
        assertEquals(4.3/227,forward.current("d"),1e-9);assertEquals(.7+2*forward.current("d"),forward.voltage("a"),1e-8);
        var reverse=DcSolver.solve(List.of(source("s","+","-",5,5),resistor("r","+","a",220),diode("d","-","a")));assertTrue(Math.abs(reverse.current("d"))<1e-7);
    }
    @Test void ledCircuitObeysKclAndFiniteSourceResistance() {
        var r=DcSolver.solve(List.of(source("s","+","-",5,5),resistor("r","+","a",220),led("d","a","-")));
        double expected=3d/245;
        assertEquals(expected,r.current("d"),1e-9);assertEquals(-expected,r.current("s"),1e-9);assertEquals(expected,r.current("r"),1e-9);
        assertEquals(2+20*expected,r.voltage("a"),1e-8);
    }
    @Test void missingReturnDoesNotLightLed() {
        var r=DcSolver.solve(List.of(source("s","+","-",5,5),resistor("r","+","a",220),led("d","a","open")));
        assertEquals(0,r.current("d"),1e-8);
    }
    @Test void reversedLedBlocksCurrent() {
        var r=DcSolver.solve(List.of(source("s","+","-",5,5),resistor("r","+","a",220),led("d","-","a")));
        assertTrue(Math.abs(r.current("d"))<1e-7);
    }
    @Test void isolatedCircuitsKeepIndependentReferences() {
        var r=DcSolver.solve(List.of(source("s1","p1","n1",5,5),resistor("r1","p1","n1",95),source("s2","p2","n2",12,5),resistor("r2","p2","n2",115)));
        assertEquals(.05,r.current("r1"),1e-9);assertEquals(.1,r.current("r2"),1e-9);assertFalse(r.commonReference("p1","p2"));
    }
    @Test void shortCircuitCurrentIsFinite() {
        var r=DcSolver.solve(List.of(source("s","p","n",5,5),resistor("w","p","n",.01)));
        assertEquals(5/5.01,r.current("w"),1e-8);
    }
}
