package top.lacrus.zephyrzeno.electron.circuit;

/** Passive backward-Euler companion, including series resistance and dielectric leakage.
 * Current is positive into the marked + terminal. One step is one server tick. */
public final class CapacitorModel {
    public static final double FARADS=.1, RATED_VOLTS=10, ESR=1, LEAK_OHMS=1e6, STEP_SECONDS=.05;
    private static final double STEP_OHMS=STEP_SECONDS/FARADS;
    private static final double LEAK_FACTOR=1+STEP_OHMS/LEAK_OHMS;

    public static DcSolver.Branch branch(String id,String plus,String minus,double storedVoltage){
        return DcSolver.Branch.source(id,plus,minus,storedVoltage/LEAK_FACTOR,ESR+STEP_OHMS/LEAK_FACTOR);
    }
    public static double nextVoltage(double storedVoltage,double current){
        return (storedVoltage+current*STEP_OHMS)/LEAK_FACTOR;
    }
    public static double joules(double storedVoltage){return .5*FARADS*storedVoltage*storedVoltage;}
    public static double overloadRatio(double storedVoltage,double current){
        return Math.max(Math.max(storedVoltage/RATED_VOLTS,-storedVoltage/.5),Math.abs(current)/.25);
    }
    private CapacitorModel(){}
}
