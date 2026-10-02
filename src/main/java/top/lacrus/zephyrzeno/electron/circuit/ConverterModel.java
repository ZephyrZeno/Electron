package top.lacrus.zephyrzeno.electron.circuit;

/** Averaged, energy-buffered model. Fixed output accepts an input range, never an enumerated input pair. */
public final class ConverterModel {
    public static final double CAPACITY=.5,EFFICIENCY=.85,DT=.05;
    public static double voltage(boolean boost){return defaults(boost).output;}
    public static ConverterSpec defaults(boolean boost){return boost?ConverterSpec.BOOST_24:ConverterSpec.BUCK_5;}
    public static DcSolver.Branch input(String id,String a,String b,boolean boost,double energy,double lastVoltage){return input(id,a,b,defaults(boost),energy,lastVoltage);}
    public static DcSolver.Branch input(String id,String a,String b,ConverterSpec spec,double energy,double lastVoltage){
        double limit=Math.min(spec.maxCurrent,Math.max(0,CAPACITY-energy)/(EFFICIENCY*spec.maxInput*DT));
        return !spec.accepts(lastVoltage)||limit<1e-10?DcSolver.Branch.resistor(id,a,b,1e12):new DcSolver.Branch(id,a,b,spec.boost?12:20,.4,true,0,limit);
    }
    public static DcSolver.Branch output(String id,String a,String b,boolean boost,double energy,boolean enabled){return output(id,a,b,defaults(boost),energy,enabled);}
    public static DcSolver.Branch output(String id,String a,String b,ConverterSpec spec,double energy,boolean enabled){
        double limit=Math.min(.5,Math.max(0,energy)/(spec.output*DT));
        return !enabled||limit<1e-10?DcSolver.Branch.resistor(id,a,b,1e12):new DcSolver.Branch(id,a,b,spec.boost?2:.5,spec.output,false,-limit,0);
    }
    public static double energy(double previous,double inputVoltage,double inputCurrent,double outputCurrent,boolean boost){return energy(previous,inputVoltage,inputCurrent,outputCurrent,defaults(boost));}
    public static double energy(double previous,double inputVoltage,double inputCurrent,double outputCurrent,ConverterSpec spec){return Math.clamp(previous+Math.max(0,inputVoltage*inputCurrent)*EFFICIENCY*DT-Math.max(0,-outputCurrent)*spec.output*DT,0,CAPACITY);}
    private ConverterModel(){}
}
