package top.lacrus.zephyrzeno.electron.mixin;

import net.minecraft.block.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.lacrus.zephyrzeno.electron.circuit.CircuitState;

@Mixin(PistonBlock.class)
public abstract class PistonAttachmentMixin {
    @Inject(method="isMovable",at=@At("HEAD"),cancellable=true)
    private static void electron$protectAttachments(BlockState state,World world,BlockPos pos,Direction direction,boolean canBreak,Direction pistonDir,CallbackInfoReturnable<Boolean> callback) {
        if(CircuitState.attached(world,pos))callback.setReturnValue(false);
    }
}
