package top.lacrus.zephyrzeno.electron.multiblock;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.*;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import top.lacrus.zephyrzeno.electron.foundry.*;

/** Only air and inventory-free structural blocks can become parts. Their originals are retained. */
public final class AssemblyPartEntity extends BlockEntity {
    public BlockPos origin=BlockPos.ORIGIN;
    public BlockState original=Blocks.AIR.getDefaultState();
    public int height,turn;
    public ComponentBlock.Kind kind=ComponentBlock.Kind.SUBSTATION;
    public boolean tower;
    public AssemblyPartEntity(BlockPos p,BlockState s){super(Assembly.PART_ENTITY,p,s);}
    public VoxelShape shape(){return AssemblyGeometry.model(kind,height,tower,turn).cells().getOrDefault(pos.subtract(origin),VoxelShapes.empty());}
    @Override protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){
        super.writeNbt(n,r);n.putLong("origin",origin.asLong());n.put("original",NbtHelper.fromBlockState(original));n.putInt("height",height);n.putInt("turn",turn);n.putString("kind",kind.name());n.putBoolean("tower",tower);
    }
    @Override protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup r){
        super.readNbt(n,r);origin=BlockPos.fromLong(n.getLong("origin"));original=NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),n.getCompound("original"));height=Math.clamp(n.getInt("height"),0,63);turn=n.getInt("turn")&3;try{kind=ComponentBlock.Kind.valueOf(n.getString("kind"));}catch(IllegalArgumentException ex){kind=ComponentBlock.Kind.SUBSTATION;}tower=n.getBoolean("tower");
    }
    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup r){return createNbt(r);}
    @Override public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
}
