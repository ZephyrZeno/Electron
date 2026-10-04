package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import net.minecraft.item.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.*;

/** Abstract game chemistry. Each wet process has a consumable and a recoverable waste stream. */
public final class PcbFabrication {
    public static Item FILM,MASK,EXPOSED,DRILLED,ETCHED,COATED,GOLD,ABRASIVE,ETCHANT,RESIST,GOLD_SALT,WASTE,RESIDUE;
    public record Stage(ProcessingBlock machine,Item input,Item reagent,Item output,double joules){}
    public static final List<Stage> STAGES=new ArrayList<>();
    public static void register(){
        FILM=Processing.item("photo_film");MASK=Processing.item("pcb_photomask");EXPOSED=Processing.item("exposed_pcb");DRILLED=Processing.item("drilled_pcb");ETCHED=Processing.item("etched_pcb");COATED=Processing.item("masked_pcb");GOLD=Processing.item("gold_finished_pcb");
        ABRASIVE=Processing.item("micro_drill_bit");ETCHANT=Processing.item("etchant_cartridge");RESIST=Processing.item("solder_mask_resin");GOLD_SALT=Processing.item("gold_bath_cartridge");WASTE=Processing.item("spent_process_cartridge");RESIDUE=Processing.item("process_residue");
        STAGES.add(new Stage(Processing.block("uv_exposure_unit",12),Pcb.BLANK,MASK,EXPOSED,80));
        STAGES.add(new Stage(Processing.block("pcb_drill",18),EXPOSED,ABRASIVE,DRILLED,160));
        STAGES.add(new Stage(Processing.block("pcb_etcher",16),DRILLED,ETCHANT,ETCHED,200));
        STAGES.add(new Stage(Processing.block("solder_mask_applicator",12),ETCHED,RESIST,COATED,120));
        STAGES.add(new Stage(Processing.block("immersion_gold_line",24),COATED,GOLD_SALT,GOLD,300));
    }
    public static boolean carriesLayout(ItemStack s){return s.isOf(Pcb.BOARD)||s.isOf(MASK)||s.isOf(EXPOSED)||s.isOf(DRILLED)||s.isOf(ETCHED)||s.isOf(COATED)||s.isOf(GOLD);}
    public static List<Item> items(){var list=new ArrayList<>(Pcb.MATERIALS);Collections.addAll(list,Pcb.BOARD,FILM,MASK,EXPOSED,DRILLED,ETCHED,COATED,GOLD,ABRASIVE,ETCHANT,RESIST,GOLD_SALT,WASTE,RESIDUE);for(var s:STAGES)list.add(s.machine.asItem());return list.stream().distinct().toList();}
    public static boolean accepts(ProcessingBlock b,int slot,ItemStack s){return STAGES.stream().anyMatch(r->r.machine==b&&(slot==0?s.isOf(r.input):slot==1&&s.isOf(r.reagent)));}
    private static Stage stage(ComponentEntity e){return STAGES.stream().filter(s->e.getCachedState().isOf(s.machine)).findFirst().orElse(null);}
    private static PcbLayout layout(ComponentEntity e,Stage s){return Pcb.layout(e.plantItems.get(s.input==Pcb.BLANK?1:0));}
    public static Processing.Recipe recipe(ComponentEntity e){
        var s=stage(e);if(s==null||!e.plantItems.get(0).isOf(s.input)||!e.plantItems.get(1).isOf(s.reagent))return null;
        var l=layout(e,s);if(l.parts().isEmpty()||l.supplyShort())return null;
        boolean wet=s.input==DRILLED||s.input==COATED;
        if(e.plantItems.get(0).getCount()<(s.input==Pcb.BLANK?l.laminateCost():1)||e.plantItems.get(1).getCount()<(s.input==Pcb.BLANK?1:l.laminateCost()))return null;
        return new Processing.Recipe(s.machine.machine+":"+l.encode(),s.machine.machine,s.input,s.input==Pcb.BLANK?l.laminateCost():1,s.reagent,s.input==Pcb.BLANK?1:l.laminateCost(),s.output,1,wet?WASTE:Items.AIR,wet?l.laminateCost():0,s.joules*l.laminateCost()+l.vias().size()*2);
    }
    public static ItemStack output(ComponentEntity e){var s=stage(e);return s==null?ItemStack.EMPTY:Pcb.designed(s.output,layout(e,s));}
    private PcbFabrication(){}
}
