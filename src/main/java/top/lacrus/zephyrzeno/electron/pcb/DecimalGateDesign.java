package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import top.lacrus.zephyrzeno.electron.logic.LogicFunction;

/** Editable hardware recipe: two 0..9 operands, add/subtract, binary magnitude and decimal correction.
 * Each cell is an actual XOR/AND half-adder PCB or a placed electrical logic gate. */
public final class DecimalGateDesign {
    public record Cell(String id,LogicFunction gate,List<String> pins){public boolean pcb(){return gate==null;}}
    public record Design(List<Cell> cells,List<String> units,String tens,String negative){}
    private final List<Cell> cells=new ArrayList<>();private int next;
    private String gate(LogicFunction f,String a,String b){String id="g"+next++,out=id+"Y";cells.add(new Cell(id,f,List.of("V","G",a,b,out)));return out;}
    private String[] half(String a,String b){String id="h"+next++,s=id+"S",c=id+"C";cells.add(new Cell(id,null,List.of("V","G",a,b,s,c)));return new String[]{s,c};}
    private String[] full(String a,String b,String carry){var x=half(a,b);var y=half(x[0],carry);return new String[]{y[0],gate(LogicFunction.OR,x[1],y[1])};}
    public static Design create(){var b=new DecimalGateDesign();String carry="SUB";var sum=new String[4];
        for(int i=0;i<4;i++){String operand=b.gate(LogicFunction.XOR,"B"+i,"SUB");var result=b.full("A"+i,operand,carry);sum[i]=result[0];carry=result[1];}
        String sign=b.gate(LogicFunction.AND,"SUB",b.gate(LogicFunction.NOT,carry,"G"));String high=b.gate(LogicFunction.AND,carry,b.gate(LogicFunction.NOT,"SUB","G"));String c=sign;var magnitude=new String[4];
        for(int i=0;i<4;i++){var r=b.half(b.gate(LogicFunction.XOR,sum[i],sign),c);magnitude[i]=r[0];c=r[1];}
        String ten=b.gate(LogicFunction.OR,high,b.gate(LogicFunction.AND,magnitude[3],b.gate(LogicFunction.OR,magnitude[2],magnitude[1])));
        var bit1=b.half(magnitude[1],ten);var bit2=b.full(magnitude[2],ten,bit1[1]);var bit3=b.half(magnitude[3],bit2[1]);
        return new Design(List.copyOf(b.cells),List.of(magnitude[0],bit1[0],bit2[0],bit3[0]),ten,sign);
    }
    private DecimalGateDesign(){}
}
