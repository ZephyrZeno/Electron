package top.lacrus.zephyrzeno.electron.power;

import java.util.List;
import org.junit.jupiter.api.Test;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.ComponentBlock;
import static org.junit.jupiter.api.Assertions.*;

class CabinetSurfaceTest {
    @Test void printedCarriersHaveOneTopSurfaceWithoutOverlayOrAirGap(){
        for(var kind:List.of(ComponentBlock.Kind.THERMAL,ComponentBlock.Kind.NUCLEAR,ComponentBlock.Kind.MAINS)){
            for(boolean wind:new boolean[]{false,true}){
                if(wind&&kind!=ComponentBlock.Kind.MAINS)continue;
                var parts=WiringCabinet.parts(kind,wind,true);
                var surfaces=WiringCabinet.surfaces(kind,wind,true);
                for(var part:parts){
                    if(part.pin()<0)continue;
                    var b=part.box();
                    var top=surfaces.stream().filter(s->s.side()==Direction.UP&&Math.abs(s.box().maxY-b.maxY)<1e-9&&b.contains(s.box().getCenter())).toList();
                    double area=top.stream().mapToDouble(s->s.box().getLengthX()*s.box().getLengthZ()).sum();
                    assertEquals(b.getLengthX()*b.getLengthZ(),area,1e-9);
                    assertEquals(1,top.stream().filter(s->s.texture().contains("_pin_")).count());
                    for(int i=0;i<top.size();i++)for(int j=i+1;j<top.size();j++){
                        Box a=top.get(i).box(),c=top.get(j).box();
                        double overlap=Math.max(0,Math.min(a.maxX,c.maxX)-Math.max(a.minX,c.minX))*Math.max(0,Math.min(a.maxZ,c.maxZ)-Math.max(a.minZ,c.minZ));
                        assertEquals(0,overlap,1e-9);
                    }
                }
            }
        }
    }
    @Test void shutterStateDoesNotMovePrintedTerminals(){
        var open=WiringCabinet.surfaces(ComponentBlock.Kind.MAINS,true,true).stream().filter(s->s.texture().contains("_pin_")).toList();
        var closed=WiringCabinet.surfaces(ComponentBlock.Kind.MAINS,true,false).stream().filter(s->s.texture().contains("_pin_")).toList();
        assertEquals(2,open.size());assertEquals(open,closed);
    }
    @Test void cableGlandsDoNotCoverPrintedRegions(){
        for(var kind:List.of(ComponentBlock.Kind.THERMAL,ComponentBlock.Kind.MAINS)){
            var parts=WiringCabinet.parts(kind,false,true);
            for(var surface:WiringCabinet.surfaces(kind,false,true)){
                if(!surface.texture().contains("_pin_"))continue;
                var label=surface.box();
                for(var part:parts){
                    var b=part.box();if(b.maxY<=label.maxY+1e-9)continue;
                    double overlap=Math.max(0,Math.min(b.maxX,label.maxX)-Math.max(b.minX,label.minX))*Math.max(0,Math.min(b.maxZ,label.maxZ)-Math.max(b.minZ,label.minZ));
                    assertEquals(0,overlap,1e-9,"A screw or cable gland obscures the printed face");
                }
            }
        }
    }
}
