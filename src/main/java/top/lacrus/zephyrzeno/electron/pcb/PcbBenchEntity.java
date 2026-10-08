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
    private final int[] reserved=new int[Pcb.INPUT_SLOTS];
    public PcbLayout draft=PcbLayout.empty();
    public boolean working;
    public double work;
    public int revision,error;
    public PcbBenchEntity(BlockPos p,BlockState s){super(p,s);}
    public boolean finishing(){return ((PcbBlock)getCachedState().getBlock()).role==PcbBlock.Role.ASSEMBLY;}
    public Item inputItem(int i){return i==0?finishing()?PcbFabrication.GOLD:PcbFabrication.FILM:i==2?net.minecraft.item.Items.BLACK_DYE:Pcb.MATERIALS.get(i);}
    public int[] costs(){if(finishing())return Pcb.costs(draft);int[] out=new int[Pcb.INPUT_SLOTS];out[0]=out[2]=draft.laminateCost();return out;}
    public boolean materials(){if(finishing()&&reserved[0]==0&&!Pcb.layout(items.get(0)).equals(draft))return false;int[] costs=costs();for(int i=0;i<Pcb.INPUT_SLOTS;i++)if(reserved[i]+(items.get(i).isOf(inputItem(i))?items.get(i).getCount():0)<costs[i])return false;return true;}
    private boolean feedstock(){int[] costs=costs();for(int i=0;i<Pcb.INPUT_SLOTS;i++)if(reserved[i]<costs[i]&&items.get(i).isOf(inputItem(i))&&!items.get(i).isEmpty())return true;return false;}
    public boolean ready(){return working&&!draft.parts().isEmpty()&&!draft.supplyShort()&&(materials()||feedstock())&&items.get(Pcb.OUTPUT_SLOT).isEmpty();}
    public void releaseMaterials(){for(int i=0;i<reserved.length;i++){int count=reserved[i];reserved[i]=0;while(count>0){int n=Math.min(count,64);var refund=finishing()&&i==0?Pcb.designed(PcbFabrication.GOLD,draft):new ItemStack(inputItem(i));refund.setCount(n);var old=items.get(i);if(old.isEmpty()){items.set(i,refund);count-=n;}else if(ItemStack.areItemsAndComponentsEqual(old,refund)&&old.getCount()<old.getMaxCount()){int moved=Math.min(n,old.getMaxCount()-old.getCount());old.increment(moved);count-=moved;}else{if(world!=null)net.minecraft.util.ItemScatterer.spawn(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,refund);count-=n;}}}working=false;work=0;markDirty();}
    public int process(boolean broken){
        if(!ready()||broken||paused||voltage<18||voltage>30||current<=0)return 0;
        int[] required=costs();for(int i=0;i<Pcb.INPUT_SLOTS;i++)if(items.get(i).isOf(inputItem(i))){int paid=Math.min(items.get(i).getCount(),Math.max(0,required[i]-reserved[i]));items.get(i).decrement(paid);reserved[i]+=paid;}
        if(!materials()){markDirty();return 0;}
        work=Math.min(draft.joules(),work+voltage*current*.05);
        if(work+1e-7>=draft.joules()){
            boolean finish=finishing();java.util.Arrays.fill(reserved,0);items.set(Pcb.OUTPUT_SLOT,Pcb.designed(finish?Pcb.BOARD:PcbFabrication.MASK,draft));working=false;work=0;
            ElectricalEffects.play((net.minecraft.server.world.ServerWorld)world,pos,ElectricalEffects.ASSEMBLE,.35f,1.25f);
        }
        markDirty();return 15;
    }
    public int status(){return getCachedState().get(ComponentBlock.BROKEN)?5:!items.get(Pcb.OUTPUT_SLOT).isEmpty()?4:!working?0:!materials()?3:paused||voltage<18||voltage>30?1:2;}
    private void changed(){revision=(revision+1)&32767;work=0;markDirty();}
    private static void remember(java.util.ArrayDeque<PcbLayout> history,PcbLayout layout){if(history.size()==32)history.removeFirst();history.addLast(layout);}
    public void edit(int op,int a,int b,int c,int d){
        error=0;
        if(op==6){releaseMaterials();return;}
        if(working){error=1;return;}
        if(op==5){if(finishing()){draft=Pcb.layout(items.get(0));changed();}if(draft.parts().isEmpty()||draft.supplyShort()){error=2;return;}if((!finishing()&&!materials())||(finishing()&&!items.get(0).isOf(PcbFabrication.GOLD))||!items.get(Pcb.OUTPUT_SLOT).isEmpty()){error=3;return;}working=true;work=0;markDirty();return;}
        if(finishing()&&op!=7){error=5;return;}
        try{
            if(op==11||op==12){var from=op==11?undo:redo;var to=op==11?redo:undo;if(!from.isEmpty()){remember(to,draft);draft=from.removeLast();changed();}return;}
            var next=switch(op){case 0->draft.place(PcbLayout.Type.values()[a],b,c,d);case 1->draft.route(a,b,c!=0);case 2->draft.erase(a,b,c);case 3->PcbLayout.empty();case 4->a==4?PcbExamples.counter():a==3?PcbExamples.pixel():a==2?SingleBoardCalculator.layout():a==1?PcbLayout.halfAdder():PcbLayout.inverter();case 7->{var reference=items.get(finishing()?0:1);if(!PcbFabrication.carriesLayout(reference))throw new IllegalArgumentException();yield Pcb.layout(reference);}case 8->draft.resize(a,b);case 9->draft.cut(a,b);case 10->draft.move(a,b,c,d);case 13->{var p=draft.parts().get(a);yield draft.move(a,p.x(),p.y(),(p.turn()+1)&3);}case 14->draft.removePart(a);case 15->draft.withLayers(a);case 16->draft.via(a,b);case 17->draft.port(a,b,c);
                case 19->PcbSelection.move(draft,PcbSelection.area(a,b),c,d);
                case 20->PcbSelection.copy(draft,PcbSelection.area(a,b),c,d);
                case 21->PcbSelection.delete(draft,PcbSelection.area(a,b));
                default->throw new IllegalArgumentException();};
            if(!next.equals(draft)){remember(undo,draft);redo.clear();draft=next;changed();}
        }
        catch(IllegalArgumentException|IndexOutOfBoundsException e){error=4;}
    }
    public PropertyDelegate properties(){return new PropertyDelegate(){public int size(){return 201;}public void set(int i,int v){}public int get(int i){
        if(i==0)return (int)(1000*work/draft.joules());if(i==1)return status();if(i==2)return revision;if(i==3)return error;
        if(i>=8&&i<8+reserved.length)return reserved[i-8];if(i==196)return draft.width();if(i==197)return draft.height();
        if(i==198)return finishing()?1:0;if(i==199)return undo.size();if(i==200)return redo.size();
        return i==195?draft.layers():0;
    }};}
    public int size(){return Pcb.INVENTORY_SIZE;}public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    public ItemStack getStack(int i){return items.get(i);}public void setStack(int i,ItemStack s){items.set(i,s);s.capCount(s.getMaxCount());if(i==0&&finishing()&&!working){draft=Pcb.layout(s);changed();}markDirty();}
    public ItemStack removeStack(int i,int amount){var s=Inventories.splitStack(items,i,amount);markDirty();return s;}public ItemStack removeStack(int i){var s=Inventories.removeStack(items,i);markDirty();return s;}
    public void clear(){items.clear();markDirty();}public boolean canPlayerUse(PlayerEntity p){return !isRemoved()&&world!=null&&world.getBlockEntity(pos)==this&&p.squaredDistanceTo(pos.toCenterPos())<=64;}
    public boolean isValid(int i,ItemStack s){if(i<0||i>=Pcb.INPUT_SLOTS||working&&(!finishing()||reserved[i]>=costs()[i]))return false;if(!finishing()&&i==1)return PcbFabrication.carriesLayout(s);return (finishing()?i!=2:i==0||i==2)&&s.isOf(inputItem(i));}
    public int[] getAvailableSlots(Direction face){return face==Direction.DOWN?new int[]{Pcb.OUTPUT_SLOT}:java.util.stream.IntStream.range(0,Pcb.INPUT_SLOTS).toArray();}
    public boolean canInsert(int i,ItemStack s,Direction face){return face!=Direction.DOWN&&isValid(i,s);}public boolean canExtract(int i,ItemStack s,Direction face){return i==Pcb.OUTPUT_SLOT;}
    public Text getDisplayName(){return getCachedState().getBlock().getName();}
    public ScreenHandler createMenu(int sync,PlayerInventory inv,PlayerEntity p){return new PcbScreenHandler(sync,inv,this,properties());}
    @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.writeNbt(n,r);var inv=new NbtCompound();Inventories.writeNbt(inv,items,r);n.put("pcb_items",inv);n.putInt("pcb_inventory_version",4);n.putIntArray("pcb_reserved",reserved.clone());n.putString("pcb_draft",draft.encode());n.putBoolean("pcb_working",working);n.putDouble("pcb_work",work);}
    @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){
        super.readNbt(n,r);items.clear();Inventories.readNbt(n.getCompound("pcb_items"),items,r);
        int version=n.getInt("pcb_inventory_version");boolean legacy=version<2;
        // v3's output now occupies the first new package slot. Never consume it as a TFF.
        int oldOutput=legacy?11:version==2?13:version==3?25:Pcb.OUTPUT_SLOT;
        if(oldOutput!=Pcb.OUTPUT_SLOT){items.set(Pcb.OUTPUT_SLOT,items.get(oldOutput));items.set(oldOutput,ItemStack.EMPTY);}
        try{draft=PcbLayout.decode(n.getString("pcb_draft"));}catch(IllegalArgumentException e){draft=PcbLayout.empty();}
        var paid=n.getIntArray("pcb_reserved");java.util.Arrays.fill(reserved,0);int[] required=costs();
        for(int i=0;i<Math.min(paid.length,Math.min(oldOutput,reserved.length));i++)reserved[i]=Math.clamp(paid[i],0,required[i]);
        working=!legacy&&n.getBoolean("pcb_working");double saved=n.getDouble("pcb_work");work=Double.isFinite(saved)?Math.clamp(saved,0,draft.joules()):0;undo.clear();redo.clear();
    }
}
