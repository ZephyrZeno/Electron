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
    private final DefaultedList<ItemStack> items=DefaultedList.ofSize(12,ItemStack.EMPTY);
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
    public boolean finishing(){return items.get(0).isOf(PcbFabrication.GOLD);}
    public Item inputItem(int i){return i==0?finishing()?PcbFabrication.GOLD:PcbFabrication.FILM:i==2?net.minecraft.item.Items.BLACK_DYE:Pcb.MATERIALS.get(i);}
    public int[] costs(){if(finishing())return Pcb.costs(draft);int[] out=new int[11];out[0]=out[2]=1;return out;}
    public boolean materials(){if(finishing()&&!Pcb.layout(items.get(0)).equals(draft))return false;int[] costs=costs();for(int i=0;i<11;i++)if(!items.get(i).isOf(inputItem(i))&&costs[i]>0||items.get(i).getCount()<costs[i])return false;return true;}
    public boolean ready(){return working&&!draft.parts().isEmpty()&&!draft.supplyShort()&&materials()&&items.get(11).isEmpty();}
    public int process(boolean broken){
        if(!ready()||broken||paused||voltage<18||voltage>30||current<=0)return 0;
        work=Math.min(draft.joules(),work+voltage*current*.05);
        if(world.getTime()%60==0)ElectricalEffects.play((net.minecraft.server.world.ServerWorld)world,pos,ElectricalEffects.MOTOR,.1f,1.4f);
        if(work+1e-7>=draft.joules()){
            boolean finish=finishing();int[] costs=costs();for(int i=0;i<11;i++)items.get(i).decrement(costs[i]);items.set(11,Pcb.designed(finish?Pcb.BOARD:PcbFabrication.MASK,draft));working=false;work=0;
            ElectricalEffects.play((net.minecraft.server.world.ServerWorld)world,pos,ElectricalEffects.ASSEMBLE,.35f,1.25f);
        }
        markDirty();return 15;
    }
    public int status(){return getCachedState().get(ComponentBlock.BROKEN)?5:!items.get(11).isEmpty()?4:!working?0:!materials()?3:paused||voltage<18||voltage>30?1:2;}
    public void edit(int op,int a,int b,int c,int d){
        error=0;
        if(op==6){working=false;work=0;markDirty();return;}
        if(working){error=1;return;}
        if(op==5){if(draft.parts().isEmpty()||draft.supplyShort()){error=2;return;}if(!materials()||!items.get(11).isEmpty()){error=3;return;}working=true;work=0;markDirty();return;}
        try{draft=switch(op){case 0->draft.place(PcbLayout.Type.values()[a],b,c,d);case 1->draft.route(a,b,c!=0);case 2->{if(a<0||a>=16||b<0||b>=12)throw new IllegalArgumentException();yield draft.erase(a,b);}case 3->PcbLayout.empty();case 4->PcbLayout.inverter();case 7->Pcb.layout(items.get(0));case 8->draft.resize(a,b);case 9->draft.cut(a,b);case 10->draft.move(a,b,c,d);default->throw new IllegalArgumentException();};revision=(revision+1)&32767;work=0;markDirty();}
        catch(IllegalArgumentException|IndexOutOfBoundsException e){error=4;}
    }
    public PropertyDelegate properties(){return new PropertyDelegate(){public int size(){return 198;}public void set(int i,int v){}public int get(int i){
        if(i==0)return (int)(1000*work/draft.joules());if(i==1)return status();if(i==2)return revision;if(i==3)return error;
        if(i==196)return draft.width();if(i==197)return draft.height();
        project();return projected[i-4];
    }};}
    public int size(){return 12;}public boolean isEmpty(){return items.stream().allMatch(ItemStack::isEmpty);}
    public ItemStack getStack(int i){return items.get(i);}public void setStack(int i,ItemStack s){items.set(i,s);s.capCount(s.getMaxCount());markDirty();}
    public ItemStack removeStack(int i,int amount){var s=Inventories.splitStack(items,i,amount);markDirty();return s;}public ItemStack removeStack(int i){var s=Inventories.removeStack(items,i);markDirty();return s;}
    public void clear(){items.clear();markDirty();}public boolean canPlayerUse(PlayerEntity p){return !isRemoved()&&world!=null&&world.getBlockEntity(pos)==this&&p.squaredDistanceTo(pos.toCenterPos())<=64;}
    public boolean isValid(int i,ItemStack s){return i<11&&(i==0?s.isOf(PcbFabrication.FILM)||s.isOf(PcbFabrication.GOLD):s.isOf(inputItem(i)));}
    public int[] getAvailableSlots(Direction face){return face==Direction.DOWN?new int[]{11}:java.util.stream.IntStream.range(0,11).toArray();}
    public boolean canInsert(int i,ItemStack s,Direction face){return face!=Direction.DOWN&&isValid(i,s);}public boolean canExtract(int i,ItemStack s,Direction face){return i==11;}
    public Text getDisplayName(){return getCachedState().getBlock().getName();}
    public ScreenHandler createMenu(int sync,PlayerInventory inv,PlayerEntity p){return new PcbScreenHandler(sync,inv,this,properties());}
    @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.writeNbt(n,r);var inv=new NbtCompound();Inventories.writeNbt(inv,items,r);n.put("pcb_items",inv);n.putString("pcb_draft",draft.encode());n.putBoolean("pcb_working",working);n.putDouble("pcb_work",work);}
    @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.readNbt(n,r);items.clear();Inventories.readNbt(n.getCompound("pcb_items"),items,r);try{draft=PcbLayout.decode(n.getString("pcb_draft"));}catch(IllegalArgumentException e){draft=PcbLayout.empty();}working=n.getBoolean("pcb_working");double saved=n.getDouble("pcb_work");work=Double.isFinite(saved)?Math.clamp(saved,0,draft.joules()):0;}
}
