package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import java.util.function.Predicate;
import top.lacrus.zephyrzeno.electron.circuit.DcSolver;
import top.lacrus.zephyrzeno.electron.logic.WireLogic;

/** Compiled copper nets are cached per board; outputs switch paid supply rails, never synthesize power. */
public final class PcbCircuit {
    public final PcbLayout layout;
    private final int[] nets,states,pending;
    private final long[] due;
    public int ledMask;
    public final int[] displays;
    public PcbCircuit(PcbLayout layout){this.layout=layout;nets=layout.nets();states=new int[layout.parts().size()*2];Arrays.fill(states,3);pending=states.clone();due=new long[states.length];displays=new int[layout.parts().size()];Arrays.fill(displays,-1);}
    private String pin(String id,int at){return id+":n"+nets[at];}
    public void stamp(String id,String[] ports,List<DcSolver.Branch> branches,long tick,boolean broken){
        for(int i=0;i<ports.length;i++)branches.add(DcSolver.Branch.resistor(id+":port"+i,ports[i],pin(id,layout.ports()[i]),broken?1e12:.005));
        branches.add(DcSolver.Branch.resistor(id,ports[0],ports[1],broken?1e12:100000));
        for(int i=0;i<layout.parts().size();i++){
            var part=layout.parts().get(i);String name=id+":c"+i,a=pin(id,part.pin(0)),b=pin(id,part.pin(1));
            if(!part.type().digital()){branches.add(broken?DcSolver.Branch.resistor(name,a,b,1e12):switch(part.type()){case R1K->DcSolver.Branch.resistor(name,a,b,1000);case R10K->DcSolver.Branch.resistor(name,a,b,10000);default->DcSolver.Branch.led(name,a,b);});}
            else{
                boolean display=part.type()==PcbLayout.Type.DISPLAY;
                branches.add(DcSolver.Branch.resistor(name,a,b,broken?1e12:display?1000:10000));
                for(int j=2;j<(display?6:4);j++)branches.add(DcSolver.Branch.resistor(name+":in"+j,pin(id,part.pin(j)),b,1e7));
                for(int out=0;out<(display?0:part.type()==PcbLayout.Type.HALF_ADDER?2:1);out++){int channel=i*2+out;if(due[channel]<=tick)states[channel]=pending[channel];branches.add(DcSolver.Branch.resistor(name+(out==0?":out":":carry"),pin(id,part.pin(4+out)),states[channel]==1?a:b,!broken&&states[channel]<2?100:1e12));}
            }
        }
    }
    public double update(String id,DcSolver.Result r,Predicate<String> driven,long tick,boolean broken){
        double stress=0;ledMask=0;
        for(int i=0;i<layout.parts().size();i++){
            var p=layout.parts().get(i);String name=id+":c"+i;double supply=r.voltage(pin(id,p.pin(0)))-r.voltage(pin(id,p.pin(1))),current=r.current(name);
            if(p.type().digital()){
                int a=WireLogic.input(r.voltage(pin(id,p.pin(2)))-r.voltage(pin(id,p.pin(1))),supply,driven.test(pin(id,p.pin(2))));
                int b=WireLogic.input(r.voltage(pin(id,p.pin(3)))-r.voltage(pin(id,p.pin(1))),supply,driven.test(pin(id,p.pin(3))));
                if(p.type()==PcbLayout.Type.DISPLAY){int value=0;for(int bit=0;bit<4;bit++){int input=WireLogic.input(r.voltage(pin(id,p.pin(bit+2)))-r.voltage(pin(id,p.pin(1))),supply,driven.test(pin(id,p.pin(bit+2))));if(input>1){value=-1;break;}value|=input<<bit;}displays[i]=broken||supply<4.5||supply>5.5?-1:value;}
                else for(int out=0;out<(p.type()==PcbLayout.Type.HALF_ADDER?2:1);out++){var function=p.type()==PcbLayout.Type.HALF_ADDER?(out==0?top.lacrus.zephyrzeno.electron.logic.LogicFunction.XOR:top.lacrus.zephyrzeno.electron.logic.LogicFunction.AND):p.type().logic;int next=broken||supply<.5?3:supply<4.5||supply>5.5?2:WireLogic.evaluate(function,a,b);int channel=i*2+out;if(next!=pending[channel]){pending[channel]=next;due[channel]=tick+2;}}
                stress=Math.max(stress,Math.max(Math.max(supply/5.5,-supply/.5),Math.abs(r.current(name+":out"))/.02));
                stress=Math.max(stress,Math.abs(r.current(name+":carry"))/.02);
            }else if(p.type()==PcbLayout.Type.LED){if(!broken&&current>.001)ledMask|=1<<i;stress=Math.max(stress,Math.max(current/.025,-supply/5));}
            else stress=Math.max(stress,Math.abs(supply*current)/.125);
        }
        return stress;
    }
    public void reset(){Arrays.fill(states,3);Arrays.fill(pending,3);Arrays.fill(due,0);Arrays.fill(displays,-1);ledMask=0;}
}
