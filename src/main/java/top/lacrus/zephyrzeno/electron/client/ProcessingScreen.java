package top.lacrus.zephyrzeno.electron.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import top.lacrus.zephyrzeno.electron.industrial.ProcessingScreenHandler;

public final class ProcessingScreen extends HandledScreen<ProcessingScreenHandler> {
    public ProcessingScreen(ProcessingScreenHandler h,PlayerInventory p,Text title){super(h,p,title);backgroundWidth=176;backgroundHeight=208;playerInventoryTitleY=113;}
    @Override protected void init(){super.init();addDrawableChild(ButtonWidget.builder(Text.translatable("plant_ui.electron.toggle"),b->client.interactionManager.clickButton(handler.syncId,0)).dimensions(x+108,y+84,60,20).build());}
    @Override protected void applyBlur(float delta){}
    @Override protected void drawBackground(DrawContext c,float dt,int mx,int my){
        c.setShaderColor(1,1,1,1);c.fill(x,y,x+176,y+208,0xff202630);c.fill(x,y,x+176,y+3,0xffa5bfe4);
        for(var slot:handler.slots){c.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff748497);c.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff303b49);}
        for(int i=0;i<4;i++)c.drawCenteredTextWithShadow(textRenderer,Text.translatable("processing_ui.electron.slot_"+i),x+28+i*44,y+28,0xd6e0eb);
        c.fill(x+8,y+67,x+168,y+75,0xff121921);c.fill(x+8,y+67,x+8+160*Math.clamp(handler.value(0),0,1000)/1000,y+75,0xff91aacd);
        c.drawText(textRenderer,Text.translatable("processing_ui.electron.state_"+handler.value(2)),x+8,y+90,0xe6d6b3,false);
    }
    @Override protected void drawForeground(DrawContext c,int mx,int my){c.drawText(textRenderer,title,8,8,0xf0f3f7,false);c.drawText(textRenderer,playerInventoryTitle,8,113,0xc2cddd,false);}
    @Override public void render(DrawContext c,int x,int y,float dt){super.render(c,x,y,dt);drawMouseoverTooltip(c,x,y);}
}
