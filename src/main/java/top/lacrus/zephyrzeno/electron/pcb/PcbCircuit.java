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
    public PcbCircuit(PcbLayout layout){this.layout=layout;nets=layout.nets();states=new int[layout.parts().size()];Arrays.fill(states,3);pending=states.clone();due=new long[states.length];}
    private String pin(String id,int at){return id+":n"+nets[at];}
    public void stamp(String id,String[] ports,List<DcSolver.Branch> branches,long tick,boolean broken){
        for(int i=0;i<ports.length;i++)branches.add(DcSolver.Branch.resistor(id+":port"+i,ports[i],pin(id,layout.ports()[i]),broken?1e12:.005));
        branches.add(DcSolver.Branch.resistor(id,ports[0],ports[1],broken?1e12:100000));
        for(int i=0;i<states.length;i++){
            var part=layout.parts().get(i);String name=id+":c"+i,a=pin(id,part.pin(0)),b=pin(id,part.pin(1));
            if(due[i]<=tick)states[i]=pending[i];
            if(part.type().logic==null){branches.add(broken?DcSolver.Branch.resistor(name,a,b,1e12):switch(part.type()){case R1K->DcSolver.Branch.resistor(name,a,b,1000);case R10K->DcSolver.Branch.resistor(name,a,b,10000);default->DcSolver.Branch.led(name,a,b);});}
            else{
                branches.add(DcSolver.Branch.resistor(name,a,b,broken?1e12:10000));
                for(int j=2;j<4;j++)branches.add(DcSolver.Branch.resistor(name+":in"+j,pin(id,part.pin(j)),b,1e7));
                branches.add(DcSolver.Branch.resistor(name+":out",pin(id,part.pin(4)),states[i]==1?a:b,!broken&&states[i]<2?100:1e12));
            }
        }
    }
    public double update(String id,DcSolver.Result r,Predicate<String> driven,long tick,boolean broken){
        double stress=0;ledMask=0;
        for(int i=0;i<states.length;i++){
            var p=layout.parts().get(i);String name=id+":c"+i;double supply=r.voltage(pin(id,p.pin(0)))-r.voltage(pin(id,p.pin(1))),current=r.current(name);
            if(p.type().logic!=null){
                int a=WireLogic.input(r.voltage(pin(id,p.pin(2)))-r.voltage(pin(id,p.pin(1))),supply,driven.test(pin(id,p.pin(2))));
                int b=WireLogic.input(r.voltage(pin(id,p.pin(3)))-r.voltage(pin(id,p.pin(1))),supply,driven.test(pin(id,p.pin(3))));
                int next=broken||supply<.5?3:supply<4.5||supply>5.5?2:WireLogic.evaluate(p.type().logic,a,b);
                if(next!=pending[i]){pending[i]=next;due[i]=tick+2;}
                stress=Math.max(stress,Math.max(Math.max(supply/5.5,-supply/.5),Math.abs(r.current(name+":out"))/.02));
            }else if(p.type()==PcbLayout.Type.LED){if(!broken&&current>.001)ledMask|=1<<i;stress=Math.max(stress,Math.max(current/.025,-supply/5));}
            else stress=Math.max(stress,Math.abs(supply*current)/.125);
        }
        return stress;
    }
    public void reset(){Arrays.fill(states,3);Arrays.fill(pending,3);Arrays.fill(due,0);ledMask=0;}
}
