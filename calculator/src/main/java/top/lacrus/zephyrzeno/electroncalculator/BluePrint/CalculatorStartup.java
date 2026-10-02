package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** A single quiescent, empty-machine state captured by a native build-time test.
 * This contains no input/result table and is used only while placing a new structure. */
public final class CalculatorStartup {
    private final List<BlockState> palette;
    private final int[] indices;
    private CalculatorStartup(List<BlockState> palette,int[] indices){this.palette=palette;this.indices=indices;}
    public BlockState state(int index){return palette.get(indices[index]);}
    private static byte[] fingerprint(CompactBlueprint plan)throws IOException {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            try(var out=new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(),digest))) {
                out.writeInt(plan.blocks.size());
                for(var e:plan.blocks.entrySet()){var p=e.getKey();var t=e.getValue();out.writeInt(p.x());out.writeInt(p.y());out.writeInt(p.z());out.writeInt(t.part().ordinal());out.writeInt(t.direction());out.writeInt(t.net());}
            }
            return digest.digest();
        }catch(NoSuchAlgorithmException error){throw new IOException(error);}
    }
    public static CalculatorStartup read(InputStream stream,CompactBlueprint plan)throws IOException {
        try(var in=new DataInputStream(new GZIPInputStream(stream))) {
            if(in.readInt()!=0x454C5301||!Arrays.equals(in.readNBytes(32),fingerprint(plan)))throw new IOException("初始状态与计算器蓝图不匹配");
            int count=in.readInt();if(count<1||count>4096)throw new IOException("Invalid startup palette");
            List<BlockState> palette=new ArrayList<>();
            for(int i=0;i<count;i++) {
                String id=in.readUTF();var block=Registries.BLOCK.get(Identifier.of(id));
                if(!Registries.BLOCK.containsId(Identifier.of(id)))throw new IOException("Unknown startup block "+id);
                BlockState state=block.getDefaultState();int properties=in.readUnsignedByte();
                for(int n=0;n<properties;n++)state=with(state,in.readUTF(),in.readUTF());
                palette.add(state);
            }
            if(in.readInt()!=plan.blocks.size())throw new IOException("Startup length mismatch");
            int[] indices=new int[plan.blocks.size()];int n=0;
            for(var tile:plan.blocks.values()) {
                int index=in.readUnsignedShort();if(index>=palette.size()||palette.get(index).getBlock()!=VanillaCalculator.state(tile).getBlock())throw new IOException("Startup block mismatch at "+n);
                indices[n++]=index;
            }
            return new CalculatorStartup(palette,indices);
        }
    }
    private static <T extends Comparable<T>> BlockState with(BlockState state,String name,String value)throws IOException {
        @SuppressWarnings("unchecked") Property<T> property=(Property<T>)state.getBlock().getStateManager().getProperty(name);
        if(property==null)throw new IOException("Unknown startup property "+name);
        return state.with(property,property.parse(value).orElseThrow(()->new IOException("Invalid startup value "+value)));
    }
    public static void capture(ServerWorld world,BlockPos origin,CompactBlueprint plan,Path file)throws IOException {
        LinkedHashMap<BlockState,Integer> palette=new LinkedHashMap<>();List<Integer> indices=new ArrayList<>();
        for(var e:plan.blocks.entrySet()) {
            BlockState state=world.getBlockState(origin.add(VanillaCalculator.pos(e.getKey())));
            if(state.getBlock()!=VanillaCalculator.state(e.getValue()).getBlock())throw new IOException("Unexpected capture block "+e.getKey());
            indices.add(palette.computeIfAbsent(state,k->palette.size()));
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(0x454C5301);out.write(fingerprint(plan));out.writeInt(palette.size());
            for(BlockState state:palette.keySet()) {
                out.writeUTF(Registries.BLOCK.getId(state.getBlock()).toString());out.writeByte(state.getEntries().size());
                for(var entry:state.getEntries().entrySet()){out.writeUTF(entry.getKey().getName());out.writeUTF(entry.getValue().toString().toLowerCase(Locale.ROOT));}
            }
            out.writeInt(indices.size());for(int index:indices)out.writeShort(index);
        }
        System.out.println("STARTUP CAPTURE blocks="+indices.size()+" states="+palette.size()+" file="+file);
    }
}
