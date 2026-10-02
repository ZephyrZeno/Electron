package top.lacrus.zephyrzeno.electron.circuit;

/** Regulated game battery module, with a charger and protected 5V output sharing a return. */
public final class BatteryModel {
    public static final double CAPACITY=200,VOLTS=5,OUTPUT_OHMS=5,OUTPUT_AMPS=.1,INPUT_AMPS=.25;
    public static final double CHARGE_EFFICIENCY=.8,DISCHARGE_EFFICIENCY=.9,STEP_SECONDS=.05;
    public static double clampEnergy(double value){return Double.isFinite(value)?Math.clamp(value,0,CAPACITY):0;}
    public static DcSolver.Branch input(String id,String plus,String minus,double energy){
        double limit=Math.min(INPUT_AMPS,(CAPACITY-clampEnergy(energy))/(CHARGE_EFFICIENCY*6*STEP_SECONDS));
        return limit<1e-10?DcSolver.Branch.resistor(id,plus,minus,1e12):new DcSolver.Branch(id,plus,minus,10,.7,true,0,limit);
    }
    public static DcSolver.Branch output(String id,String plus,String minus,double energy,boolean enabled){
        double limit=Math.min(OUTPUT_AMPS,clampEnergy(energy)*DISCHARGE_EFFICIENCY/(VOLTS*STEP_SECONDS));
        return !enabled||limit<1e-10?DcSolver.Branch.resistor(id,plus,minus,1e12):new DcSolver.Branch(id,plus,minus,OUTPUT_OHMS,VOLTS,false,-limit,0);
    }
    public static double nextEnergy(double energy,double inputVoltage,double inputCurrent,double outputCurrent){
        double accepted=Math.max(0,inputVoltage)*Math.max(0,inputCurrent)*CHARGE_EFFICIENCY*STEP_SECONDS;
        double used=Math.max(0,-outputCurrent)*VOLTS*STEP_SECONDS/DISCHARGE_EFFICIENCY;
        return clampEnergy(energy+accepted-used);
    }
    private BatteryModel(){}
}
