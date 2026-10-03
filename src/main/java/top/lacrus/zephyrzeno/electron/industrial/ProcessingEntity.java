package top.lacrus.zephyrzeno.electron.industrial;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.ComponentEntity;

/** Native hopper inventory only on processing blocks; ordinary circuit parts remain non-containers. */
public final class ProcessingEntity extends ComponentEntity implements SidedInventory {
    private final ProcessingInventory inventory=new ProcessingInventory(this);
    public ProcessingEntity(BlockPos p,BlockState s){super(p,s);}
    public int size(){return inventory.size();}public boolean isEmpty(){return inventory.isEmpty();}
    public ItemStack getStack(int slot){return inventory.getStack(slot);}public void setStack(int slot,ItemStack stack){inventory.setStack(slot,stack);}
    public ItemStack removeStack(int slot,int n){return inventory.removeStack(slot,n);}public ItemStack removeStack(int slot){return inventory.removeStack(slot);}
    public void clear(){inventory.clear();}public boolean canPlayerUse(PlayerEntity p){return inventory.canPlayerUse(p);}
    public boolean isValid(int slot,ItemStack stack){return inventory.isValid(slot,stack);}
    public int[] getAvailableSlots(Direction face){return inventory.getAvailableSlots(face);}
    public boolean canInsert(int slot,ItemStack stack,Direction face){return inventory.canInsert(slot,stack,face);}
    public boolean canExtract(int slot,ItemStack stack,Direction face){return inventory.canExtract(slot,stack,face);}
}
