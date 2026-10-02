package top.lacrus.zephyrzeno.electron;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electron.logic.*;
class WireLogicTest {
    @Test void allEightPoweredFunctionsKeepTheirTruthTables(){for(var f:LogicFunction.values())for(int bits=0;bits<4;bits++)assertEquals(f.apply((bits&1)!=0,(bits&2)!=0)?1:0,WireLogic.evaluate(f,bits&1,bits>>1),f+" "+bits);}
    @Test void floatingAndMidRailAreNotLow(){assertEquals(2,WireLogic.input(0,5,false));assertEquals(2,WireLogic.input(2.5,5,true));assertEquals(0,WireLogic.input(.2,5,true));assertEquals(1,WireLogic.input(4.8,5,true));}
    @Test void controllingInputsCanResolveUnknowns(){assertEquals(0,WireLogic.evaluate(LogicFunction.AND,0,2));assertEquals(1,WireLogic.evaluate(LogicFunction.OR,1,2));assertEquals(2,WireLogic.evaluate(LogicFunction.XOR,1,2));assertEquals(2,WireLogic.evaluate(LogicFunction.NOT,2,0));}
}
