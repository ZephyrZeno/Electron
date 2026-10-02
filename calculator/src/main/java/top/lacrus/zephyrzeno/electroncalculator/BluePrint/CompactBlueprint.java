package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import java.util.function.Consumer;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.DecimalCircuit.Module;
import static top.lacrus.zephyrzeno.electroncalculator.BluePrint.DecimalCircuit.*;

/** Physical vanilla redstone placement. Three-block routing grid with isolated layers. */
public final class CompactBlueprint {
    public record Pos(int x,int y,int z) {
        public Pos add(int dx,int dy,int dz){return new Pos(x+dx,y+dy,z+dz);}
        long key(){return ((long)x<<40)|((long)y<<24)|(z&0xffffffL);}
    }
    public enum Part { WOOL, WIRE, REPEATER, TORCH, LEVER, LAMP, STANDING_TORCH, BUTTON, COPPER_BULB, COMPARATOR, STONE, DARK, WHITE, GLASS }
    public record Tile(Part part,Module module,int direction,int net) {}
    public record Label(Pos pos,String title,String detail) {}
    public final LinkedHashMap<Pos,Tile> blocks=new LinkedHashMap<>();
    public final Map<String,Pos> inputs=new LinkedHashMap<>(),outputs=new LinkedHashMap<>();
    public final List<Label> labels=new ArrayList<>();
    public final Map<Integer,Pos> sources=new HashMap<>();
    public int width, height, depth, estimatedTicks;
    // Direction indices: east, south, west, north.
    private static final int[] DX={1,0,-1,0},DZ={0,1,0,-1};
    private final Map<Long,Integer> wires=new HashMap<>();
    private final Set<Long> reserved=new HashSet<>();
    private final Set<Long> headroom=new HashSet<>();
    private final Map<Long,Integer> portSpace=new HashMap<>();
    private final Map<Integer,LinkedHashSet<Pos>> trees=new HashMap<>();
    private final Map<Pos,Integer> strengths=new HashMap<>();
    private final Map<Integer,List<Pos>> sinks=new LinkedHashMap<>();
    private int maxX,maxZ,maxLayer;
    private boolean strictRouting=true;
    private DecimalCircuit circuit;
    private Map<Integer,Module> signalModules;
    public record FixedPanel(CompactBlueprint plan,Map<Integer,List<Pos>> sinks,Set<Pos> keepOut) {}
    public static FixedPanel panel(DecimalCircuit circuit) {
        CompactBlueprint p=new CompactBlueprint();p.circuit=circuit;p.placeNodes(true);p.placePanel();
        for(var entry:p.sources.entrySet()) {
            Pos old=entry.getValue();int id=entry.getKey();Module m=circuit.nodes.get(id).module();
            p.component(old.add(0,0,1),Part.REPEATER,1,id,m);p.wire(old.add(0,0,2),id,m);entry.setValue(old.add(0,0,2));
        }
        Set<Pos> guards=new HashSet<>();for(long k:p.reserved)guards.add(new Pos((int)(k>>>40),(int)((k>>>24)&0xffff),(int)(k&0xffffff)));
        return new FixedPanel(p,p.sinks,guards);
    }

