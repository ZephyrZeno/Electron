package top.lacrus.zephyrzeno.electron.multiblock;

import net.minecraft.block.*;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import top.lacrus.zephyrzeno.electron.foundry.*;

public final class AssemblyControllerBlock extends ComponentBlock implements SealedAssembly {
    public static final BooleanProperty FORMED=BooleanProperty.of("formed");
    public AssemblyControllerBlock(Settings s,Kind k,double r){super(s,k,r);setDefaultState(getDefaultState().with(FORMED,false));}
    @Override protected com.mojang.serialization.MapCodec<? extends BlockWithEntity> getCodec(){return createCodec(s->new AssemblyControllerBlock(s,kind,resistance));}
    @Override protected void appendProperties(StateManager.Builder<Block,BlockState> b){super.appendProperties(b);b.add(FORMED);}
    @Override protected BlockRenderType getRenderType(BlockState s){return s.get(FORMED)?BlockRenderType.INVISIBLE:BlockRenderType.MODEL;}
    @Override protected VoxelShape getOutlineShape(BlockState s,BlockView w,BlockPos p,ShapeContext c){
        if(s.get(FORMED)&&w.getBlockEntity(p) instanceof ComponentEntity e){
            var shape=AssemblyGeometry.model(kind,e.assemblyHeight,s.isOf(Foundry.TOWER_TERMINAL),s.get(TURN)).cells().getOrDefault(BlockPos.ORIGIN,net.minecraft.util.shape.VoxelShapes.empty());
            return top.lacrus.zephyrzeno.electron.power.WiringCabinet.present(s)?net.minecraft.util.shape.VoxelShapes.union(shape,top.lacrus.zephyrzeno.electron.power.WiringCabinet.shape(s,e.cabinetOpen)):shape;
        }
        return super.getOutlineShape(s,w,p,c);
    }
}
