package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import java.util.function.Predicate;
import top.lacrus.zephyrzeno.electron.circuit.DcSolver;
import top.lacrus.zephyrzeno.electron.logic.*;

/** Copper, finite supply rails and delayed logic. There is no expression/arithmetic evaluator. */
public final class PcbCircuit {
    public final PcbLayout layout;
    public final List<PcbCopperMesh.Run> surfaceCopper;
    private final int[] nets,states,pending,clocks,latches;
    private final long[] due,shift;
    private final PcbSequential[] sequential;
    private final PcbAnalog[] analog;
    private final int layoutHash;
    public final int[] controls,displays;
    public final long[] pixels;
    public final BitSet leds=new BitSet();
    public int ledMask;
    public PcbCircuit(PcbLayout layout){this.layout=layout;layoutHash=layout.encode().hashCode();surfaceCopper=PcbCopperMesh.compile(layout);nets=layout.nets();int n=layout.parts().size();states=new int[n*4];Arrays.fill(states,3);pending=states.clone();due=new long[states.length];displays=new int[n];Arrays.fill(displays,-1);controls=new int[n];clocks=new int[n];Arrays.fill(clocks,2);latches=new int[n];Arrays.fill(latches,2);shift=new long[n];pixels=new long[n];sequential=new PcbSequential[n];analog=new PcbAnalog[n];for(int i=0;i<n;i++){var type=layout.parts().get(i).type();if(type.sequential())sequential[i]=new PcbSequential(type);if(type.analog())analog[i]=new PcbAnalog(type);if(type==PcbLayout.Type.POT10K)controls[i]=5;}}
    public String net(String id,int at){return id+":n"+nets[at];}
    private String pin(String id,PcbLayout.Part p,int index){return net(id,p.pin(index));}
    private String[] pins(String id,PcbLayout.Part p){String[] names=new String[p.type().pins()];for(int j=0;j<names.length;j++)names[j]=pin(id,p,j);return names;}
    public double maxSupplyVoltage(){return layout.parts().stream().anyMatch(p->p.type()==PcbLayout.Type.BUCK5)?30:5.5;}
    private static int inputEnd(PcbLayout.Type t){return t.input()?2:t==PcbLayout.Type.SIGN?3:t==PcbLayout.Type.DISPLAY||t==PcbLayout.Type.PIXEL?6:t==PcbLayout.Type.RGB||t==PcbLayout.Type.MUX||t==PcbLayout.Type.TFF?5:4;}
    public void stamp(String id,String[] ports,List<DcSolver.Branch> branches,long tick,boolean broken){
        for(int i=0;i<ports.length;i++)branches.add(DcSolver.Branch.resistor(id+":port"+i,ports[i],net(id,layout.ports()[i]),broken?1e12:.005));
        branches.add(DcSolver.Branch.resistor(id,ports[0],ports[1],broken?1e12:100000));
        for(int i=0;i<layout.parts().size();i++){
            var p=layout.parts().get(i);var t=p.type();String name=id+":c"+i,a=pin(id,p,0),b=pin(id,p,1);
            if(analog[i]!=null){if(due[i*4]<=tick)states[i*4]=pending[i*4];analog[i].stamp(name,pins(id,p),branches,controls[i],states[i*4],broken);}
            else if(!t.digital())branches.add(broken?DcSolver.Branch.resistor(name,a,b,1e12):switch(t){case R1K->DcSolver.Branch.resistor(name,a,b,1000);case R10K->DcSolver.Branch.resistor(name,a,b,10000);default->DcSolver.Branch.led(name,a,b);});
            else{
                double resistance=t==PcbLayout.Type.DISPLAY?1000:t==PcbLayout.Type.RGB?5/(.001+.003*Integer.bitCount(Math.max(0,displays[i]))):t==PcbLayout.Type.PIXEL?5/(.002+.0003*Long.bitCount(pixels[i])):10000;
                branches.add(DcSolver.Branch.resistor(name,a,b,broken?1e12:resistance));
                for(int j=2;j<inputEnd(t);j++)branches.add(DcSolver.Branch.resistor(name+":in"+j,pin(id,p,j),b,1e7));
                for(int out=0;out<t.outputs();out++){int channel=i*4+out;if(due[channel]<=tick)states[channel]=pending[channel];branches.add(DcSolver.Branch.resistor(name+(out==0?":out":out==1?":carry":":out"+out),pin(id,p,t.outputPin(out)),states[channel]==1?a:b,!broken&&states[channel]<2?100:1e12));}
            }
        }
    }
    private int input(String id,PcbLayout.Part p,int j,DcSolver.Result r,Predicate<String> driven,double supply){String n=pin(id,p,j);return WireLogic.input(r.voltage(n)-r.voltage(pin(id,p,1)),supply,driven.test(n));}
    public double update(String id,DcSolver.Result r,Predicate<String> driven,long tick,boolean broken){
        double stress=0;leds.clear();ledMask=0;
        for(int i=0;i<layout.parts().size();i++){
            var p=layout.parts().get(i);var t=p.type();String name=id+":c"+i;double supply=r.voltage(pin(id,p,0))-r.voltage(pin(id,p,1)),current=r.current(name);
            if(analog[i]!=null){
                stress=Math.max(stress,analog[i].update(name,pins(id,p),r,driven,broken));
                if(t.outputs()>0&&pending[i*4]!=analog[i].level){pending[i*4]=analog[i].level;due[i*4]=tick+2;}
                continue;
            }
            if(t.digital()){
                boolean powered=!broken&&supply>=4.5&&supply<=5.5;int a=inputEnd(t)>2?input(id,p,2,r,driven,supply):0,b=inputEnd(t)>3?input(id,p,3,r,driven,supply):0;
                if(sequential[i]!=null)sequential[i].update(powered,a,b,t==PcbLayout.Type.TFF?input(id,p,4,r,driven,supply):0);
                if(t==PcbLayout.Type.SIGN)displays[i]=powered&&a<2?a:-1;
                else if(t==PcbLayout.Type.DISPLAY||t==PcbLayout.Type.RGB){int value=0;for(int bit=0;bit<(t==PcbLayout.Type.RGB?3:4);bit++){int level=input(id,p,bit+2,r,driven,supply);if(level>1){value=-1;break;}value|=level<<bit;}displays[i]=powered?value:-1;}
                else if(t==PcbLayout.Type.PIXEL){int latch=input(id,p,4,r,driven,supply),clear=input(id,p,5,r,driven,supply);
                    if(!powered||clear==1){shift[i]=pixels[i]=0;}
                    else{if(b==1&&clocks[i]==0&&a<2)shift[i]=(shift[i]<<1)|a;if(latch==1&&latches[i]==0)pixels[i]=shift[i];}
                    clocks[i]=b;latches[i]=latch;
                }
                else for(int out=0;out<t.outputs();out++){
                    int channel=i*4+out,next;
                    if(!powered)next=broken||supply<.5?3:2;
                    else if(sequential[i]!=null)next=sequential[i].output(out);
                    else if(t.logic!=null)next=WireLogic.evaluate(t.logic,a,b);
                    else next=switch(t){
                        case HALF_ADDER->WireLogic.evaluate(out==0?LogicFunction.XOR:LogicFunction.AND,a,b);
                        case DIAL->(Math.clamp(controls[i],0,9)>>out)&1;
                        case SWITCH->controls[i]&1;
                        case CLOCK->(int)((tick/Math.max(2,controls[i]>0?controls[i]:10))&1);
                        case TRISTATE->b==0?3:b==1?a:2;
                        case MUX->{int select=input(id,p,4,r,driven,supply);yield select==0?a:select==1?b:2;}
                        default->3;
                    };
                    if(next!=pending[channel]){pending[channel]=next;due[channel]=tick+2;}
                    stress=Math.max(stress,Math.abs(r.current(name+(out==0?":out":out==1?":carry":":out"+out)))/.02);
                }
                if(t==PcbLayout.Type.DIAL||t==PcbLayout.Type.SWITCH||t==PcbLayout.Type.CLOCK)displays[i]=powered?controls[i]:-1;
                stress=Math.max(stress,Math.max(supply/5.5,-supply/.5));
            }else if(t==PcbLayout.Type.LED){if(!broken&&current>.001){leds.set(i);if(i<32)ledMask|=1<<i;}stress=Math.max(stress,Math.max(current/.025,-supply/5));}
            else stress=Math.max(stress,Math.abs(supply*current)/.125);
        }
        return stress;
    }
    public void reset(){Arrays.fill(states,3);Arrays.fill(pending,3);Arrays.fill(due,0);Arrays.fill(displays,-1);Arrays.fill(clocks,2);Arrays.fill(latches,2);Arrays.fill(shift,0);Arrays.fill(pixels,0);for(var memory:sequential)if(memory!=null)memory.reset();for(var part:analog)if(part!=null)part.reset();leds.clear();ledMask=0;}
    public int outputState(int part,int output){if(part<0||part>=layout.parts().size()||output<0||output>=layout.parts().get(part).type().outputs())throw new IllegalArgumentException("output");return states[part*4+output];}