    /** Routes only the external pins of fixed functional templates. Internal blocks stay fixed. */
    public static CompactBlueprint connectModules(CompactBlueprint fixed, Map<Integer,List<Pos>> destinations,
            Map<Integer,Module> modules, Set<Pos> keepOut, int width, int height, int depth) {
        List<Integer> order=new ArrayList<>(destinations.keySet());order.sort(Comparator.<Integer>comparingInt(n->destinations.get(n).size()).reversed());
        int onlyNet=Integer.getInteger("electron.onlyNet",-1);if(onlyNet>=0)order.removeIf(n->n!=onlyNet);
        RoutingFailure last=null;
        for(int attempt=0;attempt<3;attempt++) {
            try{return connectModulesAttempt(fixed,destinations,modules,keepOut,width,height,depth,order);}
            catch(RoutingFailure failure){last=failure;System.out.println("MODULE ROUTE RETRY "+attempt+" "+failure.getMessage());order.remove(Integer.valueOf(failure.signal));order.add(0,failure.signal);if(attempt%3==2)Collections.shuffle(order,new Random(attempt+31L));}
        }
        throw last;
    }
    private static CompactBlueprint connectModulesAttempt(CompactBlueprint fixed, Map<Integer,List<Pos>> destinations,
            Map<Integer,Module> modules, Set<Pos> keepOut, int width, int height, int depth,List<Integer> order) {
        CompactBlueprint p=new CompactBlueprint();
        p.signalModules=modules;p.strictRouting=true;
        fixed.blocks.forEach(p::put);p.inputs.putAll(fixed.inputs);p.outputs.putAll(fixed.outputs);p.labels.addAll(fixed.labels);
        keepOut.forEach(pos->p.reserved.add(pos.key()));
        fixed.sources.forEach((id,pos)->{
            p.sources.put(id,pos);p.trees.put(id,new LinkedHashSet<>(List.of(pos)));p.strengths.put(pos,14);
        });
        fixed.sources.forEach(p::protectModulePin);
        destinations.forEach((id,positions)->positions.forEach(pos->p.protectModulePin(id,pos)));
        // A blueprint extension may branch from any powered point of an existing routed net.
        for(int net:destinations.keySet()) {
            var levels=BlueprintVerifier.modulePower(p,net);
            levels.forEach((pos,power)->{if(p.blocks.get(pos).part()==Part.WIRE){p.trees.get(net).add(pos);p.strengths.put(pos,power);}});
        }
        p.maxX=(width+2)/3;p.maxZ=(depth+2)/3;p.maxLayer=(height-2)/3;
        Set<Pos> fixedPositions=new HashSet<>(fixed.blocks.keySet());
        Set<Integer> completed=new HashSet<>();Deque<Integer> pending=new ArrayDeque<>(order);
        String checkpoint=System.getProperty("electron.routeCheckpoint");
        if(checkpoint!=null&&Files.exists(Path.of(checkpoint)))try {
            CompactBlueprint saved=read(Files.newInputStream(Path.of(checkpoint)));
            boolean matches=saved.sources.equals(fixed.sources)&&fixed.blocks.entrySet().stream().allMatch(e->e.getValue().equals(saved.blocks.get(e.getKey())));
            if(matches)for(var e:saved.blocks.entrySet()) {
                if(fixedPositions.contains(e.getKey())||(e.getValue().part()!=Part.WIRE&&e.getValue().part()!=Part.REPEATER))continue;
                Integer owner=p.portSpace.get(e.getKey().key());
                if(p.reserved.contains(e.getKey().key())||(owner!=null&&owner!=e.getValue().net())){matches=false;break;}
            }
            if(matches) {
                saved.blocks.forEach(p::put);
                for(int net:order) {
                    var levels=BlueprintVerifier.modulePower(p,net);
                    levels.forEach((pos,power)->{if(p.blocks.get(pos).part()==Part.WIRE){p.trees.get(net).add(pos);p.strengths.put(pos,power);}});
                    if(destinations.get(net).stream().allMatch(pos->levels.getOrDefault(pos,0)>=3))completed.add(net);
                }
                pending.removeIf(completed::contains);System.out.println("MODULE RESUMED "+completed.size()+"/"+order.size());
            } else System.out.println("MODULE CHECKPOINT SKIPPED: module layout changed");
        }catch(IOException failure){System.out.println("MODULE CHECKPOINT SKIPPED: "+failure.getMessage());}
        Map<Integer,Integer> failures=new HashMap<>();int revisions=0;
        while(!pending.isEmpty()) {
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Planning cancelled");
            int id=pending.removeFirst();
            List<Pos> targets=new ArrayList<>(destinations.get(id));targets.sort(Comparator.comparingInt(target->distance(p.sources.get(id),target)));
            if(targets.size()>12)System.out.println("MODULE NET "+id+" sinks="+targets.size());
            try {
                for(Pos target:targets)p.route(id,target);
                completed.add(id);
                if(completed.size()%100==0) {
                    System.out.println("MODULE ROUTE "+completed.size()+"/"+order.size()+" blocks="+p.blocks.size());
                    if(checkpoint!=null)p.checkpoint(Path.of(checkpoint));
                }
            } catch(RoutingFailure failure) {
                if(++revisions>Math.max(200,order.size()*4)||failures.merge(id,1,Integer::sum)>16) {
                    if(checkpoint!=null)p.checkpoint(Path.of(checkpoint));throw failure;
                }
                Pos center=failure.target==null?p.sources.get(id):failure.target;
                Map<Integer,Integer> blockers=new HashMap<>();
                for(Pos focus:List.of(center,p.sources.get(id)))for(int x=-8;x<=8;x++)for(int y=-6;y<=6;y++)for(int z=-8;z<=8;z++) {
                    Pos pos=focus.add(x,y,z);Tile tile=p.blocks.get(pos);
                    if(tile!=null&&tile.net()!=id&&completed.contains(tile.net())&&!fixedPositions.contains(pos)
                            &&(tile.part()==Part.WIRE||tile.part()==Part.REPEATER))blockers.merge(tile.net(),1,Integer::sum);
                }
                var victims=blockers.keySet().stream().sorted(Comparator.<Integer>comparingDouble(n->-(double)blockers.get(n)/(1+destinations.get(n).size()))).limit(3).toList();
                if(victims.isEmpty())throw failure;
                Set<Integer> affected=new LinkedHashSet<>();affected.add(id);affected.addAll(victims);
                System.out.println("MODULE LOCAL REROUTE "+affected+" near "+center+" completed="+completed.size());
                Set<Pos> remove=new HashSet<>();
                p.blocks.forEach((pos,tile)->{
                    if(affected.contains(tile.net())&&!fixedPositions.contains(pos)&&(tile.part()==Part.WIRE||tile.part()==Part.REPEATER)) {
                        remove.add(pos);if(!fixedPositions.contains(pos.add(0,-1,0)))remove.add(pos.add(0,-1,0));
                    }
                });
                remove.forEach(p.blocks::remove);completed.removeAll(affected);
                p.wires.clear();p.headroom.clear();p.blocks.forEach(p::put);
                p.trees.clear();p.strengths.clear();
                p.sources.forEach((net,pos)->{p.trees.put(net,new LinkedHashSet<>(List.of(pos)));p.strengths.put(pos,14);});
                for(int net:order) {
                    var levels=BlueprintVerifier.modulePower(p,net);
                    levels.forEach((pos,power)->{if(p.blocks.get(pos).part()==Part.WIRE){p.trees.get(net).add(pos);p.strengths.put(pos,power);}});
                }
                pending.removeIf(affected::contains);List<Integer> retry=new ArrayList<>(affected);
                for(int i=retry.size()-1;i>=0;i--)pending.addFirst(retry.get(i));
            }
        }
        p.width=p.blocks.keySet().stream().mapToInt(Pos::x).max().orElse(0)+2;
        p.height=p.blocks.keySet().stream().mapToInt(Pos::y).max().orElse(0)+3;
        p.depth=p.blocks.keySet().stream().mapToInt(Pos::z).max().orElse(0)+2;
        if(checkpoint!=null)p.checkpoint(Path.of(checkpoint));
        return p;
    }
    private void checkpoint(Path path) {
        try {
            width=blocks.keySet().stream().mapToInt(Pos::x).max().orElse(0)+2;
            height=blocks.keySet().stream().mapToInt(Pos::y).max().orElse(0)+3;
            depth=blocks.keySet().stream().mapToInt(Pos::z).max().orElse(0)+2;
            Path temporary=path.resolveSibling(path.getFileName()+".tmp");write(temporary);
            Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException failure){throw new IllegalStateException("Cannot save routing checkpoint",failure);}
    }
    private void protectModulePin(int net,Pos pos) {
        int out=1;
        for(int direction=0;direction<4;direction++) {
            Tile neighbor=blocks.get(pos.add(DX[direction],0,DZ[direction]));
            if(neighbor!=null&&neighbor.net()==net&&(neighbor.part()==Part.WIRE||neighbor.part()==Part.REPEATER)){out=(direction+2)%4;break;}
        }
        for(int step=0;step<=2;step++)for(int side=-1;side<=1;side++)for(int dy=-1;dy<=1;dy++) {
            Pos p=pos.add(DX[out]*step+DZ[out]*side,dy,DZ[out]*step+DX[out]*side);
            portSpace.merge(p.key(),net,(old,value)->old.equals(value)?old:-2);
        }
    }
    static Set<Pos> fixedPositions(DecimalCircuit circuit) {
        CompactBlueprint p=new CompactBlueprint();p.circuit=circuit;p.placeNodes();p.placePanel();return new HashSet<>(p.blocks.keySet());
    }
    static void reroute(CompactBlueprint original,int net) {
        List<Integer> affected=new ArrayList<>();affected.add(net);
        for(int attempt=0;attempt<32;attempt++) {
            CompactBlueprint p=new CompactBlueprint();p.circuit=DecimalCircuit.create();p.placeNodes();p.placePanel();
            Set<Pos> fixed=new HashSet<>(p.blocks.keySet()),removed=new HashSet<>();
            original.blocks.forEach((pos,tile)->{
                if(affected.contains(tile.net())&&!fixed.contains(pos)&&(tile.part()==Part.WIRE||tile.part()==Part.REPEATER)) {
                    removed.add(pos);if(!fixed.contains(pos.add(0,-1,0)))removed.add(pos.add(0,-1,0));
                }
            });
            original.blocks.forEach((pos,tile)->{if(!removed.contains(pos))p.put(pos,tile);});
            try {
                for(int signal:affected) {
                    var targets=p.sinks.get(signal);targets.sort(Comparator.comparingInt(target->distance(p.sources.get(signal),target)));
                    for(Pos target:targets)p.route(signal,target);
                }
                original.blocks.clear();original.blocks.putAll(p.blocks);
                original.width=original.blocks.keySet().stream().mapToInt(Pos::x).max().orElse(0)+2;
                original.height=original.blocks.keySet().stream().mapToInt(Pos::y).max().orElse(0)+3;
                original.depth=original.blocks.keySet().stream().mapToInt(Pos::z).max().orElse(0)+2;return;
            }catch(RoutingFailure failure) {
                Map<Integer,Integer> blockers=new HashMap<>();
                List<Pos> centers=new ArrayList<>();centers.add(p.sources.get(failure.signal));
                if(failure.target!=null)centers.add(failure.target);
                for(Pos center:centers)for(int x=-6;x<=6;x++)for(int y=-6;y<=6;y++)for(int z=-6;z<=6;z++) {
                    Pos pos=center.add(x,y,z);Tile tile=p.blocks.get(pos);
                    if(tile!=null&&tile.net()>=0&&!affected.contains(tile.net())&&!fixed.contains(pos)&&(tile.part()==Part.WIRE||tile.part()==Part.REPEATER))blockers.merge(tile.net(),1,Integer::sum);
                }
                var additions=blockers.entrySet().stream().sorted(Map.Entry.<Integer,Integer>comparingByValue().reversed()).limit(3).map(Map.Entry::getKey).toList();
                if(additions.isEmpty())throw failure;
                affected.remove(Integer.valueOf(failure.signal));affected.add(0,failure.signal);affected.addAll(additions);
                System.out.println("Local reroute "+affected);
            }
        }
        throw new IllegalStateException("Local reroute limit for "+net);
    }
    private static final class RoutingFailure extends IllegalStateException {
        final int signal;
        final Pos target;
        RoutingFailure(int signal,String message){this(signal,message,null);}
        RoutingFailure(int signal,String message,Pos target){super(message);this.signal=signal;this.target=target;}
    }

