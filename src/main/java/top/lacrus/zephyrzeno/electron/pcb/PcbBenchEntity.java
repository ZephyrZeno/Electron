package top.lacrus.zephyrzeno.electron.pcb;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.nbt.*;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.*;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;

public final class PcbBenchEntity extends ComponentEntity implements SidedInventory,NamedScreenHandlerFactory {
    private final DefaultedList<ItemStack> items=DefaultedList.ofSize(Pcb.INVENTORY_SIZE,ItemStack.EMPTY);
    private final java.util.ArrayDeque<PcbLayout> undo=new java.util.ArrayDeque<>(),redo=new java.util.ArrayDeque<>();
    public PcbLayout draft=PcbLayout.empty();
    public boolean working;
    public double work;
    public int revision,error;
    private PcbLayout projectedLayout;
    private final int[] projected=new int[192];
    private void project(){
        if(projectedLayout==draft)return;
        java.util.Arrays.fill(projected,0);
        for(int p:draft.cutouts())projected[p]|=256;
        for(var t:draft.tracks())projected[t.a()]|=t.b()==t.a()+1?1:2;
        for(var p:draft.parts())projected[PcbLayout.point(p.x(),p.y())]|=(p.type().ordinal()+1)<<2|p.turn()<<6;
        projectedLayout=draft;
    }
    public PcbBenchEntity(BlockPos p,BlockState s){super(p,s);}
    public boolean finishing(){return ((PcbBlock)getCachedState().getBlock()).role==PcbBlock.Role.ASSEMBLY;}
    public Item inputItem(int i){return i==0?finishing()?PcbFabrication.GOLD:PcbFabrication.FILM:i==2?net.minecraft.item.Items.BLACK_DYE:Pcb.MATERIALS.get(i);}
    public int[] costs(){if(finishing())return Pcb.costs(draft);int[] out=new int[Pcb.INPUT_SLOTS];out[0]=out[2]=1;return out;}
    public boolean materials(){if(finishing()&&!Pcb.layout(items.get(0)).equals(draft))return false;int[] costs=costs();for(int i=0;i<Pcb.INPUT_SLOTS;i++)if((!items.get(i).isOf(inputItem(i))&&costs[i]>0)||items.get(i).getCount()<costs[i])return false;return true;}
    public boolean ready(){return working&&!draft.parts().isEmpty()&&!draft.supplyShort()&&materials()&&items.get(Pcb.OUTPUT_SLOT).isEmpty();}
    public int process(boolean broken){
        if(!ready()||broken||paused||voltage<18||voltage>30||current<=0)return 0;
        work=Math.min(draft.joules(),work+voltage*current*.05);
        if(work+1e-7>=draft.joules()){
            boolean finish=finishing();int[] costs=costs();for(int i=0;i<Pcb.INPUT_SLOTS;i++)items.get(i).decrement(costs[i]);items.set(Pcb.OUTPUT_SLOT,Pcb.designed(finish?Pcb.BOARD:PcbFabrication.MASK,draft));working=false;work=0;
            ElectricalEffects.play((net.minecraft.server.world.ServerWorld)world,pos,ElectricalEffects.ASSEMBLE,.35f,1.25f);
        }
        markDirty();return 15;
    }
    public int status(){return getCachedState().get(ComponentBlock.BROKEN)?5:!items.get(Pcb.OUTPUT_SLOT).isEmpty()?4:!working?0:!materials()?3:paused||voltage<18||voltage>30?1:2;}
    private void changed(){revision=(revision+1)&32767;work=0;markDirty();}
    private static void remember(java.util.ArrayDeque<PcbLayout> history,PcbLayout layout){if(history.size()==32)history.removeFirst();history.addLast(layout);}
    public void edit(int op,int a,int b,int c,int d){
        error=0;
        if(op==6){working=false;work=0;markDirty();return;}
        if(working){error=1;return;}
        if(op==5){if(finishing()){draft=Pcb.layout(items.get(0));changed();}if(draft.parts().isEmpty()||draft.supplyShort()){error=2;return;}if(!materials()||!items.get(Pcb.OUTPUT_SLOT).isEmpty()){error=3;return;}working=true;work=0;markDirty();return;}
        if(finishing()&&op!=7){error=5;return;}
        try{
            if(op==11||op==12){var from=op==11?undo:redo;var to=op==11?redo:undo;if(!from.isEmpty()){remember(to,draft);draft=from.removeLast();changed();}return;}
            var next=switch(op){case 0->draft.place(PcbLayout.Type.values()[a],b,c,d);case 1->draft.route(a,b,c!=0);case 2->{if(a<0||a>=16||b<0||b>=12)throw new IllegalArgumentException();yield draft.erase(a,b);}case 3->PcbLayout.empty();case 4->a==1?PcbLayout.halfAdder():PcbLayout.inverter();case 7->{var reference=items.get(finishing()?0:1);if(!PcbFabrication.carriesLayout(reference))throw new IllegalArgumentException();yield Pcb.layout(reference);}case 8->draft.resize(a,b);case 9->draft.cut(a,b);case 10->draft.move(a,b,c,d);case 13->{var p=draft.parts().get(a);yield draft.move(a,p.x(),p.y(),(p.turn()+1)&3);}case 14->draft.removePart(a);default->throw new IllegalArgumentException();};
            if(!next.equals(draft)){remember(undo,draft);redo.clear();draft=next;changed();}
        }
        catch(IllegalArgumentException|IndexOutOfBoundsException e){error=4;}
    }
    public PropertyDelegate properties(){return new PropertyDelegate(){public int size(){return 201;}public void set(int i,int v){}public int get(int i){
        if(i==0)return (int)(1000*work/draft.joules());if(i==1)return status();if(i==2)return revision;if(i==3)return error;
        if(i==196)return draft.width();if(i==197)return draft.height();
        if(i==198)return finishing()?1:0;if(i==199)return undo.size();if(i==200)return redo.size();
        project();return projected[i-4];
    }};}
    public int size(){return Pcb.INVENTORY_SIZE;}public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    public ItemStack getStack(int i){return items.get(i);}public void setStack(int i,ItemStack s){items.set(i,s);s.capCount(s.getMaxCount());if(i==0&&finishing()&&!working){draft=Pcb.layout(s);changed();}markDirty();}
    public ItemStack removeStack(int i,int amount){var s=Inventories.splitStack(items,i,amount);markDirty();return s;}public ItemStack removeStack(int i){var s=Inventories.removeStack(items,i);markDirty();return s;}
    public void clear(){items.clear();markDirty();}public boolean canPlayerUse(PlayerEntity p){return !isRemoved()&&world!=null&&world.getBlockEntity(pos)==this&&p.squaredDistanceTo(pos.toCenterPos())<=64;}
    public boolean isValid(int i,ItemStack s){if(working||i<0||i>=Pcb.INPUT_SLOTS)return false;if(!finishing()&&i==1)return PcbFabrication.carriesLayout(s);return (finishing()?i!=2:i==0||i==2)&&s.isOf(inputItem(i));}
    public int[] getAvailableSlots(Direction face){return face==Direction.DOWN?new int[]{Pcb.OUTPUT_SLOT}:java.util.stream.IntStream.range(0,Pcb.INPUT_SLOTS).toArray();}
    public boolean canInsert(int i,ItemStack s,Direction face){return face!=Direction.DOWN&&isValid(i,s);}public boolean canExtract(int i,ItemStack s,Direction face){return i==Pcb.OUTPUT_SLOT;}
    public Text getDisplayName(){return getCachedState().getBlock().getName();}
    public ScreenHandler createMenu(int sync,PlayerInventory inv,PlayerEntity p){return new PcbScreenHandler(sync,inv,this,properties());}
    @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.writeNbt(n,r);var inv=new NbtCompound();Inventories.writeNbt(inv,items,r);n.put("pcb_items",inv);n.putInt("pcb_inventory_version",2);n.putString("pcb_draft",draft.encode());n.putBoolean("pcb_working",working);n.putDouble("pcb_work",work);}
    @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.readNbt(n,r);items.clear();Inventories.readNbt(n.getCompound("pcb_items"),items,r);boolean legacy=n.getInt("pcb_inventory_version")<2;if(legacy){items.set(Pcb.OUTPUT_SLOT,items.get(11));items.set(11,ItemStack.EMPTY);}try{draft=PcbLayout.decode(n.getString("pcb_draft"));}catch(IllegalArgumentException e){draft=PcbLayout.empty();}working=!legacy&&n.getBoolean("pcb_working");double saved=n.getDouble("pcb_work");work=Double.isFinite(saved)?Math.clamp(saved,0,draft.joules()):0;undo.clear();redo.clear();}
}
