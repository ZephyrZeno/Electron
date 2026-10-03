package top.lacrus.zephyrzeno.electron.appliance;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.item.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.node;

public final class Appliances {
    public static ApplianceBlock WALL,STRIP,ADAPTER;
    public static Item CORD;
    private static ApplianceBlock block(String id,ApplianceBlock.Type type){
        var b=Registry.register(Registries.BLOCK,Electron.id(id),new ApplianceBlock(AbstractBlock.Settings.create().strength(2).nonOpaque().sounds(net.minecraft.sound.BlockSoundGroup.METAL),type));
        var item=Registry.register(Registries.ITEM,Electron.id(id),new ComponentItem(b,new Item.Settings()));
        Foundry.DEVICES.put(id,b);Foundry.ITEMS.put(id,item);return b;
    }
    public static void register(){
        WALL=block("wall_outlet",ApplianceBlock.Type.WALL);STRIP=block("power_strip",ApplianceBlock.Type.STRIP);ADAPTER=block("appliance_adapter",ApplianceBlock.Type.ADAPTER);
        CORD=Registry.register(Registries.ITEM,Electron.id("appliance_power_cord"),new PowerCordItem(new Item.Settings().maxCount(16)));
        Foundry.ITEMS.put("appliance_power_cord",CORD);
    }
    public static boolean load(BlockState s){return s.getBlock() instanceof ApplianceBlock||s.getBlock() instanceof ComponentBlock b&&(b.kind==ComponentBlock.Kind.LAMP||b.kind==ComponentBlock.Kind.MOTOR);}
    public static Vec3d plug(BlockPos p,BlockState s,boolean output,int socket){
        double u=output?(s.isOf(STRIP)?4+socket*4:8):8,v=output?8:1,d=s.getBlock() instanceof ApplianceBlock?1:9;
        if(!(s.getBlock() instanceof ApplianceBlock))v=0;
        return Vec3d.of(p).add(ComponentGeometry.local(s.get(ComponentBlock.FACING),s.get(ComponentBlock.TURN),u,v,d));
    }
    public static int outlets(BlockState s){return s.isOf(STRIP)?3:s.isOf(WALL)||s.isOf(ADAPTER)?1:0;}
    public static void stamp(ServerWorld w,BlockPos p,BlockState s,ComponentEntity e,List<DcSolver.Branch> b){
        String id="d:"+p.asLong();boolean on=s.get(ComponentBlock.ENABLED)&&!s.get(ComponentBlock.BROKEN);
        if(s.isOf(ADAPTER)){
            double inLimit=Math.min(.24,Math.max(0,8-e.converterEnergy)/(.88*264*.05));
            b.add(on&&e.inputVoltage>=180&&e.inputVoltage<=264?new DcSolver.Branch("in:"+id,node(p,0),node(p,1),100,0,true,0,inLimit):DcSolver.Branch.resistor("in:"+id,node(p,0),node(p,1),1e12));
            double out=Math.min(2,Math.max(0,e.converterEnergy)/(24*.05));
            b.add(on&&e.converterEnergy>.001?new DcSolver.Branch(id,node(p,2),node(p,3),.12,24,false,-out,0):DcSolver.Branch.resistor(id,node(p,2),node(p,3),1e12));
        }else{
            b.add(DcSolver.Branch.resistor(id,node(p,0),node(p,2),on?.02:1e12));
            b.add(DcSolver.Branch.resistor("return:"+id,node(p,1),node(p,3),on?.02:1e12));
        }
    }
    public static void update(ServerWorld w,BlockPos p,BlockState s,ComponentEntity e,DcSolver.Result r){
        String id="d:"+p.asLong();e.inputVoltage=r.voltage(node(p,0))-r.voltage(node(p,1));e.inputCurrent=r.current(s.isOf(ADAPTER)?"in:"+id:id);
        e.voltage=r.voltage(node(p,2))-r.voltage(node(p,3));e.current=r.current(id);
        if(s.isOf(ADAPTER))e.converterEnergy=Math.clamp(e.converterEnergy+Math.max(0,e.inputVoltage*e.inputCurrent)*.88*.05-Math.max(0,-e.current)*24*.05,0,8);
        e.overload=Math.max(0,e.overload+(Math.abs(e.inputCurrent)>2.5||Math.abs(e.inputVoltage)>280?.1:-.05));
        if(e.overload>=1&&s.get(ComponentBlock.ENABLED)){s=s.with(ComponentBlock.ENABLED,false);e.overload=0;ElectricalEffects.play(w,p,ElectricalEffects.TRIP,.5f,1);}
        if(s.get(ComponentBlock.BROKEN))e.converterEnergy=0;
        int light=!s.get(ComponentBlock.BROKEN)&&s.get(ComponentBlock.ENABLED)&&e.voltage>1?15:0;
        if(!s.equals(w.getBlockState(p))||s.get(ComponentBlock.LEVEL)!=light)w.setBlockState(p,s.with(ComponentBlock.LEVEL,light),Block.NOTIFY_ALL);e.markDirty();
    }
    private Appliances(){}
}
