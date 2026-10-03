package top.lacrus.zephyrzeno.electron.circuit;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.*;

/** Bounded packets and atomic client replacement; world wire count is not a packet-size limit. */
public final class CircuitSnapshots {
    private static final String[] KEYS={"wires","junctions","cores","bridges","devices","appliance_cords"};
    private static final AtomicLong SERIAL=new AtomicLong();
    public static List<NbtCompound> split(NbtCompound snapshot){
        List<NbtCompound> result=new ArrayList<>();var part=new NbtCompound();int count=0;
        for(var key:KEYS)for(var entry:snapshot.getList(key,NbtElement.COMPOUND_TYPE)){
            if(count==64){result.add(part);part=new NbtCompound();count=0;}
            if(!part.contains(key))part.put(key,new NbtList());part.getList(key,NbtElement.COMPOUND_TYPE).add(entry.copy());count++;
        }
        result.add(part);long serial=SERIAL.incrementAndGet();for(int i=0;i<result.size();i++){var p=result.get(i);p.putLong("serial",serial);p.putInt("index",i);p.putBoolean("last",i==result.size()-1);p.putString("dimension",snapshot.getString("dimension"));}
        return List.copyOf(result);
    }
    public static final class Receiver {
        private NbtCompound pending;private long serial;private int index;
        public void clear(){pending=null;index=0;}
        public Optional<NbtCompound> accept(NbtCompound packet){
            if(packet.getInt("index")==0){pending=new NbtCompound();serial=packet.getLong("serial");index=0;pending.putString("dimension",packet.getString("dimension"));for(var key:KEYS)pending.put(key,new NbtList());}
            if(pending==null||serial!=packet.getLong("serial")||index!=packet.getInt("index")){clear();return Optional.empty();}
            for(var key:KEYS)for(var entry:packet.getList(key,NbtElement.COMPOUND_TYPE))pending.getList(key,NbtElement.COMPOUND_TYPE).add(entry.copy());index++;
            if(!packet.getBoolean("last"))return Optional.empty();var complete=pending;clear();return Optional.of(complete);
        }
    }
    private CircuitSnapshots(){}
}
