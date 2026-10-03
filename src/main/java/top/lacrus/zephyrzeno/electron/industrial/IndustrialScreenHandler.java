package top.lacrus.zephyrzeno.electron.industrial;

import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import top.lacrus.zephyrzeno.electron.foundry.ElectricalEffects;

public final class IndustrialScreenHandler extends ScreenHandler {
    private final Inventory inventory;private final ScreenHandlerContext context;
    public IndustrialScreenHandler(int sync,PlayerInventory player){this(sync,player,new SimpleInventory(28),ScreenHandlerContext.EMPTY);}
    public IndustrialScreenHandler(int sync,PlayerInventory player,Inventory inv,ScreenHandlerContext ctx){
        super(Industrial.SCREEN,sync);inventory=inv;context=ctx;checkSize(inv,28);inv.onOpen(player.player);
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(inv,x+y*9,8+x*18,28+y*18));
        addSlot(new Slot(inv,27,152,94){@Override public boolean canInsert(ItemStack stack){return false;}});
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(player,9+x+y*9,8+x*18,139+y*18));
        for(int x=0;x<9;x++)addSlot(new Slot(player,x,8+x*18,197));
    }
    @Override public boolean canUse(PlayerEntity player){return inventory.canPlayerUse(player);}
    @Override public boolean onButtonClick(PlayerEntity player,int id){
        if(!canUse(player)||player.getWorld().isClient)return false;
        boolean made=AssemblyRecipes.craft(inventory,id);
        if(made){sendContentUpdates();context.run((w,p)->{if(w instanceof ServerWorld server)ElectricalEffects.play(server,p,ElectricalEffects.ASSEMBLE,.65f,1);});}
        else player.sendMessage(Text.translatable("message.electron.assembly_materials"),true);return made;
    }
    @Override public ItemStack quickMove(PlayerEntity player,int index){
        var slot=slots.get(index);if(!slot.hasStack())return ItemStack.EMPTY;var stack=slot.getStack();var copy=stack.copy();
        if(index<28){if(!insertItem(stack,28,64,true))return ItemStack.EMPTY;}else if(!insertItem(stack,0,27,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();return copy;
    }
    @Override public void onClosed(PlayerEntity player){super.onClosed(player);inventory.onClose(player);}
}
