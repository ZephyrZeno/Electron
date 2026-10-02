package top.lacrus.zephyrzeno.electron;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.util.math.Vec3d;
import top.lacrus.zephyrzeno.electron.circuit.OverheadCable;

class OverheadCableTest {
    @Test void suspendedCurveKeepsEndpointsSagsAndConsumesItsFullLength(){
        var a=new Vec3d(0,10,0);var b=new Vec3d(32,10,0);var curve=OverheadCable.curve(a,b);
        assertEquals(a,curve.getFirst());assertEquals(b,curve.getLast());
        assertEquals(10-1.92,curve.get(curve.size()/2).y,1e-10);
        assertTrue(OverheadCable.units(curve)>32*16);
        assertEquals(OverheadCable.units(curve),OverheadCable.units(OverheadCable.curve(b,a)));
    }
    @Test void unequalTowerHeightsRemainBelowTheStraightSpan(){
        var a=new Vec3d(0,10,0);var b=new Vec3d(30,14,20);var curve=OverheadCable.curve(a,b);
        for(int i=1;i<curve.size()-1;i++)assertTrue(curve.get(i).y<a.lerp(b,i/(double)(curve.size()-1)).y);
    }
}
