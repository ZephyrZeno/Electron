package top.lacrus.zephyrzeno.electron.multiblock;

import java.util.*;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.ComponentBlock.Kind;

/** Immutable, texture-batched static vertices. The render loop performs no geometry construction. */
public final class AssemblyMesh {
    public record Batch(String texture,float[] vertices){}
    public record Mesh(List<Batch> batches,int vertices){}
    private record Key(Kind kind,int height,boolean tower,int turn,boolean distant){}
    private static final LinkedHashMap<Key,Mesh> CACHE=new LinkedHashMap<>(16,.75f,true);
    private static int cachedFloats;
    private static final Set<String> PANELS=Set.of("hatch","service_panel","console_panel","room_sign","intake_sign","instrument_glass");
    private final Map<String,FloatArrayList> batches=new LinkedHashMap<>();
    public static synchronized Mesh get(Kind kind,int height,boolean tower,int turn,boolean distant){
        var key=new Key(kind,height,tower,turn&3,distant);var found=CACHE.get(key);if(found!=null)return found;
        var builder=new AssemblyMesh();for(var piece:AssemblyGeometry.model(kind,height,tower,turn).pieces())builder.piece(piece,distant);
        var list=new ArrayList<Batch>();int count=0;for(var entry:builder.batches.entrySet()){var data=entry.getValue().toFloatArray();list.add(new Batch(entry.getKey(),data));count+=data.length/8;}
        var result=new Mesh(List.copyOf(list),count);CACHE.put(key,result);cachedFloats+=count*8;
        while(CACHE.size()>48||cachedFloats>8_000_000&&CACHE.size()>1){var it=CACHE.entrySet().iterator();cachedFloats-=it.next().getValue().vertices*8;it.remove();}
        return result;
    }
    private void vertex(String texture,Vec3d p,double u,double v,Vec3d normal){
        var out=batches.computeIfAbsent(texture,k->new FloatArrayList());out.add((float)p.x);out.add((float)p.y);out.add((float)p.z);out.add((float)u);out.add((float)v);out.add((float)normal.x);out.add((float)normal.y);out.add((float)normal.z);
    }
    private void quad(String texture,Vec3d[] points,boolean tiled){
        var normal=points[1].subtract(points[0]).crossProduct(points[2].subtract(points[0])).normalize();
        double width=points[0].distanceTo(points[1]),height=points[0].distanceTo(points[3]);
        int nx=tiled?Math.max(1,(int)Math.ceil(width)):1,ny=tiled?Math.max(1,(int)Math.ceil(height)):1;
        for(int x=0;x<nx;x++)for(int y=0;y<ny;y++)for(int j=0;j<4;j++){
            int dx=j==1||j==2?1:0,dy=j>=2?1:0;double u=(x+dx)/(double)nx,v=(y+dy)/(double)ny;
            var p=points[0].lerp(points[1],u).lerp(points[3].lerp(points[2],u),v);
            vertex(texture,p,tiled?dx*Math.min(1,width):dx,tiled?dy*Math.min(1,height):dy,normal);
        }
    }
    private void piece(AssemblyGeometry.Piece p,boolean distant){
        if(distant&&p.beam()&&p.sides()<=4&&p.radius()<.028)return;
        if(p.sides()>4){cylinder(p,distant?Math.max(8,p.sides()/2):p.sides());return;}
        Vec3d[] corners;
        if(p.beam()){
            var direction=p.b().subtract(p.a()).normalize();var u=direction.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-8)u=direction.crossProduct(new Vec3d(1,0,0));
            u=u.normalize().multiply(p.radius());var v=direction.crossProduct(u).normalize().multiply(p.radius());
            corners=new Vec3d[]{p.a().add(u).add(v),p.a().subtract(u).add(v),p.a().subtract(u).subtract(v),p.a().add(u).subtract(v),p.b().add(u).add(v),p.b().subtract(u).add(v),p.b().subtract(u).subtract(v),p.b().add(u).subtract(v)};
            for(var f:SIDES)quad(p.texture(),new Vec3d[]{corners[f[0]],corners[f[1]],corners[f[2]],corners[f[3]]},false);
        }else{
            var b=new Box(p.a(),p.b());
            corners=new Vec3d[]{new Vec3d(b.minX,b.minY,b.minZ),new Vec3d(b.maxX,b.minY,b.minZ),new Vec3d(b.maxX,b.maxY,b.minZ),new Vec3d(b.minX,b.maxY,b.minZ),new Vec3d(b.minX,b.minY,b.maxZ),new Vec3d(b.maxX,b.minY,b.maxZ),new Vec3d(b.maxX,b.maxY,b.maxZ),new Vec3d(b.minX,b.maxY,b.maxZ)};
            for(var f:BOX_SIDES)quad(p.texture(),new Vec3d[]{corners[f[0]],corners[f[1]],corners[f[2]],corners[f[3]]},!PANELS.contains(p.texture()));
        }
    }
    private static final int[][] SIDES={{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7},{0,3,2,1},{4,5,6,7}};
    private static final int[][] BOX_SIDES={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};
    private void cylinder(AssemblyGeometry.Piece p,int sides){
        var axis=p.b().subtract(p.a()).normalize();var u=axis.crossProduct(new Vec3d(0,0,1));if(u.lengthSquared()<1e-8)u=axis.crossProduct(new Vec3d(1,0,0));u=u.normalize();var v=u.crossProduct(axis).normalize();
        for(int i=0;i<sides;i++){
            double a=i*Math.PI*2/sides,b=(i+1d)*Math.PI*2/sides;
            var radial0=u.multiply(Math.cos(a)).add(v.multiply(Math.sin(a)));var radial1=u.multiply(Math.cos(b)).add(v.multiply(Math.sin(b)));
            var lo0=p.a().add(radial0.multiply(p.radius()));var lo1=p.a().add(radial1.multiply(p.radius()));
            var hi0=p.b().add(radial0.multiply(p.topRadius()));var hi1=p.b().add(radial1.multiply(p.topRadius()));
            var normal=radial0.add(radial1).normalize().add(axis.multiply((p.radius()-p.topRadius())/Math.max(.0001,p.a().distanceTo(p.b())))).normalize();
            vertex(p.texture(),lo0,i/(double)sides,1,normal);vertex(p.texture(),hi0,i/(double)sides,0,normal);vertex(p.texture(),hi1,(i+1d)/sides,0,normal);vertex(p.texture(),lo1,(i+1d)/sides,1,normal);
            if(p.texture().equals("cooling_concrete"))continue;
            for(int top=0;top<2;top++){
                var center=top==1?p.b():p.a();var first=top==1?hi1:lo0;var second=top==1?hi0:lo1;var n=axis.multiply(top==1?1:-1);double radius=top==1?p.topRadius():p.radius();
                for(var q:new Vec3d[]{center,first,second,center}){var rel=q.subtract(center);vertex(p.texture(),q,.5+rel.dotProduct(u)/(2*Math.max(radius,1e-7)),.5+rel.dotProduct(v)/(2*Math.max(radius,1e-7)),n);}
            }
        }
    }
    private AssemblyMesh(){}
}
