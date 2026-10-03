package top.lacrus.zephyrzeno.electron.client;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.render.*;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockRenderView;
import org.joml.Matrix4f;

/** Vanilla animated atlas sprites, resource-pack aware biome tint, contained geometry only. */
public final class ContainedWater {
    public static void draw(VertexConsumerProvider out,Matrix4f m,BlockRenderView world,BlockPos pos,Box box,boolean flowing,int light,int overlay){
        var handler=FluidRenderHandlerRegistry.INSTANCE.get(Fluids.WATER);var state=Fluids.WATER.getDefaultState();
        var sprites=handler.getFluidSprites(world,pos,state);int color=handler.getFluidColor(world,pos,state);
        var v=out.getBuffer(RenderLayers.getFluidLayer(state));
        var a=new Vec3d(box.minX,box.minY,box.minZ);var b=new Vec3d(box.maxX,box.maxY,box.maxZ);
        Vec3d[] p={a,new Vec3d(b.x,a.y,a.z),new Vec3d(b.x,b.y,a.z),new Vec3d(a.x,b.y,a.z),new Vec3d(a.x,a.y,b.z),new Vec3d(b.x,a.y,b.z),b,new Vec3d(a.x,b.y,b.z)};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};
        for(int f=0;f<faces.length;f++){
            var indices=faces[f];var n=p[indices[1]].subtract(p[indices[0]]).crossProduct(p[indices[2]].subtract(p[indices[0]])).normalize();
            var sprite=sprites[flowing||f<4?1:0];float shade=f==4?1:f==5?.5f:f<2?.8f:.6f;
            // Tile each face per world block instead of stretching one 16px tile over a reservoir.
            double width=p[indices[1]].distanceTo(p[indices[0]]),height=p[indices[3]].distanceTo(p[indices[0]]);
            int nx=Math.max(1,(int)Math.ceil(width)),ny=Math.max(1,(int)Math.ceil(height));
            for(int x=0;x<nx;x++)for(int y=0;y<ny;y++)for(int j=0;j<4;j++){
                double u=(x+(j==1||j==2?1:0))/(double)nx,t=(y+(j>=2?1:0))/(double)ny;
                var q=p[indices[0]].add(p[indices[1]].subtract(p[indices[0]]).multiply(u)).add(p[indices[3]].subtract(p[indices[0]]).multiply(t));
                v.vertex(m,(float)q.x,(float)q.y,(float)q.z).color((int)((color>>16&255)*shade),(int)((color>>8&255)*shade),(int)((color&255)*shade),255)
                    .texture(sprite.getFrameU((float)((j==1||j==2?width/nx:0))),sprite.getFrameV((float)(j>=2?height/ny:0))).light(light).normal((float)n.x,(float)n.y,(float)n.z);
            }
        }
    }
    private ContainedWater(){}
}
