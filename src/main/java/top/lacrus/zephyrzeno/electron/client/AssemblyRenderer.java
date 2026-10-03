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
                var rotor=new Matrix4f(m).translate(.5f,0,.5f).rotateY((float)(-Math.PI/2*e.getCachedState().get(ComponentBlock.TURN))).translate(-.5f,0,-.5f).translate(.5f,2.5f,-.72f).rotateZ((float)(e.rotorAngle+i*Math.PI*2/3));
                var out=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/assembled/turbine_white.png")));
                blade(out,rotor,light,overlay);
            }
        }
        if(b.kind==ComponentBlock.Kind.NUCLEAR&&e.getWorld()!=null){
            var tankPos=top.lacrus.zephyrzeno.electron.industrial.PlantStructures.tankPos(e.getPos(),e.getCachedState());
            if(e.getWorld().getBlockEntity(tankPos) instanceof top.lacrus.zephyrzeno.electron.industrial.FluidEntity tank&&tank.amount>0){
                var water=consumers.getBuffer(RenderLayer.getEntityTranslucent(Electron.id("textures/block/industrial/water.png")));
                var basin=new Matrix4f(m).translate(.5f,0,.5f).rotateY((float)(-Math.PI/2*e.getCachedState().get(ComponentBlock.TURN))).translate(-.5f,0,-.5f);
                ComponentRenderer.centeredBox(water,basin,-13.69,.22,-11.69,-9.01,.23+2.05*tank.amount/tank.capacity(),-5.41,light,overlay);
            }
        }
        // Crossarms provide additional taps bonded to the central service terminals.
        var s=e.getCachedState();int count=b.kind==ComponentBlock.Kind.WIND?0:ComponentPins.count(b.kind);
        var pin=consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/copper.png")));
        for(int i=0;i<count;i++){
            var at=ComponentGeometry.local(Direction.UP,s.get(ComponentBlock.TURN),ComponentPins.u(b.kind,i),ComponentPins.v(b.kind,i),1);
            ComponentRenderer.centeredBox(pin,m,at.x-.035,.93,at.z-.035,at.x+.035,.965,at.z+.035,light,overlay);
        }
    }
    private static void blade(VertexConsumer out,Matrix4f m,int light,int overlay){
        // Swept, tapered airfoil outline; its envelope stays inside the physical rotor disk.
        double[][] stations={{.12,-.12,.12,.11},{1.2,-.43,.18,.10},{3.3,-.23,.06,.055},{4.8,-.08,-.045,.018}};
        for(int s=1;s<stations.length;s++){
            var a=stations[s-1];var b=stations[s];
            Vec3d[] v={new Vec3d(a[1],a[0],-a[3]),new Vec3d(a[2],a[0],-a[3]),new Vec3d(b[2],b[0],-b[3]),new Vec3d(b[1],b[0],-b[3]),new Vec3d(a[1],a[0],a[3]),new Vec3d(a[2],a[0],a[3]),new Vec3d(b[2],b[0],b[3]),new Vec3d(b[1],b[0],b[3])};
            for(int[] face:new int[][]{{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}}){
                var normal=v[face[1]].subtract(v[face[0]]).crossProduct(v[face[2]].subtract(v[face[0]])).normalize();
                for(int i=0;i<4;i++){var q=v[face[i]];out.vertex(m,(float)q.x,(float)q.y,(float)q.z).color(255,255,255,255).texture(i==0||i==3?0:1,i<2?0:1).overlay(overlay).light(light).normal((float)normal.x,(float)normal.y,(float)normal.z);}
            }
        }
    }
    private static void cylinder(VertexConsumer out,Matrix4f m,AssemblyGeometry.Piece p,int light,int overlay){
        for(int i=0;i<p.sides();i++){
            double a=i*Math.PI*2/p.sides(),b=(i+1d)*Math.PI*2/p.sides();
            var aa=new Vec3d(p.a().x+Math.cos(a)*p.radius(),p.a().y,p.a().z+Math.sin(a)*p.radius());
            var bb=new Vec3d(p.a().x+Math.cos(b)*p.radius(),p.a().y,p.a().z+Math.sin(b)*p.radius());
            var aaTop=new Vec3d(p.b().x+Math.cos(a)*p.topRadius(),p.b().y,p.b().z+Math.sin(a)*p.topRadius());
            var bbTop=new Vec3d(p.b().x+Math.cos(b)*p.topRadius(),p.b().y,p.b().z+Math.sin(b)*p.topRadius());
            Vec3d[][] faces={{aa,aaTop,bbTop,bb},{p.b(),bbTop,aaTop,p.b()},{p.a(),aa,bb,p.a()}};
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
