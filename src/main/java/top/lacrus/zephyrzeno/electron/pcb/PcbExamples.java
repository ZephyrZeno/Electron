package top.lacrus.zephyrzeno.electron.pcb;

import java.util.*;

public final class PcbExamples {
    private static PcbLayout pixel;
    private static PcbLayout counter;
    private static PcbLayout timer,regulated;
    public static final int TIMER_SWITCH=0,TIMER_POT=3,TIMER_COMPARATOR=4,TIMER_LED=6;
    public static final int REGULATED_POT=1,REGULATED_LED=4;
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
    public static PcbLayout timer(){
        if(timer!=null)return timer;var p=new ArrayList<PcbLayout.Part>();var n=new LinkedHashMap<String,List<Integer>>();
        add(p,n,PcbLayout.Type.SWITCH,3,3,"V","G","G","G","TRIGGER");
        add(p,n,PcbLayout.Type.R10K,9,3,"TRIGGER","RAMP");
        add(p,n,PcbLayout.Type.CAP100UF,15,3,"RAMP","G");
        add(p,n,PcbLayout.Type.POT10K,3,13,"V","G","REF");
        add(p,n,PcbLayout.Type.COMPARATOR,15,13,"V","G","RAMP","REF","TRIP");
        add(p,n,PcbLayout.Type.R1K,23,13,"TRIP","LED");add(p,n,PcbLayout.Type.LED,27,13,"LED","G");
        add(p,n,PcbLayout.Type.DIODE,10,8,"RAMP","TRIGGER");
        int[] ports=PcbLayout.ports(32,24);String[] signals={"V","G","TRIGGER","REF","TRIP","RAMP"};for(int i=0;i<6;i++)n.get(signals[i]).add(ports[i]);
        timer=PcbRouter.route(p,n,32,24,2);return timer;
    }
    public static PcbLayout regulated(){
        if(regulated!=null)return regulated;var p=new ArrayList<PcbLayout.Part>();var n=new LinkedHashMap<String,List<Integer>>();
        add(p,n,PcbLayout.Type.BUCK5,3,5,"IN","G","V","G");
        add(p,n,PcbLayout.Type.POT10K,3,14,"V","G","CONTROL");
        add(p,n,PcbLayout.Type.MOSFET,15,14,"CONTROL","SINK","G");
        add(p,n,PcbLayout.Type.R220,15,4,"V","LED");add(p,n,PcbLayout.Type.LED,24,4,"LED","SINK");
        int[] ports=PcbLayout.ports(32,24);String[] signals={"IN","G","CONTROL","V","LED","SINK"};for(int i=0;i<6;i++)n.get(signals[i]).add(ports[i]);
        regulated=PcbRouter.route(p,n,32,24,2);return regulated;
    }
    private static void add(List<PcbLayout.Part> parts,Map<String,List<Integer>> nets,PcbLayout.Type type,int x,int y,String... signals){var p=new PcbLayout.Part(type,x,y,0);parts.add(p);for(int i=0;i<signals.length;i++)nets.computeIfAbsent(signals[i],k->new ArrayList<>()).add(p.pin(i));}
    private PcbExamples(){}
}
