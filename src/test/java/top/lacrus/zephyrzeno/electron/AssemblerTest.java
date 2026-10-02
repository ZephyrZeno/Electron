package top.lacrus.zephyrzeno.electron;

import org.junit.jupiter.api.Test;
import top.lacrus.zephyrzeno.electron.assembly.Assembler;
import static org.junit.jupiter.api.Assertions.*;

class AssemblerTest {
    @Test void resolvesForwardAndBackwardLabelsAndLiterals() {
        assertArrayEquals(new byte[]{1,10,3,16,9,4,0,0,12,0,9,0},
            Assembler.assemble("start: LDI 0b1010 ; value\nSTA 0x10\nJMP end\nNOP\nend: OUT\nJMP start"));
    }
    @Test void rejectsMalformedPrograms() {
        for (String source : new String[]{"", "LDI 256", "LDI -1", "WHAT 4", "HLT 1", "LDI", "JMP missing", "x: NOP\nx: HLT", "JMP 250", "LDI 1 2"})
            assertThrows(IllegalArgumentException.class, () -> Assembler.assemble(source), source);
        assertThrows(IllegalArgumentException.class, () -> Assembler.assemble("NOP\n".repeat(257)));
    }
    @Test void acceptsFullRom() { assertEquals(512, Assembler.assemble("NOP\n".repeat(255) + "HLT").length); }
}
