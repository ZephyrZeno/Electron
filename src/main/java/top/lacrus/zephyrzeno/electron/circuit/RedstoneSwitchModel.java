package top.lacrus.zephyrzeno.electron.circuit;

/** Electricity actuates a contact. Only an existing vanilla signal can pass through it. */
public final class RedstoneSwitchModel {
    public static final double COIL_OHMS=1000,PULL_IN_VOLTS=3.5,RELEASE_VOLTS=1.5;
    public static boolean contact(double voltage,boolean closed){return closed?voltage>RELEASE_VOLTS:voltage>=PULL_IN_VOLTS;}
    public static int transmit(int redstoneInput,boolean closed){return closed?Math.clamp(redstoneInput,0,15):0;}
    private RedstoneSwitchModel(){}
}
