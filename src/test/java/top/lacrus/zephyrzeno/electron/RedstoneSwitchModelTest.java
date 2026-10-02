package top.lacrus.zephyrzeno.electron;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import static top.lacrus.zephyrzeno.electron.circuit.DcSolver.Branch.*;
class RedstoneSwitchModelTest {
    @Test void contactPassesExistingStrengthOnly(){for(int signal=0;signal<=15;signal++){assertEquals(signal,RedstoneSwitchModel.transmit(signal,true));assertEquals(0,RedstoneSwitchModel.transmit(signal,false));}assertEquals(0,RedstoneSwitchModel.transmit(0,true));}
    @Test void coilHasPullInReleaseHysteresis(){assertFalse(RedstoneSwitchModel.contact(3,false));assertTrue(RedstoneSwitchModel.contact(3.5,false));assertTrue(RedstoneSwitchModel.contact(3,true));assertFalse(RedstoneSwitchModel.contact(1.5,true));assertFalse(RedstoneSwitchModel.contact(-5,true));}
    @Test void controlConsumesElectricPowerButRedstoneCreatesNone(){
        var r=DcSolver.solve(List.of(source("s","p","n",5,5),resistor("coil","p","n",RedstoneSwitchModel.COIL_OHMS)));
        assertEquals(5d/1005,r.current("coil"),1e-9);assertTrue(RedstoneSwitchModel.contact(r.voltage("p"),false));
        var off=DcSolver.solve(List.of(resistor("coil","p","n",RedstoneSwitchModel.COIL_OHMS)));
        assertEquals(0,off.current("coil"),1e-10);assertFalse(RedstoneSwitchModel.contact(off.voltage("p"),false));
    }
}
