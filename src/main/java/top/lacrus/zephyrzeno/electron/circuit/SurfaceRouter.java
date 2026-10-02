package top.lacrus.zephyrzeno.electron.circuit;

import java.util.*;
import java.util.function.Predicate;

/** Bounded A* along supported coplanar faces, with independent layer occupancy. */
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
        if(start.face()!=end.face()||start.plane()!=end.plane()||start.layer()!=end.layer())throw new CircuitError("error.electron.wall_plane");
        if(start.equals(end))throw new CircuitError("error.electron.different_pin");
        if(start.host().getManhattanDistance(end.host())>32)throw new CircuitError("error.electron.span");
        PriorityQueue<Candidate> open=new PriorityQueue<>(Comparator.comparingInt(Candidate::score).thenComparingInt(Candidate::cost));
        Map<SurfacePoint,Integer> cost=new HashMap<>();Map<SurfacePoint,SurfacePoint> from=new HashMap<>();
        open.add(new Candidate(start,0,distance(start,end)));cost.put(start,0);int visited=0;
        while(!open.isEmpty()&&visited++<32768) {
            Candidate c=open.remove();if(c.cost!=cost.get(c.p))continue;
            if(c.p.equals(end)){List<SurfacePoint> path=new ArrayList<>();for(SurfacePoint p=end;p!=null;p=from.get(p))path.add(p);Collections.reverse(path);return List.copyOf(path);}
            if(c.cost>=256)continue;
            for(int[] dir:new int[][]{{2,0},{0,2},{-2,0},{0,-2}}) {
                SurfacePoint next=c.p.step(dir[0],dir[1]);
                if(next.host().getManhattanDistance(start.host())>32||!available.test(next))continue;
                int g=c.cost+1;if(g>=cost.getOrDefault(next,Integer.MAX_VALUE))continue;
                cost.put(next,g);from.put(next,c.p);open.add(new Candidate(next,g,g+distance(next,end)));
            }
        }
        throw new CircuitError("error.electron.no_route");
    }
    private static int distance(SurfacePoint a,SurfacePoint b){var x=a.position();var y=b.position();return (int)Math.round((Math.abs(x.x-y.x)+Math.abs(x.y-y.y)+Math.abs(x.z-y.z))*8);}
    public static int lengthUnits(List<SurfacePoint> points){int units=(points.size()-1)*2+points.getFirst().layer()+points.getLast().layer();for(int i=1;i<points.size();i++)units+=Math.abs(points.get(i).layer()-points.get(i-1).layer());return units;}
}
