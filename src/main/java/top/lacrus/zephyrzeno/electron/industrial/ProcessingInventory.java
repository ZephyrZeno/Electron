package top.lacrus.zephyrzeno.electron.industrial;

import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import top.lacrus.zephyrzeno.electron.foundry.*;

public final class ProcessingInventory implements SidedInventory,NamedScreenHandlerFactory {
    public final ComponentEntity core;
    public ProcessingInventory(ComponentEntity e){core=e;}
    @Override public int size(){return 4;}
    @Override public boolean isEmpty(){return core.plantItems.stream().allMatch(ItemStack::isEmpty);}
    @Override public ItemStack getStack(int slot){return core.plantItems.get(slot);}
    @Override public void setStack(int slot,ItemStack stack){core.plantItems.set(slot,stack);stack.capCount(stack.getMaxCount());markDirty();}
    @Override public ItemStack removeStack(int slot,int count){var s=Inventories.splitStack(core.plantItems,slot,count);markDirty();return s;}
    @Override public ItemStack removeStack(int slot){var s=Inventories.removeStack(core.plantItems,slot);markDirty();return s;}
    @Override public void clear(){core.plantItems.clear();markDirty();}
    @Override public void markDirty(){core.markDirty();}
    @Override public boolean canPlayerUse(PlayerEntity p){return !core.isRemoved()&&p.squaredDistanceTo(core.getPos().toCenterPos())<=64;}
    @Override public boolean isValid(int slot,ItemStack s){return Processing.accepts((ProcessingBlock)core.getCachedState().getBlock(),slot,s);}
    @Override public int[] getAvailableSlots(Direction face){return face==Direction.DOWN?new int[]{2,3}:new int[]{0,1};}
    @Override public boolean canInsert(int slot,ItemStack s,Direction face){return face!=Direction.DOWN&&isValid(slot,s);}
    @Override public boolean canExtract(int slot,ItemStack s,Direction face){return slot>=2;}
    @Override public Text getDisplayName(){return core.getCachedState().getBlock().getName();}
    @Override public ScreenHandler createMenu(int sync,PlayerInventory inv,PlayerEntity p){return new ProcessingScreenHandler(sync,inv,this,properties());}
    private PropertyDelegate properties(){return new PropertyDelegate(){
        public int size(){return 3;}public void set(int i,int value){}
        public int get(int i){var r=Processing.recipe(core);return switch(i){case 0->r==null?0:(int)(1000*core.processingEnergy/r.joules());case 1->core.getCachedState().get(ComponentBlock.ENABLED)?1:0;default->core.getCachedState().get(ComponentBlock.BROKEN)?4:r==null?0:!Processing.outputRoom(core,r)?3:core.getCachedState().get(ComponentBlock.LEVEL)>0?2:1;};}
    };}
}
