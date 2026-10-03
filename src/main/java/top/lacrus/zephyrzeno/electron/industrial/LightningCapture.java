package top.lacrus.zephyrzeno.electron.industrial;

import java.util.*;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.CircuitState;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.multiblock.Assembly;
import top.lacrus.zephyrzeno.electron.power.PowerPlant;

/** Gameplay strike interception, not a claim that real rods attract all nearby lightning. */
public final class LightningCapture {
    public static final int RADIUS=64;
    public static void redirect(ServerWorld w,LightningEntity bolt){
        List<BlockPos> candidates=new ArrayList<>();
        for(var p:CircuitState.get(w).devices.keySet())if(w.isChunkLoaded(p)&&w.getBlockState(p).isOf(Foundry.LIGHTNING)){
            var s=w.getBlockState(p);double dx=p.getX()+.5-bolt.getX(),dz=p.getZ()+.5-bolt.getZ();
            if(Assembly.formed(s)&&s.get(ComponentBlock.ENABLED)&&!s.get(ComponentBlock.BROKEN)&&p.getY()+2>bolt.getY()&&dx*dx+dz*dz<=RADIUS*RADIUS&&PowerPlant.requirement(w,p,ComponentBlock.Kind.LIGHTNING).equals("ready")&&w.isSkyVisible(p.up(2)))candidates.add(p);
        }
        if(!candidates.isEmpty()){var p=candidates.get(w.random.nextInt(candidates.size()));bolt.setPosition(p.getX()+.5,p.getY()+1,p.getZ()+.5);}
    }
    private LightningCapture(){}
}
