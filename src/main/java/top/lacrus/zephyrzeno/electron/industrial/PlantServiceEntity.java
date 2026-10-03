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
    public PlantServiceBlock.Role role(){return ((PlantServiceBlock)getCachedState().getBlock()).role;}
    public ComponentEntity core(){
        if(world==null||controller==null||!world.isChunkLoaded(controller)||!Assembly.formed(world.getBlockState(controller)))return null;
        if(!(world.getBlockEntity(controller) instanceof ComponentEntity e))return null;
        var s=e.getCachedState();
        boolean nuclear=s.isOf(Foundry.NUCLEAR);if(!nuclear&&!s.isOf(Foundry.THERMAL))return null;
        return switch(role()){
            case CONTROL->pos.equals(PlantStructures.consolePos(controller,s))?e:null;
            case SOLID_FUEL->!nuclear&&pos.equals(PlantStructures.fuelPos(controller,s))?e:null;
            case FUEL_ROD->nuclear&&pos.equals(PlantStructures.fuelPos(controller,s))?e:null;
            case ASH_OUTPUT->!nuclear&&pos.equals(PlantStructures.outputPos(controller,s))?e:null;
            case SPENT_OUTPUT->nuclear&&pos.equals(PlantStructures.outputPos(controller,s))?e:null;
        };
    }
    public static void tick(World w,BlockPos p,BlockState s,PlantServiceEntity e){
        if(w.getTime()%8!=0||e.role()==PlantServiceBlock.Role.CONTROL||e.core()==null)return;
        // The marked outward face is the only chest/barrel connection. No scanning behind the machine.
        var q=p.offset(s.get(PlantServiceBlock.FACING));if(!w.isChunkLoaded(q))return;
        Inventory box=null;var be=w.getBlockEntity(q);
        if(be instanceof ChestBlockEntity){var bs=w.getBlockState(q);box=ChestBlock.getInventory((ChestBlock)bs.getBlock(),bs,w,q,true);}
        else if(be instanceof BarrelBlockEntity barrel)box=barrel;
        if(box==null)return;
        if(e.role().input()){
            for(int i=0;i<box.size();i++)if(e.insertOne(box.getStack(i))){box.removeStack(i,1);box.markDirty();return;}
        }else for(int slot:new int[]{2,1,3}){
            var stack=e.getStack(slot);if(stack.isEmpty())continue;
            for(int i=0;i<box.size();i++){var target=box.getStack(i);
                if(!box.isValid(i,stack)||!target.isEmpty()&&(!ItemStack.areItemsAndComponentsEqual(stack,target)||target.getCount()>=Math.min(target.getMaxCount(),box.getMaxCountPerStack())))continue;
                if(target.isEmpty())box.setStack(i,stack.copyWithCount(1));else target.increment(1);
                e.removeStack(slot,1);box.markDirty();return;
            }
        }
    }
    public boolean insertOne(ItemStack source){
        if(source.isEmpty())return false;
        int slot=0;
        if(!isValid(slot,source))return false;
        var target=getStack(slot);if(!target.isEmpty()&&(!ItemStack.areItemsAndComponentsEqual(target,source)||target.getCount()>=target.getMaxCount()))return false;
        if(target.isEmpty())setStack(slot,source.copyWithCount(1));else{target.increment(1);markDirty();}return true;
    }
    @Override public int size(){return 4;}
    @Override public boolean isEmpty(){for(int i=0;i<4;i++)if(!getStack(i).isEmpty())return false;return true;}
    private boolean visible(int slot){return slot>=0&&slot<4&&(role()==PlantServiceBlock.Role.CONTROL||role().input()&&slot==0||role().output()&&slot>0);}
    @Override public ItemStack getStack(int slot){var e=core();return e==null||!visible(slot)?ItemStack.EMPTY:e.plantItems.get(slot);}
    @Override public void setStack(int slot,ItemStack stack){var e=core();if(e!=null&&visible(slot)&&(stack.isEmpty()||isValid(slot,stack)||role().output()&&ItemStack.areItemsAndComponentsEqual(getStack(slot),stack)&&stack.getCount()<=getStack(slot).getCount())){e.plantItems.set(slot,stack);stack.capCount(stack.getMaxCount());markDirty();}}
    @Override public ItemStack removeStack(int slot,int count){var e=core();if(e==null)return ItemStack.EMPTY;var s=Inventories.splitStack(e.plantItems,slot,count);markDirty();return s;}
    @Override public ItemStack removeStack(int slot){var e=core();if(e==null)return ItemStack.EMPTY;var s=Inventories.removeStack(e.plantItems,slot);markDirty();return s;}
    @Override public void clear(){var e=core();if(e!=null){e.plantItems.clear();markDirty();}}
    @Override public void markDirty(){super.markDirty();var e=core();if(e!=null)e.markDirty();}
    @Override public boolean canPlayerUse(PlayerEntity p){return core()!=null&&world.getBlockEntity(pos)==this&&p.squaredDistanceTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64;}
    @Override public boolean isValid(int slot,ItemStack stack){var e=core();return e!=null&&role().input()&&slot==0&&PlantOperations.accepts(e,stack);}
    @Override public int[] getAvailableSlots(Direction side){
        if(role().input()&&(side==Direction.UP||side==getCachedState().get(PlantServiceBlock.FACING)))return new int[]{0};
        if(role().output()&&(side==Direction.DOWN||side==getCachedState().get(PlantServiceBlock.FACING)))return new int[]{2,1,3};
        return new int[0];
    }
    @Override public boolean canInsert(int slot,ItemStack stack,Direction side){return java.util.Arrays.stream(getAvailableSlots(side)).anyMatch(i->i==slot)&&isValid(slot,stack);}
    @Override public boolean canExtract(int slot,ItemStack stack,Direction side){return role().output()&&java.util.Arrays.stream(getAvailableSlots(side)).anyMatch(i->i==slot);}
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
