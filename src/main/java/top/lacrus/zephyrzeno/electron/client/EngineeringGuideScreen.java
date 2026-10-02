package top.lacrus.zephyrzeno.electron.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.lacrus.zephyrzeno.electron.Electron;

public final class EngineeringGuideScreen extends Screen {
    private int page,scroll,maxScroll;
    private static final String[] PAGES={"first_light","series_parallel","timing","power_modules","redstone_switch","wiring","wall_ports","measurement","logic","control","conversion","distribution","crafting","protection","precision","scales"};
    private static final String[] ILLUSTRATIONS={"first_light","series_parallel","rc_timing","power_modules","redstone_switch","manual_wiring",null,"meter_probes","gate_pins","control_power","dc_conversion","control_power",null,"protection","bridge_tap",null};
    public EngineeringGuideScreen(){super(Text.translatable("guide.electron.title"));}
    @Override protected void applyBlur(float delta){}
    @Override public void renderBackground(DrawContext ctx,int mouseX,int mouseY,float delta){ctx.fill(0,0,width,height,0xb0081018);}
    @Override protected void init(){int w=Math.min(500,width-24),x=(width-w)/2,y=height-34;addDrawableChild(ButtonWidget.builder(Text.translatable("guide.electron.previous"),b->{page=(page+PAGES.length-1)%PAGES.length;scroll=0;}).dimensions(x,y,90,20).build());addDrawableChild(ButtonWidget.builder(Text.translatable("guide.electron.next"),b->{page=(page+1)%PAGES.length;scroll=0;}).dimensions(x+100,y,90,20).build());addDrawableChild(ButtonWidget.builder(Text.translatable("guide.electron.close"),b->close()).dimensions(x+w-100,y,100,20).build());}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=(int)Math.clamp(scroll-vertical*24,0,maxScroll);return true;}
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta) {
        renderBackground(ctx,mouseX,mouseY,delta);int w=Math.min(500,width-24),x=(width-w)/2;
        ctx.fill(x,14,x+w,height-42,0xff11202b);ctx.fill(x,14,x+4,height-42,0xff74dbc2);
        ctx.drawTextWithShadow(textRenderer,Text.translatable("guide.electron.brand"),x+20,26,0x74dbc2);
        ctx.drawTextWithShadow(textRenderer,Text.translatable("guide.electron."+PAGES[page]+".title"),x+20,45,0xffffff);ctx.drawTextWithShadow(textRenderer,Text.translatable("guide.electron."+PAGES[page]+".subtitle"),x+20,61,0x9bafbe);
        ctx.enableScissor(x+10,80,x+w-10,height-54);
        int y=85-scroll;
        if(ILLUSTRATIONS[page]!=null){float scale=(w-40)/960f;ctx.getMatrices().push();ctx.getMatrices().translate(x+20,y,0);ctx.getMatrices().scale(scale,scale,1);ctx.drawTexture(Electron.id("textures/gui/tutorial/"+Text.translatable("guide.electron.image_locale").getString()+"/"+ILLUSTRATIONS[page]+".png"),0,0,0f,0f,960,320,960,320);ctx.getMatrices().pop();y+=(int)(320*scale)+12;}
        for(int i=0;i<4;i++){for(var line:textRenderer.wrapLines(Text.translatable("guide.electron."+PAGES[page]+".p"+i),w-40)){ctx.drawText(textRenderer,line,x+20,y,0xdce6eb,false);y+=12;}y+=10;}
        maxScroll=Math.max(0,y+scroll-(height-58));ctx.disableScissor();
        if(maxScroll>0)ctx.drawTextWithShadow(textRenderer,Text.translatable("guide.electron.scroll"),x+w-100,height-52,0x74dbc2);
        super.render(ctx,mouseX,mouseY,delta);
    }
    @Override public boolean shouldPause(){return false;}
}
