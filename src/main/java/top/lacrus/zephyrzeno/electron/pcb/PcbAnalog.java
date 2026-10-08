package top.lacrus.zephyrzeno.electron.pcb;

import java.util.List;
import java.util.function.Predicate;
import top.lacrus.zephyrzeno.electron.circuit.*;

/** Tick-scale companions, finite rail drivers and a paid averaged DC converter. */
final class PcbAnalog {
    static final double CAPACITANCE=.0001,DT=.05,CAP_STEP=DT/CAPACITANCE,LEAK=1+CAP_STEP/1e7;
    final PcbLayout.Type type;
    double stored,inputVoltage;
    int level=2;
    PcbAnalog(PcbLayout.Type type){this.type=type;}
    void reset(){stored=inputVoltage=0;level=2;}
    void stamp(String id,String[] p,List<DcSolver.Branch> out,int control,int output,boolean broken){
        if(broken){for(int j=1;j<p.length;j++)out.add(DcSolver.Branch.resistor(id+":off"+j,p[0],p[j],1e12));return;}
        switch(type){
            case R220,R100K->out.add(DcSolver.Branch.resistor(id,p[0],p[1],type==PcbLayout.Type.R220?220:100000));
            case DIODE->out.add(DcSolver.Branch.diode(id,p[0],p[1]));
            case CAP100UF->out.add(DcSolver.Branch.source(id,p[0],p[1],stored/LEAK,1+CAP_STEP/LEAK));
            case POT10K->{double fraction=Math.clamp(control,0,10)/10d;
                out.add(DcSolver.Branch.resistor(id,p[0],p[2],Math.max(1,10000*(1-fraction))));
                out.add(DcSolver.Branch.resistor(id+":lower",p[2],p[1],Math.max(1,10000*fraction)));}
            case COMPARATOR,SCHMITT->{
                out.add(DcSolver.Branch.resistor(id,p[0],p[1],10000));
                out.add(DcSolver.Branch.resistor(id+":plus",p[2],p[1],1e7));
                out.add(DcSolver.Branch.resistor(id+":minus",p[3],p[1],1e7));
                out.add(DcSolver.Branch.resistor(id+":out",p[4],output==1?p[0]:p[1],output<2?100:1e12));}
            case MOSFET->{
                out.add(DcSolver.Branch.resistor(id+":gate",p[0],p[2],1e7));
                out.add(DcSolver.Branch.resistor(id,p[1],p[2],level==1?.2:1e12));
                out.add(new DcSolver.Branch(id+":body",p[2],p[1],.2,.7,true));}
            case BUCK5->{
                out.add(ConverterModel.input(id+":in",p[0],p[1],ConverterSpec.BUCK_5,stored,inputVoltage));
                out.add(ConverterModel.output(id,p[2],p[3],ConverterSpec.BUCK_5,stored,true));
                out.add(DcSolver.Branch.resistor(id+":return",p[1],p[3],.02));}
            default->throw new IllegalStateException();
        }
    }
    double update(String id,String[] p,DcSolver.Result r,Predicate<String> driven,boolean broken){
        if(broken){reset();level=3;return 0;}
        double v=r.voltage(p[0])-r.voltage(p[1]),i=r.current(id);
        switch(type){
            case R220,R100K->{return Math.abs(v*i)/.125;}
            case DIODE->{return Math.max(Math.abs(i)/.1,-v/50);}
            case CAP100UF->{stored=(stored+i*CAP_STEP)/LEAK;return Math.max(Math.max(stored/10,-stored/.5),Math.abs(i)/.1);}
            case POT10K->{double w=r.voltage(p[2])-r.voltage(p[1]);return (Math.abs((v-w)*i)+Math.abs(w*r.current(id+":lower")))/.1;}
            case COMPARATOR,SCHMITT->{
                double plus=r.voltage(p[2])-r.voltage(p[1]),minus=r.voltage(p[3])-r.voltage(p[1]);
                if(v<.5)level=3;
                else if(v<4.5||v>5.5||!driven.test(p[2])||type==PcbLayout.Type.COMPARATOR&&!driven.test(p[3]))level=2;
                else if(type==PcbLayout.Type.SCHMITT){if(plus>=.7*v)level=1;else if(plus<=.3*v)level=0;else if(level==3)level=2;}
                else if(plus-minus>=.02)level=1;else if(plus-minus<=-.02)level=0;else if(level==3)level=2;
                double inputStress=Math.max(Math.max(plus/5.5,-plus/.5),type==PcbLayout.Type.COMPARATOR?Math.max(minus/5.5,-minus/.5):0);
                return Math.max(inputStress,Math.max(Math.abs(r.current(id+":out"))/.02,Math.max(v/5.5,-v/.5)));}
            case MOSFET->{double gate=r.voltage(p[0])-r.voltage(p[2]),drain=r.voltage(p[1])-r.voltage(p[2]);
                level=driven.test(p[0])&&ControlModel.mosfet(gate,level==1)?1:0;
                return Math.max(Math.abs(gate)/12,Math.max(Math.abs(drain)/30,Math.abs(i-r.current(id+":body"))/.2));}
            case BUCK5->{inputVoltage=v;stored=ConverterModel.energy(stored,v,r.current(id+":in"),i,ConverterSpec.BUCK_5);
                return Math.max(Math.max(v/30,-v/.5),Math.max((r.voltage(p[2])-r.voltage(p[3]))/6,0));}
            default->throw new IllegalStateException();
        }
    }
    long[] snapshot(){return new long[]{Double.doubleToLongBits(stored),Double.doubleToLongBits(inputVoltage),level};}
    boolean restore(long[] data){
        if(data.length!=3)return false;double s=Double.longBitsToDouble(data[0]),v=Double.longBitsToDouble(data[1]);
        if(!Double.isFinite(s)||!Double.isFinite(v)||Math.abs(s)>100||Math.abs(v)>1e9||data[2]<0||data[2]>3)return false;
        if(type==PcbLayout.Type.BUCK5&&(s<0||s>ConverterModel.CAPACITY))return false;
        stored=s;inputVoltage=v;level=(int)data[2];return true;
    }
}
