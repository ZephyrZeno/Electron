package top.lacrus.zephyrzeno.electron.pcb;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcbCoordinateBoundsTest {
    @Test void outOfBoardCoordinatesCannotAliasAnotherRowOrCopperLayer(){
        var layout=PcbLayout.empty().withLayers(2).via(0,1)
                .route(PcbLayout.point(0,1),PcbLayout.point(2,1),false)
                .route(PcbLayout.point(4,0,1),PcbLayout.point(6,0,1),false);
        String original=layout.encode();
        for(int[] cell:List.of(new int[]{128,0},new int[]{-128,2},new int[]{4,96},new int[]{4,-96},
                new int[]{32,0},new int[]{0,24},new int[]{Integer.MAX_VALUE,1},new int[]{Integer.MIN_VALUE,1})){
            assertThrows(IllegalArgumentException.class,()->layout.via(cell[0],cell[1]));
            assertThrows(IllegalArgumentException.class,()->layout.port(0,cell[0],cell[1]));
            assertThrows(IllegalArgumentException.class,()->layout.erase(cell[0],cell[1],0));
        }
        assertEquals(original,layout.encode());assertTrue(layout.vias().contains(PcbLayout.point(0,1)));
    }
    @Test void negativeYAndPositiveLayerCannotCancelToEraseSurfaceCopper(){
        var layout=PcbLayout.empty().withLayers(2).route(PcbLayout.point(4,0),PcbLayout.point(6,0),false);
        // point(4,-96,1) formerly encoded the valid surface point(4,0,0).
        assertThrows(IllegalArgumentException.class,()->layout.erase(4,-96,1));
        assertThrows(IllegalArgumentException.class,()->layout.erase(4,96,-1));
        assertThrows(IllegalArgumentException.class,()->layout.erase(4,0,-1));
        assertThrows(IllegalArgumentException.class,()->layout.erase(4,0,2));
        assertThrows(IllegalArgumentException.class,()->layout.erase(4,0,Integer.MAX_VALUE));
        assertEquals(2,layout.tracks().size());
    }
    @Test void onlyActualHoleCoordinatesCanRestoreMilledCells(){
        var layout=PcbLayout.empty().cut(5,4);String original=layout.encode();
        assertThrows(IllegalArgumentException.class,()->layout.cut(133,3));
        assertThrows(IllegalArgumentException.class,()->layout.cut(-123,5));
        assertThrows(IllegalArgumentException.class,()->layout.cut(5,100));
        assertEquals(original,layout.encode());assertFalse(layout.contains(5,4));assertTrue(layout.cut(5,4).contains(5,4));
    }
    @Test void validCoordinatesStillEditOnlyTheRequestedLayer(){
        var layout=PcbLayout.empty().withLayers(2).via(6,4)
                .route(PcbLayout.point(4,4),PcbLayout.point(8,4),false)
                .route(PcbLayout.point(4,4,1),PcbLayout.point(8,4,1),false);
        var erased=layout.erase(6,4,1);assertEquals(4,erased.tracks().stream().filter(t->PcbLayout.layer(t.a())==0).count());
        assertEquals(2,erased.tracks().stream().filter(t->PcbLayout.layer(t.a())==1).count());
        assertFalse(layout.via(6,4).vias().contains(PcbLayout.point(6,4)));
        assertEquals(PcbLayout.point(0,2),layout.port(0,0,2).ports()[0]);
        assertThrows(IllegalArgumentException.class,()->layout.port(-1,0,2));
        assertThrows(IllegalArgumentException.class,()->layout.port(6,0,2));
    }
    @Test void packagePlacementAndMovementRejectRawOverflowInsteadOfWrapping(){
        var layout=PcbLayout.empty().place(PcbLayout.Type.NOT,5,4,0);
        for(int[] cell:List.of(new int[]{128,0},new int[]{-128,2},new int[]{4,96},new int[]{Integer.MAX_VALUE,0},new int[]{0,Integer.MIN_VALUE})){
            assertThrows(IllegalArgumentException.class,()->layout.place(PcbLayout.Type.TFF,cell[0],cell[1],0));
            assertThrows(IllegalArgumentException.class,()->layout.move(0,cell[0],cell[1],0));
        }
        assertEquals(new PcbLayout.Part(PcbLayout.Type.NOT,5,4,0),layout.parts().getFirst());
    }
}