    /** Persist solved and pending rails together with memory so reload never invents a clock edge. */
    public long[] snapshot(){
        var data=new long[3+layout.parts().size()*27];data[0]=2;data[1]=layout.parts().size();data[2]=layoutHash;
        for(int i=0;i<layout.parts().size();i++){int at=3+i*27;
            for(int out=0;out<4;out++){data[at+out]=states[i*4+out];data[at+4+out]=pending[i*4+out];data[at+8+out]=due[i*4+out];}
            if(sequential[i]!=null){int[] memory=sequential[i].snapshot();for(int j=0;j<memory.length;j++)data[at+12+j]=memory[j];}
            data[at+20]=clocks[i];data[at+21]=latches[i];data[at+22]=shift[i];data[at+23]=pixels[i];
            if(analog[i]!=null)System.arraycopy(analog[i].snapshot(),0,data,at+24,3);
        }return data;
    }
    public boolean restore(long[] data){
        int n=layout.parts().size();if(data.length<3||data[0]!=1&&data[0]!=2)return false;
        int stride=data[0]==1?24:27;if(data.length!=3+n*stride||data[1]!=n||data[2]!=layoutHash)return false;
        // Validate the entire snapshot before touching any live state.
        var memories=new int[n][];
        for(int i=0;i<n;i++){int at=3+i*stride;for(int j=0;j<8;j++)if(data[at+j]<0||data[at+j]>3)return false;
            for(int j=8;j<12;j++)if(data[at+j]<0)return false;
            for(int j=20;j<22;j++)if(data[at+j]<0||data[at+j]>2)return false;
            if(sequential[i]!=null){int[] memory=new int[8];for(int j=0;j<8;j++){long value=data[at+12+j];if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)return false;memory[j]=(int)value;}if(!new PcbSequential(layout.parts().get(i).type()).restore(memory))return false;memories[i]=memory;}
            if(analog[i]!=null&&(stride==24||!new PcbAnalog(layout.parts().get(i).type()).restore(Arrays.copyOfRange(data,at+24,at+27))))return false;
        }
        for(int i=0;i<n;i++){int at=3+i*stride;for(int out=0;out<4;out++){states[i*4+out]=(int)data[at+out];pending[i*4+out]=(int)data[at+4+out];due[i*4+out]=data[at+8+out];}
            if(sequential[i]!=null)sequential[i].restore(memories[i]);clocks[i]=(int)data[at+20];latches[i]=(int)data[at+21];shift[i]=data[at+22];pixels[i]=data[at+23];
            if(analog[i]!=null)analog[i].restore(Arrays.copyOfRange(data,at+24,at+27));
        }return true;
    }
}
