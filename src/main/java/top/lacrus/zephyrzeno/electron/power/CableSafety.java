package top.lacrus.zephyrzeno.electron.power;

import java.util.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.circuit.CircuitParts.Wire;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.node;

/** A low 240 V span leaks through nearby ground. Ground is a lossy return, never an energy source. */
public final class CableSafety {
    public static Optional<Vec3d> lowPoint(ServerWorld w,List<Vec3d> curve){
        Vec3d lowest=null;double clearance=2;
        for(int i=8;i<curve.size()-8;i+=8){var v=curve.get(i);var p=BlockPos.ofFloored(v);
            for(int y=0;y<=2;y++){var q=p.down(y);if(!w.isChunkLoaded(q))continue;
                var state=w.getBlockState(q);
                if(state.getCollisionShape(w,q).isEmpty()&&w.getFluidState(q).isEmpty())continue;
                double gap=v.y-(q.getY()+1)-OverheadCable.RADIUS;
                if(gap<clearance){lowest=v;clearance=gap;}break;
            }
        }
        return Optional.ofNullable(lowest);
    }
    public static void stamp(ServerWorld w,CircuitState s,List<DcSolver.Branch> b,Map<UUID,Vec3d> low){
        if(w.getTime()%20==0){low.clear();for(var wire:s.wires.values())if(wire.overhead())lowPoint(w,s.overheadVertices(wire)).ifPresent(v->low.put(wire.id(),v));}
        low.keySet().retainAll(s.wires.keySet());
        for(var id:low.keySet()){
            Wire wire=s.wires.get(id);var p=wire.path().getFirst().host();
            // A diode threshold keeps 24 V insulated distribution unaffected.
            b.add(new DcSolver.Branch("ground:"+id,node(p,0),node(p,4),120,48,true));
        }
    }
    public static void effects(ServerWorld w,CircuitState s,Map<UUID,Vec3d> low){
        if(w.getTime()%20!=0)return;
        Set<UUID> shocked=new HashSet<>();
        for(var entry:low.entrySet())if(Math.abs(s.readings.current("ground:"+entry.getKey()))>.02){
            var p=entry.getValue();w.spawnParticles(ParticleTypes.ELECTRIC_SPARK,p.x,p.y,p.z,6,.1,.1,.1,.01);
            top.lacrus.zephyrzeno.electron.foundry.ElectricalEffects.play(w,BlockPos.ofFloored(p),top.lacrus.zephyrzeno.electron.foundry.ElectricalEffects.ARC,.25f,1.1f);
            for(var entity:w.getEntitiesByClass(LivingEntity.class,new Box(p,p).expand(.7),e->!e.isSpectator()))if(shocked.add(entity.getUuid()))entity.damage(w.getDamageSources().lightningBolt(),2);
        }
    }
    private CableSafety(){}
}
