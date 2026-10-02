package top.lacrus.zephyrzeno.electron.mixin;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import top.lacrus.zephyrzeno.electron.client.MagnifierZoom;

@Mixin(Mouse.class)
public abstract class MagnifierMouseMixin {
    @ModifyArgs(method="updateMouse",at=@At(value="INVOKE",target="Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void electron$precision(Args args){double factor=MagnifierZoom.factor();args.set(0,(double)args.get(0)/factor);args.set(1,(double)args.get(1)/factor);}
}
