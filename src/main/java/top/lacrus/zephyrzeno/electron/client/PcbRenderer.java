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
        for(int y=0;y<l.height();y++)for(int x=0;x<l.width();){if(!l.contains(x,y)){x++;continue;}int first=x;while(x<l.width()&&l.contains(x,y))x++;box(substrate,m,face,turn,first-.48,y-.48,x-.5,y+.5,-.23,-.06,light,overlay);}
        var copper=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/pcb/gold_trace.png")));
        for(var t:l.tracks())box(copper,m,face,turn,PcbLayout.x(t.a())-.06,PcbLayout.y(t.a())-.06,PcbLayout.x(t.b())+.06,PcbLayout.y(t.b())+.06,-.27,-.235,light,overlay);
        for(int i=0;i<l.parts().size();i++){
            var p=l.parts().get(i);String tex=broken?"scorched":"pcb/"+(!p.type().digital()?p.type()==PcbLayout.Type.LED?((e.ledMask&(1<<i))!=0?"led_on":"led_off"):"resistor":"ic_package");
            var body=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/"+tex+".png")));
            double halfX=p.type().digital()?.52:.65,halfY=p.type().digital()?.52:.28;
            if((p.turn()&1)!=0){double swap=halfX;halfX=halfY;halfY=swap;}
            box(body,m,face,turn,p.x()+1-halfX,p.y()+1-halfY,p.x()+1+halfX,p.y()+1+halfY,-.76,-.28,p.type()==PcbLayout.Type.LED&&(e.ledMask&(1<<i))!=0?LightmapTextureManager.MAX_LIGHT_COORDINATE:light,overlay);
            if(p.type()==PcbLayout.Type.DISPLAY)DisplayRenderer.glyph(out,m,face,turn,12.22,2.8+(p.x()+.5)*.66,3.8+(p.y()+.5)*.66,.66,.66,broken||i>=e.displays.length?-1:e.displays[i],p.turn(),light,overlay);
            var solder=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/pcb/solder_joint.png")));
            for(int j=0;j<p.type().pins();j++){
                int at=p.pin(j);double px=PcbLayout.x(at),py=PcbLayout.y(at),cx=p.x()+1,cy=p.y()+1;
                box(solder,m,face,turn,px-.16,py-.16,px+.16,py+.16,-.38,-.27,light,overlay);
                // Individual gull-wing leads connect the package to its actual
                // rotated pads, instead of leaving decorative floating squares.
                double edgeY=Math.clamp(py,cy-halfY,cy+halfY);
                box(solder,m,face,turn,Math.min(cx,px)-.055,edgeY-.055,Math.max(cx,px)+.055,edgeY+.055,-.42,-.33,light,overlay);
                box(solder,m,face,turn,px-.055,Math.min(edgeY,py)-.055,px+.055,Math.max(edgeY,py)+.055,-.40,-.31,light,overlay);
            }
        }
    }
    private static void box(VertexConsumer out,Matrix4f m,Direction face,int turn,double a,double b,double c,double d,double z0,double z1,int light,int overlay){ComponentRenderer.cuboid(out,m,face,turn,13,2.8+a*.66,3.8+b*.66,z0,2.8+c*.66,3.8+d*.66,z1,0,light,overlay);}
    private PcbRenderer(){}
}
