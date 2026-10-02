package top.lacrus.zephyrzeno.electron.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import top.lacrus.zephyrzeno.electron.foundry.Foundry;

public final class MagnifierZoom {
    private static KeyBinding key;
    public static void register(){
        key=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.electron.magnify",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_Z,"key.categories.electron"));
        HudRenderCallback.EVENT.register((context,tick)->{
            double scale=factor();if(scale==1)return;var client=MinecraftClient.getInstance();
            int x=context.getScaledWindowWidth()/2,y=context.getScaledWindowHeight()/2;
            context.drawBorder(x-30,y-30,60,60,0x777cffe0);
            context.drawCenteredTextWithShadow(client.textRenderer,Text.translatable("hud.electron.magnify",(int)scale),x,y+40,0xb7ffe8);
        });
    }
    public static double factor(){
        var client=MinecraftClient.getInstance();
        if(key==null||!key.isPressed()||!client.isWindowFocused()||client.currentScreen!=null||client.player==null||!client.player.isAlive()||client.player.isSpectator()||!client.player.getEquippedStack(EquipmentSlot.HEAD).isOf(Foundry.MAGNIFIER))return 1;
        return client.options.sneakKey.isPressed()?6:3;
    }
    public static double fov(double degrees){return Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(degrees)/2)/factor()));}
    private MagnifierZoom(){}
}
