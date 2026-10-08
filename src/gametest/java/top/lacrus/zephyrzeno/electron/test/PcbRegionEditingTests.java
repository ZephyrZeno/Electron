package top.lacrus.zephyrzeno.electron.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import top.lacrus.zephyrzeno.electron.foundry.ComponentBlock;
import top.lacrus.zephyrzeno.electron.pcb.*;

/** Server-side region operations must commit and enter history as a whole. */
public final class PcbRegionEditingTests implements FabricGameTest {
    private static int point(int x, int y) { return PcbLayout.point(x, y); }

    private static PcbBenchEntity bench(TestContext t, ComponentBlock block) {
        var pos = t.getAbsolutePos(new BlockPos(3, 2, 3));
        t.getWorld().setBlockState(pos.down(), Blocks.SMOOTH_STONE.getDefaultState());
        t.getWorld().setBlockState(pos, block.getDefaultState().with(ComponentBlock.FACING, Direction.UP));
        return (PcbBenchEntity) t.getWorld().getBlockEntity(pos);
    }

    private static PcbLayout module() {
        return PcbLayout.empty().withLayers(2).place(PcbLayout.Type.NOT, 3, 3, 1)
                .route(point(3, 3), point(6, 3), false)
                .route(PcbLayout.point(6, 3, 1), PcbLayout.point(6, 6, 1), true)
                .via(6, 3).cut(30, 20).port(0, 0, 2);
    }

    private static void edit(PcbBenchEntity bench, int op, int dx, int dy) {
        bench.edit(op, point(2, 2), point(7, 7), dx, dy);
    }

    @GameTest(templateName = "electron:components_test")
    public void regionCopyMoveAndDeleteEachCreateOneUndoStep(TestContext t) {
        var e = bench(t, Pcb.BENCH);
        e.draft = module().route(point(1, 3), point(3, 3), false);
        var original = e.draft;
        edit(e, 20, 12, 4);
        var copied = e.draft;
        t.assertEquals(e.error, 0, "Region copy accepted");
        t.assertEquals(copied.parts().size(), 2, "Both source and copied package exist");
        t.assertEquals(copied.vias().size(), 2, "Through-hole copied with the copper layers");
        t.assertEquals(e.properties().get(199), 1, "Whole copy has one undo step");
        e.edit(11, 0, 0, 0, 0);
        t.assertEquals(e.draft, original, "One undo removes every copied object");
        e.edit(12, 0, 0, 0, 0);
        t.assertEquals(e.draft, copied, "One redo restores the complete module");

        edit(e, 19, 0, 10);
        var moved = e.draft;
        t.assertEquals(e.error, 0, "Region move accepted");
        t.assertEquals(moved.parts().size(), 2, "Move does not duplicate packages");
        t.assertTrue(moved.parts().contains(new PcbLayout.Part(PcbLayout.Type.NOT, 3, 13, 1)), "Selected package moves relative to the region");
        t.assertFalse(moved.vias().contains(point(6, 3)), "Source through-hole moves away");
        t.assertTrue(moved.vias().contains(point(6, 13)), "Moved through-hole reaches its new position");
        t.assertTrue(moved.tracks().contains(new PcbLayout.Track(point(1, 3), point(2, 3))), "Cross-boundary wire stays at its old coordinates");
        t.assertEquals(e.properties().get(199), 2, "Move adds exactly one undo step");
        e.edit(11, 0, 0, 0, 0);
        t.assertEquals(e.draft, copied, "Move undo restores packages, copper and vias together");
        e.edit(12, 0, 0, 0, 0);
        t.assertEquals(e.draft, moved, "Move redo restores the complete move");

        e.edit(21, point(2, 12), point(7, 17), 0, 0);
        var deleted = e.draft;
        t.assertEquals(deleted.parts().size(), 1, "Delete removes only the chosen module");
        t.assertEquals(deleted.vias().size(), 1, "Delete removes the chosen through-hole");
        t.assertEquals(deleted.cutouts(), original.cutouts(), "Board outline is not edited");
        t.assertEquals(deleted.connectors(), original.connectors(), "External interfaces stay fixed");
        t.assertEquals(e.properties().get(199), 3, "Whole delete has one undo step");
        e.edit(11, 0, 0, 0, 0);
        t.assertEquals(e.draft, moved, "Deleted copper and packages restore together");
        e.edit(12, 0, 0, 0, 0);
        t.assertEquals(e.draft, deleted, "Deleted region replays exactly");
        t.assertEquals(PcbLayout.decode(deleted.encode()), deleted, "Edited design survives serialization");
        t.complete();
    }

