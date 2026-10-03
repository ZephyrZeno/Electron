package top.lacrus.zephyrzeno.electron.industrial;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;

/** Pump pressure pays for transfer. Pipe contents and intake buffer are finite, saved volumes. */
public final class FluidNetwork {
    public record Route(List<FluidEntity> pipes,FluidEntity tank){}
    public static Direction outlet(BlockState s){return Direction.fromHorizontal(s.get(ComponentBlock.TURN));}
    public static Route route(ServerWorld w,BlockPos start){
        var queue=new ArrayDeque<BlockPos>();var parents=new HashMap<BlockPos,BlockPos>();queue.add(start);parents.put(start,start);
        while(!queue.isEmpty()&&parents.size()<=256){var p=queue.removeFirst();if(!w.isChunkLoaded(p)||!(w.getBlockEntity(p) instanceof FluidEntity e)||!e.getCachedState().get(FluidBlock.OPEN))continue;
            boolean inlet=e.getCachedState().isOf(Industrial.WATER_INLET);
            if(inlet||((FluidBlock)e.getCachedState().getBlock()).tank()){
                var target=inlet?e.inletTank():e.plant()==null?e:null;
                if(target==null||target.amount>=target.capacity())continue;
                var path=new ArrayList<FluidEntity>();if(inlet)path.add(e);
                for(var q=parents.get(p);!q.equals(p);){var f=w.getBlockEntity(q);if(f instanceof FluidEntity fluid&&fluid!=e)path.add(fluid);if(q.equals(parents.get(q)))break;q=parents.get(q);}Collections.reverse(path);return new Route(path,target);
            }
            for(var d:Direction.values()){var q=p.offset(d);if(!parents.containsKey(q)&&w.isChunkLoaded(q)&&w.getBlockEntity(q) instanceof FluidEntity){parents.put(q,p);queue.addLast(q);}}
        }return null;
    }
    public static int pump(ServerWorld w,BlockPos p,BlockState s,ComponentEntity motor){
        if(motor.paused||s.get(ComponentBlock.BROKEN)||Math.abs(motor.voltage)<18||Math.abs(motor.voltage)>30)return 0;
        int budget=Math.min(50,(int)(Math.max(0,motor.voltage*motor.current)*.05/.0024));if(budget<=0)return 0;
        var d=outlet(s);var route=route(w,p.offset(d));if(route==null)return 0;
        var target=route.pipes.stream().filter(e->e.amount<e.capacity()).findFirst().orElse(route.tank);int moved=Math.min(budget,target.capacity()-target.amount);if(moved<=0)return 0;
        var intake=p.offset(d.getOpposite());if(!w.isChunkLoaded(intake))return 0;
        if(w.getBlockEntity(intake) instanceof FluidEntity source){if(!((FluidBlock)source.getCachedState().getBlock()).tank())return 0;moved=Math.min(moved,source.amount);source.change(-moved);}
        else{
            if(motor.pumpIntake<moved)motor.pumpIntake+=WaterBodies.get(w).withdraw(w,intake,moved-motor.pumpIntake);
            moved=Math.min(moved,motor.pumpIntake);motor.pumpIntake-=moved;
        }
        if(moved>0){target.change(moved);route.pipes.forEach(FluidEntity::flow);motor.markDirty();}
        return moved;
    }
    public static FluidEntity tank(ServerWorld w,BlockPos p,Block expected){return w.isChunkLoaded(p)&&w.getBlockState(p).isOf(expected)&&w.getBlockEntity(p) instanceof FluidEntity e?e:null;}
    private FluidNetwork(){}
}
