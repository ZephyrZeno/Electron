package top.lacrus.zephyrzeno.electron.industrial;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.item.ComponentItem;

/** Electrically powered batch processing. Recipes commit input/output atomically after paid work. */
public final class Processing {
    public static ProcessingBlock CRUSHER,CENTRIFUGE,KILN,FABRICATOR;
    public static Item CRUSHED_URANIUM,CONCENTRATE,TAILINGS,IRON_DUST,COPPER_DUST,CLADDING,IRON_PLATE,COPPER_COIL;
    public static ScreenHandlerType<ProcessingScreenHandler> SCREEN;
    public record Recipe(String id,String machine,Item first,int firstCount,Item second,int secondCount,Item output,int outputCount,Item byproduct,int byproductCount,double joules){}
    private static List<Recipe> cachedRecipes;
    public static List<Recipe> recipes(){if(cachedRecipes!=null)return cachedRecipes;return cachedRecipes=List.of(
        new Recipe("dry_process_waste","electric_kiln",top.lacrus.zephyrzeno.electron.pcb.PcbFabrication.WASTE,1,Items.AIR,0,top.lacrus.zephyrzeno.electron.pcb.PcbFabrication.RESIDUE,1,Items.GLASS_BOTTLE,1,120),
        new Recipe("crush_uranium","ore_crusher",Foundry.RAW_URANIUM,1,Items.AIR,0,CRUSHED_URANIUM,2,Items.AIR,0,120),
        new Recipe("crush_iron","ore_crusher",Items.RAW_IRON,1,Items.AIR,0,IRON_DUST,1,Items.AIR,0,80),
        new Recipe("crush_copper","ore_crusher",Items.RAW_COPPER,1,Items.AIR,0,COPPER_DUST,1,Items.AIR,0,80),
        new Recipe("separate_fuel","centrifuge",CRUSHED_URANIUM,2,Items.AIR,0,CONCENTRATE,1,TAILINGS,1,400),
        new Recipe("sinter_fuel","electric_kiln",CONCENTRATE,1,Items.AIR,0,Foundry.URANIUM_PELLET,1,Items.AIR,0,300),
        new Recipe("smelt_iron","electric_kiln",IRON_DUST,1,Items.AIR,0,Items.IRON_INGOT,1,Items.AIR,0,160),
        new Recipe("smelt_copper","electric_kiln",COPPER_DUST,1,Items.AIR,0,Items.COPPER_INGOT,1,Items.AIR,0,120),
        new Recipe("seal_fuel","fuel_fabricator",Foundry.URANIUM_PELLET,4,CLADDING,1,Foundry.FUEL_ROD,1,Items.AIR,0,160),
        new Recipe("press_plate","fuel_fabricator",Items.IRON_INGOT,2,Items.AIR,0,IRON_PLATE,1,Items.AIR,0,80),
        new Recipe("wind_coil","fuel_fabricator",Items.COPPER_INGOT,2,Items.AIR,0,COPPER_COIL,1,Items.AIR,0,100)
    );}
    public static Item item(String id){var item=Registry.register(Registries.ITEM,Electron.id(id),new Item(new Item.Settings()));Foundry.ITEMS.put(id,item);return item;}
    public static ProcessingBlock block(String id,double watts){
        var block=Registry.register(Registries.BLOCK,Electron.id(id),new ProcessingBlock(id,watts));
        var item=Registry.register(Registries.ITEM,Electron.id(id),new ComponentItem(block,new Item.Settings()));Foundry.DEVICES.put(id,block);Foundry.ITEMS.put(id,item);return block;
    }
    public static void register(){
        CRUSHED_URANIUM=item("crushed_uranium");CONCENTRATE=item("fuel_concentrate");TAILINGS=item("uranium_tailings");IRON_DUST=item("iron_dust");COPPER_DUST=item("copper_dust");CLADDING=item("fuel_cladding");IRON_PLATE=item("iron_plate");COPPER_COIL=item("copper_coil");
        CRUSHER=block("ore_crusher",16);CENTRIFUGE=block("centrifuge",20);KILN=block("electric_kiln",24);FABRICATOR=block("fuel_fabricator",12);
        SCREEN=Registry.register(Registries.SCREEN_HANDLER,Electron.id("processing"),new ScreenHandlerType<>(ProcessingScreenHandler::new,FeatureFlags.VANILLA_FEATURES));
    }
    private static boolean matches(ItemStack s,Item item,int count){return count==0?s.isEmpty():s.isOf(item)&&s.getCount()>=count;}
    private static boolean room(ItemStack s,Item item,int count){return count==0||s.isEmpty()||s.isOf(item)&&s.getCount()+count<=s.getMaxCount();}
    public static Recipe recipe(ComponentEntity e){
        if(!(e.getCachedState().getBlock() instanceof ProcessingBlock b))return null;
        var pcb=top.lacrus.zephyrzeno.electron.pcb.PcbFabrication.recipe(e);if(pcb!=null)return pcb;
        for(var r:recipes())if(r.machine.equals(b.machine)&&matches(e.plantItems.get(0),r.first,r.firstCount)&&matches(e.plantItems.get(1),r.second,r.secondCount))return r;return null;
    }
    public static boolean outputRoom(ComponentEntity e,Recipe r){if(top.lacrus.zephyrzeno.electron.pcb.PcbFabrication.STAGES.stream().anyMatch(s->e.getCachedState().isOf(s.machine()))&&!e.plantItems.get(2).isEmpty())return false;return room(e.plantItems.get(2),r.output,r.outputCount)&&room(e.plantItems.get(3),r.byproduct,r.byproductCount);}
    public static boolean ready(ComponentEntity e){var r=recipe(e);return r!=null&&outputRoom(e,r)&&e.getCachedState().get(ComponentBlock.ENABLED);}
    public static boolean accepts(ProcessingBlock block,int slot,ItemStack stack){return top.lacrus.zephyrzeno.electron.pcb.PcbFabrication.accepts(block,slot,stack)||slot<2&&recipes().stream().anyMatch(r->r.machine.equals(block.machine)&&(slot==0?r.firstCount>0&&stack.isOf(r.first):r.secondCount>0&&stack.isOf(r.second)));}
    private static void add(ComponentEntity e,int slot,Item item,int count){if(count==0)return;var stack=e.plantItems.get(slot);if(stack.isEmpty())e.plantItems.set(slot,new ItemStack(item,count));else stack.increment(count);}
    public static int update(ServerWorld world,BlockPos pos,ComponentEntity e,boolean broken){
        var r=recipe(e);
        if(r==null||!r.id.equals(e.processingRecipe)){e.processingRecipe=r==null?"":r.id;e.processingEnergy=0;}
        boolean powered=!broken&&!e.paused&&r!=null&&outputRoom(e,r)&&e.getCachedState().get(ComponentBlock.ENABLED)&&e.voltage>=18&&e.voltage<=30&&e.current>0;
        if(!powered){e.omega*=.85;return 0;}
        var block=(ProcessingBlock)e.getCachedState().getBlock();
        double paid=Math.max(0,e.voltage*e.current)*.05;
        e.processingEnergy=Math.min(r.joules,e.processingEnergy+paid);e.omega+=(18-e.omega)*.08;
        if(world.getTime()%60==0)ElectricalEffects.play(world,pos,ElectricalEffects.MOTOR,.12f,block==CENTRIFUGE?1.3f:.75f);
        if(e.processingEnergy+1e-7>=r.joules){
            var pcbOutput=top.lacrus.zephyrzeno.electron.pcb.PcbFabrication.output(e);
            e.plantItems.get(0).decrement(r.firstCount);if(r.secondCount>0)e.plantItems.get(1).decrement(r.secondCount);
            if(pcbOutput.isEmpty())add(e,2,r.output,r.outputCount);else e.plantItems.set(2,pcbOutput);add(e,3,r.byproduct,r.byproductCount);e.processingEnergy=0;e.processingRecipe="";
            ElectricalEffects.play(world,pos,ElectricalEffects.ASSEMBLE,.2f,1.15f);
        }
        return 15;
    }
    private Processing(){}
}
