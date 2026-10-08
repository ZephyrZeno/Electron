package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;

public final class PcbExamples {
    private static PcbLayout pixel;
    private static PcbLayout counter;
    public static final int COUNTER_CLOCK=0,COUNTER_RESET=1,COUNTER_REGISTER=2,COUNTER_DISPLAY=3;
    public static PcbLayout pixel(){if(pixel!=null)return pixel;var parts=new ArrayList<PcbLayout.Part>();var nets=new LinkedHashMap<String,List<Integer>>();String[] signals={"DATA","CLK","LATCH","CLEAR"};
        for(int i=0;i<4;i++)add(parts,nets,PcbLayout.Type.SWITCH,3+i*6,3,"V","G","G","G",signals[i]);
        add(parts,nets,PcbLayout.Type.RGB,7,12,"V","G","DATA","CLK","LATCH");add(parts,nets,PcbLayout.Type.PIXEL,17,12,"V","G","DATA","CLK","LATCH","CLEAR");
        int[] ports=PcbLayout.ports(32,24);nets.get("V").add(ports[0]);nets.get("G").add(ports[1]);for(int i=0;i<4;i++)nets.get(signals[i]).add(ports[i+2]);pixel=PcbRouter.route(parts,nets,32,24,2);return pixel;
    }
    /** A single-tile, editable counter: the player must pulse RESET after supplying 5 V. */
    public static PcbLayout counter(){
        if(counter!=null)return counter;
        var parts=new ArrayList<PcbLayout.Part>();var nets=new LinkedHashMap<String,List<Integer>>();
        add(parts,nets,PcbLayout.Type.CLOCK,3,4,"V","G","G","G","CLK");
        add(parts,nets,PcbLayout.Type.SWITCH,3,14,"V","G","G","G","RESET");
        add(parts,nets,PcbLayout.Type.COUNTER4,12,9,"V","G","CLK","RESET","Q0","Q1","Q2","Q3");
        add(parts,nets,PcbLayout.Type.DISPLAY,24,9,"V","G","Q0","Q1","Q2","Q3");
        add(parts,nets,PcbLayout.Type.R1K,23,17,"Q0","LED");
        add(parts,nets,PcbLayout.Type.LED,27,17,"LED","G");
        int[] ports=PcbLayout.ports(32,24);String[] signals={"V","G","CLK","RESET","Q0","Q1"};
        for(int i=0;i<signals.length;i++)nets.get(signals[i]).add(ports[i]);
        counter=PcbRouter.route(parts,nets,32,24,2);return counter;
    }
    private static void add(List<PcbLayout.Part> parts,Map<String,List<Integer>> nets,PcbLayout.Type type,int x,int y,String... signals){var p=new PcbLayout.Part(type,x,y,0);parts.add(p);for(int i=0;i<signals.length;i++)nets.computeIfAbsent(signals[i],k->new ArrayList<>()).add(p.pin(i));}
    private PcbExamples(){}
}
