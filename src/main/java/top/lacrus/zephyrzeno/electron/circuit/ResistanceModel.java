package top.lacrus.zephyrzeno.electron.circuit;

import java.util.*;

/** A small test source measures unpowered equivalent resistance without changing world state. */
public final class ResistanceModel {
    public record Reading(String state,double ohms){}
    public static Reading measure(List<DcSolver.Branch> all,String a,String b){
        Map<String,List<DcSolver.Branch>> adjacent=new HashMap<>();for(var e:all){adjacent.computeIfAbsent(e.a(),k->new ArrayList<>()).add(e);adjacent.computeIfAbsent(e.b(),k->new ArrayList<>()).add(e);}
        if(!adjacent.containsKey(a)||!adjacent.containsKey(b))return new Reading("open",0);
        Set<String> nodes=new HashSet<>();Set<DcSolver.Branch> edges=new LinkedHashSet<>();Deque<String> queue=new ArrayDeque<>();queue.add(a);
        while(!queue.isEmpty()){String n=queue.remove();if(!nodes.add(n))continue;for(var e:adjacent.getOrDefault(n,List.of())){edges.add(e);if(!nodes.contains(e.a()))queue.add(e.a());if(!nodes.contains(e.b()))queue.add(e.b());}}
        if(!nodes.contains(b))return new Reading("open",0);
        if(edges.stream().anyMatch(e->!e.led()&&Math.abs(e.emf())>.05))return new Reading("live",0);
        if(a.equals(b))return new Reading("value",0);
        List<DcSolver.Branch> probe=new ArrayList<>(edges);probe.add(DcSolver.Branch.source("meter:probe",a,b,.1,10));
        var result=DcSolver.solve(probe);double current=-result.current("meter:probe"),ohms=.1/current-10;
        return current<1e-9||!Double.isFinite(ohms)||ohms>1e8?new Reading("open",0):new Reading("value",Math.max(0,ohms));
    }
    private ResistanceModel(){}
}
