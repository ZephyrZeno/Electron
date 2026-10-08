package top.lacrus.zephyrzeno.electron.client;

import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.*;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.power.WiringCabinet;
import top.lacrus.zephyrzeno.electron.industrial.Industrial;

final class CabinetRenderer {
    private record Key(ComponentBlock.Kind kind,boolean wind,Direction facing,int turn,boolean open){}
    private record Batch(String texture,float[] vertices){}
    private static final Map<String,RenderLayer> LAYERS=new HashMap<>();
    private static final Map<Key,List<Batch>> MESHES=new HashMap<>();
    static void draw(ComponentEntity e,VertexConsumerProvider consumers,Matrix4f m,int light,int overlay){
        var s=e.getCachedState();var kind=((ComponentBlock)s.getBlock()).kind;
        var normals=new org.joml.Matrix3f(m).invert().transpose();
        var key=new Key(kind,s.isOf(Industrial.WIND_BASE),s.get(ComponentBlock.FACING),s.get(ComponentBlock.TURN),e.cabinetOpen);
        for(var batch:MESHES.computeIfAbsent(key,k->bake(k,s))){
            var out=consumers.getBuffer(LAYERS.computeIfAbsent(batch.texture(),name->RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/"+name+".png"))));
            var data=batch.vertices();
            for(int i=0;i<data.length;i+=8){
                float x=data[i+5],y=data[i+6],z=data[i+7];
                float nx=normals.m00()*x+normals.m10()*y+normals.m20()*z,ny=normals.m01()*x+normals.m11()*y+normals.m21()*z,nz=normals.m02()*x+normals.m12()*y+normals.m22()*z;
                float unit=1/(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                out.vertex(m,data[i],data[i+1],data[i+2]).color(255,255,255,255).texture(data[i+3],data[i+4]).overlay(overlay).light(light)
                        .normal(nx*unit,ny*unit,nz*unit);
            }
        }
    }
    private static List<Batch> bake(Key key,BlockState s){
        var batches=new LinkedHashMap<String,List<Float>>();
        for(var surface:WiringCabinet.surfaces(key.kind(),key.wind(),key.open())){
            var b=surface.box();
            var vertices=batches.computeIfAbsent(surface.texture(),t->new ArrayList<>());
            Vec3d[] points=switch(surface.side()){
                case UP->new Vec3d[]{new Vec3d(b.minX,b.maxY,b.minZ),new Vec3d(b.minX,b.maxY,b.maxZ),new Vec3d(b.maxX,b.maxY,b.maxZ),new Vec3d(b.maxX,b.maxY,b.minZ)};
                case DOWN->new Vec3d[]{new Vec3d(b.minX,b.minY,b.maxZ),new Vec3d(b.minX,b.minY,b.minZ),new Vec3d(b.maxX,b.minY,b.minZ),new Vec3d(b.maxX,b.minY,b.maxZ)};
                case NORTH->new Vec3d[]{new Vec3d(b.minX,b.maxY,b.minZ),new Vec3d(b.maxX,b.maxY,b.minZ),new Vec3d(b.maxX,b.minY,b.minZ),new Vec3d(b.minX,b.minY,b.minZ)};
                case SOUTH->new Vec3d[]{new Vec3d(b.maxX,b.maxY,b.maxZ),new Vec3d(b.minX,b.maxY,b.maxZ),new Vec3d(b.minX,b.minY,b.maxZ),new Vec3d(b.maxX,b.minY,b.maxZ)};
                case WEST->new Vec3d[]{new Vec3d(b.minX,b.maxY,b.maxZ),new Vec3d(b.minX,b.maxY,b.minZ),new Vec3d(b.minX,b.minY,b.minZ),new Vec3d(b.minX,b.minY,b.maxZ)};
                case EAST->new Vec3d[]{new Vec3d(b.maxX,b.maxY,b.minZ),new Vec3d(b.maxX,b.maxY,b.maxZ),new Vec3d(b.maxX,b.minY,b.maxZ),new Vec3d(b.maxX,b.minY,b.minZ)};
            };
            var origin=ComponentGeometry.local(s.get(ComponentBlock.FACING),s.get(ComponentBlock.TURN),0,0,16);
            var canonicalNormal=Vec3d.of(surface.side().getVector());
            var direction=ComponentGeometry.local(s.get(ComponentBlock.FACING),s.get(ComponentBlock.TURN),canonicalNormal.x*16,canonicalNormal.z*16,(1-canonicalNormal.y)*16).subtract(origin);
            for(int i=0;i<4;i++){
                var q=points[i];var p=ComponentGeometry.local(s.get(ComponentBlock.FACING),s.get(ComponentBlock.TURN),q.x*16,q.z*16,(1-q.y)*16);
                float u=i<2?0:1,v=i==1||i==2?1:0;
                if(surface.texture().contains("_pin_")){
                    // Match baked-model face UVs on wall/ceiling mounts, including reflections.
                    if(key.facing()!=Direction.UP)v=1-v;
                    if(key.facing()==Direction.NORTH||key.facing()==Direction.EAST)u=1-u;
                }
                for(float value:new float[]{(float)p.x,(float)p.y,(float)p.z,u,v,(float)direction.x,(float)direction.y,(float)direction.z})vertices.add(value);
            }
        }
        var result=new ArrayList<Batch>();
        batches.forEach((texture,vertices)->{
            float[] data=new float[vertices.size()];for(int i=0;i<data.length;i++)data[i]=vertices.get(i);
            result.add(new Batch(texture,data));
        });
        return List.copyOf(result);
    }
    private CabinetRenderer(){}
}
