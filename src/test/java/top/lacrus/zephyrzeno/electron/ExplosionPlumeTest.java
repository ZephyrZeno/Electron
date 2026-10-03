package top.lacrus.zephyrzeno.electron;

import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electron.power.*;

class ExplosionPlumeTest {
    @Test void boundedDeterministicMushroomGrowsAndDissipates() throws Exception {
        double lastHeight=0;var json=new StringBuilder("[");boolean first=true;
        for(int kind=0;kind<3;kind++)for(int age:new int[]{4,30,90,170,300}){
            var puffs=ExplosionPlume.sample(kind,age,901);assertEquals(puffs,ExplosionPlume.sample(kind,age,901));assertTrue(puffs.size()<=128);
            for(var p:puffs){assertTrue(Double.isFinite(p.offset().lengthSquared()+p.radius()));assertTrue(p.radius()>0&&p.radius()<20);assertTrue(p.alpha()>=0&&p.alpha()<=1);assertTrue(p.heat()>=0&&p.heat()<=1);}
            if(kind==2){double height=puffs.stream().mapToDouble(p->p.offset().y).max().orElse(0);assertTrue(height>lastHeight);lastHeight=height;if(age==170){double cap=puffs.stream().filter(p->p.offset().y>height*.7).mapToDouble(p->Math.hypot(p.offset().x,p.offset().z)).max().orElse(0);double stem=puffs.stream().filter(p->p.offset().y>10&&p.offset().y<height*.5).mapToDouble(p->Math.hypot(p.offset().x,p.offset().z)).max().orElse(0);assertTrue(cap>stem*3,"Cap must be visibly wider than stem");}}
            if(!first)json.append(',');first=false;json.append("{\"kind\":").append(kind).append(",\"age\":").append(age).append(",\"puffs\":[");boolean one=true;for(var p:puffs){if(!one)json.append(',');one=false;json.append(String.format(Locale.ROOT,"[%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%d,%d]",p.offset().x,p.offset().y,p.offset().z,p.radius(),p.alpha(),p.heat(),p.tile(),p.ground()?1:0));}json.append("]}");
        }
        for(int kind=0;kind<3;kind++){assertTrue(ExplosionPlume.sample(kind,-1,0).isEmpty());assertTrue(ExplosionPlume.sample(kind,ExplosionPlume.lifetime(kind),0).isEmpty());}
        var path=Path.of("build/asset-review/plant-plume-geometry.json");Files.createDirectories(path.getParent());Files.writeString(path,json.append(']').toString());
    }
    @Test void debrisPositionsDoNotDependOnFrameRate(){for(int i=0;i<28;i++){assertEquals(ExplosionPlume.debris(12,i,20,2),ExplosionPlume.debris(12,i,20,2));assertTrue(ExplosionPlume.debris(12,i,60,2).y<ExplosionPlume.debris(12,i,20,2).y);}}
}
