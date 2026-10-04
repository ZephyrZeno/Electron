package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import top.lacrus.zephyrzeno.electron.circuit.DcSolver;

class MultilayerPcbTest {
    @Test void layersCrossOnlyAtPlatedViasAndLegacyPointsMigrate(){
        var l=PcbLayout.empty().withLayers(2).route(PcbLayout.point(2,5),PcbLayout.point(12,5),false).route(PcbLayout.point(7,2,1),PcbLayout.point(7,9,1),true);
        assertNotEquals(l.nets()[PcbLayout.point(2,5)],l.nets()[PcbLayout.point(7,2,1)]);
        l=l.via(7,5);assertEquals(l.nets()[PcbLayout.point(2,5)],l.nets()[PcbLayout.point(7,2,1)]);assertEquals(l,PcbLayout.decode(l.encode()));
        var old=PcbLayout.decode("2|3,6,4,0;|16,17;17,18;|16,12|");assertEquals(PcbLayout.point(0,1),old.tracks().getFirst().a());assertEquals(16,old.width());
        assertThrows(IllegalArgumentException.class,()->PcbLayout.empty().port(0,4,4));assertEquals(PcbLayout.point(31,2),PcbLayout.empty().port(0,31,2).ports()[0]);
    }
    @Test void biggerAndMultilayerBoardsPayMoreAndNeedPhysicalSupports(){var small=PcbLayout.empty();var big=small.resize(96,72).withLayers(4);assertEquals(36,big.laminateCost());assertTrue(big.joules()>small.joules()*30);assertEquals(3,big.tilesX());assertEquals(3,big.tilesY());}
    @Test void singleBoardCalculatorUsesOnlyGatesAndActualCopperForEveryInput() throws Exception {
        var layout=SingleBoardCalculator.layout();assertEquals(58,layout.parts().size());assertEquals(4,layout.layers());assertFalse(layout.parts().stream().anyMatch(p->p.type()==PcbLayout.Type.HALF_ADDER));assertTrue(layout.vias().size()>0);assertEquals(layout,PcbLayout.decode(layout.encode()));
        Files.createDirectories(Path.of("build/asset-review"));Files.writeString(Path.of("build/asset-review/single-board-calculator.pcb"),layout.encode());
        for(int op=0;op<2;op++)for(int a=0;a<10;a++)for(int b=0;b<10;b++){
            var c=new PcbCircuit(layout);c.controls[0]=a;c.controls[1]=b;c.controls[2]=op;DcSolver.Result result=null;
            for(int tick=0;tick<80;tick++){var branches=new ArrayList<DcSolver.Branch>();c.stamp("p",new String[]{"V","G","A","B","Y","Z"},branches,tick,false);branches.add(DcSolver.Branch.source("s","V","G",5,.1));result=DcSolver.solve(branches);assertTrue(c.update("p",result,n->true,tick,false)<1.01,"No overloaded internal gates");}
            int actual=c.displays[SingleBoardCalculator.TENS]*10+c.displays[SingleBoardCalculator.ONES];if(c.displays[SingleBoardCalculator.SIGN]==1)actual=-actual;
            assertEquals(op==0?a+b:a-b,actual,"Single PCB A="+a+" B="+b+" op="+op);assertTrue(-result.current("s")>.01,"Board must spend electrical energy");
        }
    }
    private static DcSolver.Result tick(PcbCircuit c,int t,int data,int clk,int latch,int clear,double supply){var edges=new ArrayList<DcSolver.Branch>();c.stamp("p",new String[]{"V","G","A","B","Y","Z"},edges,t,false);edges.add(DcSolver.Branch.source("s","V","G",supply,.01));var p=c.layout.parts().getFirst();int[] levels={data,clk,latch,clear};for(int i=0;i<4;i++)edges.add(DcSolver.Branch.resistor("input"+i,c.net("p",p.pin(i+2)),levels[i]==1?"V":"G",100));var r=DcSolver.solve(edges);c.update("p",r,n->true,t,false);return r;}
    @Test void pixelDisplayLatchesArbitraryBitsAndLosesLightWithoutPower(){
        var p=new PcbLayout.Part(PcbLayout.Type.PIXEL,6,4,0);var nets=new LinkedHashMap<String,List<Integer>>();nets.put("V",List.of(p.pin(0),PcbLayout.ports(32,24)[0]));nets.put("G",List.of(p.pin(1),PcbLayout.ports(32,24)[1]));for(int i=2;i<6;i++)nets.put("p"+i,List.of(p.pin(i)));var c=new PcbCircuit(PcbRouter.route(List.of(p),nets,32,24,2));long bitmap=0x8142241818244281L;int t=0;
        tick(c,t++,0,0,0,0,5);for(int bit=63;bit>=0;bit--){int value=(int)((bitmap>>>bit)&1);tick(c,t++,value,0,0,0,5);tick(c,t++,value,1,0,0,5);}assertEquals(0,c.pixels[0],"No implicit refresh before latch");tick(c,t++,0,0,1,0,5);assertEquals(bitmap,c.pixels[0],"All 64 bits are controlled independently");tick(c,t++,0,0,0,0,0);assertEquals(0,c.pixels[0]);
    }
}
