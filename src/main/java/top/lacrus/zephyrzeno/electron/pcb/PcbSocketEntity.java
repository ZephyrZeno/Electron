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
    public long[] pixels=new long[0];
    public BitSet leds=new BitSet();
    private int[] savedControls=new int[0];
    private long[] savedCircuitState=new long[0];
    public PcbSocketEntity(BlockPos p,BlockState s){super(p,s);}
    public PcbCircuit circuit(){if(circuit==null){circuit=new PcbCircuit(Pcb.layout(plantItems.get(0)));System.arraycopy(savedControls,0,circuit.controls,0,Math.min(savedControls.length,circuit.controls.length));circuit.restore(savedCircuitState);}return circuit;}
    public void boardChanged(){circuit=null;savedControls=new int[0];savedCircuitState=new long[0];ledMask=0;displays=new int[0];pixels=new long[0];leds.clear();markDirty();if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}
    public void stamp(List<DcSolver.Branch> branches,long tick){String[] ports=new String[6];for(int i=0;i<6;i++)ports[i]=CircuitParts.node(pos,i);circuit().stamp("d:"+pos.asLong(),ports,branches,tick,getCachedState().get(ComponentBlock.BROKEN)||!supported(circuit().layout));}
    public void update(DcSolver.Result r,Predicate<String> driven){
        if(!supported(circuit().layout)){pause();return;}
        var state=getCachedState();boolean broken=state.get(ComponentBlock.BROKEN);String id="d:"+pos.asLong();
        double stress=circuit().update(id,r,driven,world.getTime(),broken);current=r.current(id+":port0")+r.current(id);
        stress=Math.max(stress,Math.max(voltage/5.5,-voltage/.5));overload=Math.max(0,overload+(stress>1?(stress-1)*.05:-.05));
        if(!broken&&overload>=3){ElectricalEffects.burn((ServerWorld)world,pos,state,false);world.setBlockState(pos,state.with(ComponentBlock.BROKEN,true).with(ComponentBlock.LEVEL,0));plantItems.set(0,new net.minecraft.item.ItemStack(Foundry.SCRAP));boardChanged();circuit().reset();}
        int next=world.getBlockState(pos).get(ComponentBlock.BROKEN)?0:circuit().ledMask;
        if(ledMask!=next||!Arrays.equals(displays,circuit().displays)||!Arrays.equals(pixels,circuit().pixels)||!leds.equals(circuit().leds)){ledMask=next;displays=circuit().displays.clone();pixels=circuit().pixels.clone();leds=(BitSet)circuit().leds.clone();world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}markDirty();
    }
    public void pause(){circuit().reset();if(!leds.isEmpty()||Arrays.stream(pixels).anyMatch(v->v!=0)||Arrays.stream(displays).anyMatch(v->v>=0)){ledMask=0;leds.clear();Arrays.fill(pixels,0);Arrays.fill(displays,-1);world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}}
    public static double u(PcbLayout l,double x){return 1+(x+.5)*(l.tilesX()*16-2)/l.width();}
    public static double v(PcbLayout l,double y){return 1+(y+.5)*(l.tilesY()*16-2)/l.height();}
    public List<BlockPos> supportPositions(PcbLayout l){var out=new ArrayList<BlockPos>();var s=getCachedState();for(int y=0;y<l.tilesY();y++)for(int x=0;x<l.tilesX();x++){var at=ComponentGeometry.local(s.get(ComponentBlock.FACING),s.get(ComponentBlock.TURN),8+16*x,8+16*y,13);out.add(BlockPos.ofFloored(at).add(pos));}return out;}
    public boolean supported(PcbLayout l){if(l.tilesX()==1&&l.tilesY()==1)return true;if(world==null||getCachedState().get(ComponentBlock.FACING)!=net.minecraft.util.math.Direction.UP)return false;for(var p:supportPositions(l)){if(p.equals(pos))continue;if(!world.getBlockState(p).isOf(Pcb.EXTENSION))return false;}return true;}
    public net.minecraft.util.ActionResult interact(net.minecraft.entity.player.PlayerEntity player,net.minecraft.util.hit.BlockHitResult hit){
        if(world.isClient)return net.minecraft.util.ActionResult.SUCCESS;
        if(!player.getMainHandStack().isEmpty())return net.minecraft.util.ActionResult.PASS;
        var l=circuit().layout;var s=getCachedState();
        // Dense controls can have overlapping hit radii; choose the nearest one, not list order.
        int picked=-1;double nearest=.035;
        for(int i=0;i<l.parts().size();i++){
            var p=l.parts().get(i);if(!p.type().input())continue;
            var center=ComponentGeometry.local(s.get(ComponentBlock.FACING),s.get(ComponentBlock.TURN),u(l,p.x()+1),v(l,p.y()+1),12.45).add(net.minecraft.util.math.Vec3d.of(pos));
            double distance=center.squaredDistanceTo(hit.getPos());if(distance<nearest){nearest=distance;picked=i;}
        }
        if(picked>=0){var type=l.parts().get(picked).type();int mod=type==PcbLayout.Type.DIAL?10:type==PcbLayout.Type.SWITCH?2:5;
            int value=type==PcbLayout.Type.CLOCK?Math.max(0,circuit().controls[picked]/5-1):circuit().controls[picked];
            setControl(picked,Math.floorMod(value+(player.isSneaking()?-1:1),mod)*(type==PcbLayout.Type.CLOCK?5:1)+(type==PcbLayout.Type.CLOCK?5:0));
            return net.minecraft.util.ActionResult.CONSUME;
        }
        if(player.isSneaking()&&!plantItems.get(0).isEmpty()){var item=plantItems.get(0);plantItems.set(0,net.minecraft.item.ItemStack.EMPTY);boardChanged();player.getInventory().offerOrDrop(item);}
        else player.sendMessage(net.minecraft.text.Text.translatable(plantItems.get(0).isEmpty()?"pcb.electron.insert":"pcb.electron.board_controls"),true);
        return net.minecraft.util.ActionResult.CONSUME;
    }
    public void setControl(int index,int value){var c=circuit();if(index<0||index>=c.controls.length||!c.layout.parts().get(index).type().input())return;int clamped=Math.clamp(value,0,c.layout.parts().get(index).type()==PcbLayout.Type.DIAL?9:c.layout.parts().get(index).type()==PcbLayout.Type.SWITCH?1:25);if(c.controls[index]==clamped)return;c.controls[index]=clamped;savedControls=c.controls.clone();markDirty();if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}
    private void writeVisibleNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.writeNbt(n,r);n.putIntArray("pcb_controls",(circuit==null?savedControls:circuit.controls).clone());}
    @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){writeVisibleNbt(n,r);n.putLongArray("pcb_circuit_state",circuit==null?savedCircuitState.clone():circuit.snapshot());}
    @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.readNbt(n,r);circuit=null;ledMask=n.getInt("pcb_leds");displays=n.getIntArray("pcb_displays");pixels=n.getLongArray("pcb_pixels");leds=BitSet.valueOf(n.getLongArray("pcb_led_bits"));savedControls=n.getIntArray("pcb_controls");savedCircuitState=n.getLongArray("pcb_circuit_state");}
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup r){var n=new NbtCompound();writeVisibleNbt(n,r);n.putInt("pcb_leds",ledMask);n.putIntArray("pcb_displays",displays.clone());n.putLongArray("pcb_pixels",pixels.clone());n.putLongArray("pcb_led_bits",leds.toLongArray());return n;}
}
