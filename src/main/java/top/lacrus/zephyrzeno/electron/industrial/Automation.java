package top.lacrus.zephyrzeno.electron.industrial;

import java.util.List;
import net.minecraft.block.*;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.item.ComponentItem;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.node;

/** Powered local sensors expose isolated contacts; emergency light stores only paid energy. */
public final class Automation {
    public static ComponentBlock WATER,TEMPERATURE,EMERGENCY;
    private static ComponentBlock block(String id,boolean lamp){
        var b=Registry.register(Registries.BLOCK,Electron.id(id),new ComponentBlock(AbstractBlock.Settings.create().strength(2).nonOpaque().sounds(BlockSoundGroup.METAL).pistonBehavior(PistonBehavior.BLOCK).luminance(s->lamp?s.get(ComponentBlock.LEVEL):0),lamp?ComponentBlock.Kind.LAMP:ComponentBlock.Kind.RELAY,lamp?480:2500){
            @Override public boolean rearPowerPorts(){return lamp;}
            @Override protected net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState s,net.minecraft.world.BlockView w,BlockPos p,ShapeContext context){return lamp?top.lacrus.zephyrzeno.electron.pcb.WorkshopGeometry.shape("emergency_lamp",s.get(FACING),s.get(TURN)):super.getOutlineShape(s,w,p,context);}
            @Override protected ActionResult onUse(BlockState s,World w,BlockPos p,PlayerEntity player,BlockHitResult hit){if(!w.isClient&&w.getBlockEntity(p) instanceof ComponentEntity e){
                if(lamp)w.setBlockState(p,s.cycle(ENABLED));else {e.loadMode=(e.loadMode+1)%3;e.markDirty();player.sendMessage(Text.translatable("automation.electron."+(this==WATER?"water_":"temperature_")+e.loadMode),true);}
                ElectricalEffects.play((ServerWorld)w,p,ElectricalEffects.SWITCH,.3f,1);
            }return ActionResult.SUCCESS;}
        });
        Foundry.DEVICES.put(id,b);var item=Registry.register(Registries.ITEM,Electron.id(id),new ComponentItem(b,new Item.Settings().maxCount(lamp?1:64)));Foundry.ITEMS.put(id,item);return b;
    }
    public static void register(){WATER=block("water_level_sensor",false);TEMPERATURE=block("temperature_sensor",false);EMERGENCY=block("emergency_lamp",true);}
    public static boolean handles(ComponentBlock b){return b==WATER||b==TEMPERATURE||b==EMERGENCY;}
    public static void stamp(ComponentBlock b,ComponentEntity e,List<DcSolver.Branch> out){
        var p=e.getPos();String id="d:"+p.asLong();boolean broken=e.getCachedState().get(ComponentBlock.BROKEN);
        if(b==EMERGENCY){out.add(DcSolver.Branch.resistor(id,node(p,0),node(p,1),broken||!e.getCachedState().get(ComponentBlock.ENABLED)?1e12:480));out.add(DcSolver.Branch.resistor(id+":charge",node(p,0),node(p,1),broken||e.energy>=200?1e12:96));}
        else{out.add(DcSolver.Branch.resistor("coil:"+id,node(p,0),node(p,1),broken?1e12:2500));out.add(DcSolver.Branch.resistor(id,node(p,2),node(p,3),!broken&&e.contactClosed?.02:1e12));}
    }
    public static int update(ServerWorld world,BlockPos p,ComponentEntity e,ComponentBlock b,DcSolver.Result r){
        String id="d:"+p.asLong();boolean broken=e.getCachedState().get(ComponentBlock.BROKEN);
        if(b==EMERGENCY){
            boolean powered=!broken&&e.voltage>=18&&e.voltage<=28&&e.current>0,enabled=e.getCachedState().get(ComponentBlock.ENABLED);
            double charge=!broken&&e.voltage>=18&&e.voltage<=28?Math.max(0,e.voltage*r.current(id+":charge"))*.05*.9:0;e.inputCurrent=r.current(id+":charge");
            boolean lit=!broken&&enabled&&(powered||e.energy>=.06);
            e.energy=broken?0:Math.clamp(e.energy+charge-(lit&&!powered?.06:0),0,200);e.current+=e.inputCurrent;
            return lit?powered?15:11:0;
        }
        e.inputVoltage=e.voltage;e.inputCurrent=r.current("coil:"+id);e.voltage=r.voltage(node(p,2))-r.voltage(node(p,3));
        boolean valid=!broken&&e.inputVoltage>=4.5&&e.inputVoltage<=5.5,found=false,close=false;
        if(valid)for(var dir:Direction.values()){
            var target=p.offset(dir);if(!world.isChunkLoaded(target))continue;
            if(b==WATER&&world.getBlockEntity(target) instanceof FluidEntity tank&&tank.capacity()>=1000){
                if(found){close=false;break;}found=true;double fill=tank.amount/(double)tank.capacity();int mode=e.loadMode;
                close=mode==0?fill<(e.contactClosed?.75:.25):fill>=(mode==1?.25:.75)-(e.contactClosed?.05:0);
            }else if(b==TEMPERATURE&&world.getBlockEntity(target) instanceof ComponentEntity device&&top.lacrus.zephyrzeno.electron.power.GridDevices.handles(((ComponentBlock)device.getCachedState().getBlock()).kind)){
                if(found){close=false;break;}found=true;double limit=new int[]{60,90,120}[e.loadMode];close=!device.getCachedState().get(ComponentBlock.BROKEN)&&device.temperature<=limit-(e.contactClosed?0:5);
            }
        }
        close=valid&&found&&close;if(e.contactClosed!=close){e.contactClosed=close;ElectricalEffects.play(world,p,ElectricalEffects.SWITCH,.25f,.95f);}return close?15:0;
    }
    private Automation(){}
}
