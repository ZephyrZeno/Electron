package top.lacrus.zephyrzeno.electron;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;

class SurfaceRouterTest {
    @Test void sameCellSupportsEightIndependentTracksPerLayer() {
        Set<SurfacePoint> occupied=new HashSet<>();
        for(int layer=0;layer<2;layer++)for(int v=1;v<16;v+=2){var a=new SurfacePoint(BlockPos.ORIGIN,Direction.UP,1,v,layer);var b=new SurfacePoint(BlockPos.ORIGIN,Direction.UP,15,v,layer);var path=SurfaceRouter.route(a,b,p->p.host().equals(BlockPos.ORIGIN)&&!occupied.contains(p));assertEquals(14+(layer==1?2:0),SurfaceRouter.lengthUnits(path));occupied.addAll(path);}
        assertEquals(128,occupied.size());
    }
    @Test void upperLayerCrossesWithoutSharingPoints() {
        var a=new SurfacePoint(BlockPos.ORIGIN,Direction.NORTH,1,7,0);var b=new SurfacePoint(BlockPos.ORIGIN,Direction.NORTH,15,7,0);
        var low=SurfaceRouter.route(a,b,p->true);var upper=SurfaceRouter.route(new SurfacePoint(BlockPos.ORIGIN,Direction.NORTH,7,1,1),new SurfacePoint(BlockPos.ORIGIN,Direction.NORTH,7,15,1),p->!low.contains(p));assertEquals(8,upper.size());
    }
    @Test void oppositeFacesDoNotAllowWireThroughWall() {
        assertThrows(IllegalArgumentException.class,()->SurfaceRouter.route(new SurfacePoint(BlockPos.ORIGIN,Direction.NORTH,3,7,0),new SurfacePoint(BlockPos.ORIGIN,Direction.SOUTH,3,7,0),p->true));
    }
    @Test void wallCoreNumberingIsStableOnAllSixFaces() {
        for(Direction face:Direction.values()){var core=new CircuitParts.WallCore(UUID.randomUUID(),BlockPos.ORIGIN,face,0,"stone");for(int pin=0;pin<2;pin++){var a=core.endpoint(0,pin).point().position();var b=core.endpoint(1,pin).point().position();assertEquals(1.0625,a.distanceTo(b),1e-8);}}
    }
}
