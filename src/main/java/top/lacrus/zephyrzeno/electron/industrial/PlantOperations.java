package top.lacrus.zephyrzeno.electron.industrial;

import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.multiblock.Assembly;

/** Server-side fuel and bucket accounting. Inputs, outputs and active fuel are persisted once on the core. */
public final class PlantOperations {
    public static boolean accepts(ComponentEntity e,ItemStack s){return e.getCachedState().isOf(Foundry.NUCLEAR)?s.isOf(Foundry.FUEL_ROD):s.isOf(Items.COAL)||s.isOf(Items.CHARCOAL)||s.isOf(Items.COAL_BLOCK);}
    public static void bind(ServerWorld w,ComponentEntity e){
        var s=e.getCachedState();if(!Assembly.formed(s))return;
        for(var p:java.util.List.of(PlantStructures.consolePos(e.getPos(),s),PlantStructures.fuelPos(e.getPos(),s)))if(w.getBlockEntity(p) instanceof PlantServiceEntity service){
            service.bind(e.getPos());var face=net.minecraft.util.math.Direction.SOUTH;
            if(p.equals(PlantStructures.fuelPos(e.getPos(),s)))face=s.isOf(Foundry.NUCLEAR)?net.minecraft.util.math.Direction.EAST:net.minecraft.util.math.Direction.WEST;
            for(int i=0;i<s.get(ComponentBlock.TURN);i++)face=face.rotateYClockwise();
            if(service.getCachedState().get(PlantServiceBlock.FACING)!=face)w.setBlockState(p,service.getCachedState().with(PlantServiceBlock.FACING,face),Block.NOTIFY_LISTENERS);
        }
    }
    private static boolean room(ItemStack out,Item item){return out.isEmpty()||out.isOf(item)&&out.getCount()<out.getMaxCount();}
    private static void output(ComponentEntity e,int slot,Item item){var out=e.plantItems.get(slot);if(out.isEmpty())e.plantItems.set(slot,new ItemStack(item));else out.increment(1);}
    public static void feed(ServerWorld w,ComponentEntity e){
        var s=e.getCachedState();if(!Assembly.formed(s))return;bind(w,e);
        var bucket=e.plantItems.get(1);var tank=w.getBlockEntity(PlantStructures.tankPos(e.getPos(),s));
        if(bucket.isOf(Items.WATER_BUCKET)&&tank instanceof FluidEntity f&&f.capacity()-f.amount>=1000&&room(e.plantItems.get(3),Items.BUCKET)){
            f.change(1000);bucket.decrement(1);output(e,3,Items.BUCKET);e.markDirty();
        }
        boolean nuclear=s.isOf(Foundry.NUCLEAR);
        if(nuclear&&e.fuelLoaded&&e.fuelEnergy<=1e-8&&room(e.plantItems.get(2),Foundry.SPENT_ROD)){
            output(e,2,Foundry.SPENT_ROD);e.fuelLoaded=false;e.markDirty();
        }
        if(!s.get(ComponentBlock.ENABLED)||s.get(ComponentBlock.BROKEN))return;
        var fuel=e.plantItems.get(0);if(!accepts(e,fuel))return;
        double energy=nuclear?200000:fuel.isOf(Items.COAL_BLOCK)?144000:16000;
        if(nuclear?e.fuelLoaded||e.fuelEnergy>1e-8:e.fuelEnergy+energy>200000)return;
        fuel.decrement(1);e.fuelEnergy+=energy;e.fuelLoaded=nuclear;e.markDirty();
    }
    private PlantOperations(){}
}
