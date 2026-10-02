package top.lacrus.zephyrzeno.electron.mechanical;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import top.lacrus.zephyrzeno.electron.foundry.*;

public final class MechanicalEntity extends BlockEntity {
    public ItemStack mounted=ItemStack.EMPTY;
    public double speed,angle,previousAngle;
    public MechanicalEntity(BlockPos p,BlockState s){super(Foundry.MECHANICAL_ENTITY,p,s);}
    public static void tick(World w,BlockPos p,BlockState s,MechanicalEntity e){
        e.previousAngle=e.angle;
        if(!w.isClient){
            double next=MechanicalNetwork.speed(w,p,s.get(MechanicalBlock.FACING).getAxis())*s.get(MechanicalBlock.FACING).getDirection().offset();
            if(Math.abs(next-e.speed)>.05){e.speed=next;e.sync();}
        }
        e.angle+=Math.clamp(e.speed*.05,-.8,.8);
        if(Math.abs(e.angle)>10000){e.angle=0;e.previousAngle=0;}
    }
    public void sync(){markDirty();if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),3);}
    @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.writeNbt(n,r);if(!mounted.isEmpty())n.put("mounted",mounted.encode(r));n.putDouble("speed",speed);}
    @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.readNbt(n,r);mounted=n.contains("mounted",10)?ItemStack.fromNbt(r,n.getCompound("mounted")).orElse(ItemStack.EMPTY):ItemStack.EMPTY;double v=n.getDouble("speed");speed=Double.isFinite(v)?Math.clamp(v,-600,600):0;}
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup r){return createNbt(r);}
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
