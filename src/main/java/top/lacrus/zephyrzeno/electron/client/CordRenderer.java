package top.lacrus.zephyrzeno.electron.client;

import net.minecraft.client.render.*;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.circuit.CircuitState;

final class CordRenderer {
    static void draw(VertexConsumerProvider consumers,Matrix4f m,Vec3d camera,CircuitState s){
        var out=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/power_cord.png")));
        for(var cord:s.cords.values()){
            if(cord.path().stream().noneMatch(p->p.squaredDistanceTo(camera)<48*48))continue;
            var points=cord.path();Vec3d previous=points.getFirst();
            for(int i=1;i<points.size()-1;i++){
                var corner=points.get(i);var next=points.get(i+1);
                double radius=Math.min(.12,Math.min(previous.distanceTo(corner),next.distanceTo(corner))*.25);
                if(radius<.0001)continue;
                var start=corner.add(previous.subtract(corner).normalize().multiply(radius));
                var end=corner.add(next.subtract(corner).normalize().multiply(radius));
                WireRenderer.segment(out,m,camera,previous,start,.026,0);Vec3d last=start;
                for(int j=1;j<=4;j++){double t=j/4d;var q=start.multiply((1-t)*(1-t)).add(corner.multiply(2*t*(1-t))).add(end.multiply(t*t));WireRenderer.segment(out,m,camera,last,q,.026,0);last=q;}
                previous=end;
            }
            WireRenderer.segment(out,m,camera,previous,points.getLast(),.026,0);
            for(int i:new int[]{0,points.size()-1}){
                var at=points.get(i);var tail=points.get(i==0?1:i-1).subtract(at).normalize();
                WireRenderer.segment(out,m,camera,at,at.add(tail.multiply(.13)),.063,0);
                WireRenderer.segment(out,m,camera,at.add(tail.multiply(.13)),at.add(tail.multiply(.24)),.04,0);
            }
        }
    }
    private CordRenderer(){}
}
