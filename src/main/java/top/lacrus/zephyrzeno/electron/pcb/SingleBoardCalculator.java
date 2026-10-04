package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;

/** Builds one editable four-layer board. Every half-adder is expanded into XOR and AND packages. */
public final class SingleBoardCalculator {
    public static final int INPUT_A=0,INPUT_B=1,OPERATION=2,SIGN=3,TENS=4,ONES=5;
    private static final class Ready {static final PcbLayout BOARD=create();}
    public static PcbLayout layout(){return Ready.BOARD;}
    private static PcbLayout create(){
        var parts=new ArrayList<PcbLayout.Part>();var nets=new LinkedHashMap<String,List<Integer>>();
        add(parts,nets,PcbLayout.Type.DIAL,5,4,"V","G","A0","A1","A2","A3");
        add(parts,nets,PcbLayout.Type.DIAL,16,4,"V","G","B0","B1","B2","B3");
        add(parts,nets,PcbLayout.Type.SWITCH,27,4,"V","G","G","G","SUB");
        var design=DecimalGateDesign.create();
        // Sign and digits only decode their input signals; no display performs arithmetic.
        add(parts,nets,PcbLayout.Type.SIGN,45,4,"V","G",design.negative(),"G","G");
        add(parts,nets,PcbLayout.Type.DISPLAY,55,4,"V","G",design.tens(),"G","G","G");
        add(parts,nets,PcbLayout.Type.DISPLAY,65,4,"V","G",design.units().get(0),design.units().get(1),design.units().get(2),design.units().get(3));
        for(var cell:design.cells()){
            var p=cell.pins();if(cell.pcb()){
                gate(parts,nets,PcbLayout.Type.XOR,p.get(2),p.get(3),p.get(4));gate(parts,nets,PcbLayout.Type.AND,p.get(2),p.get(3),p.get(5));
            }else gate(parts,nets,PcbLayout.Type.valueOf(cell.gate().name()),p.get(2),p.get(3),p.get(4));
        }
        int w=80,h=88;int[] ports=PcbLayout.ports(w,h);nets.get("V").add(ports[0]);nets.get("G").add(ports[1]);
        // Four optional probe pads expose sign, tens, units bit 0 and bit 1.
        String[] exposed={design.negative(),design.tens(),design.units().get(0),design.units().get(1)};for(int i=0;i<4;i++)nets.get(exposed[i]).add(ports[i+2]);
        return PcbRouter.route(parts,nets,w,h,4);
    }
    private static void gate(List<PcbLayout.Part> parts,Map<String,List<Integer>> nets,PcbLayout.Type type,String a,String b,String out){int n=parts.size()-6;add(parts,nets,type,5+n%7*10,15+n/7*9,"V","G",a,b,out);}
    private static void add(List<PcbLayout.Part> parts,Map<String,List<Integer>> nets,PcbLayout.Type type,int x,int y,String... signals){var p=new PcbLayout.Part(type,x,y,0);parts.add(p);for(int i=0;i<signals.length;i++)nets.computeIfAbsent(signals[i],k->new ArrayList<>()).add(p.pin(i));}
    private SingleBoardCalculator(){}
}
