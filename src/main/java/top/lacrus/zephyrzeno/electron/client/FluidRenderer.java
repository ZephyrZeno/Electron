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
        var out=consumers.getBuffer(RenderLayer.getEntityTranslucent(Electron.id("textures/block/industrial/water.png")));
        double fraction=e.amount/(double)e.capacity();
        if(block.tank())ComponentRenderer.centeredBox(out,m,.13,.05,.13,.87,.05+.82*fraction,.87,light,overlay);
        else{
            ComponentRenderer.centeredBox(out,m,.37,.37,.37,.63,.37+.26*fraction,.63,light,overlay);
            for(var d:Direction.values())if(e.getCachedState().get(FluidBlock.LINKS[d.getId()])){
                var v=Vec3d.of(d.getVector()).multiply(.5);var b=new Box(.5,.5,.5,.5+v.x,.5+v.y,.5+v.z).expand(.10);
                ComponentRenderer.centeredBox(out,m,b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,light,overlay);
                if(e.getWorld()!=null&&e.flowingUntil>e.getWorld().getTime()){
                    double phase=((e.getWorld().getTime()+delta)%12)/12;var q=new Vec3d(.5,.5,.5).add(v.multiply(phase));
                    var shine=consumers.getBuffer(RenderLayer.getEntityTranslucent(Electron.id("textures/block/industrial/water_flow.png")));
                    ComponentRenderer.centeredBox(shine,m,q.x-.106,q.y-.106,q.z-.106,q.x+.106,q.y+.106,q.z+.106,light,overlay);
                }
            }
        }
    }
}
