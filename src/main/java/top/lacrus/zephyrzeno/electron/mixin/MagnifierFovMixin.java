package top.lacrus.zephyrzeno.electron.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.lacrus.zephyrzeno.electron.client.MagnifierZoom;

@Mixin(GameRenderer.class)
public abstract class MagnifierFovMixin {
    @Inject(method="getFov",at=@At("RETURN"),cancellable=true)
    private void electron$magnify(Camera camera,float tickDelta,boolean changingFov,CallbackInfoReturnable<Double> result){
        if(changingFov&&MagnifierZoom.factor()>1)result.setReturnValue(MagnifierZoom.fov(result.getReturnValue()));
    }
}
