package top.lacrus.zephyrzeno.electron.client;

import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.industrial.*;

public final class FluidRenderer implements BlockEntityRenderer<FluidEntity> {
    public FluidRenderer(BlockEntityRendererFactory.Context c){}
    @Override public void render(FluidEntity e,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){
        if(e.amount<=0)return;var m=matrices.peek().getPositionMatrix();var block=(FluidBlock)e.getCachedState().getBlock();
        if(e.getWorld()==null)return;boolean flowing=e.flowingUntil>e.getWorld().getTime();
        double fraction=e.amount/(double)e.capacity();
        if(block.tank())ContainedWater.draw(consumers,m,e.getWorld(),e.getPos(),new Box(.13,.05,.13,.87,.05+.82*fraction,.87),false,light,overlay);
        else{
            ContainedWater.draw(consumers,m,e.getWorld(),e.getPos(),new Box(.37,.37,.37,.63,.37+.26*fraction,.63),flowing,light,overlay);
            for(var d:Direction.values())if(e.getCachedState().get(FluidBlock.LINKS[d.getId()])){
                var v=Vec3d.of(d.getVector()).multiply(.5);var b=new Box(.5,.5,.5,.5+v.x,.5+v.y,.5+v.z).expand(.10);
                ContainedWater.draw(consumers,m,e.getWorld(),e.getPos(),new Box(b.minX,b.minY,b.minZ,b.maxX,b.minY+(b.maxY-b.minY)*fraction,b.maxZ),flowing,light,overlay);
            }
        }
    }
}
