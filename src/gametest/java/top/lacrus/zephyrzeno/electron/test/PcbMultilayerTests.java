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
import top.lacrus.zephyrzeno.electron.industrial.*;
import top.lacrus.zephyrzeno.electron.pcb.*;
import static top.lacrus.zephyrzeno.electron.circuit.CircuitParts.node;

public final class PcbMultilayerTests implements FabricGameTest {
    @GameTest(templateName="electron:components_test")
    public void denseControlsSelectNearestPackageInsteadOfEarlierNeighbour(TestContext t){
        var e=(PcbSocketEntity)put(t,Pcb.SOCKET,4,4);var layout=PcbExamples.pixel();
        e.plantItems.set(0,Pcb.board(layout));e.boardChanged();var player=t.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        for(int i=0;i<4;i++){
            var part=layout.parts().get(i);var center=ComponentGeometry.local(Direction.UP,0,PcbSocketEntity.u(layout,part.x()+1),PcbSocketEntity.v(layout,part.y()+1),13).add(Vec3d.of(e.getPos()));
            var hit=new net.minecraft.util.hit.BlockHitResult(center,Direction.UP,e.getPos(),false);e.interact(player,hit);
            for(int n=0;n<4;n++)t.assertEquals(e.circuit().controls[n],n<=i?1:0,"Clicking a dense switch cannot toggle its neighbour");
        }
        t.complete();
    }
    @GameTest(templateName="electron:components_test")
    public void largeBoardKeepsPaidProcessingEnergyAcrossReload(TestContext t){
        var layout=PcbLayout.inverter().resize(128,96).withLayers(4);
        var stage=PcbFabrication.STAGES.getLast();var e=(ProcessingEntity)put(t,stage.machine(),3,3);
        e.setStack(0,Pcb.designed(PcbFabrication.COATED,layout));
        e.setStack(1,new ItemStack(PcbFabrication.GOLD_SALT,layout.laminateCost()));
        var recipe=PcbFabrication.recipe(e);t.assertTrue(recipe.joules()>15000,"Large batches exceed the old saved-work limit");
        e.processingRecipe=recipe.id();e.processingEnergy=15000;
        var saved=e.createNbt(t.getWorld().getRegistryManager());e.read(saved,t.getWorld().getRegistryManager());
        t.assertEquals(e.processingEnergy,15000d,"Reload keeps all previously paid work");
        e.voltage=0;e.current=0;Processing.update(t.getWorld(),e.getPos(),e,false);
        t.assertEquals(e.processingEnergy,15000d,"An unpowered job retains, but does not increase, paid work");
        t.assertTrue(e.plantItems.get(2).isEmpty(),"Stored partial work cannot produce a free finished board");t.complete();
    }
    @GameTest(templateName="electron:components_test")
    public void wallMatrixKeepsWholeFrameOrientation(TestContext t){var tiles=new ArrayList<DisplayEntity>();for(int y=0;y<2;y++)for(int x=0;x<2;x++){var at=t.getAbsolutePos(new BlockPos(3+x,3+y,3));t.getWorld().setBlockState(at.south(),Blocks.SMOOTH_STONE.getDefaultState());t.getWorld().setBlockState(at,Displays.PIXEL.getDefaultState().with(ComponentBlock.FACING,Direction.NORTH));tiles.add((DisplayEntity)t.getWorld().getBlockEntity(at));}var root=tiles.getFirst();PixelMatrix.update(root,0,true);for(int i=0;i<256;i++){int bit=i==0?1:0;PixelMatrix.update(root,bit,true);PixelMatrix.update(root,bit|2,true);}PixelMatrix.update(root,4,true);t.assertEquals(tiles.get(3).pixelBits,Long.MIN_VALUE,"Top-left pixel uses the mirrored top-left tile on a north wall");for(int i=0;i<3;i++)t.assertEquals(tiles.get(i).pixelBits,0L,"No repeated or transposed tiles");t.complete();}
    private static ComponentEntity put(TestContext t,ComponentBlock b,int x,int z){var p=t.getAbsolutePos(new BlockPos(x,2,z));t.getWorld().setBlockState(p.down(),Blocks.SMOOTH_STONE.getDefaultState());t.getWorld().setBlockState(p,b.getDefaultState().with(ComponentBlock.FACING,Direction.UP));return (ComponentEntity)t.getWorld().getBlockEntity(p);}
    @GameTest(templateName="electron:components_test")
    public void largeBoardRequiresPaidCarrierAndControlsSurviveReload(TestContext t){var e=(PcbSocketEntity)put(t,Pcb.SOCKET,4,4);var layout=SingleBoardCalculator.layout();t.assertFalse(e.supported(layout),"Large PCB cannot float on one tile");for(var at:e.supportPositions(layout))if(!at.equals(e.getPos())){t.getWorld().setBlockState(at.down(),Blocks.SMOOTH_STONE.getDefaultState());t.getWorld().setBlockState(at,Pcb.EXTENSION.getDefaultState());}t.assertTrue(e.supported(layout),"All twelve carrier tiles support one board");e.plantItems.set(0,Pcb.board(layout));e.boardChanged();e.setControl(0,9);var saved=e.createNbt(t.getWorld().getRegistryManager());var restored=new PcbSocketEntity(e.getPos(),e.getCachedState());restored.read(saved,t.getWorld().getRegistryManager());t.assertEquals(restored.circuit().controls[0],9,"Dial setting survives reload");t.assertEquals(restored.circuit().layout,layout,"One complete board survives reload");t.getWorld().setBlockState(e.supportPositions(layout).getLast(),Blocks.AIR.getDefaultState());t.assertFalse(e.supported(layout),"Missing tile stops operation");t.complete();}
    @GameTest(templateName="electron:components_test")
    public void scaledFabricationRejectsUnderpayment(TestContext t){var layout=PcbLayout.inverter().resize(64,48).withLayers(2);var stage=PcbFabrication.STAGES.getFirst();var e=(ProcessingEntity)put(t,stage.machine(),3,3);e.setStack(0,new ItemStack(Pcb.BLANK,1));e.setStack(1,Pcb.designed(PcbFabrication.MASK,layout));t.assertTrue(PcbFabrication.recipe(e)==null,"A large board cannot consume one small laminate");e.setStack(0,new ItemStack(Pcb.BLANK,layout.laminateCost()));var r=PcbFabrication.recipe(e);t.assertTrue(r!=null,"Exact laminate payment permits exposure");t.assertEquals(r.firstCount(),8,"Four areas times two layers");t.assertEquals(r.joules(),stage.joules()*8,"Real electrical work scales with area and layer count");t.complete();}
    @GameTest(templateName="electron:components_test")
    public void assemblyAcceptsMoreThanOneStackWithoutDuplication(TestContext t){var layout=PcbLayout.empty().resize(48,48);for(int i=0;i<69;i++)layout=layout.place(PcbLayout.Type.AND,2+i%8*4,2+i/8*4,0);var e=(PcbBenchEntity)put(t,Pcb.ASSEMBLY,3,3);e.setStack(0,Pcb.designed(PcbFabrication.GOLD,layout));e.setStack(1,new ItemStack(Pcb.SOLDER,layout.solderCost()));int slot=3+PcbLayout.Type.AND.ordinal();e.setStack(slot,new ItemStack(Pcb.PARTS.get(PcbLayout.Type.AND),64));e.edit(5,0,0,0,0);e.voltage=24;e.current=1;e.process(false);t.assertTrue(e.working,"Large assembly waits for a second stack");t.assertTrue(e.getStack(Pcb.OUTPUT_SLOT).isEmpty(),"Incomplete parts cannot produce a board");t.assertTrue(e.isValid(slot,new ItemStack(Pcb.PARTS.get(PcbLayout.Type.AND))),"Hopper can add remaining parts during the job");var saved=e.createNbt(t.getWorld().getRegistryManager());e.read(saved,t.getWorld().getRegistryManager());e.setStack(slot,new ItemStack(Pcb.PARTS.get(PcbLayout.Type.AND),5));for(int i=0;i<2000;i++){e.voltage=24;e.current=1;e.process(false);}t.assertTrue(Pcb.layout(e.getStack(Pcb.OUTPUT_SLOT)).equals(layout),"All 69 paid packages assemble after reload");t.assertTrue(e.getStack(slot).isEmpty(),"Both stacks consumed exactly");e.releaseMaterials();t.assertTrue(e.getStack(0).isEmpty(),"Completed job cannot refund its substrate");t.complete();}
    @GameTest(templateName="electron:components_test")
    public void cancelledAssemblyReturnsReservedMaterialsExactly(TestContext t){var e=(PcbBenchEntity)put(t,Pcb.ASSEMBLY,3,3);var layout=PcbLayout.inverter();e.setStack(0,Pcb.designed(PcbFabrication.GOLD,layout));e.setStack(1,new ItemStack(Pcb.SOLDER,1));e.edit(5,0,0,0,0);e.voltage=24;e.current=1;e.process(false);e.edit(6,0,0,0,0);t.assertEquals(Pcb.layout(e.getStack(0)),layout,"Cancel returns patterned substrate");t.assertEquals(e.getStack(1).getCount(),1,"Cancel returns solder once");e.edit(6,0,0,0,0);t.assertEquals(e.getStack(1).getCount(),1,"Repeated cancel cannot duplicate materials");t.complete();}
    @GameTest(templateName="electron:components_test")
    public void versionTwoOutputMigratesPastNewPartSlots(TestContext t){var e=(PcbBenchEntity)put(t,Pcb.ASSEMBLY,3,3);e.setStack(12,new ItemStack(Pcb.PARTS.get(PcbLayout.Type.DISPLAY),5));e.setStack(13,Pcb.board(PcbLayout.halfAdder()));var n=e.createNbt(t.getWorld().getRegistryManager());n.putInt("pcb_inventory_version",2);e.read(n,t.getWorld().getRegistryManager());t.assertTrue(e.getStack(Pcb.OUTPUT_SLOT).isOf(Pcb.BOARD),"v2 output moved to current output slot");t.assertTrue(e.getStack(13).isEmpty(),"New NOR slot does not eat output");t.assertEquals(e.getStack(12).getCount(),5,"Old numeric parts survive");t.complete();}
    @GameTest(templateName="electron:components_test")
    public void twoByTwoPixelScreenAcceptsAnArbitrary256BitFrame(TestContext t){var tiles=new ArrayList<DisplayEntity>();for(int y=0;y<2;y++)for(int x=0;x<2;x++)tiles.add((DisplayEntity)put(t,Displays.PIXEL,3+x,3+y));var root=tiles.getFirst();var group=PixelMatrix.group(root);t.assertEquals(group.width(),2,"Panels expand width");t.assertEquals(group.height(),2,"Panels expand height");PixelMatrix.update(root,0,true);
        for(int i=0;i<256;i++){int data=(i/16+i%16)%3==0?1:0;PixelMatrix.update(root,data,true);PixelMatrix.update(root,data|2,true);}PixelMatrix.update(root,4,true);
        for(int n=0;n<4;n++){long expected=0;for(int y=0;y<8;y++)for(int x=0;x<8;x++)if((n/2*8+y+n%2*8+x)%3==0)expected|=1L<<(63-y*8-x);t.assertEquals(tiles.get(n).pixelBits,expected,"Each panel shows its own slice, not a repeated tile");}PixelMatrix.update(root,0,false);for(var tile:tiles)t.assertEquals(tile.pixelBits,0L,"Loss of supply clears every tile");t.complete();}
}
