package top.lacrus.zephyrzeno.electron.industrial;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

public final class FluidEntity extends BlockEntity {
    public int amount;
    public long flowingUntil;
    private BlockPos plantController;
    public FluidEntity(BlockPos p,BlockState s){super(Industrial.FLUID_ENTITY,p,s);}
    public int capacity(){return ((FluidBlock)getCachedState().getBlock()).capacity();}
    public void bind(BlockPos core){if(!core.equals(plantController)){plantController=core.toImmutable();markDirty();}}
    public top.lacrus.zephyrzeno.electron.foundry.ComponentEntity plant(){
        if(world==null||plantController==null||!world.isChunkLoaded(plantController)||!top.lacrus.zephyrzeno.electron.multiblock.Assembly.formed(world.getBlockState(plantController)))return null;
        if(!(world.getBlockEntity(plantController) instanceof top.lacrus.zephyrzeno.electron.foundry.ComponentEntity e))return null;
        var s=e.getCachedState();return pos.equals(PlantStructures.tankPos(plantController,s))||pos.equals(PlantStructures.waterPos(plantController,s))?e:null;
    }
    public FluidEntity inletTank(){
        var core=plant();if(core==null||!getCachedState().isOf(Industrial.WATER_INLET))return null;
        var p=PlantStructures.tankPos(core.getPos(),core.getCachedState());return world.isChunkLoaded(p)&&world.getBlockEntity(p) instanceof FluidEntity tank&&tank.plant()==core?tank:null;
    }
    public void change(int delta){int next=Math.clamp(amount+delta,0,capacity());if(next==amount)return;amount=next;flowingUntil=world==null?0:world.getTime()+12;markDirty();if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}
    public void flow(){if(world!=null&&flowingUntil-world.getTime()<6){flowingUntil=world.getTime()+12;world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}}
    @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.writeNbt(n,r);n.putInt("water_mb",amount);n.putLong("flowing_until",flowingUntil);if(plantController!=null)n.putLong("plant_controller",plantController.asLong());}
    @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.readNbt(n,r);amount=Math.clamp(n.getInt("water_mb"),0,capacity());flowingUntil=n.getLong("flowing_until");plantController=n.contains("plant_controller")?BlockPos.fromLong(n.getLong("plant_controller")):null;}
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup r){return createNbt(r);}
    @Override public net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket toUpdatePacket(){return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);}
}