    @GameTest(templateName = "electron:components_test")
    public void invalidRegionOperationChangesNeitherDesignNorHistory(TestContext t) {
        var e = bench(t, Pcb.BENCH);
        e.draft = module().place(PcbLayout.Type.AND, 15, 7, 0);
        var original = e.draft;
        int revision = e.revision;
        for (int op : new int[]{19, 20}) {
            edit(e, op, 12, 4);
            t.assertEquals(e.error, 4, "Colliding region is rejected");
            t.assertEquals(e.draft, original, "Collision cannot move copper before rejecting the package");
            t.assertEquals(e.revision, revision, "Rejected edit has no new revision");
            t.assertEquals(e.properties().get(199), 0, "Rejected edit has no undo entry");
        }
        e.edit(21, point(0, 0), point(32, 23), 0, 0);
        t.assertEquals(e.error, 4, "Out-of-board selection is rejected");
        t.assertEquals(e.draft, original, "Invalid delete preserves every object");
        edit(e, 20, 0, 10);
        e.edit(11, 0, 0, 0, 0);
        t.assertEquals(e.properties().get(200), 1, "Successful edit can be redone");
        edit(e, 20, Integer.MAX_VALUE, 0);
        t.assertEquals(e.draft, original, "Overflowing translation is rejected atomically");
        t.assertEquals(e.properties().get(200), 1, "Invalid operation does not destroy pending redo");
        edit(e, 21, 0, 0);
        t.assertEquals(e.properties().get(200), 0, "A new valid edit invalidates redo");
        t.complete();
    }

    @GameTest(templateName = "electron:components_test")
    public void paidManufacturingLocksRegionEditsAndHistoryUntilStopped(TestContext t) {
        var e = bench(t, Pcb.BENCH);
        e.draft = module();
        edit(e, 20, 12, 4);
        var design = e.draft;
        int cost = design.laminateCost();
        e.setStack(0, new ItemStack(PcbFabrication.FILM, cost));
        e.setStack(2, new ItemStack(Items.BLACK_DYE, cost));
        e.edit(5, 0, 0, 0, 0);
        t.assertTrue(e.working, "Design station starts a paid manufacturing job");
        e.voltage = 24;
        e.current = 1;
        e.process(false);
        t.assertTrue(e.work > 0, "Real electrical work has been spent");
        int revision = e.revision, history = e.properties().get(199);
        double work = e.work;
        for (int op : new int[]{19, 20, 21, 11, 12}) {
            edit(e, op, 0, 10);
            t.assertEquals(e.error, 1, "Manufacturing rejects geometry and history commands");
            t.assertEquals(e.draft, design, "Running manufacturing design remains fixed");
            t.assertEquals(e.revision, revision, "Rejected running edit has no revision");
            t.assertEquals(e.properties().get(199), history, "Running edit does not change history");
            t.assertEquals(e.work, work, "Rejected edit does not clear paid work");
            t.assertEquals(e.properties().get(8), cost, "Reserved film remains accounted for");
            t.assertEquals(e.properties().get(10), cost, "Reserved ink remains accounted for");
        }
        e.edit(6, 0, 0, 0, 0);
        t.assertFalse(e.working, "Explicit stop unlocks the design");
        t.assertEquals(e.getStack(0).getCount(), cost, "Stopping refunds paid film once");
        t.assertEquals(e.getStack(2).getCount(), cost, "Stopping refunds paid ink once");
        edit(e, 21, 0, 0);
        t.assertEquals(e.error, 0, "Region edit is allowed after stopping");
        t.assertEquals(e.draft.parts().size(), 1, "Only the selected original module is removed");
        t.complete();
    }

    @GameTest(templateName = "electron:components_test")
    public void assemblyStationCannotUseRegionCommandsToRewriteCopper(TestContext t) {
        var e = bench(t, Pcb.ASSEMBLY);
        var design = module();
        e.setStack(0, Pcb.designed(PcbFabrication.GOLD, design));
        int revision = e.revision;
        for (int op : new int[]{19, 20, 21, 11, 12}) {
            edit(e, op, 12, 4);
            t.assertEquals(e.error, 5, "Assembly station reports a read-only design");
            t.assertEquals(e.draft, design, "Assembly cannot rewrite patterned copper");
            t.assertEquals(Pcb.layout(e.getStack(0)), design, "Physical substrate is unchanged");
            t.assertEquals(e.getStack(0).getCount(), 1, "Rejected command does not duplicate or consume the substrate");
            t.assertEquals(e.revision, revision, "Rejected assembly edit has no revision");
            t.assertEquals(e.properties().get(199), 0, "Read-only station has no edit history");
        }
        t.complete();
    }

