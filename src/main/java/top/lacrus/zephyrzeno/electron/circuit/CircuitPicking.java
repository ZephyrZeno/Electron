package top.lacrus.zephyrzeno.electron.circuit;

import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import top.lacrus.zephyrzeno.electron.foundry.ComponentBlock;

/** The HUD, preview and server use the same projected target and snap radius. */
public final class CircuitPicking {
    public static SurfacePoint pick(World world,CircuitState state,BlockHitResult hit,int layer){
        var block=world.getBlockState(hit.getBlockPos());
        var origin=hit.getBlockPos();
        if(world.getBlockEntity(origin) instanceof top.lacrus.zephyrzeno.electron.multiblock.AssemblyPartEntity part)origin=part.origin;
        if(world.getBlockState(origin).isOf(top.lacrus.zephyrzeno.electron.foundry.Foundry.TOWER_TERMINAL)){
            var core=origin;
            var contact=state.endpoints().values().stream().filter(e->e.point().host().equals(core)).min(java.util.Comparator.comparingDouble(e->OverheadCable.attachment(state,e.point()).squaredDistanceTo(hit.getPos())));
            if(contact.isPresent()&&OverheadCable.attachment(state,contact.get().point()).squaredDistanceTo(hit.getPos())<.22)return contact.get().point().layer(layer);
        }
        Direction face=hit.getSide();
        if(block.getBlock() instanceof ComponentBlock component){
            var front=block.get(ComponentBlock.FACING);var projected=SurfacePoint.hit(hit.getBlockPos(),front,hit.getPos(),layer);
            double depth=Math.abs(state.position(projected).subtract(hit.getPos()).dotProduct(Vec3d.of(front.getVector())));
            if(!component.kind.large()||depth<.125)face=front;
        }
        var grid=SurfacePoint.hit(hit.getBlockPos(),face,hit.getPos(),layer);
        if(state.contactAt(grid).isPresent()||state.wires.values().stream().anyMatch(w->w.path().contains(grid)))return grid;
        Vec3d normal=Vec3d.of(face.getVector());Direction selectedFace=face;
        return state.endpoints().values().stream().map(e->e.point()).filter(p->p.host().equals(grid.host())&&p.face()==selectedFace)
            .filter(p->state.contactAt(p.layer(layer)).isPresent())
            .filter(p->planeDistance(state.position(p),hit.getPos(),normal)<.085*.085)
            .min(java.util.Comparator.comparingDouble(p->planeDistance(state.position(p),hit.getPos(),normal)))
            .map(p->p.layer(layer)).orElse(grid);
    }
    private static double planeDistance(Vec3d a,Vec3d b,Vec3d normal){var d=a.subtract(b);double n=d.dotProduct(normal);return d.lengthSquared()-n*n;}
    private CircuitPicking(){}
}
