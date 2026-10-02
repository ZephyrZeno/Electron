package top.lacrus.zephyrzeno.electron.circuit;

/** 24V brushed motor: resistive armature, back EMF, inertia, friction and selectable shaft load. */
public final class MotorModel {
    public static final double RESISTANCE=12,K=.06,INERTIA=.00008,DRAG=.00004,DT=.05;
    public static double emf(double omega){return K*omega;}
    public static double load(int mode){return mode==1?.018:.003;}
    public static double step(double omega,double current,int mode){
        if(mode==2)return 0;
        double drive=K*current,sign=Math.abs(omega)>1e-6?Math.signum(omega):Math.signum(drive);
        double next=(omega+DT*(drive-sign*load(mode))/INERTIA)/(1+DT*DRAG/INERTIA);
        if(Math.signum(next)!=sign&&Math.abs(drive)<=load(mode))next=0;
        return Math.clamp(next,-600,600);
    }
    public static double rpm(double omega){return omega*60/(2*Math.PI);}
    public static double energy(double omega){return .5*INERTIA*omega*omega;}
    private MotorModel(){}
}
