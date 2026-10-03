package top.lacrus.zephyrzeno.electron.mixin;

import net.minecraft.entity.LightningEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.lacrus.zephyrzeno.electron.industrial.LightningCapture;

@Mixin(LightningEntity.class)
public abstract class LightningCaptureMixin {
    @Unique private boolean electron$redirected;
    @Inject(method="tick",at=@At("HEAD"))
    private void electron$redirect(CallbackInfo ci){var bolt=(LightningEntity)(Object)this;if(!electron$redirected&&bolt.getWorld() instanceof ServerWorld world){electron$redirected=true;LightningCapture.redirect(world,bolt);}}
}
