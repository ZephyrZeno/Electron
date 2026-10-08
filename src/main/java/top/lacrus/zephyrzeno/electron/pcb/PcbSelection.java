package top.lacrus.zephyrzeno.electron.pcb;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;

/** Atomic region edits. Board outline and external connectors always stay in place. */
public final class PcbSelection {
    private PcbSelection() {}

    /** Inclusive coordinates on the board; the region applies to every copper layer. */
    public record Area(int minX, int minY, int maxX, int maxY) {
        public Area {
            if (minX < 0 || minY < 0 || minX > maxX || minY > maxY
                    || maxX >= PcbLayout.WIDTH || maxY >= PcbLayout.HEIGHT)
                throw new IllegalArgumentException("selection_bounds");
        }

        public boolean contains(int x, int y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }

        public boolean contains(PcbLayout.Part part) {
            return contains(part.x(), part.y()) && contains(part.x() + 2, part.y() + 2);
        }

        private boolean containsPoint(int point) {
            return contains(PcbLayout.x(point), PcbLayout.y(point));
        }

        private boolean contains(PcbLayout.Track track) {
            return containsPoint(track.a()) && containsPoint(track.b());
        }
    }

    public static Area area(int a, int b) {
        if (a < 0 || b < 0 || PcbLayout.layer(a) != 0 || PcbLayout.layer(b) != 0)
            throw new IllegalArgumentException("selection_bounds");
        return new Area(Math.min(PcbLayout.x(a), PcbLayout.x(b)), Math.min(PcbLayout.y(a), PcbLayout.y(b)),
                Math.max(PcbLayout.x(a), PcbLayout.x(b)), Math.max(PcbLayout.y(a), PcbLayout.y(b)));
    }

    public static boolean hasContents(PcbLayout layout, Area area) {
        validateArea(layout, area);
        return layout.parts().stream().anyMatch(area::contains)
                || layout.tracks().stream().anyMatch(area::contains)
                || layout.vias().stream().anyMatch(area::containsPoint);
    }

    /** Tracks with one endpoint outside the region stay in place, including during a move/delete. */
    public static int boundaryTracks(PcbLayout layout, Area area) {
        validateArea(layout, area);
        int count = 0;
        for (var track : layout.tracks())
            if (area.containsPoint(track.a()) != area.containsPoint(track.b())) count++;
        return count;
    }

    public static PcbLayout copy(PcbLayout layout, Area area, int dx, int dy) {
        return edit(layout, area, dx, dy, true, true);
    }

    public static PcbLayout move(PcbLayout layout, Area area, int dx, int dy) {
        return edit(layout, area, dx, dy, false, true);
    }

    public static PcbLayout delete(PcbLayout layout, Area area) {
        return edit(layout, area, 0, 0, false, false);
    }

    private static PcbLayout edit(PcbLayout layout, Area area, int dx, int dy,
                                  boolean keepSource, boolean translate) {
        if (!hasContents(layout, area)) throw new IllegalArgumentException("selection_empty");
        if (translate && dx == 0 && dy == 0) throw new IllegalArgumentException("selection_unchanged");
        var parts = new ArrayList<PcbLayout.Part>();
        var tracks = new ArrayList<PcbLayout.Track>();
        var vias = new LinkedHashSet<Integer>();
        // Preserve source ordering; copied packages append so existing component indices stay stable.
        for (var part : layout.parts()) if (keepSource || !area.contains(part)) parts.add(part);
        for (var track : layout.tracks()) if (keepSource || !area.contains(track)) tracks.add(track);
        for (int via : layout.vias()) if (keepSource || !area.containsPoint(via)) vias.add(via);
        if (translate) {
            for (var part : layout.parts()) if (area.contains(part)) {
                int at = shifted(layout, PcbLayout.point(part.x(), part.y()), dx, dy);
                parts.add(new PcbLayout.Part(part.type(), PcbLayout.x(at), PcbLayout.y(at), part.turn()));
            }
            for (var track : layout.tracks()) if (area.contains(track))
                tracks.add(new PcbLayout.Track(shifted(layout, track.a(), dx, dy), shifted(layout, track.b(), dx, dy)));
            for (int via : layout.vias()) if (area.containsPoint(via))
                vias.add(shifted(layout, via, dx, dy));
        }
        // A single immutable-layout validation commits the edit or rejects every changed object.
        var edited = new PcbLayout(parts, tracks, layout.width(), layout.height(), layout.cutouts(),
                layout.layers(), vias, layout.connectors());
        if (edited.equals(layout)) throw new IllegalArgumentException("selection_unchanged");
        return edited;
    }

    private static void validateArea(PcbLayout layout, Area area) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(area, "area");
        if (area.maxX() >= layout.width() || area.maxY() >= layout.height())
            throw new IllegalArgumentException("selection_bounds");
    }

    private static int shifted(PcbLayout layout, int point, int dx, int dy) {
        // Check before encoding: otherwise x == WIDTH aliases x == 0 on the next row.
        long x = (long) PcbLayout.x(point) + dx;
        long y = (long) PcbLayout.y(point) + dy;
        if (x < 0 || y < 0 || x >= layout.width() || y >= layout.height())
            throw new IllegalArgumentException("selection_bounds");
        return PcbLayout.point((int) x, (int) y, PcbLayout.layer(point));
    }
}
