package top.lacrus.zephyrzeno.electron.industrial;

import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import top.lacrus.zephyrzeno.electron.foundry.ComponentBlock;

public final class PlantScreenHandler extends ScreenHandler {
    private final Inventory inventory;private final PropertyDelegate properties;
    public PlantScreenHandler(int sync,PlayerInventory player){this(sync,player,new SimpleInventory(4),new ArrayPropertyDelegate(6));}
    public PlantScreenHandler(int sync,PlayerInventory player,Inventory inv,PropertyDelegate data){
        super(Industrial.PLANT_SCREEN,sync);inventory=inv;properties=data;checkSize(inv,4);addProperties(data);
        for(int i=0;i<4;i++)addSlot(new Slot(inv,i,20+i*44,45){
            @Override public boolean canInsert(ItemStack s){return false;}
            @Override public boolean canTakeItems(PlayerEntity p){return false;}
        });
        for(int y=0;y<3;y++)for(int x=0;x<9;x++)addSlot(new Slot(player,9+x+y*9,8+x*18,130+y*18));
        for(int x=0;x<9;x++)addSlot(new Slot(player,x,8+x*18,188));
    }
    public int value(int i){return properties.get(i);}
    @Override public boolean canUse(PlayerEntity p){return inventory.canPlayerUse(p);}
    @Override public boolean onButtonClick(PlayerEntity p,int id){
        if(id!=0||!canUse(p)||p.getWorld().isClient||!(inventory instanceof PlantServiceEntity service))return false;
        var core=service.core();if(core==null||core.getCachedState().get(ComponentBlock.BROKEN))return false;
        p.getWorld().setBlockState(core.getPos(),core.getCachedState().cycle(ComponentBlock.ENABLED));sendContentUpdates();return true;
    }
    @Override public ItemStack quickMove(PlayerEntity p,int index){
        return ItemStack.EMPTY;
    }
}
