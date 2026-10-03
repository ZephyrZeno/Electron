package top.lacrus.zephyrzeno.electron.power;

import java.util.List;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.node;

public final class GridDevices {
    public static boolean handles(ComponentBlock.Kind kind){return PowerPlant.source(kind)||kind==ComponentBlock.Kind.SUBSTATION||kind==ComponentBlock.Kind.MAINS;}
    public static void stamp(ServerWorld w,BlockPos p,BlockState s,ComponentEntity e,List<DcSolver.Branch> b){
        var component=(ComponentBlock)s.getBlock();var kind=component.kind;var spec=StationSpec.of(component.resistance);String id="d:"+p.asLong(),a=node(p,0),z=node(p,1);boolean enabled=s.get(ComponentBlock.ENABLED)&&!s.get(ComponentBlock.BROKEN);
        if(PowerPlant.source(kind)){PowerPlant.prepare(w,p,s,e);b.add(GridModel.source(id,a,z,e.energy,enabled&&e.plantReady,PowerPlant.voltage(s)));}
        else if(kind==ComponentBlock.Kind.SUBSTATION){e.plantReady=PowerPlant.requirement(w,p,kind).equals("ready");b.add(GridModel.stationInput("in:"+id,a,z,e.converterEnergy,enabled&&e.plantReady?e.inputVoltage:0,spec));b.add(GridModel.stationOutput(id,node(p,2),node(p,3),e.converterEnergy,enabled&&e.plantReady,spec));}
        else{b.add(DcSolver.Branch.resistor(id,a,node(p,2),enabled?.02:1e12));b.add(DcSolver.Branch.resistor("return:"+id,z,node(p,3),enabled?.02:1e12));}
    }
    public static void update(ServerWorld w,BlockPos p,BlockState s,ComponentEntity e,DcSolver.Result r){
        var component=(ComponentBlock)s.getBlock();var kind=component.kind;var spec=StationSpec.of(component.resistance);String id="d:"+p.asLong();int level=0;
        if(PowerPlant.source(kind)){e.energy=GridModel.consume(e.energy,e.current,PowerPlant.voltage(s));level=e.plantReady&&s.get(ComponentBlock.ENABLED)&&e.energy>1e-6?15:0;}
        else if(kind==ComponentBlock.Kind.SUBSTATION){e.inputVoltage=e.voltage;e.inputCurrent=r.current("in:"+id);e.voltage=r.voltage(node(p,2))-r.voltage(node(p,3));e.converterEnergy=GridModel.stationEnergy(e.converterEnergy,e.inputVoltage,e.inputCurrent,e.current,spec);level=e.voltage>spec.output()*.75?15:0;}
        else{
            e.inputVoltage=e.voltage;e.voltage=r.voltage(node(p,2))-r.voltage(node(p,3));
            e.meterEnergy+=Math.max(0,e.current*e.voltage)*.05;
            e.overload=Math.max(0,e.overload+(Math.abs(e.current)>2?(Math.abs(e.current)/2-1)*.05:-.1));
            if(e.overload>.5&&s.get(ComponentBlock.ENABLED)){s=s.with(ComponentBlock.ENABLED,false);e.overload=0;ElectricalEffects.play(w,p,ElectricalEffects.FAIL,.5f,1);}
            level=s.get(ComponentBlock.ENABLED)&&Math.abs(e.voltage)>1?15:0;
        }
        if(s.get(ComponentBlock.BROKEN))level=0;e.markDirty();if(w.getBlockState(p)!=s||s.get(ComponentBlock.LEVEL)!=level)w.setBlockState(p,s.with(ComponentBlock.LEVEL,level),Block.NOTIFY_ALL);
    }
    private GridDevices(){}
}
