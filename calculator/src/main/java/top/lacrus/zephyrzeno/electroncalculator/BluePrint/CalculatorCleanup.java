package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.nio.file.*;
import java.util.*;
import static top.lacrus.zephyrzeno.electroncalculator.BluePrint.CompactBlueprint.*;

/** Prunes obsolete panel sinks, preserving every fixed arithmetic cell and new display pin. */
public final class CalculatorCleanup {
    private static final int[] DX={1,0,-1,0},DZ={0,1,0,-1};
    public static void verifyRetainedRoutes(CompactBlueprint before,CompactBlueprint after) {
        Set<Integer> nets=new HashSet<>();before.blocks.forEach((p,t)->{if(t.part()==Part.LAMP&&p.x()<ConsoleLayout.OFFSET_X)nets.add(t.net());});
        for(int net:nets) {
            var oldPower=BlueprintVerifier.modulePower(before,net);var newPower=BlueprintVerifier.modulePower(after,net);
            for(Pos p:oldPower.keySet()) {
                Tile tile=after.blocks.get(p);
                if(tile!=null&&tile.net()==net&&(tile.part()==Part.WIRE||tile.part()==Part.REPEATER)&&!newPower.containsKey(p))throw new IllegalStateException("Cleanup disconnected retained net "+net+" at "+p);
            }
        }
    }
    public static int removeLegacyDisplay(CompactBlueprint plan) {
        int before=plan.blocks.size();
        Set<Pos> removed=new HashSet<>(),required=new HashSet<>(),legacyPixels=new HashSet<>();
        Map<Pos,Tile> fixed=ModularLayout.moduleBlocks(DecimalCircuit.create());
        Set<Integer> displayNets=new HashSet<>();
        plan.blocks.forEach((p,t)->{
            if(t.part()==Part.LAMP&&p.x()<ConsoleLayout.OFFSET_X) {
                legacyPixels.add(p);displayNets.add(t.net());
            }
            if(p.x()>=ConsoleLayout.OFFSET_X&&(t.part()==Part.WIRE||t.part()==Part.REPEATER))required.add(p);
        });
        fixed.forEach((p,t)->{if(t.net()>=0)required.add(p);});
        required.addAll(plan.sources.values());
        // Some new screen routes branch from the wire above an old lamp. Its support
        // remains a plain wool block if needed; only the obsolete light is removed.
        for(Pos p:legacyPixels)plan.blocks.put(p,new Tile(Part.WOOL,DecimalCircuit.Module.DISPLAY,0,-1));
        for(int net:displayNets) {
            Map<Pos,List<Pos>> reverse=new HashMap<>();Set<Pos> positions=new HashSet<>();
            plan.blocks.forEach((p,t)->{if(t.net()==net&&(t.part()==Part.WIRE||t.part()==Part.REPEATER))positions.add(p);});
            for(Pos p:positions) {
                Tile from=plan.blocks.get(p);
                for(int dir=0;dir<4;dir++)for(int dy=-1;dy<=1;dy++) {
                    Pos next=p.add(DX[dir],dy,DZ[dir]);if(!positions.contains(next))continue;
                    Tile to=plan.blocks.get(next);
                    if(from.part()==Part.REPEATER&&((from.direction()&3)!=dir||dy!=0))continue;
                    if(to.part()==Part.REPEATER&&((to.direction()&3)!=dir||dy!=0))continue;
                    if(dy!=0&&plan.blocks.containsKey((dy>0?p:next).add(0,1,0)))continue;
                    reverse.computeIfAbsent(next,k->new ArrayList<>()).add(p);
                }
            }
            Set<Pos> keep=new HashSet<>();Deque<Pos> queue=new ArrayDeque<>();
            for(Pos p:positions)if(required.contains(p)){keep.add(p);queue.add(p);}
            while(!queue.isEmpty())for(Pos p:reverse.getOrDefault(queue.removeFirst(),List.of()))if(keep.add(p))queue.add(p);
            for(Pos p:positions)if(!keep.contains(p)&&!fixed.containsKey(p)){plan.blocks.remove(p);removed.add(p);}
        }
        for(Pos p:legacyPixels)if(!plan.blocks.containsKey(p.add(0,1,0))){plan.blocks.remove(p);removed.add(p);}
        // Remove supports belonging solely to a deleted route; retain supports under other parts.
        for(Pos p:removed) {
            Pos support=p.add(0,-1,0);Tile t=plan.blocks.get(support);
            if(t!=null&&t.part()==Part.WOOL&&!fixed.containsKey(support)&&!plan.blocks.containsKey(p))plan.blocks.remove(support);
        }
        plan.labels.replaceAll(l->l.title().equals("输入错误")?new Label(l.pos(),"输入错误指示灯","仅灯亮才表示错误"):
            l.title().equals("运算／按键处理中")?new Label(l.pos(),"忙状态指示灯","灯亮等待；灯灭可输入"):l);
        if(plan.blocks.entrySet().stream().anyMatch(e->e.getKey().x()<ConsoleLayout.OFFSET_X&&e.getValue().part()==Part.LAMP))throw new IllegalStateException("Legacy lamp survived");
        System.out.println("LEGACY DISPLAY CLEANUP removed="+(before-plan.blocks.size()));return before-plan.blocks.size();
    }
    public static void main(String[] args)throws Exception {
        var plan=CompactBlueprint.read(Files.newInputStream(Path.of(args[0])));
        if(args.length>2&&args[2].equals("check")) {
            var clean=CompactBlueprint.read(Files.newInputStream(Path.of(args[1])));
            verifyRetainedRoutes(plan,clean);System.out.println("RETAINED DISPLAY ROUTES PASS");
            return;
        }
        removeLegacyDisplay(plan);plan.write(Path.of(args[1]));
    }
}
