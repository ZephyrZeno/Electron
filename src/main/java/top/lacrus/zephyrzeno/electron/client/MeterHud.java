package top.lacrus.zephyrzeno.electron.client;

import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.*;
import net.minecraft.text.*;
import top.lacrus.zephyrzeno.electron.foundry.Foundry;

public final class MeterHud {
    private static List<Text> lines=List.of();private static int mode;private static long received;
    public static void receive(NbtCompound packet){var client=MinecraftClient.getInstance();if(client.world==null)return;List<Text> result=new ArrayList<>();for(var line:packet.getList("lines",NbtElement.STRING_TYPE)){var text=Text.Serialization.fromJson(line.asString(),client.world.getRegistryManager());if(text!=null)result.add(text);}lines=List.copyOf(result);mode=packet.getInt("mode");received=System.currentTimeMillis();}
    public static void clear(){lines=List.of();received=0;}
    public static void render(DrawContext ctx){
        var client=MinecraftClient.getInstance();if(client.player==null||client.currentScreen!=null||client.options.hudHidden||(!client.player.getMainHandStack().isOf(Foundry.METER)&&!client.player.getOffHandStack().isOf(Foundry.METER)))return;
        int w=Math.min(230,client.getWindow().getScaledWidth()-20);List<OrderedText> wrapped=new ArrayList<>();
        var data=System.currentTimeMillis()-received>2000?List.of(Text.translatable("meter.electron.ready")):lines;
        for(var line:data)wrapped.addAll(client.textRenderer.wrapLines(line,w-22));
        int h=48+wrapped.size()*11,x=client.getWindow().getScaledWidth()-w-9,y=client.getWindow().getScaledHeight()-h-35;
        ctx.fill(x,y,x+w,y+h,0xedbf8139);ctx.fill(x+3,y+3,x+w-3,y+h-3,0xf017242c);ctx.drawTextWithShadow(client.textRenderer,Text.translatable("meter.electron.title"),x+10,y+9,0xffd397);
        ctx.drawText(client.textRenderer,mode==1?"Ω / DC":"V / A / DC",x+w-77,y+9,0x8de2b1,false);ctx.fill(x+8,y+24,x+w-8,y+h-17,0xff142f2b);
        int yy=y+28;for(var line:wrapped){ctx.drawText(client.textRenderer,line,x+11,yy,0xb9f4c9,false);yy+=11;}
        ctx.drawText(client.textRenderer,Text.translatable("meter.electron.live"),x+10,y+h-12,0x9aabb3,false);
    }
    private MeterHud(){}
}
