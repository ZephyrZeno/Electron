package top.lacrus.zephyrzeno.electron.power;

import top.lacrus.zephyrzeno.electron.circuit.DcSolver;

/** Rectified 240 V distribution bus. These are game ratings, not an AC phase simulator. */
public final class GridModel {
    public static final double VOLTAGE=240,CAPACITY=4000,DT=.05,STATION_EFFICIENCY=.92,STATION_BUFFER=24;
    public static DcSolver.Branch source(String id,String a,String b,double energy,boolean enabled){
        double limit=Math.min(2,Math.max(0,energy)/(VOLTAGE*DT));
        return enabled&&limit>1e-8?new DcSolver.Branch(id,a,b,2,VOLTAGE,false,-limit,0):DcSolver.Branch.resistor(id,a,b,1e12);
    }
    public static double consume(double energy,double current){return Math.clamp(energy-Math.max(0,-current)*VOLTAGE*DT,0,CAPACITY);}
    public static DcSolver.Branch stationInput(String id,String a,String b,double energy,double lastVoltage){
        double limit=Math.min(.48,Math.max(0,STATION_BUFFER-energy)/(264*STATION_EFFICIENCY*DT));
        return lastVoltage>=180&&lastVoltage<=264&&limit>1e-9?new DcSolver.Branch(id,a,b,100,0,true,0,limit):DcSolver.Branch.resistor(id,a,b,1e12);
    }
    public static DcSolver.Branch stationOutput(String id,String a,String b,double energy,boolean enabled){
        double limit=Math.min(4,Math.max(0,energy)/(24*DT));
        // Ignore numerical off-state leakage until a real startup charge is available.
        // An almost empty ideal 24 V source cannot start a downstream converter reliably.
        return enabled&&energy>=.001?new DcSolver.Branch(id,a,b,.1,24,false,-limit,0):DcSolver.Branch.resistor(id,a,b,1e12);
    }
    public static double stationEnergy(double energy,double voltage,double input,double output){return Math.clamp(energy+Math.max(0,voltage*input)*STATION_EFFICIENCY*DT-Math.max(0,-output)*24*DT,0,STATION_BUFFER);}
    public static double wind(long tick,int elevation,boolean rain){return Math.clamp((.6+.2*Math.sin(tick/2400d))*(.8+Math.max(0,elevation-64)/200d)*(rain?1.25:1),.2,1);}
    private GridModel(){}
}
