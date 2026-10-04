package top.lacrus.zephyrzeno.electron.display;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.block.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.logic.WireLogic;

public final class DisplayEntity extends ComponentEntity {
    public int setting,value=-1;
    public boolean signMode;
    public long pixelBits;
    long[] pixelShift=new long[0];
    int pixelClock=2,pixelLatch=2;
    public DisplayEntity(BlockPos p,BlockState s){super(p,s);}
    public DisplayBlock.Role role(){return ((DisplayBlock)getCachedState().getBlock()).role;}
    public static Direction axis(BlockState s,boolean v){var a=ComponentGeometry.local(s.get(ComponentBlock.FACING),s.get(ComponentBlock.TURN),8,8,13);var b=ComponentGeometry.local(s.get(ComponentBlock.FACING),s.get(ComponentBlock.TURN),v?8:16,v?16:8,13).subtract(a);return Direction.getFacing(b.x,b.y,b.z);}
    public boolean joins(BlockPos other){if(world==null||!world.isChunkLoaded(other))return false;var a=getCachedState();var b=world.getBlockState(other);return (role()==DisplayBlock.Role.PANEL||role()==DisplayBlock.Role.PIXEL)&&b.isOf(a.getBlock())&&b.get(ComponentBlock.FACING)==a.get(ComponentBlock.FACING)&&b.get(ComponentBlock.TURN).equals(a.get(ComponentBlock.TURN))&&!a.get(ComponentBlock.BROKEN)&&!b.get(ComponentBlock.BROKEN);}
    public void stamp(List<DcSolver.Branch> out){var s=getCachedState();boolean broken=s.get(ComponentBlock.BROKEN);String id="d:"+pos.asLong(),a=CircuitParts.node(pos,0),b=CircuitParts.node(pos,1);out.add(DcSolver.Branch.resistor(id,a,b,broken?1e12:role()==DisplayBlock.Role.PIXEL?5/(.002+Long.bitCount(pixelBits)*.0003):role()==DisplayBlock.Role.PANEL?500:1000));
        for(int i=0;i<4;i++)out.add(DcSolver.Branch.resistor(id+":bit"+i,CircuitParts.node(pos,i+2),role()==DisplayBlock.Role.DIAL&&(setting&(1<<i))!=0?a:b,broken?1e12:role()==DisplayBlock.Role.DIAL?100:1e7));
        if((role()==DisplayBlock.Role.PANEL||role()==DisplayBlock.Role.PIXEL)&&!broken)for(boolean v:new boolean[]{false,true}){var other=pos.offset(axis(s,v));if(joins(other))for(int pin=0;pin<6;pin++)out.add(DcSolver.Branch.resistor(id+":edge"+v+":"+pin,CircuitParts.node(pos,pin),CircuitParts.node(other,pin),.01));}
    }
    public void update(DcSolver.Result r,Predicate<String> driven){String id="d:"+pos.asLong();int next=0;for(int i=0;i<4;i++){int bit=WireLogic.input(r.voltage(CircuitParts.node(pos,i+2))-r.voltage(CircuitParts.node(pos,1)),voltage,driven.test(CircuitParts.node(pos,i+2)));if(bit>1){next=-1;break;}next|=bit<<i;}
        boolean broken=getCachedState().get(ComponentBlock.BROKEN);if(broken||voltage<4.5||voltage>5.5)next=-1;
        double stress=Math.max(voltage/5.5,-voltage/.5);if(role()==DisplayBlock.Role.DIAL)for(int i=0;i<4;i++){double amps=r.current(id+":bit"+i);stress=Math.max(stress,Math.abs(amps)/.02);if((setting&(1<<i))!=0)current+=Math.max(0,-amps);}
        overload=Math.max(0,overload+(stress>1?(stress-1)*.05:-.05));if(!broken&&overload>=3){ElectricalEffects.burn((ServerWorld)world,pos,getCachedState(),false);world.setBlockState(pos,getCachedState().with(ComponentBlock.BROKEN,true));next=-1;}
        if(role()==DisplayBlock.Role.PIXEL)PixelMatrix.update(this,next,!broken&&voltage>=4.5&&voltage<=5.5);
        if(value!=next){value=next;sync();}else if(overload>0)markDirty();
    }
    public void pause(){if(role()==DisplayBlock.Role.PIXEL){PixelMatrix.update(this,-1,false);pixelBits=0;}if(value!=-1){value=-1;sync();}}
    public void toggleMode(){boolean next=!signMode;if(role()!=DisplayBlock.Role.PANEL){signMode=next;sync();return;}var todo=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();todo.add(pos);seen.add(pos);while(!todo.isEmpty()&&seen.size()<=256){var at=todo.remove();if(world.getBlockEntity(at) instanceof DisplayEntity e){e.signMode=next;e.sync();}for(boolean v:new boolean[]{false,true})for(int step:new int[]{-1,1}){var other=at.offset(axis(getCachedState(),v),step);if(!seen.contains(other)&&joins(other)){seen.add(other);todo.add(other);}}}}
    public int glyph(){return signMode?(value>=0&&(value&1)!=0?16:-1):value;}
    public void sync(){markDirty();if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}
    @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.writeNbt(n,r);n.putInt("display_setting",setting);n.putBoolean("display_sign",signMode);}
    @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.readNbt(n,r);setting=Math.clamp(n.getInt("display_setting"),0,9);signMode=n.getBoolean("display_sign");value=n.contains("display_value")?n.getInt("display_value"):-1;pixelBits=n.getLong("display_pixels");}
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup r){var n=createNbt(r);n.putInt("display_value",value);n.putLong("display_pixels",pixelBits);return n;}
}
