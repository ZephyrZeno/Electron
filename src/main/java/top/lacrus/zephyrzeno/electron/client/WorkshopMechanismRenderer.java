package top.lacrus.zephyrzeno.electron.client;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.ProcessingBlock;

/** Uses the actual textured model parts, baked once per resource reload. */
public final class WorkshopMechanismRenderer {
    public static void register(){ModelLoadingPlugin.register(c->c.addModels(Electron.id("block/pcb_drill_mechanism"),Electron.id("block/immersion_gold_line_mechanism")));}
    public static void draw(ComponentEntity e,ProcessingBlock block,MatrixStack stack,VertexConsumerProvider out,double dt,int light,int overlay){
        if(!block.machine.equals("pcb_drill")&&!block.machine.equals("immersion_gold_line"))return;
        e.rotorAngle+=dt*.13*e.displayedLight;
        var s=e.getCachedState();var face=s.get(ComponentBlock.FACING);int turn=s.get(ComponentBlock.TURN);
        var o=ComponentGeometry.local(face,turn,0,0,16);
        var a=ComponentGeometry.local(face,turn,16,0,16).subtract(o);
        var b=ComponentGeometry.local(face,turn,0,0,0).subtract(o);
        var c=ComponentGeometry.local(face,turn,0,16,16).subtract(o);
        var frame=new Matrix4f().setColumn(0,new Vector4f((float)a.x,(float)a.y,(float)a.z,0)).setColumn(1,new Vector4f((float)b.x,(float)b.y,(float)b.z,0)).setColumn(2,new Vector4f((float)c.x,(float)c.y,(float)c.z,0)).setColumn(3,new Vector4f((float)o.x,(float)o.y,(float)o.z,1));
        stack.push();stack.multiplyPositionMatrix(frame);stack.peek().getNormalMatrix().mul(new Matrix3f(frame).invert().transpose());
        double motion=(1-Math.cos(e.rotorAngle))*.5*e.displayedLight;
        if(block.machine.equals("pcb_drill"))stack.translate(0,motion*.065,0);else stack.translate(0,motion*.09,0);
        var client=MinecraftClient.getInstance();var model=((FabricBakedModelManager)client.getBakedModelManager()).getModel(Electron.id("block/"+block.machine+"_mechanism"));
        client.getBlockRenderManager().getModelRenderer().render(stack.peek(),out.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)),s,model,1,1,1,light,overlay);
        stack.pop();
    }
    private WorkshopMechanismRenderer(){}
}
