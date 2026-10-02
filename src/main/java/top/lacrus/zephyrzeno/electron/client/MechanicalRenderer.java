package top.lacrus.zephyrzeno.electron.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.mechanical.*;

public final class MechanicalRenderer implements BlockEntityRenderer<MechanicalEntity> {
    public MechanicalRenderer(BlockEntityRendererFactory.Context context){}
    @Override public void render(MechanicalEntity e,float delta,MatrixStack m,VertexConsumerProvider v,int light,int overlay){
        var face=e.getCachedState().get(MechanicalBlock.FACING);
        m.push();m.translate(.5,.5,.5);m.multiply(Direction.UP.getRotationQuaternion().conjugate());m.multiply(face.getRotationQuaternion());
        m.multiply(RotationAxis.POSITIVE_Y.rotation((float)MathHelper.lerp(delta,e.previousAngle,e.angle)));
        // Axle has a keyed square profile so its rotation is visible.
        ComponentRenderer.centeredBox(v.getBuffer(RenderLayer.getEntityCutout(Electron.id("textures/block/silver.png"))),m.peek().getPositionMatrix(),-.075,-.5,-.075,.075,.5,.075,light,overlay);
        if(e.getCachedState().getBlock() instanceof MechanicalBlock b&&b.table){
            ComponentRenderer.centeredBox(v.getBuffer(RenderLayer.getEntityCutout(Electron.id("textures/block/trim.png"))),m.peek().getPositionMatrix(),-.4,.2,-.4,.4,.35,.4,light,overlay);
            if(!e.mounted.isEmpty()){m.translate(0,.72,0);m.scale(.8f,.8f,.8f);MinecraftClient.getInstance().getItemRenderer().renderItem(e.mounted,ModelTransformationMode.FIXED,light,overlay,m,v,e.getWorld(),0);}
        }
        m.pop();
    }
    @Override public boolean rendersOutsideBoundingBox(MechanicalEntity e){return true;}
}