    private void put(Pos p,Tile tile) {
        Tile old=blocks.putIfAbsent(p,tile);
        if(old!=null&&!old.equals(tile))throw new IllegalStateException("Placement conflict at "+p+": "+old+" / "+tile);
        if(tile.part()==Part.WIRE||tile.part()==Part.REPEATER){wires.put(p.key(),tile.net());headroom.add(p.add(0,1,0).key());}
    }
    private void wool(Pos p,Module module){
        Tile old=blocks.get(p);
        if(old==null)put(p,new Tile(Part.WOOL,module,0,-1));
        else if(!solidSupport(old.part()))throw new IllegalStateException("Missing support at "+p);
    }
    private void wire(Pos p,int net,Module module){wool(p.add(0,-1,0),module);put(p,new Tile(Part.WIRE,module,0,net));}
    private void component(Pos p,Part part,int facing,int net,Module module){wool(p.add(0,-1,0),module);put(p,new Tile(part,module,facing,net));}
    private void endpoint(int signal,Pos p,boolean source){
        int radius=p.z()>=60?Integer.getInteger("electron.portRadius",2):2;
        for(int x=-radius;x<=radius;x++)for(int y=-radius;y<=radius;y++)for(int z=-radius;z<=radius;z++) {
            long key=p.add(x,y,z).key();
            portSpace.merge(key,signal,(old,value)->old.equals(value)?old:-2);
        }
        if(source){sources.put(signal,p);trees.computeIfAbsent(signal,k->new LinkedHashSet<>()).add(p);strengths.put(p,9);}
        else sinks.computeIfAbsent(signal,k->new ArrayList<>()).add(p);
    }
    private void raisedEndpoint(int signal,Pos p,int direction,boolean source,Module module) {
        for(int step=1;step<=3;step++)wire(p.add(DX[direction]*step,step,DZ[direction]*step),signal,module);
        endpoint(signal,p.add(DX[direction]*3,3,DZ[direction]*3),source);
    }

