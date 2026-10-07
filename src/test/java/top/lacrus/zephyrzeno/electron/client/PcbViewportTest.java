package top.lacrus.zephyrzeno.electron.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcbViewportTest {
    @Test void leftAndMiddleDragUseLogicalCoordinatesAndReleaseOutsideCanvas(){
        for(int button:new int[]{0,2}){
            var view=new PcbViewport();view.boardSize(128,96);
            double x=view.screenX(40),y=view.screenY(30);
            view.beginPan(button,100,80);
            assertTrue(view.drag(button,135,101));
            assertEquals(x+35,view.screenX(40),1e-8);assertEquals(y+21,view.screenY(30),1e-8);
            assertEquals(40,view.column(x+35),1e-8);assertEquals(30,view.row(y+21),1e-8);
            assertFalse(view.endPan(button==0?2:0));assertTrue(view.dragging());
            assertTrue(view.endPan(button));assertFalse(view.drag(button,400,500));
        }
    }
    @Test void zoomKeepsCursorOverSamePadEvenAfterPanning(){
        var view=new PcbViewport();view.boardSize(128,96);view.pan(-53,17);
        double u=view.column(137),v=view.row(83);
        for(int steps:new int[]{4,-2,8,-3}){
            view.zoomAt(137,83,steps);
            assertEquals(u,view.column(137),1e-8);assertEquals(v,view.row(83),1e-8);
        }
    }
    @Test void repeatedSnapshotsAndResizeDoNotRecenterTheBoard(){
        var view=new PcbViewport();view.boardSize(96,72);view.zoomAt(70,130,3);view.pan(-75,-40);
        double x=view.screenX(0),y=view.screenY(0),zoom=view.zoom();
        view.boardSize(96,72);
        assertEquals(x,view.screenX(0));assertEquals(y,view.screenY(0));assertEquals(zoom,view.zoom());
        view.fit();assertEquals(136,(view.screenX(0)+view.screenX(95))/2,1e-8);
        view.boardSize(32,24);assertEquals(105.5,(view.screenY(0)+view.screenY(23))/2,1e-8);
    }
    @Test void extremeDragsKeepSomeBoardReachable(){
        var view=new PcbViewport();view.boardSize(128,96);
        view.pan(-1e8,-1e8);assertTrue(view.screenX(127)+view.zoom()/2>=24);assertTrue(view.screenY(95)+view.zoom()/2>=24);
        view.pan(1e8,1e8);assertTrue(view.screenX(0)-view.zoom()/2<=248);assertTrue(view.screenY(0)-view.zoom()/2<=187);
    }
}
