package top.lacrus.zephyrzeno.electron.foundry;

import java.util.List;
import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import top.lacrus.zephyrzeno.electron.circuit.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.node;

/** Electrical control and distribution devices; every load is stamped into the physical network. */
public final class PowerDevices {
    public static boolean handles(ComponentBlock.Kind k){return switch(k){case MOSFET,RELAY,BOOST,BUCK,GENERATOR,DISTRIBUTION,BREAKER,LAMP,MOTOR->true;default->false;};}
    public static void stamp(ComponentBlock block,BlockState state,ComponentEntity e,BlockPos p,List<DcSolver.Branch> b){
        String id="d:"+p.asLong(),a=node(p,0),z=node(p,1);boolean broken=state.get(ComponentBlock.BROKEN),enabled=state.get(ComponentBlock.ENABLED);
        if(e instanceof top.lacrus.zephyrzeno.electron.pcb.PcbBenchEntity bench){b.add(DcSolver.Branch.resistor(id,a,z,broken||!bench.ready()?1e12:24));return;}
        if(top.lacrus.zephyrzeno.electron.industrial.Automation.handles(block)){top.lacrus.zephyrzeno.electron.industrial.Automation.stamp(block,e,b);return;}
        if(block instanceof top.lacrus.zephyrzeno.electron.industrial.ProcessingBlock){b.add(DcSolver.Branch.resistor(id,a,z,broken||!enabled||!top.lacrus.zephyrzeno.electron.industrial.Processing.ready(e)?1e12:block.resistance));return;}
        if(block==top.lacrus.zephyrzeno.electron.industrial.Industrial.PUMP){b.add(DcSolver.Branch.resistor(id,a,z,broken?1e12:240));return;}
        switch(block.kind){
            case MOSFET->{String source=node(p,2);b.add(DcSolver.Branch.resistor("gate:"+id,a,source,broken?1e12:1e6));b.add(DcSolver.Branch.resistor(id,z,source,!broken&&e.contactClosed?.08:1e12));b.add(broken?DcSolver.Branch.resistor("body:"+id,source,z,1e12):new DcSolver.Branch("body:"+id,source,z,.1,.7,true));}
            case RELAY->{b.add(DcSolver.Branch.resistor("coil:"+id,a,z,broken?1e12:1000));b.add(DcSolver.Branch.resistor(id,node(p,2),node(p,3),!broken&&e.contactClosed?.02:1e12));}
            case BOOST,BUCK->{var spec=ConverterSpec.of(block.kind==ComponentBlock.Kind.BOOST,block.resistance);b.add(broken?DcSolver.Branch.resistor("in:"+id,a,z,1e12):ConverterModel.input("in:"+id,a,z,spec,e.converterEnergy,e.inputVoltage));b.add(ConverterModel.output(id,node(p,2),node(p,3),spec,broken?0:e.converterEnergy,enabled));b.add(DcSolver.Branch.resistor("return:"+id,z,node(p,3),.02));}
            case GENERATOR->b.add(ControlModel.generator(id,a,z,broken?0:e.energy,enabled));
            case DISTRIBUTION->{for(int rail=0;rail<2;rail++)for(int pin=0;pin<3;pin++)b.add(DcSolver.Branch.resistor("distribution:"+id+":"+rail+":"+pin,node(p,rail*4+pin),node(p,rail*4+pin+1),broken?1e12:.0005));}
            case BREAKER->b.add(DcSolver.Branch.resistor(id,a,z,!broken&&enabled?.02:1e12));
            case LAMP->b.add(DcSolver.Branch.resistor(id,a,z,broken||!enabled?1e12:block.resistance));
            case MOTOR->b.add(broken?DcSolver.Branch.resistor(id,a,z,1e12):DcSolver.Branch.source(id,a,z,MotorModel.emf(e.omega),MotorModel.RESISTANCE));
            default->throw new IllegalArgumentException("Not a power device");
        }
    }
    public static void pause(ServerWorld world,BlockPos pos,BlockState state,ComponentEntity e){
        var k=((ComponentBlock)state.getBlock()).kind;if(k==ComponentBlock.Kind.RELAY||k==ComponentBlock.Kind.MOSFET)e.contactClosed=e.pendingContact=false;
        if(state.get(ComponentBlock.LEVEL)!=0)world.setBlockState(pos,state.with(ComponentBlock.LEVEL,0),Block.NOTIFY_ALL);
    }
    public static void update(ServerWorld world,BlockPos pos,BlockState state,ComponentEntity e,DcSolver.Result r){
        var kind=((ComponentBlock)state.getBlock()).kind;String id="d:"+pos.asLong();boolean broken=state.get(ComponentBlock.BROKEN);double ratio=0;int level=0;
        if(e instanceof top.lacrus.zephyrzeno.electron.pcb.PcbBenchEntity bench){level=bench.process(broken);ratio=Math.max(e.voltage/30,-e.voltage/.5);
        }else if(top.lacrus.zephyrzeno.electron.industrial.Automation.handles((ComponentBlock)state.getBlock())){
            level=top.lacrus.zephyrzeno.electron.industrial.Automation.update(world,pos,e,(ComponentBlock)state.getBlock(),r);ratio=kind==ComponentBlock.Kind.LAMP?Math.max(e.voltage/28,-e.voltage/.5):Math.max(Math.max(e.inputVoltage/5.5,-e.inputVoltage/.5),Math.max(Math.abs(e.voltage)/30,Math.abs(e.current)/2));
        }else if(state.getBlock() instanceof top.lacrus.zephyrzeno.electron.industrial.ProcessingBlock){
            level=top.lacrus.zephyrzeno.electron.industrial.Processing.update(world,pos,e,broken);ratio=Math.max(e.voltage/30,-e.voltage/.5);
        }else switch(kind){
            case MOSFET->{e.inputVoltage=r.voltage(node(pos,0))-r.voltage(node(pos,2));e.inputCurrent=r.current("gate:"+id);e.voltage=r.voltage(node(pos,1))-r.voltage(node(pos,2));e.current=r.current(id)-r.current("body:"+id);e.contactClosed=!broken&&ControlModel.mosfet(e.inputVoltage,e.contactClosed);ratio=Math.max(Math.abs(e.inputVoltage)/12,Math.max(Math.abs(e.voltage)/30,Math.abs(e.current)/2));level=e.contactClosed?15:0;}
            case RELAY->{e.inputVoltage=e.voltage;e.inputCurrent=r.current("coil:"+id);e.voltage=r.voltage(node(pos,2))-r.voltage(node(pos,3));boolean close=!broken&&RedstoneSwitchModel.contact(e.inputVoltage,e.contactClosed);if(close!=e.pendingContact){e.pendingContact=close;e.contactDue=world.getTime()+2;}if(e.contactDue<=world.getTime()&&e.contactClosed!=e.pendingContact){e.contactClosed=e.pendingContact;ElectricalEffects.play(world,pos,ElectricalEffects.SWITCH,.35f,.9f);}ratio=Math.max(Math.max(e.inputVoltage/6,-e.inputVoltage/.5),Math.max(Math.abs(e.voltage)/30,Math.abs(e.current)/2));level=e.contactClosed?15:0;}
            case BOOST,BUCK->{var spec=ConverterSpec.of(kind==ComponentBlock.Kind.BOOST,((ComponentBlock)state.getBlock()).resistance);e.inputVoltage=e.voltage;e.inputCurrent=r.current("in:"+id);e.voltage=r.voltage(node(pos,2))-r.voltage(node(pos,3));e.converterEnergy=broken?0:ConverterModel.energy(e.converterEnergy,e.inputVoltage,e.inputCurrent,e.current,spec);ratio=Math.max(Math.max(e.inputVoltage/spec.maxInput,-e.inputVoltage/.5),Math.max(e.voltage/(spec.output*1.2),-e.voltage/.5));level=e.voltage>.5?15:0;}
            case GENERATOR->{e.energy=Math.max(0,e.energy-Math.max(0,-e.current)*24*.05);level=e.energy>1e-8?15:0;ratio=Math.max(0,Math.abs(e.voltage)/30);}
            case DISTRIBUTION->{level=0;}
            case BREAKER->{e.overload=ControlModel.breakerHeat(e.overload,e.current);if(e.overload>=.8&&state.get(ComponentBlock.ENABLED)){state=state.with(ComponentBlock.ENABLED,false);e.overload=0;top.lacrus.zephyrzeno.electron.power.FaultEffects.emit(world,ElectricalEffects.front(pos,state).add(-.08,0,0),ElectricalEffects.front(pos,state),top.lacrus.zephyrzeno.electron.power.FaultEffects.TRIP,Math.abs(e.voltage*e.current));}level=state.get(ComponentBlock.ENABLED)?15:0;}
            case LAMP->{level=!state.get(ComponentBlock.ENABLED)||Math.abs(e.current)<1e-7?0:(int)Math.clamp(Math.round(15*Math.sqrt(Math.clamp(Math.abs(e.voltage)/24,0,1))),0,15);ratio=Math.abs(e.voltage)/28;}
            case MOTOR->{if(state.isOf(top.lacrus.zephyrzeno.electron.industrial.Industrial.PUMP)){int flow=top.lacrus.zephyrzeno.electron.industrial.FluidNetwork.pump(world,pos,state,e);level=flow>0?15:0;ratio=Math.abs(e.voltage)/30;e.omega=flow*6;break;}e.omega=broken?0:MotorModel.step(e.omega,e.current,Math.max(e.loadMode,top.lacrus.zephyrzeno.electron.mechanical.MechanicalNetwork.load(world,pos,state.get(ComponentBlock.FACING))));ratio=Math.max(Math.abs(e.current)/1.5,Math.abs(e.voltage)/30);level=(int)Math.clamp(Math.round(Math.abs(e.omega)/350*15),0,15);state=state.with(ComponentBlock.ENABLED,e.omega>=0);}
            default->throw new IllegalArgumentException("Not a power device");
        }
        if(kind!=ComponentBlock.Kind.BREAKER){e.overload=Math.max(0,e.overload+(ratio>1?(ratio-1)*.05:-.05));if(!broken&&e.overload>=3){ElectricalEffects.burn(world,pos,state,ratio>=2.5);state=state.with(ComponentBlock.BROKEN,true);e.contactClosed=e.pendingContact=false;e.energy=e.converterEnergy=e.omega=0;broken=true;}else if(!broken&&e.overload>.45&&ratio>1&&world.getTime()-e.lastEffectTick>=16){e.lastEffectTick=world.getTime();ElectricalEffects.overload(world,pos,state);}}
        if(broken)level=0;e.markDirty();if(state!=world.getBlockState(pos)||state.get(ComponentBlock.LEVEL)!=level)world.setBlockState(pos,state.with(ComponentBlock.LEVEL,level),Block.NOTIFY_ALL);
    }
    private PowerDevices(){}
}
