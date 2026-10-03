package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;

public final class PcbScreenHandler extends ScreenHandler {
    private final Inventory inventory;
    private final PropertyDelegate data;
    private final int[] layoutData=new int[194];
    private PcbLayout cachedLayout;
    public PcbScreenHandler(int sync,PlayerInventory inv){this(sync,inv,new SimpleInventory(12),new ArrayPropertyDelegate(198));}
    public PcbScreenHandler(int sync,PlayerInventory inv,Inventory machine,PropertyDelegate properties){
        super(Pcb.SCREEN,sync);inventory=machine;data=properties;addProperties(data);
        for(int i=0;i<12;i++){int n=i;addSlot(new Slot(machine,i,i==11?316:244+(i%3)*28,i==11?211:66+(i/3)*29){public boolean canInsert(ItemStack s){return n<11&&(n==0?s.isOf(PcbFabrication.FILM)||s.isOf(PcbFabrication.GOLD):s.isOf(n==2?net.minecraft.item.Items.BLACK_DYE:Pcb.MATERIALS.get(n)));}public boolean canTakeItems(PlayerEntity p){return n==11||!(machine instanceof PcbBenchEntity e)||!e.working;}});}
        for(int row=0;row<3;row++)for(int x=0;x<9;x++)addSlot(new Slot(inv,9+x+row*9,97+x*18,249+row*18));for(int x=0;x<9;x++)addSlot(new Slot(inv,x,97+x*18,307));
    }
    public int value(int i){return data.get(i);}
    public PcbLayout layout(){
        boolean changed=cachedLayout==null;
        for(int i=0;i<layoutData.length;i++){int value=data.get(i+4);if(layoutData[i]!=value){layoutData[i]=value;changed=true;}}
        if(!changed)return cachedLayout;
        var parts=new ArrayList<PcbLayout.Part>();var tracks=new ArrayList<PcbLayout.Track>();var cut=new HashSet<Integer>();
        try{for(int i=0;i<192;i++){int v=layoutData[i];if((v&256)!=0)cut.add(i);if((v&1)!=0)tracks.add(new PcbLayout.Track(i,i+1));if((v&2)!=0)tracks.add(new PcbLayout.Track(i,i+16));int type=(v>>2)&15;if(type>0)parts.add(new PcbLayout.Part(PcbLayout.Type.values()[type-1],PcbLayout.x(i),PcbLayout.y(i),(v>>6)&3));}cachedLayout=new PcbLayout(parts,tracks,layoutData[192],layoutData[193],cut);}
        catch(IllegalArgumentException|IndexOutOfBoundsException e){cachedLayout=PcbLayout.empty();}
        return cachedLayout;
    }
    public void action(PlayerEntity p,PcbNetworking.Action a){if(canUse(p)&&inventory instanceof PcbBenchEntity e&&a.revision()==e.revision){e.edit(a.op(),a.a(),a.b(),a.c(),a.d());sendContentUpdates();}}
    @Override public boolean canUse(PlayerEntity p){return inventory.canPlayerUse(p);}
    @Override public ItemStack quickMove(PlayerEntity p,int index){if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasStack()||!slot.canTakeItems(p))return ItemStack.EMPTY;var s=slot.getStack();var copy=s.copy();if(index<12){if(!insertItem(s,12,48,true))return ItemStack.EMPTY;}else if(!insertItem(s,0,11,false))return ItemStack.EMPTY;if(s.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();return copy;}
}
