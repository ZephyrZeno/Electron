package top.lacrus.zephyrzeno.electron.circuit;

/** Game module ratings inspired by fixed-output DC converter families, not pin-compatible silicon models. */
public enum ConverterSpec {
    BUCK_3V3(false,3.3,4.5,30,.5),BUCK_5(false,5,7,30,.5),BUCK_12(false,12,14,30,.5),
    BOOST_12(true,12,2.5,10,.3),BOOST_24(true,24,2.5,20,.3);
    public final boolean boost;public final double output,minInput,maxInput,maxCurrent;
    ConverterSpec(boolean boost,double output,double minInput,double maxInput,double maxCurrent){this.boost=boost;this.output=output;this.minInput=minInput;this.maxInput=maxInput;this.maxCurrent=maxCurrent;}
    public static ConverterSpec of(boolean boost,double output){for(var spec:values())if(spec.boost==boost&&Math.abs(spec.output-output)<.01)return spec;throw new IllegalArgumentException("Unknown converter rating");}
    public boolean accepts(double input){return input>=minInput&&input<=maxInput;}
}