    public static CompactBlueprint create(DecimalCircuit circuit,Consumer<String> progress) {
        List<Integer> priority=new ArrayList<>();
        for(String value:System.getProperty("electron.routingPriority","").split(","))if(!value.isBlank()) {
            int id=Integer.parseInt(value);if(id>=0&&id<circuit.nodes.size()&&!priority.contains(id))priority.add(id);
        }
        circuit.outputs.forEach((name,id)->{if(!name.startsWith("BCD")&&!priority.contains(id))priority.add(id);});
        Set<Integer> retried=new HashSet<>();
        for(int attempt=0;attempt<48;attempt++) {
            try{return createAttempt(circuit,progress,priority);}
            catch(RoutingFailure failure) {
                if(!retried.add(failure.signal))throw failure;
                priority.remove(Integer.valueOf(failure.signal));priority.add(0,failure.signal);progress.accept("重排受阻线路 "+failure.signal+"，第 "+(attempt+2)+" 次规划");
            }
        }
        throw new IllegalStateException("Routing retry limit reached");
    }
    private static CompactBlueprint createAttempt(DecimalCircuit circuit,Consumer<String> progress,List<Integer> priority) {
        CompactBlueprint p=new CompactBlueprint();p.circuit=circuit;p.strictRouting=Boolean.getBoolean("electron.strictRouting");p.placeNodes();p.placePanel();
        int completed=0;
        List<Integer> nets=new ArrayList<>(p.sinks.keySet());
        // High fanout nets first; their trees form distribution trunks.
        nets.sort(Comparator.<Integer>comparingInt(n->priority.contains(n)?priority.indexOf(n):priority.size()+1)
                .thenComparing(Comparator.<Integer>comparingInt(n->p.sinks.get(n).size()).reversed()));
        for(int net:nets) {
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Planning cancelled");
            var targets=p.sinks.get(net);
            targets.sort(Comparator.comparingInt(target->distance(p.sources.get(net),target)));
            for(Pos target:targets)p.route(net,target);
            if(++completed%100==0)progress.accept("布线 "+completed+"/"+nets.size()+"，"+p.blocks.size()+" 个方块");
        }
        p.width=p.blocks.keySet().stream().mapToInt(Pos::x).max().orElse(0)+2;
        p.height=p.blocks.keySet().stream().mapToInt(Pos::y).max().orElse(0)+3;
        p.depth=p.blocks.keySet().stream().mapToInt(Pos::z).max().orElse(0)+2;
        progress.accept("检查信号衰减和中继器回路");
        p.estimatedTicks=BlueprintVerifier.repair(p);
        if(BlueprintOptimizer.compactRepeaters(p)>0)p.estimatedTicks=BlueprintVerifier.verify(p);
        p.wires.clear();p.reserved.clear();p.headroom.clear();p.portSpace.clear();p.trees.clear();p.sinks.clear();p.strengths.clear();p.circuit=null;
        return p;
    }

