package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.util.*;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.DecimalCircuit.Module;

/** Synthesizes next-state gates. Two physical latch banks prevent repeated writes during a pulse. */
public final class KeypadCircuit {
    public static final List<String> KEYS=List.of("0","1","2","3","4","5","6","7","8","9","PLUS","MINUS","MULTIPLY","DIVIDE","CLEAR","BACK","RUN");
    public final DecimalCircuit circuit=DecimalCircuit.empty(Module.CONTROL);
    public final Map<String,Integer> state=new LinkedHashMap<>(),shadow=new LinkedHashMap<>(),key=new LinkedHashMap<>();
    private final int[] clock={circuit.input("CLOCK1"),circuit.input("CLOCK2"),circuit.input("CLOCK3"),circuit.input("CLOCK4"),circuit.input("CLOCK5"),circuit.input("CLOCK6")};
    private final int sampleHold=circuit.or(circuit.not(clock[0]),clock[1]),commitHold=circuit.or(circuit.not(clock[2]),clock[3]);
    private KeypadCircuit() {
        var c=circuit;
        for(String name:KEYS)key.put(name,c.input("KEY_"+name));
        int reset=clock[4],end=clock[5],calcBusy=c.input("CALC_BUSY");
        for(String prefix:List.of("AT","AU","BT","BU","XAT","XAU","XBT","XBU"))for(int i=0;i<4;i++)reserve(prefix+i);
        for(String name:List.of("HAS_A","TWO_A","HAS_B","TWO_B","OP_SET","OP0","OP1","XOP0","XOP1","ERROR","RUN","DONE"))reserve(name);
        int any=c.low(),many=c.low();
        for(int k:key.values()){many=c.or(many,c.and(any,k));any=c.or(any,k);}
        int valid=c.and(any,c.not(many)),digit=c.low();for(int i=0;i<10;i++)digit=c.or(digit,key.get(""+i));digit=c.and(digit,valid);
        int clear=c.and(valid,key.get("CLEAR")),back=c.and(valid,key.get("BACK")),begin=c.and(digit,q("DONE")),clearDraft=c.or(clear,begin),stage=c.and(q("OP_SET"),c.not(begin));
        int aTwo=c.and(q("TWO_A"),c.not(begin)),bTwo=c.and(q("TWO_B"),c.not(begin));
        int aWrite=c.and(digit,c.and(c.not(stage),c.not(aTwo))),bWrite=c.and(digit,c.and(stage,c.not(bTwo)));
        int aBack=c.and(back,c.not(stage)),bBack=c.and(back,stage);
        int[] encoded=new int[4];for(int bit=0;bit<4;bit++){int b=c.low();for(int d=0;d<10;d++)if((d&(1<<bit))!=0)b=c.or(b,key.get(""+d));encoded[bit]=b;}
        for(String p:List.of("A","B")) {
            int write=p.equals("A")?aWrite:bWrite,erase=p.equals("A")?aBack:bBack,has=c.and(q("HAS_"+p),c.not(begin)),two=p.equals("A")?aTwo:bTwo;
            for(int bit=0;bit<4;bit++) {
                int units=c.and(q(p+"U"+bit),c.not(begin)),tens=c.and(q(p+"T"+bit),c.not(begin));
                next(p+"T"+bit,c.and(c.not(clear),c.mux(erase,c.mux(write,tens,c.and(has,units)),c.low())));
                next(p+"U"+bit,c.and(c.not(clear),c.mux(erase,c.mux(write,units,encoded[bit]),c.and(two,tens))));
            }
            next("HAS_"+p,c.and(c.not(clear),c.mux(erase,c.or(has,write),two)));
            next("TWO_"+p,c.and(c.not(clear),c.and(c.not(erase),c.or(two,c.and(write,has)))));
        }
        int opKey=c.or(c.or(key.get("PLUS"),key.get("MINUS")),c.or(key.get("MULTIPLY"),key.get("DIVIDE")));
        int choose=c.and(valid,c.and(opKey,c.and(q("HAS_A"),c.not(q("HAS_B")))));
        int undo=c.and(bBack,c.not(q("HAS_B")));
        next("OP_SET",c.and(c.not(c.or(clearDraft,undo)),c.or(stage,choose)));
        next("OP0",c.and(c.not(clearDraft),c.mux(choose,q("OP0"),c.or(key.get("MINUS"),key.get("DIVIDE")))));
        next("OP1",c.and(c.not(clearDraft),c.mux(choose,q("OP1"),c.or(key.get("MULTIPLY"),key.get("DIVIDE")))));
        int complete=c.and(stage,c.and(q("HAS_A"),q("HAS_B"))),run=c.and(valid,c.and(key.get("RUN"),complete));
        next("RUN",c.or(run,clear));next("DONE",c.and(c.not(clearDraft),c.or(q("DONE"),run)));
        for(String p:List.of("AT","AU","BT","BU"))for(int bit=0;bit<4;bit++)next("X"+p+bit,c.and(c.not(clear),c.mux(run,q("X"+p+bit),q(p+bit))));
        for(int bit=0;bit<2;bit++)next("XOP"+bit,c.and(c.not(clear),c.mux(run,q("XOP"+bit),q("OP"+bit))));
        int bad=c.or(many,c.or(c.and(digit,c.mux(stage,aTwo,bTwo)),c.or(c.and(opKey,c.not(c.and(q("HAS_A"),c.not(q("HAS_B"))))),c.and(key.get("RUN"),c.not(complete)))));
        next("ERROR",c.and(c.not(clear),bad));
        c.outputs.put("REQUEST",any);c.outputs.put("VALID_RUN",run);
        c.outputs.put("BUSY",c.or(calcBusy,c.or(any,c.any(clock))));c.outputs.put("READY",c.not(c.outputs.get("BUSY")));
        c.outputs.put("GO",c.and(q("RUN"),c.and(clock[4],c.not(clock[5]))));
        c.outputs.put("RESULT_HOLD",c.not(calcBusy));
        for(String name:KEYS)c.outputs.put("RESET_"+name,c.and(reset,key.get(name)));
        for(String p:List.of("AT","AU","BT","BU")) {
            int[] bits=new int[4];for(int bit=0;bit<4;bit++){bits[bit]=q(p+bit);c.outputs.put("DRAFT_"+p+bit,bits[bit]);c.outputs.put(p+bit,q("X"+p+bit));}
            c.module(Module.DISPLAY);c.sevenSegment("INPUT_"+p+"_",bits);c.module(Module.CONTROL);
        }
        for(int bit=0;bit<2;bit++)c.outputs.put("OP"+bit,q("XOP"+bit));
        c.outputs.put("INPUT_ERROR",q("ERROR"));
        for(String name:List.of("PLUS","MINUS","MULTIPLY","DIVIDE")) {
            int value=List.of("PLUS","MINUS","MULTIPLY","DIVIDE").indexOf(name);
            c.outputs.put("SELECT_"+name,c.and(stage,c.and((value&1)==0?c.not(q("OP0")):q("OP0"),(value&2)==0?c.not(q("OP1")):q("OP1"))));
        }
        for(String name:state.keySet())c.outputs.put("STATE_"+name,q(name));
    }
    private int q(String name){return state.get(name);}
    private void reserve(String name) {
        circuit.module(Module.MEMORY);
        int t=circuit.reserveRegister("T_"+name,sampleHold),q=circuit.reserveRegister(name,commitHold);
        shadow.put(name,t);state.put(name,q);circuit.connectRegister(q,t);circuit.module(Module.CONTROL);
    }
    private void next(String name,int signal){circuit.connectRegister(shadow.get(name),signal);}
    public static KeypadCircuit create(){return new KeypadCircuit();}

    /** Test oracle for one complete two-phase transaction; never called by game runtime. */
    public Map<String,Boolean> step(Map<String,Boolean> previous,Set<String> pressed) {
        boolean[] values=new boolean[circuit.nodes.size()];values[1]=true;
        for(var e:state.entrySet())values[e.getValue()]=previous.getOrDefault(e.getKey(),false);
        Map<Integer,Boolean> memo=new HashMap<>();
        for(String name:KEYS)memo.put(key.get(name),pressed.contains(name));
        state.forEach((name,id)->memo.put(id,previous.getOrDefault(name,false)));
        Map<String,Boolean> result=new LinkedHashMap<>();shadow.forEach((name,id)->result.put(name,value(circuit.nodes.get(id).a(),memo)));
        return result;
    }
    private boolean value(int id,Map<Integer,Boolean> memo) {
        if(id==0)return false;if(id==1)return true;if(memo.containsKey(id))return memo.get(id);
        var n=circuit.nodes.get(id);boolean v=switch(n.kind()){case INPUT->false;case NOR->!(value(n.a(),memo)||value(n.b(),memo));case REGISTER->throw new IllegalStateException("Unbound register "+id);};memo.put(id,v);return v;
    }
}
