package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.circuit.*;

public final class PcbSocketEntity extends ComponentEntity {
    private PcbCircuit circuit;
    public int ledMask;
    public int[] displays=new int[0];
    public PcbSocketEntity(BlockPos p,BlockState s){super(p,s);}
    public PcbCircuit circuit(){if(circuit==null)circuit=new PcbCircuit(Pcb.layout(plantItems.get(0)));return circuit;}
    public void boardChanged(){circuit=null;ledMask=0;displays=new int[0];markDirty();if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}
    public void stamp(List<DcSolver.Branch> branches,long tick){String[] ports=new String[6];for(int i=0;i<6;i++)ports[i]=CircuitParts.node(pos,i);circuit().stamp("d:"+pos.asLong(),ports,branches,tick,getCachedState().get(ComponentBlock.BROKEN));}
    public void update(DcSolver.Result r,Predicate<String> driven){
        var state=getCachedState();boolean broken=state.get(ComponentBlock.BROKEN);String id="d:"+pos.asLong();
        double stress=circuit().update(id,r,driven,world.getTime(),broken);current=r.current(id+":port0")+r.current(id);
        stress=Math.max(stress,Math.max(voltage/5.5,-voltage/.5));overload=Math.max(0,overload+(stress>1?(stress-1)*.05:-.05));
        if(!broken&&overload>=3){ElectricalEffects.burn((ServerWorld)world,pos,state,false);world.setBlockState(pos,state.with(ComponentBlock.BROKEN,true).with(ComponentBlock.LEVEL,0));plantItems.set(0,new net.minecraft.item.ItemStack(Foundry.SCRAP));boardChanged();circuit().reset();}
        int next=world.getBlockState(pos).get(ComponentBlock.BROKEN)?0:circuit().ledMask;
        if(ledMask!=next||!Arrays.equals(displays,circuit().displays)){ledMask=next;displays=circuit().displays.clone();world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}markDirty();
    }
    public void pause(){circuit().reset();if(ledMask!=0||Arrays.stream(displays).anyMatch(v->v>=0)){ledMask=0;Arrays.fill(displays,-1);world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}}
    @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.readNbt(n,r);circuit=null;ledMask=n.getInt("pcb_leds");displays=n.getIntArray("pcb_displays");}
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup r){var n=createNbt(r);n.putInt("pcb_leds",ledMask);n.putIntArray("pcb_displays",displays);return n;}
}
