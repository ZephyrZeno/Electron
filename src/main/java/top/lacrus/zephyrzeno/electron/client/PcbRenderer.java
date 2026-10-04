package top.lacrus.zephyrzeno.electron.client;

import net.minecraft.client.render.*;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.ComponentBlock;
import top.lacrus.zephyrzeno.electron.pcb.*;

/** Miniature geometry follows the stored design, including milled holes, rotated pins and live LEDs. */
public final class PcbRenderer {
    public static void draw(PcbSocketEntity e,VertexConsumerProvider out,Matrix4f m,int light,int overlay){
        if(!e.plantItems.get(0).isOf(Pcb.BOARD))return;var l=e.circuit().layout;var face=e.getCachedState().get(ComponentBlock.FACING);int turn=e.getCachedState().get(ComponentBlock.TURN);boolean broken=e.getCachedState().get(ComponentBlock.BROKEN);
        var substrate=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/pcb/laminate.png")));
        // Merge adjacent substrate cells in each row to keep holes exact without 192 separate boxes.
        for(int y=0;y<l.height();y++)for(int x=0;x<l.width();){if(!l.contains(x,y)){x++;continue;}int first=x;while(x<l.width()&&l.contains(x,y))x++;box(l,substrate,m,face,turn,first-.48,y-.48,x-.5,y+.5,-.23,-.06,light,overlay);}
        var copper=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/pcb/gold_trace.png")));
        for(var t:e.circuit().surfaceCopper)box(l,copper,m,face,turn,t.x0()-.045,t.y0()-.045,t.x1()+.045,t.y1()+.045,-.27,-.235,light,overlay);
        var plated=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/pcb/solder_joint.png")));
        for(int at:l.vias()){double x=PcbLayout.x(at),y=PcbLayout.y(at);box(l,plated,m,face,turn,x-.11,y-.11,x+.11,y+.11,-.31,-.27,light,overlay);}
        for(int at:l.ports()){double x=PcbLayout.x(at),y=PcbLayout.y(at);box(l,copper,m,face,turn,x-.3,y-.32,x+.3,y+.32,-.31,-.27,light,overlay);}
        for(int i=0;i<l.parts().size();i++){
            var p=l.parts().get(i);String tex=broken?"scorched":"pcb/"+(!p.type().digital()?p.type()==PcbLayout.Type.LED?(e.leds.get(i)?"led_on":"led_off"):"resistor":"ic_package");
            var body=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/"+tex+".png")));
            double halfX=p.type().digital()?.52:.65,halfY=p.type().digital()?.52:.28;
            if((p.turn()&1)!=0){double swap=halfX;halfX=halfY;halfY=swap;}
            box(l,body,m,face,turn,p.x()+1-halfX,p.y()+1-halfY,p.x()+1+halfX,p.y()+1+halfY,-.76,-.28,p.type()==PcbLayout.Type.LED&&e.leds.get(i)?LightmapTextureManager.MAX_LIGHT_COORDINATE:light,overlay);
            if(p.type()==PcbLayout.Type.DISPLAY||p.type()==PcbLayout.Type.DIAL||p.type()==PcbLayout.Type.SIGN)DisplayRenderer.glyph(out,m,face,turn,12.22,PcbSocketEntity.u(l,p.x()+.5),PcbSocketEntity.v(l,p.y()+.5),PcbSocketEntity.u(l,1)-PcbSocketEntity.u(l,0),PcbSocketEntity.v(l,1)-PcbSocketEntity.v(l,0),broken||i>=e.displays.length?-1:p.type()==PcbLayout.Type.SIGN?e.displays[i]==1?16:-1:e.displays[i],p.turn(),light,overlay);
            if(p.type()==PcbLayout.Type.RGB){int color=broken||i>=e.displays.length?0:Math.max(0,e.displays[i]);var lens=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/pcb/rgb_"+color+".png")));box(l,lens,m,face,turn,p.x()+.58,p.y()+.58,p.x()+1.42,p.y()+1.42,-.82,-.77,color==0?light:LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay);}
            if(p.type()==PcbLayout.Type.PIXEL)for(int row=0;row<8;row++)for(int col=0;col<8;col++){boolean on=!broken&&i<e.pixels.length&&(e.pixels[i]&(1L<<(63-row*8-col)))!=0;double u=(col+.5)/8,v=(row+.5)/8;if(face.getAxis()!=Direction.Axis.Y)v=1-v;if(face==Direction.NORTH||face==Direction.EAST)u=1-u;for(int k=0;k<p.turn();k++){double old=u;u=1-v;v=old;}var pixel=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/display/segment_"+(on?"on":"off")+".png")));double a=p.x()+.48+u*1.04,b=p.y()+.48+v*1.04;box(l,pixel,m,face,turn,a-.052,b-.052,a+.052,b+.052,-.79,-.77,on?LightmapTextureManager.MAX_LIGHT_COORDINATE:light,overlay);}
            if(p.type()==PcbLayout.Type.SWITCH){var handle=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/pcb/brushed_steel.png")));double u=e.circuit().controls[i]==0?.7:1.3,v=1;for(int k=0;k<p.turn();k++){double old=u;u=2-v;v=old;}double hx=(p.turn()&1)==0?.15:.28,hy=(p.turn()&1)==0?.28:.15;box(l,handle,m,face,turn,p.x()+u-hx,p.y()+v-hy,p.x()+u+hx,p.y()+v+hy,-.97,-.77,light,overlay);}
            var solder=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/pcb/solder_joint.png")));
            for(int j=0;j<p.type().pins();j++){
                int at=p.pin(j);double px=PcbLayout.x(at),py=PcbLayout.y(at),cx=p.x()+1,cy=p.y()+1;
                box(l,solder,m,face,turn,px-.16,py-.16,px+.16,py+.16,-.38,-.27,light,overlay);
                // Individual gull-wing leads connect the package to its actual
                // rotated pads, instead of leaving decorative floating squares.
                double edgeY=Math.clamp(py,cy-halfY,cy+halfY);
                box(l,solder,m,face,turn,Math.min(cx,px)-.055,edgeY-.055,Math.max(cx,px)+.055,edgeY+.055,-.42,-.33,light,overlay);
                box(l,solder,m,face,turn,px-.055,Math.min(edgeY,py)-.055,px+.055,Math.max(edgeY,py)+.055,-.40,-.31,light,overlay);
            }
        }
    }
    private static void box(PcbLayout l,VertexConsumer out,Matrix4f m,Direction face,int turn,double a,double b,double c,double d,double z0,double z1,int light,int overlay){ComponentRenderer.cuboid(out,m,face,turn,13,PcbSocketEntity.u(l,a),PcbSocketEntity.v(l,b),z0,PcbSocketEntity.u(l,c),PcbSocketEntity.v(l,d),z1,0,light,overlay);}
    private PcbRenderer(){}
}
