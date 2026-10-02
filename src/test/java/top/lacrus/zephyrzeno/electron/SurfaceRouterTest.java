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
        assertThrows(IllegalArgumentException.class,()->SurfaceRouter.route(new SurfacePoint(BlockPos.ORIGIN,Direction.NORTH,3,7,0),new SurfacePoint(BlockPos.ORIGIN,Direction.SOUTH,3,7,0),p->p.host().equals(BlockPos.ORIGIN)&&(p.face()==Direction.NORTH||p.face()==Direction.SOUTH)));
    }
    @Test void exposedEdgesWrapOnEveryAxisWithoutCuttingThroughTheBlock(){
        for(Direction a:Direction.values())for(Direction b:Direction.values())if(a.getAxis()!=b.getAxis()){
            var start=new SurfacePoint(BlockPos.ORIGIN,a,7,7,0);var end=new SurfacePoint(BlockPos.ORIGIN,b,7,7,0);
            var route=SurfaceRouter.route(start,end,p->p.host().equals(BlockPos.ORIGIN)&&(p.face()==a||p.face()==b));
            var vertices=WireGeometry.vertices(route,p->0);
            for(int i=1;i<vertices.size();i++)for(int j=0;j<=4;j++){
                var p=vertices.get(i-1).lerp(vertices.get(i),j/4d);
                assertTrue(p.x<0||p.x>1||p.y<0||p.y>1||p.z<0||p.z>1,"Corner must stay outside the host: "+a+" -> "+b+" "+p);
            }
            assertEquals(route,SurfaceRouter.route(start,end,p->p.host().equals(BlockPos.ORIGIN)&&(p.face()==a||p.face()==b)));
        }
    }
    @Test void insideCornersAndStepsConnectExposedSurfaces(){
        Set<BlockPos> solids=Set.of(BlockPos.ORIGIN,new BlockPos(1,0,0),new BlockPos(1,1,0));
        var route=SurfaceRouter.route(new SurfacePoint(BlockPos.ORIGIN,Direction.UP,7,7,0),new SurfacePoint(new BlockPos(1,1,0),Direction.UP,7,7,0),p->solids.contains(p.host())&&!solids.contains(p.host().offset(p.face())));
        assertTrue(route.stream().anyMatch(p->p.face()==Direction.WEST));
        assertTrue(route.stream().allMatch(p->solids.contains(p.host())&&!solids.contains(p.host().offset(p.face()))));
    }
    @Test void thinBoardConnectsToTabletopOnAllSixFaces(){
        for(var face:Direction.values()){
            var board=new SurfacePoint(BlockPos.ORIGIN,face,15,7,0);
            var support=board.host().offset(board.uDirection()).offset(face.getOpposite());
            var table=new SurfacePoint(support,face,1,7,0);
            var path=SurfaceRouter.route(board,table,p->p.equals(board)||p.equals(table),p->p.host().equals(board.host())?13:0);
            assertEquals(List.of(board,table),path);
            assertEquals(5,WireGeometry.lengthUnits(path,p->p.host().equals(board.host())?13:0));
        }
    }
    @Test void gapCannotBeBridgedBySurfaceWire(){
        var a=new SurfacePoint(BlockPos.ORIGIN,Direction.UP,7,7,0);var b=new SurfacePoint(new BlockPos(2,0,0),Direction.UP,7,7,0);
        assertThrows(IllegalArgumentException.class,()->SurfaceRouter.route(a,b,p->p.host().equals(a.host())||p.host().equals(b.host())));
    }
    @Test void wallCoreNumberingIsStableOnAllSixFaces() {
        for(Direction face:Direction.values()){var core=new CircuitParts.WallCore(UUID.randomUUID(),BlockPos.ORIGIN,face,0,"stone");for(int pin=0;pin<2;pin++){var a=core.endpoint(0,pin).point().position();var b=core.endpoint(1,pin).point().position();assertEquals(1.0625,a.distanceTo(b),1e-8);}}
    }
}
