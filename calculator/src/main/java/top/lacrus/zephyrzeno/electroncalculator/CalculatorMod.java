package top.lacrus.zephyrzeno.electroncalculator;

import net.fabricmc.api.ModInitializer;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.DecimalCalculatorCommands;

/** Standalone generator: neither requires nor loads the Circuit Foundry mod. */
public final class CalculatorMod implements ModInitializer {
    @Override public void onInitialize() { DecimalCalculatorCommands.register(); }
}
