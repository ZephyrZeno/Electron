package top.lacrus.zephyrzeno.electron.pcb;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PcbSelectionTest {
    private static int point(int x, int y) { return PcbLayout.point(x, y); }

    private static PcbLayout module() {
        return PcbLayout.empty().withLayers(2)
                .place(PcbLayout.Type.NOT, 3, 3, 1)
                .route(point(3, 3), point(6, 3), false)
                .route(PcbLayout.point(6, 3, 1), PcbLayout.point(6, 6, 1), true)
                .via(6, 3);
    }

    private static PcbSelection.Area moduleArea() {
        return PcbSelection.area(point(2, 2), point(7, 7));
    }

    @Test void copiesCompleteModuleAcrossCopperLayersWithoutChangingSource() {
        var source = module();
        String original = source.encode();
        var copy = PcbSelection.copy(source, moduleArea(), 12, 4);
        assertEquals(original, source.encode());
        assertEquals(2, copy.parts().size());
        assertEquals(source.parts().getFirst(), copy.parts().getFirst());
        assertEquals(new PcbLayout.Part(PcbLayout.Type.NOT, 15, 7, 1), copy.parts().getLast());
        assertEquals(source.tracks().size() * 2, copy.tracks().size());
        for (var track : source.tracks()) {
            int a = PcbLayout.point(PcbLayout.x(track.a()) + 12, PcbLayout.y(track.a()) + 4, PcbLayout.layer(track.a()));
            int b = PcbLayout.point(PcbLayout.x(track.b()) + 12, PcbLayout.y(track.b()) + 4, PcbLayout.layer(track.b()));
            assertTrue(copy.tracks().contains(new PcbLayout.Track(a, b)));
        }
        assertEquals(Set.of(point(6, 3), point(18, 7)), copy.vias());
        int[] nets = copy.nets();
        assertEquals(nets[point(15, 7)], nets[PcbLayout.point(18, 10, 1)]);
        assertNotEquals(nets[point(3, 3)], nets[point(15, 7)], "Copied modules do not gain implicit connections");
        assertEquals(source.connectors(), copy.connectors());
        assertEquals(copy, PcbLayout.decode(copy.encode()));
    }

    @Test void failedEditsLeaveSourceAndAllItsObjectsUnchanged() {
        var source = module().place(PcbLayout.Type.AND, 15, 7, 0).cut(24, 18);
        String original = source.encode();
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.copy(source, moduleArea(), 12, 4));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.move(source, moduleArea(), 12, 4));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.move(source, moduleArea(), 28, 0));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.copy(source, moduleArea(), Integer.MAX_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.move(source, moduleArea(), Integer.MIN_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.copy(source, moduleArea(), 21, 15), "A footprint cannot cross a board cutout");
        assertEquals(original, source.encode());
        assertEquals(source, PcbLayout.decode(original));
    }

    @Test void movingLeavesBoundaryCopperAndPartiallySelectedPackagesInPlace() {
        var source = module().route(point(1, 3), point(3, 3), false)
                .route(PcbLayout.point(6, 6, 1), PcbLayout.point(6, 9, 1), true)
                .place(PcbLayout.Type.LED, 7, 5, 0);
        var area = moduleArea();
        assertEquals(2, PcbSelection.boundaryTracks(source, area));
        assertFalse(area.contains(source.parts().getLast()));
        var moved = PcbSelection.move(source, area, 12, 4);
        assertFalse(moved.parts().contains(source.parts().getFirst()));
        assertTrue(moved.parts().contains(source.parts().getLast()));
        assertTrue(moved.parts().contains(new PcbLayout.Part(PcbLayout.Type.NOT, 15, 7, 1)));
        for (var track : source.tracks()) {
            boolean a = area.contains(PcbLayout.x(track.a()), PcbLayout.y(track.a()));
            boolean b = area.contains(PcbLayout.x(track.b()), PcbLayout.y(track.b()));
            assertEquals(!(a && b), moved.tracks().contains(track), "Only wholly selected copper moves");
        }
        assertEquals(Set.of(point(18, 7)), moved.vias());
        assertEquals(source.connectors(), moved.connectors());
        assertEquals(moved, PcbLayout.decode(moved.encode()));
    }

    @Test void deletingPreservesBoardOutlinePortsAndCrossBoundaryTracks() {
        var source = module().cut(30, 20).port(0, 0, 2)
                .route(point(0, 2), point(3, 2), false);
        var area = PcbSelection.area(point(0, 0), point(7, 7));
        var deleted = PcbSelection.delete(source, area);
        assertTrue(deleted.parts().isEmpty());
        assertTrue(deleted.tracks().isEmpty());
        assertTrue(deleted.vias().isEmpty());
        assertEquals(source.width(), deleted.width());
        assertEquals(source.height(), deleted.height());
        assertEquals(source.layers(), deleted.layers());
        assertEquals(source.cutouts(), deleted.cutouts());
        assertEquals(source.connectors(), deleted.connectors(), "Selecting the edge never deletes a fixed external interface");

        var crossing = module().route(point(1, 3), point(3, 3), false);
        var cut = PcbSelection.delete(crossing, moduleArea());
        assertEquals(List.of(new PcbLayout.Track(point(1, 3), point(2, 3))), cut.tracks());
        assertEquals(1, PcbSelection.boundaryTracks(crossing, moduleArea()));
        assertEquals(cut, PcbLayout.decode(cut.encode()));
    }

    @Test void selectionNormalizesCornersRejectsInvalidRegionsAndRejectsEmptyOrStationaryEdits() {
        var source = module();
        assertEquals(moduleArea(), PcbSelection.area(point(7, 7), point(2, 2)));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.area(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.area(0, PcbLayout.point(2, 2, 1)));
        assertThrows(IllegalArgumentException.class, () -> new PcbSelection.Area(4, 2, 3, 5));
        var outside = PcbSelection.area(point(0, 0), point(32, 23));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.hasContents(source, outside));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.boundaryTracks(source, outside));
        var empty = PcbSelection.area(point(20, 12), point(25, 18));
        assertFalse(PcbSelection.hasContents(source, empty));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.delete(source, empty));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.copy(source, empty, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.move(source, moduleArea(), 0, 0));
        var copper = PcbLayout.empty().route(point(2, 2), point(3, 2), false);
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.copy(copper, moduleArea(), 0, 0));
    }

    @Test void copperCanBeEditedUnderAnUnselectedPackageAndDoesNotWrapAtBoardEdge() {
        var source = PcbLayout.empty().place(PcbLayout.Type.XOR, 3, 3, 0)
                .route(point(3, 4), point(4, 4), false);
        var row = PcbSelection.area(point(3, 4), point(4, 4));
        var moved = PcbSelection.move(source, row, 4, 0);
        assertEquals(source.parts(), moved.parts());
        assertEquals(List.of(new PcbLayout.Track(point(7, 4), point(8, 4))), moved.tracks());
        var wide = PcbLayout.empty().resize(PcbLayout.WIDTH, 24)
                .route(point(126, 4), point(127, 4), false);
        var edge = PcbSelection.area(point(126, 4), point(127, 4));
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.move(wide, edge, 1, 0));
    }

    @Test void exceedingPackageCapacityRejectsTheWholeCopy() {
        var parts = new ArrayList<PcbLayout.Part>();
        for (int row = 0; row < 16; row++) for (int column = 0; column < 16; column++)
            parts.add(new PcbLayout.Part(PcbLayout.Type.NOT, 1 + column * 3, row * 3, 0));
        var source = new PcbLayout(parts, List.of(), 128, 96, Set.of());
        var area = PcbSelection.area(point(1, 0), point(3, 2));
        assertEquals(PcbLayout.MAX_PARTS, source.parts().size());
        assertThrows(IllegalArgumentException.class, () -> PcbSelection.copy(source, area, 60, 60));
        assertEquals(parts, source.parts());
    }
}
