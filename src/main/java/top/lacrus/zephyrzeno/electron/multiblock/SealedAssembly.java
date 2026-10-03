package top.lacrus.zephyrzeno.electron.multiblock;

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

/** Fluid flow may not replace machine cells just because their collision is not a full cube. */
public interface SealedAssembly extends FluidFillable {
    @Override default boolean canFillWithFluid(PlayerEntity p,BlockView w,BlockPos pos,BlockState state,Fluid fluid){return false;}
    @Override default boolean tryFillWithFluid(WorldAccess w,BlockPos pos,BlockState state,FluidState fluid){return false;}
}
