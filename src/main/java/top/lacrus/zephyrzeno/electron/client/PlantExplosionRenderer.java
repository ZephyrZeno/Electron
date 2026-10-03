package top.lacrus.zephyrzeno.electron.client;

import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.power.*;

/** One event creates a bounded local effect; all sounds are played by the server at the vessel. */
public final class PlantExplosionRenderer {
    private record Accident(Vec3d at,double groundY,int kind,long seed,long born,String dimension,int[] impacts){}
    private static final Deque<Accident> active=new ArrayDeque<>();
    public static void clear(){active.clear();}
    public static void receive(NbtCompound n){var client=MinecraftClient.getInstance();if(client.world==null)return;int kind=n.getInt("kind");var at=new Vec3d(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"));if(kind<0||kind>2||!Double.isFinite(at.lengthSquared()))return;String dim=n.getString("dimension");if(!dim.equals(client.world.getRegistryKey().getValue().toString()))return;while(active.size()>=4)active.removeFirst();int[] hits=new int[28];Arrays.fill(hits,Integer.MAX_VALUE);double groundY=n.contains("ground_y")?n.getDouble("ground_y"):at.y;if(!Double.isFinite(groundY))return;active.addLast(new Accident(at,groundY,kind,n.getLong("seed"),client.world.getTime(),dim,hits));}
    public static void render(WorldRenderContext ctx){var client=MinecraftClient.getInstance();var world=client.world;if(world==null||ctx.matrixStack()==null||ctx.consumers()==null)return;double now=world.getTime()+ctx.tickCounter().getTickDelta(false);var camera=ctx.camera().getPos();var m=ctx.matrixStack().peek().getPositionMatrix();var q=ctx.camera().getRotation();var vr=new Vector3f(1,0,0).rotate(q);var vu=new Vector3f(0,1,0).rotate(q);var right=new Vec3d(vr.x,vr.y,vr.z);var up=new Vec3d(vu.x,vu.y,vu.z);
        active.removeIf(a->now-a.born>=ExplosionPlume.lifetime(a.kind)||!world.getRegistryKey().getValue().toString().equals(a.dimension));
        for(var a:active){double age=now-a.born;if(age<0||a.at.squaredDistanceTo(camera)>224*224)continue;int light=WorldRenderer.getLightmapCoordinates(world,BlockPos.ofFloored(a.at).up());
            var puffs=new ArrayList<>(ExplosionPlume.sample(a.kind,age,a.seed));puffs.sort(Comparator.comparingDouble((ExplosionPlume.Puff p)->position(a,p).squaredDistanceTo(camera)).reversed());
            for(var puff:puffs){if(puff.alpha()<.005)continue;boolean hot=puff.heat()>.25;var buffer=ctx.consumers().getBuffer(hot?RenderLayer.getEntityTranslucentEmissive(Electron.id("textures/effect/plant_smoke.png")):RenderLayer.getEntityTranslucent(Electron.id("textures/effect/plant_smoke.png")));float h=puff.heat();int r=(int)(160+95*h),g=(int)(164-36*h),b=(int)(170-138*h);billboard(buffer,m,position(a,puff).subtract(camera),right,up,puff.radius(),puff.tile(),r,g,b,puff.alpha(),hot?LightmapTextureManager.MAX_LIGHT_COORDINATE:light);}
            if(age<7){var out=ctx.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(Electron.id("textures/effect/blast_flash.png")));double radius=(a.kind==2?9:a.kind==1?4:1.7)*(1+age*.1);billboard(out,m,a.at.subtract(camera),right,up,radius,-1,255,229,179,(float)((1-age/7)*.8),LightmapTextureManager.MAX_LIGHT_COORDINATE);}
            if(age<28){var out=ctx.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(Electron.id("textures/effect/filament.png")));double radius=age*(a.kind==2?1.7:a.kind==1?.8:.4),width=.15+age*.015;float alpha=(float)((1-age/28)*.5);for(int i=0;i<64;i++){double aa=i*2*Math.PI/64,bb=(i+1)*2*Math.PI/64;var center=new Vec3d(a.at.x,a.groundY,a.at.z).subtract(camera);Vec3d[] points={center.add(Math.cos(aa)*(radius-width),0,Math.sin(aa)*(radius-width)),center.add(Math.cos(aa)*(radius+width),0,Math.sin(aa)*(radius+width)),center.add(Math.cos(bb)*(radius+width),0,Math.sin(bb)*(radius+width)),center.add(Math.cos(bb)*(radius-width),0,Math.sin(bb)*(radius-width))};quad(out,m,points,0,1,240,211,165,alpha,LightmapTextureManager.MAX_LIGHT_COORDINATE);}}
            if(age<65&&client.player!=null){var debris=ctx.consumers().getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/assembled/galvanized.png")));int tick=(int)age;for(int i=0;i<a.impacts.length;i++){if(a.impacts[i]<=tick)continue;var p=a.at.add(ExplosionPlume.debris(a.seed,i,age,a.kind));var prev=a.at.add(ExplosionPlume.debris(a.seed,i,Math.max(0,age-1),a.kind));if(world.raycast(new RaycastContext(prev,p,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,client.player)).getType()!=HitResult.Type.MISS){a.impacts[i]=tick;continue;}billboard(debris,m,p.subtract(camera),right,up,.06+(i%4)*.025,-1,180,170,155,1,light);}}
        }
    }
    private static Vec3d position(Accident a,ExplosionPlume.Puff p){return p.offset().add(a.at.x,p.ground()?a.groundY:a.at.y,a.at.z);}
    private static void billboard(VertexConsumer out,Matrix4f m,Vec3d p,Vec3d right,Vec3d up,double radius,int tile,int r,int g,int b,float alpha,int light){var x=right.multiply(radius);var y=up.multiply(radius);Vec3d[] points={p.subtract(x).subtract(y),p.add(x).subtract(y),p.add(x).add(y),p.subtract(x).add(y)};quad(out,m,points,tile<0?0:tile/4f,tile<0?1:(tile+1)/4f,r,g,b,alpha,light);}
    private static void quad(VertexConsumer out,Matrix4f m,Vec3d[] points,float u0,float u1,int r,int g,int b,float alpha,int light){for(int i=0;i<4;i++){var p=points[i];out.vertex(m,(float)p.x,(float)p.y,(float)p.z).color(r,g,b,Math.clamp((int)(alpha*255),0,255)).texture(i==0||i==3?u0:u1,i<2?1:0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0,1,0);}}
    private PlantExplosionRenderer(){}
}
