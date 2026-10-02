package top.lacrus.zephyrzeno.electron.client;

import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.circuit.CircuitParts.*;

/** Lit, UV-mapped insulation. Fine wire and cable share geometry with paid routing paths. */
final class WireRenderer {
    static void draw(VertexConsumerProvider consumers,Matrix4f matrix,Vec3d camera,CircuitState circuit,Wire w){
        var out=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/"+(w.cable()?"cable_":"wire_")+w.color()+".png")));
        var points=w.overhead()?circuit.overheadVertices(w):WireGeometry.vertices(w.path(),circuit::inset);
        double radius=w.cable()?OverheadCable.RADIUS:.010,offset=0;
        for(int i=1;i<points.size();i++){var a=points.get(i-1);var b=points.get(i);segment(out,matrix,camera,a,b,radius,offset);offset+=a.distanceTo(b)*4;}
        if(!w.overhead())for(int i=0;i<2;i++){
            var p=i==0?w.path().getFirst():w.path().getLast();var contact=circuit.endpoints().get(i==0?w.a():w.b());
            if(contact!=null&&contact.point().layer()!=p.layer())segment(out,matrix,camera,circuit.position(contact.point()),circuit.position(p),radius,offset);
        }
    }
    private static void segment(VertexConsumer out,Matrix4f m,Vec3d camera,Vec3d start,Vec3d end,double r,double uv){
        Vec3d axis=end.subtract(start);double length=axis.length();if(length<1e-8)return;
        axis=axis.normalize();Vec3d u=axis.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-8)u=axis.crossProduct(new Vec3d(1,0,0));u=u.normalize().multiply(r);Vec3d v=axis.crossProduct(u).normalize().multiply(r);
        var a=start.subtract(camera);var b=end.subtract(camera);
        Vec3d[] p={a.add(u).add(v),a.subtract(u).add(v),a.subtract(u).subtract(v),a.add(u).subtract(v),b.add(u).add(v),b.subtract(u).add(v),b.subtract(u).subtract(v),b.add(u).subtract(v)};
        int light=WorldRenderer.getLightmapCoordinates(MinecraftClient.getInstance().world,BlockPos.ofFloored(start.lerp(end,.5)));
        for(int[] face:new int[][]{{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7},{0,3,2,1},{4,5,6,7}}){
            Vec3d n=p[face[1]].subtract(p[face[0]]).crossProduct(p[face[2]].subtract(p[face[0]])).normalize();
            for(int j=0;j<4;j++){var q=p[face[j]];out.vertex(m,(float)q.x,(float)q.y,(float)q.z).color(255,255,255,255).texture(j==0||j==3?0:1,j<2?0:1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal((float)n.x,(float)n.y,(float)n.z);}
        }
    }
    private WireRenderer(){}
}
