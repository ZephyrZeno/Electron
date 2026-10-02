package top.lacrus.zephyrzeno.electron;

import org.junit.jupiter.api.Test;
import java.util.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import static org.junit.jupiter.api.Assertions.*;

public final class PowerControlModelTest {
    @Test void resistanceMeasuresSeriesAndParallel(){
        var series=List.of(DcSolver.Branch.resistor("r1","a","m",100),DcSolver.Branch.resistor("r2","m","b",220));assertEquals(320,ResistanceModel.measure(series,"a","b").ohms(),1e-7);
        var parallel=List.of(DcSolver.Branch.resistor("r1","a","b",100),DcSolver.Branch.resistor("r2","a","b",220));assertEquals(68.75,ResistanceModel.measure(parallel,"a","b").ohms(),1e-7);
    }
    @Test void resistanceRefusesLiveSourcesAndDisconnectedNodes(){
        var branches=List.of(DcSolver.Branch.source("v","p","g",5,5),DcSolver.Branch.resistor("r","p","g",100));assertEquals("live",ResistanceModel.measure(branches,"p","g").state());assertEquals("open",ResistanceModel.measure(branches,"p","missing").state());
        var stored=List.of(CapacitorModel.branch("c","p","g",3));assertEquals("live",ResistanceModel.measure(stored,"p","g").state());
    }
    @Test void generatorNeverSpendsMissingEnergyAndLimitsShort(){
        var full=DcSolver.solve(List.of(ControlModel.generator("v","p","g",100,true),DcSolver.Branch.resistor("short","p","g",.01)));assertEquals(2,-full.current("v"),1e-8);
        var last=DcSolver.solve(List.of(ControlModel.generator("v","p","g",.01,true),DcSolver.Branch.resistor("short","p","g",.01)));assertTrue(-last.current("v")*24*.05<=.01000001);
        var empty=DcSolver.solve(List.of(ControlModel.generator("v","p","g",0,true),DcSolver.Branch.resistor("load","p","g",12)));assertEquals(0,empty.current("load"),1e-12);
    }
    @Test void mosfetHasDefinedGateThresholds(){assertFalse(ControlModel.mosfet(2,false));assertTrue(ControlModel.mosfet(3,false));assertTrue(ControlModel.mosfet(2,true));assertFalse(ControlModel.mosfet(1.5,true));}
    @Test void breakerAllowsStartingMotorButTripsLockedRotor(){
        double omega=0,heat=0;for(int tick=0;tick<100;tick++){var r=DcSolver.solve(List.of(ControlModel.generator("v","p","g",100,true),DcSolver.Branch.source("m","p","g",MotorModel.emf(omega),MotorModel.RESISTANCE)));omega=MotorModel.step(omega,r.current("m"),0);heat=ControlModel.breakerHeat(heat,r.current("m"));assertTrue(heat<.8);}
        assertTrue(omega>200);for(int tick=0;tick<30;tick++)heat=ControlModel.breakerHeat(heat,1.5);assertTrue(heat>.8);
    }
    @Test void motorLoadLowersSpeedAndConsumesPower(){double free=speed(0),loaded=speed(1);assertTrue(free>loaded&&loaded>0);assertEquals(0,MotorModel.step(100,1,2));assertTrue(MotorModel.step(0,-1,0)<0);}
    private double speed(int load){double w=0,input=0;for(int i=0;i<400;i++){double current=(24-MotorModel.emf(w))/(4+MotorModel.RESISTANCE);input+=Math.max(0,24*current)*.05;w=MotorModel.step(w,current,load);assertTrue(MotorModel.energy(w)<=input+1e-6);}return w;}
    @Test void convertersCannotOutputBeforeCharging(){for(boolean boost:new boolean[]{false,true}){var r=DcSolver.solve(List.of(ConverterModel.output("out","p","g",boost,0,true),DcSolver.Branch.resistor("load","p","g",100)));assertEquals(0,r.current("load"),1e-12);}}
    @Test void converterOutputIsEnergyBoundedAndInputDoesNotOverfill(){for(boolean boost:new boolean[]{false,true}){var r=DcSolver.solve(List.of(ConverterModel.output("out","p","g",boost,.001,true),DcSolver.Branch.resistor("load","p","g",.001)));assertTrue(-r.current("out")*ConverterModel.voltage(boost)*.05<=.00100001);assertEquals(.5,ConverterModel.energy(.49,24,1,0,boost),1e-12);}}
    @Test void converterChargesAndRegulatesWithLoss(){for(boolean boost:new boolean[]{true,false}){double in=boost?5:24,energy=0,accepted=0,delivered=0;for(int tick=0;tick<600;tick++){var r=DcSolver.solve(List.of(DcSolver.Branch.source("src","in","g",in,.1),ConverterModel.input("charge","in","g",boost,energy,in),ConverterModel.output("out","p","g",boost,energy,true),DcSolver.Branch.resistor("load","p","g",boost?2400:100)));double pin=r.voltage("in")*r.current("charge"),pout=-r.current("out")*ConverterModel.voltage(boost);accepted+=pin*.05;delivered+=pout*.05;energy=ConverterModel.energy(energy,r.voltage("in"),r.current("charge"),r.current("out"),boost);if(tick>40)assertEquals(ConverterModel.voltage(boost),r.voltage("p"),.1);assertTrue(delivered+energy<=accepted*.85+1e-7);}}}
}
