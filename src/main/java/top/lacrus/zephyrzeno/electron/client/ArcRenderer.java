package top.lacrus.zephyrzeno.electron.client;

import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.power.*;

/** Camera-facing emissive filaments and ballistic metal sparks; no custom shader or global flash. */
public final class ArcRenderer {
    private record Arc(Vec3d a,Vec3d b,long seed,int kind,float strength,long born,String dimension){}
    private static final Deque<Arc> arcs=new ArrayDeque<>();
    public static void clear(){arcs.clear();}
    public static void receive(NbtCompound n){
        var world=MinecraftClient.getInstance().world;if(world==null)return;
        var a=new Vec3d(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"));var b=new Vec3d(n.getDouble("X"),n.getDouble("Y"),n.getDouble("Z"));
        if(!Double.isFinite(a.lengthSquared()+b.lengthSquared())||a.squaredDistanceTo(b)>4096)return;
        while(arcs.size()>=128)arcs.removeFirst();arcs.addLast(new Arc(a,b,n.getLong("seed"),n.getInt("kind"),Math.clamp(n.getFloat("strength"),.15f,1),world.getTime(),n.getString("dimension")));
    }
    public static void render(WorldRenderContext context){
        var world=MinecraftClient.getInstance().world;if(world==null||context.matrixStack()==null||context.consumers()==null)return;
        double now=world.getTime()+context.tickCounter().getTickDelta(false);var camera=context.camera().getPos();var matrix=context.matrixStack().peek().getPositionMatrix();
        var out=context.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(Electron.id("textures/effect/filament.png")));
        arcs.removeIf(a->now-a.born>16||!world.getRegistryKey().getValue().toString().equals(a.dimension));
        for(var arc:arcs){double age=now-arc.born;if(age<0)continue;
            if(arc.kind==FaultEffects.OVERLOAD||arc.kind==FaultEffects.MELT){double heatAlpha=Math.max(0,1-age/16);var a=arc.a;var b=arc.b;if(arc.kind==FaultEffects.MELT){a=a.add(0,-.004*age*age,0);b=b.add(0,-.009*age*age,0);}ribbon(out,matrix,a,b,camera,.033,64,36,25,(int)(heatAlpha*220));ribbon(out,matrix,a,b,camera,.014,255,115,25,(int)(heatAlpha*220));}
            if(age<5){double alpha=(1-age/5)*(.7+.3*Math.sin(age*9+arc.seed));
                for(var filament:ArcGeometry.create(arc.a,arc.b,arc.seed+((long)age/2)*31)){
                    var points=filament.points();for(int i=1;i<points.size();i++){
                        double width=(.010+arc.strength*.014)*filament.width();
                        ribbon(out,matrix,points.get(i-1),points.get(i),camera,width*3.4,105,145,255,(int)(alpha*70));
                        ribbon(out,matrix,points.get(i-1),points.get(i),camera,width,205,227,255,(int)(alpha*240));
                        ribbon(out,matrix,points.get(i-1),points.get(i),camera,width*.28,255,255,245,(int)(alpha*255));
                    }
                }
            }
            var random=new Random(arc.seed);int count=arc.kind==FaultEffects.BURST?30:arc.kind==FaultEffects.MELT?24:10;
            for(int i=0;i<count;i++){
                var velocity=new Vec3d(random.nextGaussian()*.075,.025+random.nextDouble()*.15,random.nextGaussian()*.075).multiply(.5+arc.strength);
                var end=arc.b.add(velocity.multiply(age)).add(0,-.009*age*age,0);
                // Stop a spark at the first solid surface, instead of drawing streaks through equipment.
                var hit=world.raycast(new net.minecraft.world.RaycastContext(arc.b,end,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,MinecraftClient.getInstance().player));
                if(hit.getType()!=net.minecraft.util.hit.HitResult.Type.MISS)continue;
                var tail=end.subtract(velocity.multiply(.6)).add(0,.018*age*.6,0);
                ribbon(out,matrix,tail,end,camera,.006+arc.strength*.005,255,(int)(230-age*7),65,(int)(255*(1-age/16)));
            }
        }
    }
    private static void ribbon(VertexConsumer out,Matrix4f m,Vec3d a,Vec3d b,Vec3d camera,double width,int r,int g,int blue,int alpha){
        var side=b.subtract(a).crossProduct(camera.subtract(a.lerp(b,.5))).normalize().multiply(width);
        Vec3d[] p={a.subtract(side),a.add(side),b.add(side),b.subtract(side)};
        for(int i=0;i<4;i++){var q=p[i].subtract(camera);out.vertex(m,(float)q.x,(float)q.y,(float)q.z).color(r,g,blue,Math.clamp(alpha,0,255)).texture(i==1||i==2?1:0,i<2?0:1).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0,1,0);}
    }
    private ArcRenderer(){}
}
