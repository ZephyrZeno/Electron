package top.lacrus.zephyrzeno.electron.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import top.lacrus.zephyrzeno.electron.industrial.PlantScreenHandler;

public final class PlantScreen extends HandledScreen<PlantScreenHandler> {
    public PlantScreen(PlantScreenHandler h,PlayerInventory inv,Text title){super(h,inv,title);backgroundWidth=176;backgroundHeight=213;playerInventoryTitleY=118;}
    @Override protected void init(){super.init();addDrawableChild(ButtonWidget.builder(Text.translatable("plant_ui.electron.toggle"),b->client.interactionManager.clickButton(handler.syncId,0)).dimensions(x+107,y+91,61,20).build());}
    @Override protected void applyBlur(float delta){}
    @Override protected void drawBackground(DrawContext c,float dt,int mx,int my){
        c.fill(x,y,x+176,y+213,0xff142630);c.fill(x,y,x+176,y+3,0xff70d9bd);
        for(var slot:handler.slots){c.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff57717c);c.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff243c46);}
        for(int i=0;i<4;i++)c.drawCenteredTextWithShadow(textRenderer,Text.translatable("plant_ui.electron.slot_"+i),x+28+i*44,y+31,0xcfe3e8);
        c.drawText(textRenderer,Text.translatable(handler.value(0)==1?"plant_ui.electron.nuclear":"plant_ui.electron.thermal"),x+8,y+18,0x95c6d0,false);
        c.drawText(textRenderer,Text.translatable("plant_ui.electron.stores",handler.value(2),handler.value(3)/10),x+8,y+71,0xb8d9d1,false);
        c.drawText(textRenderer,Text.translatable("plant_ui.electron.status_"+handler.value(5)),x+8,y+96,0xf1d7a5,false);
    }
    @Override protected void drawForeground(DrawContext c,int mx,int my){c.drawText(textRenderer,title,8,7,0xe5f3ef,false);c.drawText(textRenderer,playerInventoryTitle,8,118,0xb8ced3,false);}
    @Override public void render(DrawContext c,int mx,int my,float dt){super.render(c,mx,my,dt);drawMouseoverTooltip(c,mx,my);}
}
