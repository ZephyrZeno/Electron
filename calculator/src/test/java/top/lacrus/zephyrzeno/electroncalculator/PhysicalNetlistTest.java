package top.lacrus.zephyrzeno.electron;

import java.util.*;
import org.junit.jupiter.api.Test;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.*;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalNetlistTest {
    @Test void groupedModulesPreserveAllCalculatorOutputs() {
        DecimalCircuit c=DecimalCircuit.create();PhysicalNetlist net=PhysicalNetlist.of(c);
        for(int op=0;op<4;op++)for(int a=0;a<100;a++)for(int b=0;b<100;b++) {
            var input=DecimalCircuitTest.input(a,b,op);boolean[] values=new boolean[c.nodes.size()];values[1]=true;
            for(var u:net.units) {
                int[] in=u.inputs();
                if(u.type()==PhysicalNetlist.Type.ADDER) {
                    int bits=(in.length-1)/2,sum=values[in[bits*2]]?1:0;
                    for(int i=0;i<bits;i++){if(values[in[i]])sum+=1<<i;if(values[in[bits+i]])sum+=1<<i;}
                    for(int i=0;i<=bits;i++)if(net.owns(u,u.outputs()[i]))values[u.outputs()[i]]=(sum&(1<<i))!=0;
                } else values[u.outputs()[0]]=switch(u.type()) {
                    case INPUT->input.get(u.name());
                    case NOR->!(values[in[0]]||(in.length>1&&values[in[1]]));
                    case AND->values[in[0]]&&values[in[1]];
                    case OR->values[in[0]]||values[in[1]];
                    case XOR->values[in[0]]^values[in[1]];
                    case REGISTER->values[in[0]];
                    default->throw new IllegalStateException();
                };
            }
            var expected=c.evaluate(input);
            for(var output:c.outputs.entrySet())assertEquals(expected.get(output.getKey()),values[output.getValue()],output.getKey()+" "+a+","+b+","+op);
        }
    }
}
