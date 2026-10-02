package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.util.*;

/** Static physical timing with frozen latch outputs during each transfer phase. */
public final class KeypadTiming {
    private final Map<Integer,PhysicalNetlist.Unit> owners=new HashMap<>();
    private final Map<Integer,Integer> delays,memo=new HashMap<>();
    private KeypadTiming(KeypadCircuit k,CompactBlueprint plan,int offset) {
        var physical=BlueprintVerifier.signalDelays(plan);delays=new HashMap<>();
        physical.forEach((net,delay)->{if(net>=offset&&net<offset+k.circuit.nodes.size())delays.put(net-offset,delay);});
        var list=PhysicalNetlist.of(k.circuit);
        for(var unit:list.units)for(int out:unit.outputs())if(list.owns(unit,out))owners.put(out,unit);
    }
    private int arrival(int signal) {
        if(signal<=1)return 0;if(memo.containsKey(signal))return memo.get(signal);
        var unit=owners.get(signal);
        if(unit==null||unit.type()==PhysicalNetlist.Type.INPUT||unit.type()==PhysicalNetlist.Type.REGISTER)return 0;
        int result=0;for(int input:unit.inputs())result=Math.max(result,arrival(input)+delays.getOrDefault(input,0));
        result+=switch(unit.type()){case NOR->6;case OR->4;case AND->8;case XOR->10;default->14;};
        memo.put(signal,result);return result;
    }
    public static int[] deadlines(KeypadCircuit k,CompactBlueprint plan) {
        return deadlines(k,plan,0);
    }
    public static int[] deadlines(KeypadCircuit k,CompactBlueprint plan,int offset) {
        var timing=new KeypadTiming(k,plan,offset);int data=0,hold=0,shadow=0;
        for(int id:k.shadow.values()) {
            var node=k.circuit.nodes.get(id);
            data=Math.max(data,timing.arrival(node.a())+timing.delays.getOrDefault(node.a(),0));
            hold=Math.max(hold,timing.arrival(node.b())+timing.delays.getOrDefault(node.b(),0));
            shadow=Math.max(shadow,timing.delays.getOrDefault(id,0));
        }
        for(int id:k.state.values()) {
            var node=k.circuit.nodes.get(id);
            hold=Math.max(hold,timing.arrival(node.b())+timing.delays.getOrDefault(node.b(),0));
        }
        // Account for control distribution, data buffers and distinct falling edges.
        int start=data+100,window=hold+shadow+100,gap=hold+100;
        int[] result={start,start+window,start+window+gap,start+2*window+gap,start+2*window+2*gap,start+3*window+2*gap};
        System.out.println("KEYPAD TIMING data="+data+" hold="+hold+" shadow="+shadow+" taps="+Arrays.toString(result));
        return result;
    }
    public static void main(String[] args)throws Exception {
        var plan=CompactBlueprint.read(java.nio.file.Files.newInputStream(java.nio.file.Path.of(args[0])));
        deadlines(KeypadCircuit.create(),plan,args.length>1?Integer.parseInt(args[1]):0);
    }
}
