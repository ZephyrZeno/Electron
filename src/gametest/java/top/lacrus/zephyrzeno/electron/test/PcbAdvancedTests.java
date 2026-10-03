package top.lacrus.zephyrzeno.electron.test;

import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.display.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.pcb.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.node;

public final class PcbAdvancedTests implements FabricGameTest {
    private static ComponentEntity put(TestContext t,ComponentBlock b,int x,int z){var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p.down(),Blocks.SMOOTH_STONE.getDefaultState());t.getWorld().setBlockState(p,b.getDefaultState().with(ComponentBlock.FACING,Direction.UP));return (ComponentEntity)t.getWorld().getBlockEntity(p);}
    @GameTest(templateName="electron:components_test")
    public void rolesRejectWrongInputsAndAssemblyCannotRewriteCopper(TestContext t){var d=(PcbBenchEntity)put(t,Pcb.BENCH,2,2);var a=(PcbBenchEntity)put(t,Pcb.ASSEMBLY,5,2);
        t.assertTrue(d.isValid(0,new ItemStack(PcbFabrication.FILM)),"Design accepts film");t.assertFalse(d.isValid(0,Pcb.designed(PcbFabrication.GOLD,PcbLayout.inverter())),"Design rejects gold board as feedstock");t.assertFalse(d.isValid(1,new ItemStack(Pcb.SOLDER)),"Design cannot solder");t.assertFalse(a.isValid(0,new ItemStack(PcbFabrication.FILM)),"Assembly cannot plot film");
        d.setStack(1,Pcb.designed(PcbFabrication.MASK,PcbLayout.halfAdder()));d.edit(7,0,0,0,0);t.assertEquals(d.draft,PcbLayout.halfAdder(),"Design reads a reusable mask reference");t.assertEquals(d.getStack(1).getCount(),1,"Reference is not consumed");
        a.setStack(0,Pcb.designed(PcbFabrication.GOLD,PcbLayout.halfAdder()));t.assertEquals(a.draft,PcbLayout.halfAdder(),"Assembly reads actual copper automatically");a.edit(3,0,0,0,0);t.assertEquals(a.draft,PcbLayout.halfAdder(),"Clear command cannot redesign copper");t.assertEquals(a.error,5,"Wrong workstation is reported");t.complete();}
    @GameTest(templateName="electron:components_test")
    public void serverUndoRedoAndRotationPreserveTrackHistory(TestContext t){var d=(PcbBenchEntity)put(t,Pcb.BENCH,3,3);d.edit(4,1,0,0,0);var original=d.draft;d.edit(13,0,0,0,0);t.assertEquals(d.draft.parts().getFirst().turn(),1,"Rotate selection");t.assertEquals(d.draft.tracks(),original.tracks(),"Copper is not silently moved");d.edit(11,0,0,0,0);t.assertEquals(d.draft,original,"Undo rotation");d.edit(12,0,0,0,0);t.assertEquals(d.draft.parts().getFirst().turn(),1,"Redo rotation");d.edit(14,0,0,0,0);t.assertTrue(d.draft.parts().isEmpty(),"Delete removes selected package");d.edit(11,0,0,0,0);t.assertEquals(d.draft.parts().size(),1,"Undo deletion");d.edit(9,15,11,0,0);t.assertEquals(d.properties().get(200),0,"New edit invalidates redo");t.complete();}
    @GameTest(templateName="electron:components_test")
    public void oldOutputSlotMigratesWithoutEatingTheBoard(TestContext t){var a=(PcbBenchEntity)put(t,Pcb.ASSEMBLY,3,3);a.setStack(11,Pcb.board(PcbLayout.inverter()));a.setStack(0,new ItemStack(PcbFabrication.FILM,3));var n=a.createNbt(t.getWorld().getRegistryManager());n.remove("pcb_inventory_version");n.putBoolean("pcb_working",true);var restored=new PcbBenchEntity(a.getPos(),a.getCachedState());restored.read(n,t.getWorld().getRegistryManager());t.assertTrue(restored.getStack(Pcb.OUTPUT_SLOT).isOf(Pcb.BOARD),"Old output survives migration");t.assertTrue(restored.getStack(11).isEmpty(),"New part slot cannot eat legacy output");t.assertEquals(restored.getStack(0).getCount(),3,"Legacy material remains recoverable");t.assertFalse(restored.working,"Old mixed-role job stops safely");t.complete();}
    @GameTest(templateName="electron:components_test")
    public void numericDecoderUsesPaidRailsAndUnknownInputsBlank(TestContext t){var dial=(DisplayEntity)put(t,Displays.DIAL,2,3);var screen=(DisplayEntity)put(t,Displays.SMALL,6,3);dial.setting=9;
        var branches=new ArrayList<DcSolver.Branch>();dial.stamp(branches);screen.stamp(branches);for(int pin=0;pin<6;pin++)branches.add(DcSolver.Branch.resistor("w"+pin,node(dial.getPos(),pin),node(screen.getPos(),pin),.02));branches.add(DcSolver.Branch.source("s",node(dial.getPos(),0),node(dial.getPos(),1),5,.1));var r=DcSolver.solve(branches);screen.voltage=r.voltage(node(screen.getPos(),0))-r.voltage(node(screen.getPos(),1));screen.update(r,n->true);t.assertEquals(screen.value,9,"Four physical input bits decode to 9");screen.signMode=true;screen.value=8;t.assertEquals(screen.glyph(),-1,"Only D0 drives minus mode");screen.value=9;t.assertEquals(screen.glyph(),16,"High D0 shows minus");t.assertTrue(-r.current("s")>.009,"Both components load actual supply");screen.update(r,n->false);t.assertEquals(screen.value,-1,"Floating input blanks the screen");screen.voltage=0;screen.update(r,n->true);t.assertEquals(screen.value,-1,"No supply means no display");t.complete();}
    @GameTest(templateName="electron:components_test")
    public void adjacentPanelsShareSixEdgeContactsWithoutFreeSource(TestContext t){var a=(DisplayEntity)put(t,Displays.PANEL,3,3);var b=(DisplayEntity)put(t,Displays.PANEL,4,3);t.assertTrue(a.joins(b.getPos()),"Coplanar panels join");var edges=new ArrayList<DcSolver.Branch>();a.stamp(edges);t.assertEquals(edges.stream().filter(e->e.id().contains(":edge")).count(),6L,"Every data/power contact has its own branch");t.assertTrue(edges.stream().allMatch(e->e.emf()==0),"Panel cannot generate voltage");t.getWorld().setBlockState(b.getPos(),b.getCachedState().with(ComponentBlock.TURN,1));t.assertFalse(a.joins(b.getPos()),"Different orientations do not short data buses");t.complete();}
}
