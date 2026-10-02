package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.nio.file.*;
import java.util.*;
import static top.lacrus.zephyrzeno.electroncalculator.BluePrint.CompactBlueprint.*;

/** Offline connectivity check, separate from the Minecraft integration suite. */
public final class BlueprintVerifier {
    private static final int[] DX={1,0,-1,0},DZ={0,1,0,-1};
    private BlueprintVerifier() {}
    private static final class Feedback extends IllegalStateException {
        final Set<Pos> affected;
        Feedback(int net,Set<Pos> affected){super("Repeater feedback loop net="+net+" near "+affected.iterator().next());this.affected=affected;}
    }
    public static int verify(CompactBlueprint plan) {
        return verifyInternal(plan,false,new HashMap<>());
    }
    /** Checks inter-module wiring without treating the tested module internals as individual nets. */
    public static int verifyModuleRoutes(CompactBlueprint plan,Map<Integer,List<Pos>> destinations,PhysicalNetlist netlist) {
        Map<Integer,List<Pos>> networks=new HashMap<>();
        plan.blocks.forEach((p,t)->{if(t.net()>=0&&(t.part()==Part.WIRE||t.part()==Part.REPEATER))networks.computeIfAbsent(t.net(),k->new ArrayList<>()).add(p);});
        Map<Integer,Integer> delays=new HashMap<>();
        for(var entry:destinations.entrySet()) {
            int net=entry.getKey();Map<Pos,Integer> levels=power(plan,net,14);
            for(Pos target:entry.getValue())if(levels.getOrDefault(target,0)<3)
                throw new IllegalStateException("Module pin lacks power: net="+net+" pin="+target+" power="+levels.getOrDefault(target,0));
            delays.put(net,verifyNoFeedback(plan,net,networks.get(net)));
        }
        Map<Integer,Integer> ready=new HashMap<>();ready.put(0,0);ready.put(1,0);
        for(var unit:netlist.units) {
            int time=0;for(int input:unit.inputs())time=Math.max(time,ready.getOrDefault(input,0)+delays.getOrDefault(input,0));
            if(unit.type()!=PhysicalNetlist.Type.INPUT)time+=unit.type()==PhysicalNetlist.Type.ADDER?16*((unit.inputs().length-1)/2)+32:14;
            for(int out:unit.outputs())if(netlist.owns(unit,out))ready.put(out,time);
        }
        int max=0;for(int net:delays.keySet())max=Math.max(max,ready.getOrDefault(net,0)+delays.get(net));
        System.out.println("Verified "+destinations.size()+" module nets");return max+max/4+100;
    }
    static int repair(CompactBlueprint plan) {
        DecimalCircuit circuit=plan.inputs.containsKey("AT0")?DecimalCircuit.create():DecimalCircuit.smoke();
        for(int id=0;id<circuit.nodes.size();id++) {
            var node=circuit.nodes.get(id);if(node.kind()!=DecimalCircuit.Kind.REGISTER)continue;
            Pos p=plan.sources.get(id).add(-3,-3,-8);
            plan.blocks.put(p,new Tile(Part.REPEATER,node.module(),1,node.a()));
        }
        return verifyInternal(plan,true,new HashMap<>());
    }
    private static int verifyInternal(CompactBlueprint plan,boolean repair,Map<Integer,Integer> reroutes) {
        plan.blocks.forEach((p,t)->{
            if(t.part()!=Part.LEVER&&t.part()!=Part.TORCH)return;
            for(int[] d:new int[][]{{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,1,0},{0,-1,0}}) {
                Pos next=p.add(d[0],d[1],d[2]);Tile other=plan.blocks.get(next);
                if(other!=null&&(other.part()==Part.WIRE||other.part()==Part.REPEATER)&&other.net()!=t.net())
                    throw new IllegalStateException("Unintended component contact "+p+" -> "+next);
            }
        });
        Map<Integer,List<Pos>> networks=new HashMap<>();
        plan.blocks.forEach((p,t)->{if(t.part()==Part.WIRE||t.part()==Part.REPEATER)networks.computeIfAbsent(t.net(),k->new ArrayList<>()).add(p);});
        int checked=0;
        Map<Integer,Integer> netDelays=new HashMap<>();
        Set<Pos> fixed=repair?CompactBlueprint.fixedPositions(plan.inputs.containsKey("AT0")?DecimalCircuit.create():DecimalCircuit.smoke()):null;
        for(var entry:networks.entrySet()) {
            int net=entry.getKey();Pos source=plan.sources.get(net);
            if(source==null)throw new IllegalStateException("No source: "+net);
            Map<Pos,Integer> power=power(plan,net);
            for(Pos p:entry.getValue()) {
                if(sourceStub(p,source))continue;
                if(!power.containsKey(p)) {
                    if(fixed!=null) {
                        if(reroutes.merge(net,1,Integer::sum)>3)throw new IllegalStateException("Unresolved attenuation on net "+net);
                        System.out.println("Rerouting attenuated net "+net+" at "+p);CompactBlueprint.reroute(plan,net);return verifyInternal(plan,true,reroutes);
                    }
                    throw new IllegalStateException("Unpowered route net="+net+" source="+source+" target="+p+" tile="+plan.blocks.get(p));
                }
                checked++;
            }
            while(true) {
                try{netDelays.put(net,verifyNoFeedback(plan,net,entry.getValue()));break;}
                catch(Feedback loop) {
                    if(fixed==null)throw loop;
                    boolean repaired=false;
                    var candidates=loop.affected.stream().filter(p->!fixed.contains(p)).sorted(Comparator.comparingInt(p->plan.blocks.get(p).part()==Part.REPEATER?0:1)).toList();
                    for(Pos candidate:candidates) {
                        Tile old=plan.blocks.remove(candidate);
                        Map<Pos,Integer> reachable=power(plan,net);
                        boolean valid=entry.getValue().stream().filter(fixed::contains).filter(p->!sourceStub(p,source)).allMatch(reachable::containsKey);
                        List<Pos> remaining=entry.getValue().stream().filter(p->!p.equals(candidate)&&(fixed.contains(p)||reachable.containsKey(p))).toList();
                        if(valid) {
                            try{verifyNoFeedback(plan,net,remaining);}
                            catch(Feedback next){valid=next.affected.size()<loop.affected.size();}
                        }
                        if(valid) {
                            for(Pos p:entry.getValue())if(!fixed.contains(p)&&!reachable.containsKey(p))plan.blocks.remove(p);
                            entry.setValue(new ArrayList<>(remaining));repaired=true;
                            System.out.println("Pruned feedback net="+net+" at "+candidate);break;
                        }
                        plan.blocks.put(candidate,old);
                    }
                    if(!repaired) {
                        System.out.println("Rerouting cyclic net "+net);
                        if(reroutes.merge(net,1,Integer::sum)>3)throw new IllegalStateException("Unresolved feedback on net "+net);
                        CompactBlueprint.reroute(plan,net);
                        return verifyInternal(plan,true,reroutes);
                    }
                }
            }
        }
        System.out.println("Verified "+networks.size()+" nets, "+checked+" reachable wire/repeater positions");
        DecimalCircuit circuit=plan.inputs.containsKey("AT0")?DecimalCircuit.create():DecimalCircuit.smoke();
        int[] ready=new int[circuit.nodes.size()];
        for(int i=0;i<ready.length;i++) {
            var node=circuit.nodes.get(i);if(node.kind()==DecimalCircuit.Kind.INPUT)continue;
            ready[i]=Math.max(ready[node.a()]+netDelays.getOrDefault(node.a(),0),ready[node.b()]+netDelays.getOrDefault(node.b(),0))+2;
        }
        int max=0;for(String name:plan.outputs.keySet()){int id=circuit.outputs.get(name);max=Math.max(max,ready[id]+netDelays.getOrDefault(id,0));}
        int budget=max+max/4+100;
        System.out.println("Conservative propagation budget "+budget+" game ticks");return budget;
    }
    private static boolean sourceStub(Pos p,Pos source){return p.y()<source.y()&&Math.abs(p.x()-source.x())+Math.abs(p.z()-source.z())<=6;}
    private static Map<Pos,Integer> power(CompactBlueprint plan,int net) {
        return power(plan,net,9);
    }
    static Map<Pos,Integer> modulePower(CompactBlueprint plan,int net){return power(plan,net,14);}
    public static Map<Integer,Integer> signalDelays(CompactBlueprint plan) {
        Map<Integer,List<Pos>> networks=new HashMap<>();
        plan.blocks.forEach((p,t)->{if(t.net()>=0&&(t.part()==Part.WIRE||t.part()==Part.REPEATER))networks.computeIfAbsent(t.net(),k->new ArrayList<>()).add(p);});
        Map<Integer,Integer> result=new HashMap<>();
        networks.forEach((net,positions)->{if(plan.sources.containsKey(net))result.put(net,verifyNoFeedback(plan,net,positions));});
        return result;
    }
    private static Map<Pos,Integer> power(CompactBlueprint plan,int net,int initial) {
            Pos source=plan.sources.get(net);
            Map<Pos,Integer> power=new HashMap<>();Deque<Pos> queue=new ArrayDeque<>();
            // Six dust blocks separate the source terminal from its torch or input lever.
            power.put(source,initial);queue.add(source);
            while(!queue.isEmpty()) {
                Pos p=queue.removeFirst();Tile tile=plan.blocks.get(p);int level=power.get(p);
                for(int dir=0;dir<4;dir++)for(int dy=-1;dy<=1;dy++) {
                    Pos next=p.add(DX[dir],dy,DZ[dir]);Tile to=plan.blocks.get(next);
                    if(to==null||to.net()!=net||(to.part()!=Part.WIRE&&to.part()!=Part.REPEATER))continue;
                    if(tile.part()==Part.REPEATER&&((tile.direction()&3)!=dir||dy!=0))continue;
                    if(to.part()==Part.REPEATER&&((to.direction()&3)!=dir||dy!=0))continue;
                    if(dy!=0&&plan.blocks.containsKey((dy>0?p:next).add(0,1,0)))continue;
                    int nextPower=to.part()==Part.REPEATER?15:tile.part()==Part.REPEATER?15:level-1;
                    if(nextPower<=0||nextPower<=power.getOrDefault(next,0))continue;
                    power.put(next,nextPower);queue.addLast(next);
                }
            }
            return power;
    }
    private static int verifyNoFeedback(CompactBlueprint plan,int net,List<Pos> positions) {
        Map<Pos,Integer> groups=new HashMap<>();int count=0;
        for(Pos start:positions) {
            if(groups.containsKey(start))continue;
            int group=count++;groups.put(start,group);
            if(plan.blocks.get(start).part()!=Part.WIRE)continue;
            Deque<Pos> queue=new ArrayDeque<>();queue.add(start);
            while(!queue.isEmpty()) {
                Pos p=queue.removeFirst();
                for(int dir=0;dir<4;dir++)for(int dy=-1;dy<=1;dy++) {
                    Pos next=p.add(DX[dir],dy,DZ[dir]);Tile to=plan.blocks.get(next);
                    if(to==null||to.net()!=net||to.part()!=Part.WIRE||groups.containsKey(next))continue;
                    if(dy!=0&&plan.blocks.containsKey((dy>0?p:next).add(0,1,0)))continue;
                    groups.put(next,group);queue.addLast(next);
                }
            }
        }
        List<Set<Integer>> edges=new ArrayList<>();for(int i=0;i<count;i++)edges.add(new HashSet<>());
        int[] indegree=new int[count];
        int[] repeater=new int[count];
        for(Pos p:positions) {
            Tile tile=plan.blocks.get(p);if(tile.part()!=Part.REPEATER)continue;
            int dir=tile.direction()&3,id=groups.get(p);
            repeater[id]=((tile.direction()>>2)+1)*2;
            Integer before=groups.get(p.add(-DX[dir],0,-DZ[dir])),after=groups.get(p.add(DX[dir],0,DZ[dir]));
            if(before!=null&&edges.get(before).add(id))indegree[id]++;
            if(after!=null&&edges.get(id).add(after))indegree[after]++;
        }
        Deque<Integer> queue=new ArrayDeque<>();for(int i=0;i<count;i++)if(indegree[i]==0)queue.add(i);
        int[] delay=new int[count];Arrays.fill(delay,-1);delay[groups.get(plan.sources.get(net))]=0;
        int visited=0;while(!queue.isEmpty()) {
            int n=queue.removeFirst();visited++;
            for(int next:edges.get(n)) {
                if(delay[n]>=0)delay[next]=Math.max(delay[next],delay[n]+repeater[next]);
                if(--indegree[next]==0)queue.addLast(next);
            }
        }
        if(visited!=count) {
            Set<Pos> affected=new HashSet<>();positions.stream().filter(pos->indegree[groups.get(pos)]>0).forEach(affected::add);
            throw new Feedback(net,affected);
        }
        return Arrays.stream(delay).max().orElse(0);
    }
    public static void main(String[] args)throws Exception {
        CompactBlueprint plan=CompactBlueprint.read(Files.newInputStream(Path.of(args[0])));
        plan.estimatedTicks=Boolean.getBoolean("electron.pruneFeedback")?repair(plan):verify(plan);
        if(args.length>1)plan.write(Path.of(args[1]));
    }
}
