package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.util.*;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.DecimalCircuit.*;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.DecimalCircuit.Module;

/** Retains word-sized adders instead of lowering their internals to independently routed gates. */
public final class PhysicalNetlist {
    public enum Type { INPUT, NOR, AND, OR, XOR, REGISTER, ADDER }
    public record Unit(Type type,int[] inputs,int[] outputs,Module module,String name) {}
    public final List<Unit> units=new ArrayList<>();
    private final Map<Integer,Unit> owners=new HashMap<>();
    private final Set<Unit> emitted=Collections.newSetFromMap(new IdentityHashMap<>());
    private final DecimalCircuit circuit;
    private PhysicalNetlist(DecimalCircuit circuit){this.circuit=circuit;}
    public static PhysicalNetlist of(DecimalCircuit circuit) {
        PhysicalNetlist p=new PhysicalNetlist(circuit);
        for(var cell:circuit.logicCells)p.owners.put(cell.output(),new Unit(Type.valueOf(cell.type()),cell.inputs(),new int[]{cell.output()},cell.module(),cell.type()));
        for(var a:circuit.adders) {
            int bits=a.a().length;int[] inputs=new int[bits*2+1],outputs=new int[bits+1];
            System.arraycopy(a.a(),0,inputs,0,bits);System.arraycopy(a.b(),0,inputs,bits,bits);inputs[bits*2]=a.carryIn();
            System.arraycopy(a.sum(),0,outputs,0,bits);outputs[bits]=a.carryOut();
            Unit unit=new Unit(Type.ADDER,inputs,outputs,a.module(),"加法器");
            for(int out:outputs)if(out>=a.firstNode())p.owners.put(out,unit);
        }
        circuit.outputs.values().forEach(p::visit);return p;
    }
    private void visit(int signal) {
        if(signal<=1)return; // Each template receives its own local constant source.
        Unit unit=owners.get(signal);
        if(unit==null) {
            Node node=circuit.nodes.get(signal);
            int[] inputs=node.kind()==Kind.INPUT?new int[0]:node.a()==node.b()?new int[]{node.a()}:new int[]{node.a(),node.b()};
            unit=new Unit(Type.valueOf(node.kind().name()),inputs,new int[]{signal},node.module(),node.name());owners.put(signal,unit);
        }
        if(!emitted.add(unit))return;
        for(int input:unit.inputs())visit(input);
        units.add(unit);
    }
    public boolean owns(Unit unit,int signal){return owners.get(signal)==unit;}
    public static void main(String[] args) {
        PhysicalNetlist p=of(DecimalCircuit.create());
        for(Type t:Type.values())System.out.println(t+" "+p.units.stream().filter(u->u.type()==t).count());
        System.out.println("Full-adder slices "+p.units.stream().filter(u->u.type()==Type.ADDER).mapToInt(u->(u.inputs().length-1)/2).sum());
    }
}
