package top.lacrus.zephyrzeno.electron.circuit;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.util.math.*;

/** Bounded A* on exposed block faces. Faces meet around edges, never through solid blocks. */
public final class SurfaceRouter {
    /** Player wiring is a literal straight grid segment. Corners are explicit player-placed nodes. */
    public static List<SurfacePoint> straight(SurfacePoint start,SurfacePoint end,Predicate<SurfacePoint> available){
        if(start.face()!=end.face()||start.plane()!=end.plane()||start.layer()!=end.layer())throw new CircuitError("error.electron.same_plane");
        if(start.equals(end))throw new CircuitError("error.electron.different_node");
        var delta=end.position().subtract(start.position());double du=delta.dotProduct(net.minecraft.util.math.Vec3d.of(start.uDirection().getVector())),dv=delta.dotProduct(net.minecraft.util.math.Vec3d.of(start.vDirection().getVector()));
        if(Math.abs(du)>.0001&&Math.abs(dv)>.0001)throw new CircuitError("error.electron.aligned");
        int steps=(int)Math.round((Math.abs(du)+Math.abs(dv))*8);if(steps>256)throw new CircuitError("error.electron.length");
        List<SurfacePoint> result=new ArrayList<>();SurfacePoint p=start;int u=(int)Math.signum(du)*2,v=(int)Math.signum(dv)*2;
        for(int i=0;i<=steps;i++){if(!available.test(p))throw new CircuitError("error.electron.blocked");result.add(p);p=p.step(u,v);}return List.copyOf(result);
    }
    private record Candidate(SurfacePoint p,int cost,int score) {}
    public static List<SurfacePoint> route(SurfacePoint start,SurfacePoint end,Predicate<SurfacePoint> available) {
        return route(start,end,available,p->0);
    }
    public static List<SurfacePoint> route(SurfacePoint start,SurfacePoint end,Predicate<SurfacePoint> available,ToIntFunction<SurfacePoint> inset) {
        if(start.layer()!=end.layer())throw new CircuitError("error.electron.wall_plane");
        if(start.equals(end))throw new CircuitError("error.electron.different_pin");
        if(start.host().getManhattanDistance(end.host())>32)throw new CircuitError("error.electron.span");
        if(!available.test(start)||!available.test(end))throw new CircuitError("error.electron.endpoint_blocked");
        PriorityQueue<Candidate> open=new PriorityQueue<>(Comparator.comparingInt(Candidate::score).thenComparingInt(Candidate::cost));
        Map<SurfacePoint,Integer> cost=new HashMap<>();Map<SurfacePoint,SurfacePoint> from=new HashMap<>();
        open.add(new Candidate(start,0,distance(start,end,inset)));cost.put(start,0);int visited=0;
        while(!open.isEmpty()&&visited++<32768) {
            Candidate c=open.remove();if(c.cost!=cost.get(c.p))continue;
            if(c.p.equals(end)){List<SurfacePoint> path=new ArrayList<>();for(SurfacePoint p=end;p!=null;p=from.get(p))path.add(p);Collections.reverse(path);return List.copyOf(path);}
            if(c.cost>=512)continue;
            for(SurfacePoint next:neighbors(c.p,inset)) {
                if(next.host().getManhattanDistance(start.host())>32||!available.test(next))continue;
                int g=c.cost+WireGeometry.segmentUnits(c.p,next,inset);if(g>512||g>=cost.getOrDefault(next,Integer.MAX_VALUE))continue;
                cost.put(next,g);from.put(next,c.p);open.add(new Candidate(next,g,g+distance(next,end,inset)));
            }
        }
        throw new CircuitError("error.electron.no_route");
    }
    private static List<SurfacePoint> neighbors(SurfacePoint p,ToIntFunction<SurfacePoint> inset){
        List<SurfacePoint> result=new ArrayList<>(16);
        for(int[] dir:new int[][]{{2,0},{0,2},{-2,0},{0,-2}}){
            var flat=p.step(dir[0],dir[1]);result.add(flat);
            if(flat.host().equals(p.host()))continue;
            Direction tangent=dir[0]!=0?p.uDirection():p.vDirection();if(dir[0]+dir[1]<0)tangent=tangent.getOpposite();
            result.add(SurfacePoint.hit(p.host(),tangent,p.position(),p.layer()));
            result.add(SurfacePoint.hit(p.host().offset(p.face()).offset(tangent),tangent.getOpposite(),p.position(),p.layer()));
            // A thin mounted board occupies the cell above its support. Join its edge to the
            // adjacent tabletop, using real surface height rather than the enclosing block cell.
            for(int offset:new int[]{-1,1}){
                var shifted=new SurfacePoint(flat.host().offset(p.face(),offset),p.face(),flat.u(),flat.v(),p.layer());
                double rise=Math.abs(WireGeometry.position(shifted,inset).subtract(WireGeometry.position(p,inset)).dotProduct(Vec3d.of(p.face().getVector())));
                if(rise<=.25&& (inset.applyAsInt(p)>0||inset.applyAsInt(shifted)>0))result.add(shifted);
            }
        }
        return result;
    }
    private static int distance(SurfacePoint a,SurfacePoint b,ToIntFunction<SurfacePoint> inset){var x=WireGeometry.position(a,inset);var y=WireGeometry.position(b,inset);return (int)Math.floor((Math.abs(x.x-y.x)+Math.abs(x.y-y.y)+Math.abs(x.z-y.z))*16+1e-8);}
    public static int lengthUnits(List<SurfacePoint> points){return WireGeometry.lengthUnits(points,p->0);}
}