    private void placeNodes() {
        placeNodes(false);
    }
    private void placeNodes(boolean inputsOnly) {
        int inputIndex=0,gateIndex=0;
        int columns=24,rows=24,pitch=18;
        for(int id=0;id<circuit.nodes.size();id++) {
            Node n=circuit.nodes.get(id);
            if(inputsOnly&&n.kind()!=Kind.INPUT)continue;
            if(n.kind()==Kind.INPUT) {
                int x;
                if(n.name().equals("ZERO"))x=3;
                else if(n.name().equals("OP0"))x=63;
                else if(n.name().equals("OP1"))x=60;
                else if(n.name().equals("HOLD"))x=72;
                else if(n.name().matches("[AB][TU][0-3]")) {
                    int group=switch(n.name().substring(0,2)){case "AT"->0;case "AU"->1;case "BT"->2;default->3;};
                    x=9+group*12+(3-Integer.parseInt(n.name().substring(2)))*3;
                } else x=9+inputIndex++*3;
                Pos pos=new Pos(x,2,6);
                if(!n.name().equals("ZERO")){
                    component(pos,Part.LEVER,3,id,Module.INPUT);inputs.put(n.name(),pos);
                    for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)reserved.add(pos.add(dx,dy,dz).key());
                }
                else wool(pos,Module.CONTROL);
                for(int step=1;step<=3;step++)wire(pos.add(0,0,step),id,n.module());
                raisedEndpoint(id,pos.add(0,0,3),1,true,n.module());
                continue;
            }
            int col=gateIndex%columns,row=gateIndex/columns%rows,level=gateIndex/(columns*rows);gateIndex++;
            Pos c=new Pos(12+col*pitch,2+level*12,66+row*pitch);
            for(int x=-2;x<=2;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=2;z++)reserved.add(c.add(x,y,z).key());
            if(n.kind()==Kind.NOR) {
                wool(c,n.module());
                put(c.add(0,0,1),new Tile(Part.TORCH,n.module(),1,id));
                component(c.add(-1,0,0),Part.REPEATER,0,n.a(),n.module());
                if(n.a()!=n.b())component(c.add(1,0,0),Part.REPEATER,2,n.b(),n.module());
                for(int side:new int[]{-1,1}) {
                    if(side==1&&n.a()==n.b())continue;
                    int net=side==-1?n.a():n.b();
                    wire(c.add(side*2,0,0),net,n.module());wire(c.add(side*3,0,0),net,n.module());
                    raisedEndpoint(net,c.add(side*3,0,0),3,false,n.module());
                }
                wire(c.add(0,0,2),id,n.module());wire(c.add(0,0,3),id,n.module());
                raisedEndpoint(id,c.add(0,0,3),1,true,n.module());
            } else {
                component(c,Part.REPEATER,1,id,n.module());
                component(c.add(1,0,0),Part.REPEATER,2,n.b(),n.module());
                for(int step=0;step<=3;step++)wire(c.add(-step,0,-1),n.a(),n.module());
                component(c.add(-3,0,-2),Part.REPEATER,1,n.a(),n.module());wire(c.add(-3,0,-3),n.a(),n.module());
                wire(c.add(2,0,0),n.b(),n.module());wire(c.add(3,0,0),n.b(),n.module());
                for(int step=1;step<=3;step++)wire(c.add(0,0,step),id,n.module());
                raisedEndpoint(n.a(),c.add(-3,0,-3),3,false,n.module());raisedEndpoint(n.b(),c.add(3,0,0),0,false,n.module());raisedEndpoint(id,c.add(0,0,3),1,true,n.module());
            }
        }
        maxX=columns*6+12;maxZ=rows*6+36;maxLayer=(gateIndex/(columns*rows)+1)*4+12;
    }

    private void lamp(String name,Pos pos) {
        if(!circuit.outputs.containsKey(name))return;
        int net=circuit.outputs.get(name);
        put(pos.add(0,-1,0),new Tile(Part.LAMP,Module.DISPLAY,0,net));wire(pos,net,Module.DISPLAY);
        endpoint(net,pos,false);outputs.put(name,pos.add(0,-1,0));
    }
    private void placePanel() {
        for(int bit=0;bit<14;bit++)lamp("R"+bit,new Pos(9+(13-bit)*3,2,45));
        lamp("NEG",new Pos(6,2,27));lamp("ERROR",new Pos(66,2,27));lamp("DOT",new Pos(33,2,33));
        int[][] root={{3,0},{6,3},{6,9},{3,12},{0,9},{0,3},{3,6}};
        for(int digit=0;digit<4;digit++)for(int segment=0;segment<7;segment++) {
            String name="SEG"+digit+"_"+segment;
            if(!circuit.outputs.containsKey(name))continue;
            Pos start=new Pos(12+(3-digit)*12+root[segment][0],2,21+root[segment][1]);
            lamp(name,start);
            boolean horizontal=segment==0||segment==3||segment==6;
            for(int step=1;step<3;step++) {
                Pos pos=start.add(horizontal?step:0,0,horizontal?0:step);
                put(pos.add(0,-1,0),new Tile(Part.LAMP,Module.DISPLAY,0,circuit.outputs.get(name)));
                wire(pos,circuit.outputs.get(name),Module.DISPLAY);
            }
        }
        labels.add(new Label(new Pos(12,1,3),"甲数十位","八 四 二 一"));
        labels.add(new Label(new Pos(24,1,3),"甲数个位","八 四 二 一"));
        labels.add(new Label(new Pos(36,1,3),"乙数十位","八 四 二 一"));
        labels.add(new Label(new Pos(48,1,3),"乙数个位","八 四 二 一"));
        labels.add(new Label(new Pos(60,1,3),"运算选择","零加 一减 二乘 三除"));
        labels.add(new Label(new Pos(69,1,3),"保持结果","开：保存　关：更新"));
        labels.add(new Label(new Pos(30,1,18),"十进制结果","除法保留两位小数"));
        labels.add(new Label(new Pos(30,1,42),"二进制存储","左高位　右低位"));
        labels.add(new Label(new Pos(66,1,30),"错误指示","除零／数字大于九"));
        String[] names={"输入","控制","进制转换","加法","减法","乘法","除法","存储","显示"};
        for(Module m:Module.values()) {
            Pos pos=new Pos(6+m.ordinal()*9,1,54);wool(pos,m);
            labels.add(new Label(pos.add(0,1,0),names[m.ordinal()]+"模块","羊毛颜色对应线路"));
        }
        for(Label label:labels)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<=2;y++)reserved.add(label.pos().add(x,y,z).key());
    }

    private static int distance(Pos a,Pos b){return Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z())+Math.abs(a.y()-b.y())*2;}
    private record Search(Pos pos,int charge,boolean boosted,int g,int score,Search parent) {}
    private void route(int net,Pos target) {
        Set<Pos> tree=trees.get(net);
        if(tree==null)throw new IllegalStateException("Missing source "+net);
        if(tree.contains(target))return;
        Pos goal=tree.stream().filter(p->signalModules==null||strengths.get(p)>=8).min(Comparator.comparingInt(p->distance(p,target))).orElse(sources.get(net));
        boolean coarsePossible=tree.stream().anyMatch(p->Math.floorMod(p.x()-target.x(),3)==0&&Math.floorMod(p.y()-target.y(),3)==0&&Math.floorMod(p.z()-target.z(),3)==0);
        Search found=coarsePossible?search(net,target,goal,tree,false):null;
        if(found==null)found=search(net,target,goal,tree,true);
        if(found==null&&tree.size()>1) {
            List<Pos> goals=new ArrayList<>();goals.add(goal);
            for(Pos candidate:tree.stream().sorted(Comparator.comparingInt(p->distance(p,target))).toList()) {
                if(goals.stream().anyMatch(p->distance(p,candidate)<15))continue;
                goals.add(candidate);found=search(net,target,candidate,tree,true);
                if(found!=null||goals.size()>=5)break;
            }
        }
        if(found==null) {
            if(signalModules!=null&&Integer.getInteger("electron.onlyNet",-1)>=0)for(Pos pin:List.of(sources.get(net),target)) {
                System.out.println("BLOCKED PIN "+pin+" port="+portSpace.get(pin.key()));
                for(int d=0;d<4;d++)for(int dy=-1;dy<=1;dy++) {
                    Pos q=pin.add(DX[d],dy,DZ[d]);
                    System.out.println("  "+q+" tile="+blocks.get(q)+" below="+blocks.get(q.add(0,-1,0))+" port="+portSpace.get(q.key())+" reserved="+reserved.contains(q.key())+" head="+headroom.contains(q.key())+" clear="+edgeClear(pin,d,dy,1,net,tree));
                }
            }
            throw new RoutingFailure(net,"Cannot route signal "+net+" from "+goal+" (tree="+tree.size()+") to "+target,target);
        }
        Module module=signalModules==null?circuit.nodes.get(net).module():signalModules.get(net);
        Search current=found;
        int strength=strengths.get(current.pos());
        while(current.parent()!=null) {
            Pos from=current.pos(),to=current.parent().pos();
            int dir=direction(from,to),rise=Integer.signum(to.y()-from.y()),length=length(from,to);
            for(int step=1;step<=length;step++) {
                Pos p=from.add(DX[dir]*step,rise*step,DZ[dir]*step);
                if(step==1&&rise==0&&length==3&&blocks.get(p)==null)component(p,Part.REPEATER,dir,net,module);
                else if(blocks.get(p)==null)wire(p,net,module);
                strength=blocks.get(p).part()==Part.REPEATER?15:strength-1;
                if(strength<=0)throw new RoutingFailure(net,"Signal attenuation at "+p);
                if(signalModules!=null&&blocks.get(p).part()==Part.WIRE){tree.add(p);strengths.put(p,strength);}
            }
            tree.add(to);strengths.put(to,strength);current=current.parent();
        }
    }
    private Search search(int net,Pos target,Pos goal,Set<Pos> tree,boolean fine) {
        PriorityQueue<Search> open=new PriorityQueue<>(Comparator.comparingInt(Search::score));
        Map<Long,Integer> costs=new HashMap<>();
        Search start=new Search(target,0,false,0,distance(target,goal),null);open.add(start);
        Search found=null;int visits=0;
        while(!open.isEmpty()) {
            if((visits&1023)==0&&Thread.currentThread().isInterrupted())throw new IllegalStateException("Planning cancelled");
            Search current=open.poll();
            long key=current.pos().key()*16+current.charge()+(current.boosted()?8:0);
            if(costs.getOrDefault(key,Integer.MAX_VALUE)<current.g())continue;
            if(tree.contains(current.pos())&&(current.boosted()||signalModules!=null)
                    &&strengths.get(current.pos())>current.charge()+(signalModules==null?0:3)){found=current;break;}
            if(++visits>(fine?240000:60000))break;
            for(int dir=0;dir<4;dir++)for(int rise=-1;rise<=1;rise++)for(int length:fine?new int[]{3,2,1}:new int[]{3}) {
                boolean reset=rise==0&&length==3;
                int charge=reset?0:current.charge()+length;
                if(charge>6)continue;
                Pos next=current.pos().add(DX[dir]*length,rise*length,DZ[dir]*length);
                if(next.x()<3||next.x()>maxX*3||next.z()<3||next.z()>maxZ*3||next.y()<1||next.y()>2+maxLayer*3)continue;
                boolean boosted=current.boosted()||reset;
                int cost=current.g()+length+(rise==0?0:length)+(next.y()==2?2:0);
                long nk=next.key()*16+charge+(boosted?8:0);
                if(cost>=costs.getOrDefault(nk,Integer.MAX_VALUE))continue;
                if(!edgeClear(current.pos(),dir,rise,length,net,tree))continue;
                if(!pathClear(current,dir,rise,length))continue;
                costs.put(nk,cost);open.add(new Search(next,charge,boosted,cost,cost+distance(next,goal)*2,current));
            }
        }
        return found;
    }
    private static int direction(Pos from,Pos to){return to.x()>from.x()?0:to.x()<from.x()?2:to.z()>from.z()?1:3;}
    private static int length(Pos from,Pos to){return Math.max(Math.abs(to.x()-from.x()),Math.abs(to.z()-from.z()));}

    private boolean pathClear(Search current,int direction,int rise,int length) {
        for(int step=1;step<=length;step++) {
            Pos added=current.pos().add(DX[direction]*step,rise*step,DZ[direction]*step);
            for(Search old=current;old.parent()!=null;old=old.parent()) {
                Pos from=old.pos(),to=old.parent().pos();
                if(added.x()+1<Math.min(from.x(),to.x())||added.x()-1>Math.max(from.x(),to.x())
                        ||added.z()+1<Math.min(from.z(),to.z())||added.z()-1>Math.max(from.z(),to.z())
                        ||added.y()+2<Math.min(from.y(),to.y())||added.y()-2>Math.max(from.y(),to.y()))continue;
                int dx=Integer.signum(to.x()-from.x()),dy=Integer.signum(to.y()-from.y()),dz=Integer.signum(to.z()-from.z());
                for(int s=0;s<=length(from,to);s++) {
                    Pos p=from.add(dx*s,dy*s,dz*s);
                    if(added.x()==p.x()&&added.z()==p.z()&&Math.abs(added.y()-p.y())<=2)return false;
                    if(strictRouting&&Math.abs(added.x()-p.x())+Math.abs(added.z()-p.z())==1&&Math.abs(added.y()-p.y())<=1
                            &&!(step==1&&p.equals(current.pos())))return false;
                }
            }
        }
        return true;
    }

    private boolean edgeClear(Pos from,int dir,int rise,int length,int net,Set<Pos> tree) {
        Pos end=from.add(DX[dir]*length,rise*length,DZ[dir]*length);
        boolean joining=tree.contains(end);
        for(int step=1;step<=length;step++) {
            Pos p=from.add(DX[dir]*step,rise*step,DZ[dir]*step);
            Integer portOwner=portSpace.get(p.key());
            if(portOwner!=null&&portOwner!=net)return false;
            if(headroom.contains(p.key())||headroom.contains(p.add(0,-1,0).key()))return false;
            Tile at=blocks.get(p),below=blocks.get(p.add(0,-1,0));
            if(at!=null) {
                if(step!=length||!tree.contains(p)||at.part()!=Part.WIRE||at.net()!=net)return false;
            } else if(reserved.contains(p.key()))return false;
            if(below!=null&&!solidSupport(below.part()))return false;
            if(reserved.contains(p.add(0,-1,0).key())&&below==null)return false;
            // A stair needs headroom above its lower wire.
            if(rise!=0) {
                Pos lower=rise>0?p.add(-DX[dir],-1,-DZ[dir]):p;
                Tile overhead=blocks.get(lower.add(0,1,0));
                if(overhead!=null)return false;
            }
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                if(Math.abs(dx)+Math.abs(dz)>1)continue;
                int yrange=dx==0&&dz==0?2:1;
                for(int dy=-yrange;dy<=yrange;dy++) {
                    Integer other=wires.get(p.add(dx,dy,dz).key());
                    if(other!=null&&other!=net)return false;
                    // A branch may touch its starting terminal and its one chosen tree join only.
                    // Accidental contact with another branch can bypass a repeater and latch the net on.
                    if(strictRouting&&other!=null&&other==net&&!tree.contains(p)) {
                        Pos neighbor=p.add(dx,dy,dz);
                        if(!neighbor.equals(from)&&!(joining&&neighbor.equals(end)))return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean solidSupport(Part p){return p==Part.WOOL||p==Part.LAMP||p==Part.STONE||p==Part.DARK||p==Part.WHITE;}

    /** Stores the compiler output; no world calculation or input lookup table is stored. */
    public void write(Path path) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(path))))) {
            out.writeInt(0x454C4301);out.writeInt(width);out.writeInt(height);out.writeInt(depth);out.writeInt(estimatedTicks);
            out.writeInt(blocks.size());
            for(var e:blocks.entrySet()) {
                writePos(out,e.getKey());Tile t=e.getValue();out.writeByte(t.part().ordinal());out.writeByte(t.module().ordinal());out.writeByte(t.direction());out.writeInt(t.net());
            }
            writeNames(out,inputs);writeNames(out,outputs);
            out.writeInt(sources.size());for(var e:sources.entrySet()){out.writeInt(e.getKey());writePos(out,e.getValue());}
            out.writeInt(labels.size());for(Label l:labels){writePos(out,l.pos());out.writeUTF(l.title());out.writeUTF(l.detail());}
        }
    }
    private static void writePos(DataOutputStream out,Pos p)throws IOException{out.writeShort(p.x());out.writeShort(p.y());out.writeShort(p.z());}
    private static Pos readPos(DataInputStream in)throws IOException{return new Pos(in.readUnsignedShort(),in.readUnsignedShort(),in.readUnsignedShort());}
    private static void writeNames(DataOutputStream out,Map<String,Pos> map)throws IOException {
        out.writeInt(map.size());for(var e:map.entrySet()){out.writeUTF(e.getKey());writePos(out,e.getValue());}
    }
    private static void readNames(DataInputStream in,Map<String,Pos> map)throws IOException {
        int count=in.readInt();for(int i=0;i<count;i++)map.put(in.readUTF(),readPos(in));
    }
    public static CompactBlueprint read(InputStream stream)throws IOException {
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream)))) {
            if(in.readInt()!=0x454C4301)throw new IOException("Invalid Electron blueprint");
            CompactBlueprint p=new CompactBlueprint();p.width=in.readInt();p.height=in.readInt();p.depth=in.readInt();p.estimatedTicks=in.readInt();
            int count=in.readInt();for(int i=0;i<count;i++){Pos pos=readPos(in);p.blocks.put(pos,new Tile(Part.values()[in.readUnsignedByte()],Module.values()[in.readUnsignedByte()],in.readUnsignedByte(),in.readInt()));}
            readNames(in,p.inputs);readNames(in,p.outputs);
            count=in.readInt();for(int i=0;i<count;i++)p.sources.put(in.readInt(),readPos(in));
            count=in.readInt();for(int i=0;i<count;i++)p.labels.add(new Label(readPos(in),in.readUTF(),in.readUTF()));
            return p;
        }
    }

    public static void main(String[] args)throws IOException {
        long started=System.nanoTime();
        CompactBlueprint result=create(args.length>0&&args[0].equals("smoke")?DecimalCircuit.smoke():DecimalCircuit.create(),System.out::println);
        System.out.println("PLAN "+result.width+"x"+result.height+"x"+result.depth+" blocks="+result.blocks.size()+" seconds="+(System.nanoTime()-started)/1e9);
        if(args.length>1)result.write(Path.of(args[1]));
    }
}
