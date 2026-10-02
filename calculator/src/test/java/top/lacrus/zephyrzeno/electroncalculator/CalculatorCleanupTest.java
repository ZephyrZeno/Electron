package top.lacrus.zephyrzeno.electron;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.*;

public class CalculatorCleanupTest {
    @Test void removesLegacySinksWithoutChangingArithmeticCellsOrStandingScreen()throws Exception {
        var old=CompactBlueprint.read(Files.newInputStream(Path.of("src/main/resources/data/electron_calculator/calculator-legacy.ebp")));
        var current=CompactBlueprint.read(Files.newInputStream(Path.of("src/main/resources/data/electron_calculator/calculator.ebp")));
        assertTrue(old.blocks.size()-current.blocks.size()>3000,"Obsolete display and dedicated branches removed");
        ModularLayout.moduleBlocks(DecimalCircuit.create()).forEach((pos,tile)->assertEquals(old.blocks.get(pos),current.blocks.get(pos),"Arithmetic cell "+pos));
        old.blocks.forEach((pos,tile)->{
            if(pos.x()>=ConsoleLayout.OFFSET_X)assertEquals(tile,current.blocks.get(pos),"Standing screen and input controller "+pos);
            if(pos.x()<ConsoleLayout.OFFSET_X&&tile.part()==CompactBlueprint.Part.LAMP)assertTrue(current.blocks.get(pos)==null||current.blocks.get(pos).part()==CompactBlueprint.Part.WOOL,"Legacy lamp removed or converted to shared wire support "+pos);
        });
        assertEquals(old.inputs,current.inputs);
        assertEquals(old.outputs,current.outputs);
        assertEquals(old.sources,current.sources);
        CalculatorCleanup.verifyRetainedRoutes(old,current);
    }
}
