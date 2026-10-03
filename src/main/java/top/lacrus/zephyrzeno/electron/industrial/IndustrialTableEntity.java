package top.lacrus.zephyrzeno.electron.industrial;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.*;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

public final class IndustrialTableEntity extends BlockEntity implements Inventory,NamedScreenHandlerFactory {
    private final DefaultedList<ItemStack> stacks=DefaultedList.ofSize(28,ItemStack.EMPTY);
    public IndustrialTableEntity(BlockPos p,BlockState s){super(Industrial.TABLE_ENTITY,p,s);}
    @Override public int size(){return stacks.size();}
    @Override public boolean isEmpty(){return stacks.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getStack(int slot){return stacks.get(slot);}
    @Override public ItemStack removeStack(int slot,int count){var out=Inventories.splitStack(stacks,slot,count);if(!out.isEmpty())markDirty();return out;}
    @Override public ItemStack removeStack(int slot){var out=Inventories.removeStack(stacks,slot);markDirty();return out;}
    @Override public void setStack(int slot,ItemStack stack){stacks.set(slot,stack);stack.capCount(getMaxCountPerStack());markDirty();}
    @Override public boolean canPlayerUse(PlayerEntity p){return world!=null&&world.getBlockEntity(pos)==this&&IndustrialTableBlock.complete(world,pos,getCachedState())&&p.squaredDistanceTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    @Override public void clear(){stacks.clear();markDirty();}
    @Override public Text getDisplayName(){return Text.translatable("block.electron.industrial_assembly_table");}
    @Override public ScreenHandler createMenu(int sync,PlayerInventory inv,PlayerEntity player){return new IndustrialScreenHandler(sync,inv,this,ScreenHandlerContext.create(world,pos));}
    @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.writeNbt(n,r);Inventories.writeNbt(n,stacks,r);}
    @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.readNbt(n,r);Inventories.readNbt(n,stacks,r);}
}
