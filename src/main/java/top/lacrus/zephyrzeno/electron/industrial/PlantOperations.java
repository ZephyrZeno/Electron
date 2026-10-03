package top.lacrus.zephyrzeno.electron.industrial;

import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.multiblock.Assembly;

/** Fuel accounting shared by typed ports. Cooling water must enter through the fixed pipe inlet. */
public final class PlantOperations {
    public static boolean accepts(ComponentEntity e,ItemStack s){return e.getCachedState().isOf(Foundry.NUCLEAR)?s.isOf(Foundry.FUEL_ROD):s.isOf(Items.COAL)||s.isOf(Items.CHARCOAL)||s.isOf(Items.COAL_BLOCK);}
    public static void bind(ServerWorld w,ComponentEntity e){
        var s=e.getCachedState();if(!Assembly.formed(s))return;
        for(var p:java.util.List.of(PlantStructures.consolePos(e.getPos(),s),PlantStructures.fuelPos(e.getPos(),s),PlantStructures.outputPos(e.getPos(),s)))if(w.getBlockEntity(p) instanceof PlantServiceEntity service){
            service.bind(e.getPos());var face=PlantStructures.serviceFace(s,p.equals(PlantStructures.consolePos(e.getPos(),s)));
            if(service.getCachedState().get(PlantServiceBlock.FACING)!=face)w.setBlockState(p,service.getCachedState().with(PlantServiceBlock.FACING,face),Block.NOTIFY_LISTENERS);
        }
        for(var p:java.util.List.of(PlantStructures.tankPos(e.getPos(),s),PlantStructures.waterPos(e.getPos(),s)))if(w.getBlockEntity(p) instanceof FluidEntity fluid)fluid.bind(e.getPos());
    }
    private static boolean room(ItemStack out,Item item){return out.isEmpty()||out.isOf(item)&&out.getCount()<out.getMaxCount();}
    private static void output(ComponentEntity e,int slot,Item item){var out=e.plantItems.get(slot);if(out.isEmpty())e.plantItems.set(slot,new ItemStack(item));else out.increment(1);}
    public static void feed(ServerWorld w,ComponentEntity e){
        var s=e.getCachedState();if(!Assembly.formed(s))return;bind(w,e);
        boolean nuclear=s.isOf(Foundry.NUCLEAR);
        if(nuclear&&e.fuelLoaded&&e.fuelEnergy<=1e-8&&room(e.plantItems.get(2),Foundry.SPENT_ROD)){
            output(e,2,Foundry.SPENT_ROD);e.fuelLoaded=false;e.markDirty();
        }
        if(!s.get(ComponentBlock.ENABLED)||s.get(ComponentBlock.BROKEN))return;
        var fuel=e.plantItems.get(0);if(!accepts(e,fuel))return;
        double energy=nuclear?200000:fuel.isOf(Items.COAL_BLOCK)?144000:16000;
        if(nuclear?e.fuelLoaded||e.fuelEnergy>1e-8:e.fuelEnergy+energy>200000)return;
        if(!nuclear&&!room(e.plantItems.get(2),Industrial.ASH))return;
        if(!nuclear)output(e,2,Industrial.ASH);
        fuel.decrement(1);e.fuelEnergy+=energy;e.fuelLoaded=nuclear;e.markDirty();
    }
    private PlantOperations(){}
}
