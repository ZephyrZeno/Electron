package top.lacrus.zephyrzeno.electron.circuit;

public final class ControlModel {
    public static boolean mosfet(double gateSource,boolean on){return on?gateSource>1.5:gateSource>=3;}
    public static double breakerHeat(double heat,double current){double ratio=Math.abs(current);return Math.max(0,heat+(ratio>1?(ratio*ratio-1)*.05:-.1));}
    public static DcSolver.Branch generator(String id,String plus,String minus,double energy,boolean enabled){double limit=Math.min(2,Math.max(0,energy)/(24*.05));return enabled&&limit>1e-9?new DcSolver.Branch(id,plus,minus,4,24,false,-limit,0):DcSolver.Branch.resistor(id,plus,minus,1e12);}
    private ControlModel(){}
}
