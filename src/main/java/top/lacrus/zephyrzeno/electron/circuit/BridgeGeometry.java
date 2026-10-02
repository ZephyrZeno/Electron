package top.lacrus.zephyrzeno.electron.circuit;

import java.util.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.CircuitParts.Bridge;

/** Insulating feet sit outside the trace grid; corner cells reserve their working clearance. */
public final class BridgeGeometry {
    public record Part(Box bounds,int color){}
    public static boolean reserved(SurfacePoint p){return (p.u()==1||p.u()==15)&&(p.v()==1||p.v()==15);}
    public static List<Part> parts(CircuitState state,Bridge bridge){
        var anchor=new SurfacePoint(bridge.host(),bridge.face(),1,1,0);
        var normal=Vec3d.of(bridge.face().getVector());
        var origin=state.position(anchor).subtract(normal.multiply(.03125)).subtract(Vec3d.of(anchor.uDirection().getVector()).multiply(1/16d)).subtract(Vec3d.of(anchor.vDirection().getVector()).multiply(1/16d));
        List<Part> result=new ArrayList<>();
        for(double u:new double[]{.375,15.625})for(double v:new double[]{.375,15.625}){
            var base=origin.add(Vec3d.of(anchor.uDirection().getVector()).multiply(u/16)).add(Vec3d.of(anchor.vDirection().getVector()).multiply(v/16));
            result.add(part(base,normal,.013,.022,.013,0x626d78));
            result.add(part(base,normal,.052,.018,.026,0xe5d4ad));
            result.add(part(base,normal,.084,.022,.007,0xb9a786));
            result.add(part(base,normal,.094,.010,.003,0x555962));
        }
        return List.copyOf(result);
    }
    private static Part part(Vec3d base,Vec3d normal,double height,double radius,double depth,int color){
        var c=base.add(normal.multiply(height));double x=normal.x!=0?depth:radius,y=normal.y!=0?depth:radius,z=normal.z!=0?depth:radius;
        return new Part(new Box(c.add(-x,-y,-z),c.add(x,y,z)),color);
    }
    private BridgeGeometry(){}
}
