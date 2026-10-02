package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.nio.file.*;
import java.util.*;
import static top.lacrus.zephyrzeno.electroncalculator.BluePrint.CompactBlueprint.*;

/** Removes redundant repeaters only inside branch-free, level wire runs. */
public final class BlueprintOptimizer {
    private static final int[] DX={1,0,-1,0},DZ={0,1,0,-1};
    public static int compactRepeaters(CompactBlueprint plan) {
        DecimalCircuit c=plan.inputs.containsKey("AT0")?DecimalCircuit.create():DecimalCircuit.smoke();
        Set<Pos> fixed=CompactBlueprint.fixedPositions(c);
        List<Pos> repeaters=plan.blocks.entrySet().stream().filter(e->e.getValue().part()==Part.REPEATER).map(Map.Entry::getKey).toList();
        int removed=0;
        for(Pos start:repeaters) {
            Tile first=plan.blocks.get(start);if(first.part()!=Part.REPEATER)continue;
            int dir=first.direction(),end=0;
            for(int step=1;step<=15;step++) {
                Pos p=start.add(DX[dir]*step,0,DZ[dir]*step);Tile tile=plan.blocks.get(p);
                if(tile==null||tile.net()!=first.net())break;
                if(step%3==0) {
                    if(tile.part()!=Part.REPEATER||tile.direction()!=dir)break;
                    end=step;
                    if(fixed.contains(p))break;
                }else if(tile.part()!=Part.WIRE)break;
                if(hasSideContact(plan,p,dir))break;
            }
            for(int step=3;step<end;step+=3) {
                Pos p=start.add(DX[dir]*step,0,DZ[dir]*step);Tile old=plan.blocks.get(p);
                plan.blocks.put(p,new Tile(Part.WIRE,old.module(),0,old.net()));removed++;
            }
        }
        System.out.println("Removed "+removed+" redundant repeaters out of "+repeaters.size());return removed;
    }
    private static boolean hasSideContact(CompactBlueprint plan,Pos p,int forward) {
        for(int dir=0;dir<4;dir++)for(int dy=-1;dy<=1;dy++) {
            if(dy==0&&(dir==forward||dir==(forward+2)%4))continue;
            Tile t=plan.blocks.get(p.add(DX[dir],dy,DZ[dir]));
            if(t!=null&&(t.part()==Part.WIRE||t.part()==Part.REPEATER||t.part()==Part.LEVER||t.part()==Part.TORCH))return true;
        }
        return false;
    }
    public static void main(String[] args)throws Exception {
        CompactBlueprint p=CompactBlueprint.read(Files.newInputStream(Path.of(args[0])));
        compactRepeaters(p);p.estimatedTicks=BlueprintVerifier.verify(p);p.write(Path.of(args[1]));
    }
}
