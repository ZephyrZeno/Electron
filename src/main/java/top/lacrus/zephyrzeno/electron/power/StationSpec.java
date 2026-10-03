package top.lacrus.zephyrzeno.electron.power;

/** Factory winding ratios; changing the range requires crafting and replacing the hardware. */
public record StationSpec(double input,double output,double capacity) {
    public static final StationSpec LV=new StationSpec(240,24,24);
    public static StationSpec of(double input){return input>=1000?new StationSpec(input,240,2400):LV;}
    public double min(){return input*.75;}
    public double max(){return input*1.1;}
    public double inputAmps(){return 126/input;}
}
