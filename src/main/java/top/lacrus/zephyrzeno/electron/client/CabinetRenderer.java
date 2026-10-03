package top.lacrus.zephyrzeno.electron.client;

import java.util.*;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.power.WiringCabinet;
import top.lacrus.zephyrzeno.electron.industrial.Industrial;

final class CabinetRenderer {
    private static final Map<String,RenderLayer> LAYERS=new HashMap<>();
    static void draw(ComponentEntity e,VertexConsumerProvider consumers,Matrix4f m,int light,int overlay){
        var s=e.getCachedState();var kind=((ComponentBlock)s.getBlock()).kind;
        for(var part:WiringCabinet.parts(kind,s.isOf(Industrial.WIND_BASE),e.cabinetOpen)){
            var b=WiringCabinet.orient(part.box(),s);
            var out=consumers.getBuffer(LAYERS.computeIfAbsent(part.texture(),name->RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/assembled/"+name+".png"))));
            ComponentRenderer.centeredBox(out,m,b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ,light,overlay);
        }
    }
    private CabinetRenderer(){}
}
