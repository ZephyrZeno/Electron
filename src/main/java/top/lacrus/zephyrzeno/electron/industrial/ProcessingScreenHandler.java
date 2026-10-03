package top.lacrus.zephyrzeno.electron.industrial;

import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import top.lacrus.zephyrzeno.electron.foundry.ComponentBlock;

public final class ProcessingScreenHandler extends ScreenHandler {
    private final Inventory inventory;private final PropertyDelegate properties;
    public ProcessingScreenHandler(int sync,PlayerInventory inv){this(sync,inv,new SimpleInventory(4),new ArrayPropertyDelegate(3));}
    public ProcessingScreenHandler(int sync,PlayerInventory inv,Inventory machine,PropertyDelegate data){
        super(Processing.SCREEN,sync);inventory=machine;properties=data;addProperties(data);
        for(int i=0;i<4;i++){final int slot=i;addSlot(new Slot(machine,i,20+i*44,42){public boolean canInsert(ItemStack s){return slot<2&&machine.isValid(slot,s);}});}
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inv,9+x+y*9,8+x*18,125+y*18));for(int x=0;x<9;x++)addSlot(new Slot(inv,x,8+x*18,183));
    }
    public int value(int i){return properties.get(i);}
    @Override public boolean canUse(PlayerEntity p){return inventory.canPlayerUse(p);}
    @Override public boolean onButtonClick(PlayerEntity p,int id){if(id!=0||!canUse(p)||!(inventory instanceof ProcessingInventory inv)||p.getWorld().isClient)return false;var e=inv.core;p.getWorld().setBlockState(e.getPos(),e.getCachedState().cycle(ComponentBlock.ENABLED));return true;}
    @Override public ItemStack quickMove(PlayerEntity p,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasStack())return ItemStack.EMPTY;var s=slot.getStack();var result=s.copy();if(index<4){if(!insertItem(s,4,40,true))return ItemStack.EMPTY;}else if(!insertItem(s,0,2,false))return ItemStack.EMPTY;if(s.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();return result;}
}
