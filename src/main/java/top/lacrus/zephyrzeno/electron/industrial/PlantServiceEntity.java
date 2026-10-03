package top.lacrus.zephyrzeno.electron.industrial;

import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.multiblock.Assembly;

public final class PlantServiceEntity extends BlockEntity implements SidedInventory,NamedScreenHandlerFactory {
    private BlockPos controller;
    public PlantServiceEntity(BlockPos p,BlockState s){super(Industrial.SERVICE_ENTITY,p,s);}
    public void bind(BlockPos p){if(!p.equals(controller)){controller=p.toImmutable();markDirty();}}
    public ComponentEntity core(){
        if(world==null||controller==null||!world.isChunkLoaded(controller)||!Assembly.formed(world.getBlockState(controller)))return null;
        if(!(world.getBlockEntity(controller) instanceof ComponentEntity e))return null;
        var s=e.getCachedState();
        return (pos.equals(PlantStructures.consolePos(controller,s))||pos.equals(PlantStructures.fuelPos(controller,s)))?e:null;
    }
    public static void tick(World w,BlockPos p,BlockState s,PlantServiceEntity e){
        if(w.getTime()%8!=0||!((PlantServiceBlock)s.getBlock()).port||e.core()==null)return;
        // Adjacent chests/barrels supply one accepted item every eight ticks. Hoppers use SidedInventory.
        for(var d:Direction.values()){
            var q=p.offset(d);if(!w.isChunkLoaded(q))continue;var be=w.getBlockEntity(q);
            Inventory source=null;
            if(be instanceof ChestBlockEntity){var state=w.getBlockState(q);source=ChestBlock.getInventory((ChestBlock)state.getBlock(),state,w,q,true);}
            else if(be instanceof BarrelBlockEntity barrel)source=barrel;
            if(source==null)continue;
            for(int i=0;i<source.size();i++)if(e.insertOne(source.getStack(i))){source.removeStack(i,1);source.markDirty();return;}
        }
    }
    public boolean insertOne(ItemStack source){
        if(source.isEmpty())return false;
        int slot=source.isOf(Items.WATER_BUCKET)?1:0;
        if(!isValid(slot,source))return false;
        var target=getStack(slot);if(!target.isEmpty()&&(!ItemStack.areItemsAndComponentsEqual(target,source)||target.getCount()>=target.getMaxCount()))return false;
        if(target.isEmpty())setStack(slot,source.copyWithCount(1));else{target.increment(1);markDirty();}return true;
    }
    @Override public int size(){return 4;}
    @Override public boolean isEmpty(){for(int i=0;i<4;i++)if(!getStack(i).isEmpty())return false;return true;}
    @Override public ItemStack getStack(int slot){var e=core();return e==null?ItemStack.EMPTY:e.plantItems.get(slot);}
    @Override public void setStack(int slot,ItemStack stack){var e=core();if(e!=null){e.plantItems.set(slot,stack);stack.capCount(stack.getMaxCount());markDirty();}}
    @Override public ItemStack removeStack(int slot,int count){var e=core();if(e==null)return ItemStack.EMPTY;var s=Inventories.splitStack(e.plantItems,slot,count);markDirty();return s;}
    @Override public ItemStack removeStack(int slot){var e=core();if(e==null)return ItemStack.EMPTY;var s=Inventories.removeStack(e.plantItems,slot);markDirty();return s;}
    @Override public void clear(){var e=core();if(e!=null){e.plantItems.clear();markDirty();}}
    @Override public void markDirty(){super.markDirty();var e=core();if(e!=null)e.markDirty();}
    @Override public boolean canPlayerUse(PlayerEntity p){return core()!=null&&world.getBlockEntity(pos)==this&&p.squaredDistanceTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    @Override public boolean isValid(int slot,ItemStack stack){var e=core();return e!=null&&(slot==1?stack.isOf(Items.WATER_BUCKET):slot==0&&PlantOperations.accepts(e,stack));}
    @Override public int[] getAvailableSlots(Direction side){return side==Direction.DOWN?new int[]{2,3}:new int[]{0,1};}
    @Override public boolean canInsert(int slot,ItemStack stack,Direction side){return side!=Direction.DOWN&&isValid(slot,stack);}
    @Override public boolean canExtract(int slot,ItemStack stack,Direction side){return slot==2||slot==3;}
    @Override public Text getDisplayName(){return Text.translatable("container.electron.plant_service");}
    @Override public ScreenHandler createMenu(int sync,PlayerInventory inv,PlayerEntity p){return new PlantScreenHandler(sync,inv,this,properties());}
    private PropertyDelegate properties(){return new PropertyDelegate(){
        @Override public int size(){return 6;}
        @Override public void set(int index,int value){}
        @Override public int get(int i){var e=core();if(e==null)return 0;var s=e.getCachedState();var tank=world.getBlockEntity(PlantStructures.tankPos(e.getPos(),s));return switch(i){
            case 0->s.isOf(Foundry.NUCLEAR)?1:0;case 1->s.get(ComponentBlock.ENABLED)?1:0;case 2->(int)Math.round(e.fuelEnergy/2000);
            case 3->tank instanceof FluidEntity f?(int)(1000L*f.amount/f.capacity()):0;case 4->(int)e.temperature;
            default->s.get(ComponentBlock.BROKEN)?4:!s.get(ComponentBlock.ENABLED)?0:!e.plantReady?3:e.fuelEnergy<=0?2:1;};}
    };}
    @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.writeNbt(n,r);if(controller!=null)n.putLong("controller",controller.asLong());}
    @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){super.readNbt(n,r);controller=n.contains("controller")?BlockPos.fromLong(n.getLong("controller")):null;}
}
