package top.lacrus.zephyrzeno.electron.client;

import java.util.*;
import net.minecraft.client.render.*;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.display.*;
import top.lacrus.zephyrzeno.electron.foundry.*;

public final class DisplayRenderer {
    public static void glyph(VertexConsumerProvider out,Matrix4f m,Direction face,int turn,double inset,double u,double v,double w,double h,int value,int light,int overlay){glyph(out,m,face,turn,inset,u,v,w,h,value,0,light,overlay);}
    public static void glyph(VertexConsumerProvider out,Matrix4f m,Direction face,int turn,double inset,double u,double v,double w,double h,int value,int packageTurn,int light,int overlay){
        int mask=SevenSegment.mask(value);for(int i=0;i<7;i++){
            var r=SevenSegment.SEGMENTS[i];double a=r[0],b=r[1],c=r[2],d=r[3];
            // Local wall V points upwards; screen row coordinates point downwards.
            // North/east mounts also reverse the camera's horizontal basis.
            if(face.getAxis()!=Direction.Axis.Y){b=1-b;d=1-d;}
            if(face==Direction.NORTH||face==Direction.EAST){a=1-a;c=1-c;}
            for(int j=0;j<(packageTurn&3);j++){double oldA=a,oldC=c;a=1-b;c=1-d;b=oldA;d=oldC;}
            boolean on=(mask&(1<<i))!=0;var buffer=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/display/"+(on?"segment_on":"segment_off")+".png")));
            ComponentRenderer.cuboid(buffer,m,face,turn,13,u+Math.min(a,c)*w,v+Math.min(b,d)*h,inset-13-.025,u+Math.max(a,c)*w,v+Math.max(b,d)*h,inset-13,0,on?LightmapTextureManager.MAX_LIGHT_COORDINATE:light,overlay);
        }
    }
    private record Screen(BlockPos root,int width,int height){}
    private static final Map<BlockPos,Screen> screens=new HashMap<>();
    private static Object lastWorld;private static long lastTick=Long.MIN_VALUE;
    private static Screen screen(DisplayEntity e){long tick=e.getWorld().getTime();if(lastWorld!=e.getWorld()||tick!=lastTick){screens.clear();lastWorld=e.getWorld();lastTick=tick;}if(screens.containsKey(e.getPos()))return screens.get(e.getPos());var s=e.getCachedState();var u=DisplayEntity.axis(s,false);var v=DisplayEntity.axis(s,true);var points=new HashMap<BlockPos,int[]>();var queue=new ArrayDeque<BlockPos>();points.put(e.getPos(),new int[]{0,0});queue.add(e.getPos());int minU=0,maxU=0,minV=0,maxV=0;
        while(!queue.isEmpty()&&points.size()<=256){var p=queue.remove();int[] at=points.get(p);for(int i=0;i<4;i++){int du=i==0?1:i==1?-1:0,dv=i==2?1:i==3?-1:0;var n=p.offset(u,du).offset(v,dv);if(!points.containsKey(n)&&e.joins(n)){int a=at[0]+du,b=at[1]+dv;points.put(n,new int[]{a,b});queue.add(n);minU=Math.min(minU,a);maxU=Math.max(maxU,a);minV=Math.min(minV,b);maxV=Math.max(maxV,b);}}}
        boolean rectangle=points.size()==(maxU-minU+1)*(maxV-minV+1)&&points.size()<=256;
        var group=new Screen(e.getPos().offset(u,minU).offset(v,minV),maxU-minU+1,maxV-minV+1);for(var p:points.keySet())screens.put(p,rectangle?group:new Screen(p,1,1));return screens.get(e.getPos());
    }
    public static void draw(DisplayEntity e,VertexConsumerProvider out,Matrix4f m,int light,int overlay){var s=e.getCachedState();var face=s.get(ComponentBlock.FACING);int turn=s.get(ComponentBlock.TURN);int value=e.role()==DisplayBlock.Role.DIAL?e.value:e.glyph();
        if(e.role()==DisplayBlock.Role.PIXEL){
            var glass=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/display/glass.png")));ComponentRenderer.cuboid(glass,m,face,turn,13,.1,.1,-.32,15.9,15.9,-.30,0,light,overlay);
            for(int row=0;row<8;row++)for(int col=0;col<8;col++){boolean on=e.value>=0&&(e.pixelBits&(1L<<(63-row*8-col)))!=0;double a=col*2+.2,b=row*2+.2,c=a+1.6,d=b+1.6;if(face.getAxis()!=Direction.Axis.Y){b=16-b;d=16-d;}if(face==Direction.NORTH||face==Direction.EAST){a=16-a;c=16-c;}var pixel=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/display/segment_"+(on?"on":"off")+".png")));ComponentRenderer.cuboid(pixel,m,face,turn,13,Math.min(a,c),Math.min(b,d),-.35,Math.max(a,c),Math.max(b,d),-.33,0,on?LightmapTextureManager.MAX_LIGHT_COORDINATE:light,overlay);}return;
        }
        if(e.role()!=DisplayBlock.Role.PANEL){glyph(out,m,face,turn,12.15,4.5,3.3,6.5,8.5,value,light,overlay);return;}
        var screen=screen(e);if(!screen.root.equals(e.getPos()))return;double w=16d*screen.width,h=16d*screen.height;var bezel=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/display/frame.png")));ComponentRenderer.cuboid(bezel,m,face,turn,13,0,0,-.30,w,h,-.04,0,light,overlay);var glass=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/display/glass.png")));ComponentRenderer.cuboid(glass,m,face,turn,13,.7,.7,-.32,w-.7,h-.7,-.30,0,light,overlay);
        double gh=Math.min(h-3,(w-3)*1.65),gw=gh/1.65;glyph(out,m,face,turn,12.65,(w-gw)/2,(h-gh)/2,gw,gh,value,light,overlay);
    }
    private DisplayRenderer(){}
}
