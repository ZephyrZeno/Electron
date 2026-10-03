package top.lacrus.zephyrzeno.electron.client;

import net.minecraft.client.render.*;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.multiblock.*;

final class AssemblyRenderer {
    static void draw(ComponentEntity e,ComponentBlock b,VertexConsumerProvider consumers,Matrix4f m,double dt,int light,int overlay){
        boolean tower=e.getCachedState().isOf(Foundry.TOWER_TERMINAL);
        for(var p:AssemblyGeometry.model(b.kind,e.assemblyHeight,tower,e.getCachedState().get(ComponentBlock.TURN)).pieces()){
            var out=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/assembled/"+p.texture()+".png")));
            if(p.sides()>4)cylinder(out,m,p,light,overlay);
            else if(p.beam())beam(out,m,p.a(),p.b(),p.radius(),light,overlay);
            else ComponentRenderer.centeredBox(out,m,p.a().x,p.a().y,p.a().z,p.b().x,p.b().y,p.b().z,light,overlay);
        }
        if(b.kind==ComponentBlock.Kind.WIND){
            e.rotorAngle+=dt*.045*e.displayedLight;
            for(int i=0;i<3;i++){
                var rotor=new Matrix4f(m).translate(.5f,2.5f,-.72f).rotateZ((float)(e.rotorAngle+i*Math.PI*2/3));
                var out=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/assembled/turbine_white.png")));
                ComponentRenderer.centeredBox(out,rotor,-.12,.12,-.09,.12,.7,.09,light,overlay);
                ComponentRenderer.centeredBox(out,rotor,-.105,.7,-.07,.07,1.5,.07,light,overlay);
                ComponentRenderer.centeredBox(out,rotor,-.08,1.5,-.045,.015,2.15,.045,light,overlay);
            }
        }
        // Crossarms provide additional taps bonded to the central service terminals.
        var s=e.getCachedState();int count=ComponentPins.count(b.kind);
        var pin=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/copper.png")));
        for(int i=0;i<count;i++){
            var at=ComponentGeometry.local(Direction.UP,s.get(ComponentBlock.TURN),ComponentPins.u(b.kind,i),ComponentPins.v(b.kind,i),1);
            ComponentRenderer.centeredBox(pin,m,at.x-.035,.93,at.z-.035,at.x+.035,.965,at.z+.035,light,overlay);
        }
    }
    private static void cylinder(VertexConsumer out,Matrix4f m,AssemblyGeometry.Piece p,int light,int overlay){
        for(int i=0;i<p.sides();i++){
            double a=i*Math.PI*2/p.sides(),b=(i+1d)*Math.PI*2/p.sides();
            var aa=new Vec3d(p.a().x+Math.cos(a)*p.radius(),p.a().y,p.a().z+Math.sin(a)*p.radius());
            var bb=new Vec3d(p.a().x+Math.cos(b)*p.radius(),p.a().y,p.a().z+Math.sin(b)*p.radius());
            var up=new Vec3d(0,p.b().y-p.a().y,0);
            Vec3d[][] faces={{aa,aa.add(up),bb.add(up),bb},{p.b(),bb.add(up),aa.add(up),p.b()},{p.a(),aa,bb,p.a()}};
            for(var face:faces){
                var normal=face[1].subtract(face[0]).crossProduct(face[2].subtract(face[0])).normalize();
                for(int j=0;j<4;j++){var q=face[j];out.vertex(m,(float)q.x,(float)q.y,(float)q.z).color(255,255,255,255).texture(j==0||j==3?0:1,j<2?0:1).overlay(overlay).light(light).normal((float)normal.x,(float)normal.y,(float)normal.z);}
            }
        }
    }
    private static void beam(VertexConsumer out,Matrix4f m,Vec3d a,Vec3d b,double r,int light,int overlay){
        var axis=b.subtract(a).normalize();var u=axis.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-8)u=axis.crossProduct(new Vec3d(1,0,0));u=u.normalize().multiply(r);var v=axis.crossProduct(u).normalize().multiply(r);
        Vec3d[] p={a.add(u).add(v),a.subtract(u).add(v),a.subtract(u).subtract(v),a.add(u).subtract(v),b.add(u).add(v),b.subtract(u).add(v),b.subtract(u).subtract(v),b.add(u).subtract(v)};
        for(int[] side:new int[][]{{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7},{0,3,2,1},{4,5,6,7}}){
            var normal=p[side[1]].subtract(p[side[0]]).crossProduct(p[side[2]].subtract(p[side[0]])).normalize();
            for(int i=0;i<4;i++){var q=p[side[i]];out.vertex(m,(float)q.x,(float)q.y,(float)q.z).color(255,255,255,255).texture(i==0||i==3?0:1,i<2?0:1).overlay(overlay).light(light).normal((float)normal.x,(float)normal.y,(float)normal.z);}
        }
    }
    private AssemblyRenderer(){}
}
