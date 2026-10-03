package top.lacrus.zephyrzeno.electron.power;

import top.lacrus.zephyrzeno.electron.circuit.DcSolver;

/** Rectified 240 V distribution bus. These are game ratings, not an AC phase simulator. */
public final class GridModel {
    public static final double VOLTAGE=240,CAPACITY=4000,DT=.05,STATION_EFFICIENCY=.92,STATION_BUFFER=24;
    public static DcSolver.Branch source(String id,String a,String b,double energy,boolean enabled){
        return source(id,a,b,energy,enabled,VOLTAGE);
    }
    public static DcSolver.Branch source(String id,String a,String b,double energy,boolean enabled,double volts){
        // Reserve the solver's limiter conductance too: its tiny current matters at tens of kV.
        double limit=Math.max(0,Math.min(480/volts,Math.max(0,energy)/(volts*DT))-volts*1e-12);
        return enabled&&limit>1e-8?new DcSolver.Branch(id,a,b,2,volts,false,-limit,0):DcSolver.Branch.resistor(id,a,b,1e12);
    }
    public static double consume(double energy,double current,double volts){return Math.clamp(energy-Math.max(0,-current)*volts*DT,0,CAPACITY);}
    public static double consume(double energy,double current){return Math.clamp(energy-Math.max(0,-current)*VOLTAGE*DT,0,CAPACITY);}
    public static DcSolver.Branch stationInput(String id,String a,String b,double energy,double lastVoltage){
        return stationInput(id,a,b,energy,lastVoltage,StationSpec.LV);
    }
    public static DcSolver.Branch stationInput(String id,String a,String b,double energy,double lastVoltage,StationSpec spec){
        double limit=Math.min(spec.inputAmps(),Math.max(0,spec.capacity()-energy)/(spec.max()*STATION_EFFICIENCY*DT));
        return lastVoltage>=spec.min()&&lastVoltage<=spec.max()&&limit>1e-9?new DcSolver.Branch(id,a,b,100*Math.pow(spec.input()/240,2),0,true,0,limit):DcSolver.Branch.resistor(id,a,b,1e12);
    }
    public static DcSolver.Branch stationOutput(String id,String a,String b,double energy,boolean enabled){
        return stationOutput(id,a,b,energy,enabled,StationSpec.LV);
    }
    public static DcSolver.Branch stationOutput(String id,String a,String b,double energy,boolean enabled,StationSpec spec){
        double limit=Math.max(0,Math.min(4,Math.max(0,energy)/(spec.output()*DT))-spec.output()*1e-12);
        // Ignore numerical off-state leakage until a real startup charge is available.
        // An almost empty ideal 24 V source cannot start a downstream converter reliably.
        return enabled&&energy>=.001?new DcSolver.Branch(id,a,b,.1,spec.output(),false,-limit,0):DcSolver.Branch.resistor(id,a,b,1e12);
    }
    public static double stationEnergy(double energy,double voltage,double input,double output,StationSpec spec){return Math.clamp(energy+(voltage>=spec.min()&&voltage<=spec.max()?Math.max(0,voltage*input)*STATION_EFFICIENCY*DT:0)-Math.max(0,-output)*spec.output()*DT,0,spec.capacity());}
    public static double stationEnergy(double energy,double voltage,double input,double output){return Math.clamp(energy+Math.max(0,voltage*input)*STATION_EFFICIENCY*DT-Math.max(0,-output)*24*DT,0,STATION_BUFFER);}
    public static double wind(long tick,int elevation,boolean rain){return Math.clamp((.6+.2*Math.sin(tick/2400d))*(.8+Math.max(0,elevation-64)/200d)*(rain?1.25:1),.2,1);}
    private GridModel(){}
}
