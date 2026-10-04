package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;

/** Bounded deterministic autorouter for supplied teaching designs. Output is ordinary editable copper. */
public final class PcbRouter {
    public static PcbLayout route(List<PcbLayout.Part> parts,Map<String,List<Integer>> signals,int width,int height,int layers){
        int size=PcbLayout.CELLS*layers;int[] owner=new int[size];Arrays.fill(owner,-1);var tracks=new LinkedHashSet<PcbLayout.Track>();var vias=new HashSet<Integer>();
        var groups=new ArrayList<>(signals.values());for(int n=0;n<groups.size();n++)for(int pad:groups.get(n)){if(owner[pad]>=0&&owner[pad]!=n)throw new IllegalArgumentException("shared pad");for(int l=0;l<layers;l++)owner[pad+l*PcbLayout.CELLS]=n;if(layers>1)vias.add(pad);}
        var order=new ArrayList<Integer>();for(int i=0;i<groups.size();i++)order.add(i);order.sort(Comparator.comparingInt(i->groups.get(i).size()));
        for(int net:order){var pads=groups.get(net);var connected=new HashSet<Integer>();for(int l=0;l<layers;l++)connected.add(pads.getFirst()+l*PcbLayout.CELLS);for(int i=1;i<pads.size();i++){
            int from=pads.get(i);if(connected.contains(from))continue;
            int[] previous=new int[size],distance=new int[size];Arrays.fill(previous,-1);Arrays.fill(distance,Integer.MAX_VALUE);
            record Step(int point,int cost,int priority){}
            var queue=new PriorityQueue<Step>(Comparator.comparingInt(Step::priority).thenComparingInt(Step::point));
            int minX=width,minY=height,maxX=0,maxY=0;for(int at:connected){minX=Math.min(minX,PcbLayout.x(at));minY=Math.min(minY,PcbLayout.y(at));maxX=Math.max(maxX,PcbLayout.x(at));maxY=Math.max(maxY,PcbLayout.y(at));}
            queue.add(new Step(from,0,0));distance[from]=0;int goal=-1;
            while(!queue.isEmpty()){
                var step=queue.remove();int at=step.point;if(step.cost!=distance[at])continue;if(connected.contains(at)){goal=at;break;}
                int x=PcbLayout.x(at),y=PcbLayout.y(at),layer=PcbLayout.layer(at);
                for(int direction=0;direction<6;direction++){
                    int nx=x+(direction==0?1:direction==1?-1:0),ny=y+(direction==2?1:direction==3?-1:0),nl=layer+(direction==4?1:direction==5?-1:0);
                    if(nx<0||ny<0||nx>=width||ny>=height||nl<0||nl>=layers)continue;int next=PcbLayout.point(nx,ny,nl);if(owner[next]>=0&&owner[next]!=net)continue;
                    if(nl!=layer){boolean clear=true;int base=next%PcbLayout.CELLS;for(int l=0;l<layers;l++)if(owner[base+l*PcbLayout.CELLS]>=0&&owner[base+l*PcbLayout.CELLS]!=net){clear=false;break;}if(!clear)continue;}
                    int cost=step.cost+(nl!=layer?18:layer==0?4:(layer==1&&nx!=x||layer==2&&ny!=y)?1:3);
                    if(cost>=distance[next])continue;distance[next]=cost;previous[next]=at;
                    int heuristic=Math.max(0,minX-nx)+Math.max(0,nx-maxX)+Math.max(0,minY-ny)+Math.max(0,ny-maxY);queue.add(new Step(next,cost,cost+heuristic));
                }
            }
            if(goal<0)throw new IllegalStateException("Unroutable PCB net "+net+" pad "+i);
            for(int at=goal;at!=from;){int before=previous[at];owner[at]=owner[before]=net;connected.add(at);connected.add(before);
                if(PcbLayout.layer(at)==PcbLayout.layer(before))tracks.add(new PcbLayout.Track(at,before));else{int via=at%PcbLayout.CELLS;vias.add(via);for(int l=0;l<layers;l++){owner[via+l*PcbLayout.CELLS]=net;connected.add(via+l*PcbLayout.CELLS);}}
                at=before;
            }
        }}
        var layout=new PcbLayout(parts,new ArrayList<>(tracks),width,height,Set.of(),layers,vias,Arrays.stream(PcbLayout.ports(width,height)).boxed().toList());
        var nets=layout.nets();var unique=new HashSet<Integer>();for(var group:groups){int n=nets[group.getFirst()];if(!unique.add(n))throw new IllegalStateException("Router shorted distinct nets");for(int pad:group)if(nets[pad]!=n)throw new IllegalStateException("Router left an open pad");}
        return layout;
    }
    private PcbRouter(){}
}
