package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.nio.file.*;
import java.util.*;
import static top.lacrus.zephyrzeno.electroncalculator.BluePrint.CompactBlueprint.*;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.DecimalCircuit.Module;

/** Extends the released arithmetic blueprint; the arithmetic modules are kept at their coordinates. */
public final class ConsoleCompiler {
    public static final int FRONT_NET=100000,TIMER_NET=200000;
    private final CompactBlueprint plan=new CompactBlueprint();
    private final Map<Integer,List<Pos>> bridges=new LinkedHashMap<>();
    private final Map<Integer,Module> colors=new HashMap<>();
    private final Set<Pos> guards=new HashSet<>();
    private final KeypadCircuit keypad=KeypadCircuit.create();
    private final ConsoleLayout console=ConsoleLayout.create(keypad);
    private ConsoleCompiler() {}
    private static Pos front(Pos p){return p.add(ConsoleLayout.OFFSET_X,0,0);}
    private void put(Pos p,Tile t){Tile old=plan.blocks.putIfAbsent(p,t);if(old!=null&&!old.equals(t))throw new IllegalStateException("Extension overlap "+p+" "+old+" / "+t);}
    private void component(Pos p,Part part,int dir,int net,Module module){plan.blocks.putIfAbsent(p.add(0,-1,0),new Tile(Part.WOOL,module,0,-1));put(p,new Tile(part,module,dir,net));}
    private void bridge(int id,Pos p){bridges.computeIfAbsent(id,k->new ArrayList<>()).add(p);colors.putIfAbsent(id,Module.CONTROL);}
    public static CompactBlueprint frontend() {
        KeypadCircuit k=KeypadCircuit.create();String debug=System.getProperty("electron.debugSignal");
        if(debug!=null){System.setProperty("electron.onlyNet",""+k.circuit.outputs.get(debug));System.clearProperty("electron.routeCheckpoint");}
        return ModularLayout.create(k.circuit,ConsoleLayout.create(k).panel(),8,12,120);
    }
    public static CompactBlueprint extend(CompactBlueprint core,CompactBlueprint frontend) {return new ConsoleCompiler().build(core,frontend);}
    private CompactBlueprint build(CompactBlueprint core,CompactBlueprint frontend) {
        plan.blocks.putAll(core.blocks);plan.sources.putAll(core.sources);
        DecimalCircuit arithmetic=DecimalCircuit.create();
        // Obsolete lever-panel signs are not copied; their empty sign zones can carry input bridges.
        ModularLayout.keepOut(arithmetic).stream().filter(p->p.z()>=60).forEach(guards::add);
        ModularLayout.keepOut(keypad.circuit,ConsoleLayout.create(keypad).panel(),8,12,120).forEach(p->guards.add(front(p)));
        Map<Integer,Integer> replaced=new HashMap<>();
        for(String name:core.inputs.keySet()) {
            int replacement=FRONT_NET+keypad.circuit.outputs.get(name.equals("HOLD")?"RESULT_HOLD":name);
            replaced.put(arithmetic.inputs.get(name),replacement);
            Pos old=core.inputs.get(name);plan.blocks.put(old,new Tile(Part.REPEATER,Module.INPUT,1,replacement));
            component(old.add(0,0,-1),Part.WIRE,0,replacement,Module.INPUT);bridge(replacement,old.add(0,0,-1));
        }
        plan.blocks.replaceAll((p,t)->replaced.containsKey(t.net())?new Tile(t.part(),t.module(),t.direction(),replaced.get(t.net())):t);
        replaced.keySet().forEach(plan.sources::remove);
        frontend.blocks.forEach((p,t)->put(front(p),new Tile(t.part(),t.module(),t.direction(),t.net()<0?t.net():FRONT_NET+t.net())));
        frontend.sources.forEach((id,p)->plan.sources.put(FRONT_NET+id,front(p)));
        frontend.inputs.forEach((name,p)->{if(name.startsWith("KEY_"))plan.inputs.put(name,front(p));});
        frontend.outputs.forEach((name,p)->plan.outputs.put(name,front(p)));
        frontend.labels.forEach(l->plan.labels.add(new Label(front(l.pos()),l.title(),l.detail())));
        plan.labels.replaceAll(l->l.title().equals("清空与退格")?new Label(l.pos(),l.title(),"C清空；←删末位"):l);
        console.guards.forEach(p->guards.add(front(p)));
        bufferConsoleLamps();
        // The isolated execution latch sits outside the already-routed keypad corridors.
        Pos execution=new Pos(ConsoleLayout.OFFSET_X+240,1,42);
        for(Pos p:List.of(console.calcStart,console.calcStart.add(0,0,1),console.calcStart.add(0,0,2),console.calcDone.add(0,0,-1),console.calcDone,console.calcStart.add(1,0,2)))plan.blocks.remove(front(p));
        int busyInput=FRONT_NET+keypad.circuit.inputs.get("CALC_BUSY");
        Pos oldBusy=plan.sources.get(busyInput);
        component(execution,Part.COPPER_BULB,0,-1,Module.CONTROL);
        component(execution.add(1,0,0),Part.COMPARATOR,0,busyInput,Module.CONTROL);
        component(execution.add(2,0,0),Part.WIRE,0,busyInput,Module.CONTROL);
        component(execution.add(3,1,0),Part.WIRE,0,busyInput,Module.CONTROL);
        plan.sources.put(busyInput,execution.add(3,1,0));bridge(busyInput,oldBusy);
        // Dust above a solid support can power the bulb underneath. Keep the busy
        // readback bus out of this volume so it cannot feed its own latch power input.
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=3;dy++)for(int dz=-1;dz<=1;dz++)guards.add(execution.add(dx,dy,dz));
        for(var e:console.resultPins.entrySet()) {
            String name=e.getKey();int id=arithmetic.outputs.get(name);colors.put(id,Module.DISPLAY);
            // Retain the existing source and branch from its already verified redstone network.
            for(Pos p:e.getValue()) {
                Pos pin=front(p);Tile oldTile=plan.blocks.get(pin);plan.blocks.put(pin,new Tile(oldTile.part(),oldTile.module(),oldTile.direction(),id));bridge(id,pin);
                for(int dz:new int[]{-1,-2}){Pos at=pin.add(0,0,dz);Tile pixel=plan.blocks.get(at);plan.blocks.put(at,new Tile(pixel.part(),pixel.module(),pixel.direction(),id));}
            }
        }
        int longest=frontend.estimatedTicks;
        int[] at=KeypadTiming.deadlines(keypad,frontend);
        // The joined clock buses travel farther than the standalone fixture. Keep a full
        // frontend settling interval between edges so all latch controls finish propagating.
        int phase=Math.max(longest/3,700);
        for(int i=1;i<at.length;i++)at[i]=at[0]+i*phase;
        // Separate delay tracks carry request and run pulses. Repeaters execute all time sequencing.
        int timerY=Math.max(core.height,frontend.height)+6;
        List<Pos> taps=delayTrack(plan.sources.get(FRONT_NET+keypad.circuit.outputs.get("REQUEST")),at,new Pos(5,timerY,5),TIMER_NET);
        for(int i=0;i<6;i++) {
            String name="CLOCK"+(i+1);int id=FRONT_NET+keypad.circuit.inputs.get(name);Pos target=front(console.clockTargets.get(name));
            Pos lever=front(frontend.inputs.get(name));plan.blocks.put(lever,new Tile(Part.REPEATER,Module.CONTROL,1,id));
            component(target,Part.WIRE,0,id,Module.CONTROL);plan.sources.put(id,bufferTap(taps.get(i),id));bridge(id,target);
        }
        int run=FRONT_NET+keypad.circuit.outputs.get("GO");
        int runWait=core.estimatedTicks+longest+1000;
        // Start and finish must originate from the same filtered pulse. Raw gate startup
        // flashes can toggle a copper bulb yet disappear in a long repeater delay chain.
        List<Pos> done=delayTrack(plan.sources.get(run),new int[]{16,runWait},new Pos(5,timerY+6,5),TIMER_NET+1);
        int begin=TIMER_NET+3;Pos start=execution.add(0,0,-7);component(start,Part.WIRE,0,begin,Module.CONTROL);plan.sources.put(begin,bufferTap(done.getFirst(),begin));bridge(begin,start);
        int finish=TIMER_NET+2;Pos end=execution.add(0,0,7);component(end,Part.WIRE,0,finish,Module.CONTROL);plan.sources.put(finish,bufferTap(done.getLast(),finish));bridge(finish,end);
        // Fixed separated leads let the router reach both controls without squeezing
        // their three buses together immediately beside the protected execution cell.
        for(int distance=1;distance<=6;distance++) {
            Part part=distance==1||distance==3||distance==6?Part.REPEATER:Part.WIRE;
            component(start.add(0,0,distance),part,1,begin,Module.CONTROL);
            component(end.add(0,0,-distance),part,3,finish,Module.CONTROL);
        }
        colors.put(run,Module.CONTROL);
        plan.width=plan.blocks.keySet().stream().mapToInt(Pos::x).max().orElse(0)+3;
        plan.height=plan.blocks.keySet().stream().mapToInt(Pos::y).max().orElse(0)+3;
        plan.depth=plan.blocks.keySet().stream().mapToInt(Pos::z).max().orElse(0)+3;
        System.out.println("CONSOLE JOIN "+plan.blocks.size()+" fixed blocks, "+bridges.size()+" bridges; keypad budget="+longest+", run wait="+runWait);
        System.setProperty("electron.routeCheckpoint","build/console-checkpoint.ebp");
        String debug=System.getProperty("electron.debugSignal");
        if(debug!=null&&debug.matches("[0-9]+")){System.setProperty("electron.onlyNet",debug);System.clearProperty("electron.routeCheckpoint");}
        try{plan.write(java.nio.file.Path.of("build/console-fixed.ebp"));}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}
        CompactBlueprint result=CompactBlueprint.connectModules(plan,bridges,colors,guards,plan.width+30,plan.height+12,plan.depth+30);
        BlueprintVerifier.verifyModuleRoutes(result,bridges,PhysicalNetlist.of(DecimalCircuit.empty(Module.CONTROL)));
        result.blocks.forEach((p,t)->{
            if(t.part()!=Part.COPPER_BULB)return;
            for(int up=1;up<=2;up++) {
                Tile above=result.blocks.get(p.add(0,up,0));
                if(above!=null&&(above.part()==Part.WIRE||above.part()==Part.REPEATER))throw new IllegalStateException("Power line above copper request latch at "+p);
            }
        });
        result.estimatedTicks=runWait+at[5]*2;
        CalculatorCleanup.removeLegacyDisplay(result);
        System.out.println("CONSOLE BLUEPRINT "+result.width+"x"+result.height+"x"+result.depth+" blocks="+result.blocks.size());
        return result;
    }
    private void bufferConsoleLamps() {
        // Dust arriving sideways does not power a lamp beside its unconnected face.
        // A repeater gives every vertical pixel a reliable north-facing drive terminal.
        var pixels=plan.blocks.entrySet().stream().filter(e->e.getKey().x()>=ConsoleLayout.OFFSET_X&&e.getValue().part()==Part.LAMP).toList();
        Set<Pos> moved=new HashSet<>();
        for(var e:pixels) {
            Pos p=e.getKey(),lamp=p.add(0,0,-1);Tile t=e.getValue();
            if(plan.blocks.containsKey(lamp))throw new IllegalStateException("Screen pixel buffer overlaps "+lamp);
            plan.blocks.putIfAbsent(p.add(0,-1,0),new Tile(Part.WOOL,Module.DISPLAY,0,-1));
            plan.blocks.put(p,new Tile(Part.REPEATER,Module.DISPLAY,3,t.net()));put(lamp,t);moved.add(p);
            // A lamp strongly powered by its driver also powers nearby dust. Keep its
            // front and side faces insulated so a display branch cannot latch itself on.
            for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=0;dz++)guards.add(lamp.add(dx,dy,dz));
        }
        plan.outputs.replaceAll((name,p)->moved.contains(p)?p.add(0,0,-1):p);
    }
    private List<Pos> delayTrack(Pos source,int[] deadlines,Pos origin,int net) {
        int next=0,time=0;List<Pos> taps=new ArrayList<>();Pos p=origin;
        int feed=plan.sources.entrySet().stream().filter(e->e.getValue().equals(source)).map(Map.Entry::getKey).findFirst().orElseThrow();
        component(p,Part.WIRE,0,feed,Module.CONTROL);bridge(feed,p);
        int x=origin.x(),z=origin.z(),dir=0,max=245;
        while(next<deadlines.length) {
            int remaining=deadlines[next]-time;
            if(remaining<=0){taps.add(p);next++;continue;}
            int ticks=Math.min(8,remaining);ticks=Math.max(2,ticks&~1);
            if((dir==0&&x>=max-3)||(dir==2&&x<=origin.x()+3)) {
                // Repeaters only power the block ahead. Dust in front provides the corner;
                // a wire beside the last repeater cannot turn its output into the next row.
                x+=dir==0?1:-1;p=new Pos(x,origin.y(),z);
                component(p,Part.WIRE,0,net,Module.CONTROL);
                for(int step=1;step<=6;step++)component(new Pos(x,origin.y(),z+step),step==2||step==4?Part.REPEATER:Part.WIRE,1,net,Module.CONTROL);
                z+=6;time+=4;dir=2-dir;p=new Pos(x,origin.y(),z);continue;
            }
            x+=dir==0?1:-1;p=new Pos(x,origin.y(),z);
            component(p,Part.REPEATER,dir+((ticks/2-1)<<2),net,Module.CONTROL);time+=ticks;
            // Every tap terminates in dust so it can branch without reversing the delay chain.
            if(time>=deadlines[next]){x+=dir==0?1:-1;p=new Pos(x,origin.y(),z);component(p,Part.WIRE,0,net,Module.CONTROL);taps.add(p);next++;}
        }
        CompactBlueprint timing=new CompactBlueprint();timing.blocks.putAll(plan.blocks);
        timing.blocks.put(origin,new Tile(Part.WIRE,Module.CONTROL,0,net));timing.sources.put(net,origin);
        var reached=BlueprintVerifier.modulePower(timing,net);
        for(Pos tap:taps)if(reached.getOrDefault(tap,0)<3)throw new IllegalStateException("Broken vanilla delay track at "+tap);
        // Tap wires are treated as distinct external sources, while their delay blocks retain the track net.
        return taps;
    }
    private Pos bufferTap(Pos p,int net) {
        component(p.add(0,0,-1),Part.REPEATER,3,net,Module.CONTROL);
        Pos source=p.add(0,0,-2);component(source,Part.WIRE,0,net,Module.CONTROL);return source;
    }
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("front")){frontend().write(Path.of(args.length>1?args[1]:"build/keypad.ebp"));return;}
        CompactBlueprint core=CompactBlueprint.read(Files.newInputStream(Path.of("build/modular-calculator.ebp")));
        Path keypad=Path.of("build/keypad.ebp");CompactBlueprint front=Files.exists(keypad)?CompactBlueprint.read(Files.newInputStream(keypad)):frontend();front.write(keypad);
        extend(core,front).write(Path.of(args.length==0?"build/console-calculator.ebp":args[0]));
    }
}
