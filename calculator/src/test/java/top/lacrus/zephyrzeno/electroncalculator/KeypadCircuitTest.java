package top.lacrus.zephyrzeno.electron;

import java.util.*;
import org.junit.jupiter.api.Test;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.KeypadCircuit;
import static org.junit.jupiter.api.Assertions.*;

class KeypadCircuitTest {
    private final KeypadCircuit keypad=KeypadCircuit.create();
    private Map<String,Boolean> state=new HashMap<>();
    private void press(String key){state=keypad.step(state,Set.of(key));}
    private int number(String prefix){int n=0;for(int bit=0;bit<4;bit++){if(state.getOrDefault(prefix+"T"+bit,false))n+=10<<bit;if(state.getOrDefault(prefix+"U"+bit,false))n+=1<<bit;}return n;}
    @Test void appendSelectSnapshotAndClear() {
        press("1");press("2");assertEquals(12,number("A"));assertEquals(0,number("XA"));
        press("MULTIPLY");press("3");press("RUN");assertEquals(12,number("XA"));assertEquals(3,number("XB"));
        assertTrue(state.get("XOP1"));assertFalse(state.get("XOP0"));assertTrue(state.get("RUN"));
        press("CLEAR");assertEquals(0,number("A"));assertFalse(state.get("HAS_A"));assertEquals(0,number("XA"));
    }
    @Test void twoDigitsBackspaceAndOperatorReplacement() {
        press("9");press("9");press("3");assertEquals(99,number("A"));assertTrue(state.get("ERROR"));
        press("BACK");assertEquals(9,number("A"));assertFalse(state.get("TWO_A"));
        press("PLUS");press("DIVIDE");assertTrue(state.get("OP0"));assertTrue(state.get("OP1"));
        press("0");press("5");assertEquals(5,number("B"));press("BACK");assertEquals(0,number("B"));assertTrue(state.get("HAS_B"));
        press("BACK");assertFalse(state.get("HAS_B"));press("BACK");assertFalse(state.get("OP_SET"));
    }
    @Test void rejectIncompleteAndOverlappingKeys() {
        press("RUN");assertTrue(state.get("ERROR"));assertFalse(state.get("RUN"));
        state=keypad.step(state,Set.of("1","2"));assertFalse(state.get("HAS_A"));assertTrue(state.get("ERROR"));
        press("CLEAR");press("1");press("PLUS");press("RUN");assertFalse(state.get("RUN"));assertEquals(0,number("XA"));
    }
    @Test void digitAfterRunBeginsAnotherExpression() {
        press("9");press("9");press("MULTIPLY");press("9");press("9");press("RUN");
        press("1");assertEquals(1,number("A"));assertEquals(0,number("B"));assertFalse(state.get("OP_SET"));assertEquals(99,number("XA"));
        press("PLUS");press("1");press("RUN");assertEquals(1,number("XA"));assertEquals(1,number("XB"));assertFalse(state.get("XOP1"));
    }
    @Test void everyOperandAndOperationIsCapturedWithoutArithmetic() {
        for(int op=0;op<4;op++)for(int a=0;a<100;a++)for(int b=0;b<100;b++) {
            state=new HashMap<>();press(""+(a/10));press(""+(a%10));press(List.of("PLUS","MINUS","MULTIPLY","DIVIDE").get(op));press(""+(b/10));press(""+(b%10));press("RUN");
            assertEquals(a,number("XA"));assertEquals(b,number("XB"));assertEquals(op,(state.get("XOP0")?1:0)+(state.get("XOP1")?2:0));
        }
    }
}
