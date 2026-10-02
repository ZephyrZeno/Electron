package top.lacrus.zephyrzeno.electron;

import java.util.*;
import org.junit.jupiter.api.Test;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.DecimalCircuit;
import static org.junit.jupiter.api.Assertions.*;

class DecimalCircuitTest {
    static Map<String,Boolean> input(int a,int b,int op) {
        Map<String,Boolean> values=new HashMap<>();
        for(var entry:Map.of("AT",a/10,"AU",a%10,"BT",b/10,"BU",b%10).entrySet())
            for(int i=0;i<4;i++)values.put(entry.getKey()+i,(entry.getValue()&(1<<i))!=0);
        values.put("OP0",(op&1)!=0);values.put("OP1",(op&2)!=0);values.put("HOLD",false);return values;
    }
    static int read(Map<String,Boolean> values,String prefix,int bits) {
        int result=0;for(int bit=0;bit<bits;bit++)if(values.get(prefix+bit))result|=1<<bit;return result;
    }
    @Test void allTwoDigitOperationsAndDecimalConversion() {
        DecimalCircuit c=DecimalCircuit.create();
        System.out.println("Decimal circuit nodes="+c.nodes.size());
        for(int op=0;op<4;op++)for(int a=0;a<100;a++)for(int b=0;b<100;b++) {
            var out=c.evaluate(input(a,b,op));
            boolean error=op==3&&b==0;
            int expected=error?0:switch(op){case 0->a+b;case 1->Math.abs(a-b);case 2->a*b;default->(a*100+b/2)/b;};
            assertEquals(expected,read(out,"R",14),a+","+b+","+op);
            assertEquals(error,out.get("ERROR"));
            assertEquals(op==1&&a<b,out.get("NEG"));
            assertEquals(op==3,out.get("DOT"));
            int bcd=read(out,"BCD",16),decoded=0;
            for(int digit=0;digit<4;digit++)decoded+=((bcd>>(digit*4))&15)*(int)Math.pow(10,digit);
            assertEquals(expected,decoded,"BCD");
            int[] segments={0x3f,0x06,0x5b,0x4f,0x66,0x6d,0x7d,0x07,0x7f,0x6f};
            for(int digit=0;digit<4;digit++)for(int segment=0;segment<7;segment++)
                assertEquals((segments[(bcd>>(digit*4))&15]&(1<<segment))!=0,out.get("SEG"+digit+"_"+segment),"Seven segment display");
        }
    }
    @Test void invalidDecimalDigitIsRejected() {
        var values=input(0,0,0);values.put("AU3",true);values.put("AU1",true);
        var out=DecimalCircuit.create().evaluate(values);
        assertTrue(out.get("ERROR"));assertEquals(0,read(out,"R",14));
    }
}