    @GameTest(templateName = "electron:components_test")
    public void screenHandlerRejectsReplayedRegionCommandsAndRemoteUse(TestContext t) {
        var e = bench(t, Pcb.BENCH);
        e.draft = module();
        var player = t.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        player.setPosition(e.getPos().toCenterPos());
        var handler = new PcbScreenHandler(7, player.getInventory(), e, e.properties());
        t.assertTrue(handler.designSynchronized(), "Server handler always reads the authoritative live design");
        var action = new PcbNetworking.Action(7, e.revision, 20, point(2, 2), point(7, 7), 12, 4);
        handler.action(player, action);
        t.assertEquals(e.draft.parts().size(), 2, "Current-revision action passes the real server handler");
        var copied = e.draft;
        int revision = e.revision;
        handler.action(player, action);
        t.assertEquals(e.draft, copied, "Replaying old action cannot copy twice");
        t.assertEquals(e.revision, revision, "Rejected replay does not advance revision");
        t.assertEquals(e.properties().get(199), 1, "Rejected replay does not add an undo entry");
        handler.action(player, new PcbNetworking.Action(7, action.revision(), 18, 2, 0, 0, 0));
        t.assertEquals(handler.materialPage, 2, "View-only material paging is independent of a stale design revision");
        t.assertEquals(e.draft, copied, "Material paging cannot change the board");
        handler.action(player, new PcbNetworking.Action(7, revision, 18, 3, 0, 0, 0));
        t.assertEquals(handler.materialPage, 2, "Out-of-range material page is ignored");
        player.setPosition(e.getPos().toCenterPos().add(20, 0, 0));
        handler.action(player, new PcbNetworking.Action(7, revision, 21, point(2, 2), point(7, 7), 0, 0));
        t.assertEquals(e.draft, copied, "A player out of reach cannot change the station");
        t.assertEquals(e.revision, revision, "Out-of-range request has no revision");
        t.complete();
    }

    @GameTest(templateName = "electron:components_test")
    public void clientLayoutAndPropertyRevisionMustMatchBeforeEditing(TestContext t) {
        var player = t.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        var properties = new net.minecraft.screen.ArrayPropertyDelegate(201);
        properties.set(2, 7);
        var handler = new PcbScreenHandler(8, player.getInventory(),
                new net.minecraft.inventory.SimpleInventory(Pcb.INVENTORY_SIZE), properties);
        var original = module();
        var changed = original.move(0, 12, 10, 2);
        t.assertFalse(handler.designSynchronized(), "An empty placeholder is not a synchronized board");
        handler.snapshot(original.encode(), 7);
        t.assertTrue(handler.designSynchronized(), "Matching initial snapshot permits editing");

        properties.set(2, 8);
        t.assertFalse(handler.designSynchronized(), "New properties with old layout must lock editing");
        handler.snapshot("broken-layout", 8);
        t.assertFalse(handler.designSynchronized(), "Failed decode must not advance the layout revision");
        t.assertEquals(handler.layout(), original, "Failed decode leaves the prior board intact");
        handler.snapshot(original.encode(), 7);
        t.assertFalse(handler.designSynchronized(), "An old snapshot cannot satisfy the current revision");
        handler.snapshot(changed.encode(), 8);
        t.assertTrue(handler.designSynchronized(), "Matching new snapshot unlocks the design");
        t.assertEquals(handler.layout(), changed, "New layout and its revision are committed together");
        handler.snapshot(original.encode(), 7);
        t.assertTrue(handler.designSynchronized(), "A late older snapshot cannot invalidate the accepted current one");
        t.assertEquals(handler.layout(), changed, "A stale packet cannot replace the current board");

        handler.snapshot(original.encode(), 9);
        t.assertFalse(handler.designSynchronized(), "A snapshot ahead of its property update also locks editing");
        properties.set(2, 9);
        t.assertTrue(handler.designSynchronized(), "Delayed properties can complete synchronization");
        handler.snapshot(changed.encode(), -1);
        handler.snapshot(changed.encode(), 32768);
        t.assertTrue(handler.designSynchronized(), "Out-of-range revisions are ignored");
        t.assertEquals(handler.layout(), original, "Invalid revision cannot change the design");
        handler.snapshot(changed.encode());
        t.assertEquals(handler.layout(), changed, "Convenience snapshot uses the current property revision");
        t.assertTrue(handler.designSynchronized(), "Test injection overload remains synchronized");
        t.complete();
    }

    @GameTest(templateName = "electron:components_test")
    public void clientSnapshotRevisionWrapIsAcceptedWithoutReplayingThePreviousBoard(TestContext t) {
        var player = t.createMockPlayer(net.minecraft.world.GameMode.CREATIVE);
        var properties = new net.minecraft.screen.ArrayPropertyDelegate(201);
        properties.set(2, 32767);
        var handler = new PcbScreenHandler(9, player.getInventory(),
                new net.minecraft.inventory.SimpleInventory(Pcb.INVENTORY_SIZE), properties);
        var original = module();
        var changed = original.move(0, 12, 10, 2);
        handler.snapshot(original.encode(), 32767);
        t.assertTrue(handler.designSynchronized(), "Last revision in the counter range is valid");
        properties.set(2, 0);
        t.assertFalse(handler.designSynchronized(), "Wrap property update locks the old board");
        handler.snapshot(changed.encode(), 0);
        t.assertTrue(handler.designSynchronized(), "Revision zero follows 32767");
        t.assertEquals(handler.layout(), changed, "Wrapped revision carries the new layout");
        handler.snapshot(original.encode(), 32767);
        t.assertEquals(handler.layout(), changed, "Pre-wrap late packet cannot replace post-wrap layout");
        t.assertTrue(handler.designSynchronized(), "Late pre-wrap packet preserves current synchronization");
        t.complete();
    }
}
