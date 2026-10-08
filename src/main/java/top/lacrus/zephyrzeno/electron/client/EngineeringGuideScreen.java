package top.lacrus.zephyrzeno.electron.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import top.lacrus.zephyrzeno.electron.Electron;

/** Opaque paper cards, a contents rail, and one background pass before the article. */
public final class EngineeringGuideScreen extends Screen {
    private int page,scroll,maxScroll,contentsScroll;
    private int left,panelWidth,rail,articleX,articleWidth,bottom;
    private boolean zoom;
    private static final String[] PAGES={"first_light","series_parallel","timing","power_modules","redstone_switch","wiring","wall_ports","measurement","logic","control","conversion","distribution","crafting","protection","precision","scales","power_grid","mechanics","multiblocks","high_voltage","industrial_build","fluids","processing","nuclear_site","wind_and_wires","faults","pcb_design","pcb_process","automation","plug_power","digital_display","pcb_logic","pcb_pixels","pcb_analog","environment"};
    private static final String[] ILLUSTRATIONS={"first_light","series_parallel","rc_timing","power_modules","redstone_switch","manual_wiring",null,"meter_probes","gate_pins","control_power","dc_conversion","control_power",null,"protection","bridge_tap",null,"power_grid","mechanics","multiblocks","high_voltage","industrial_build","fluids","processing","nuclear_site","wind_and_wires","faults","pcb_design","pcb_process","automation","plug_power","digital_display","pcb_logic","pcb_pixels","pcb_analog","environment"};
    public EngineeringGuideScreen(){super(Text.translatable("guide.electron.title"));}
    @Override protected void applyBlur(float delta){}
    // Screen.render calls this before its widgets in 1.21. Drawing here would cover our article again.
    @Override public void renderBackground(DrawContext c,int x,int y,float d){}
    private void select(int n){
        page=Math.floorMod(n,PAGES.length);scroll=0;zoom=false;
        int visible=Math.max(25,bottom-72);
        if(page*25<contentsScroll)contentsScroll=page*25;
        if(page*25+25>contentsScroll+visible)contentsScroll=page*25+25-visible;
    }
    private ButtonWidget navigation(String key,int x,int width,Runnable action){
        return addDrawableChild(new ButtonWidget(x,height-29,width,20,Text.translatable(key),b->action.run(),message->message.get()){
            @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
                boolean focus=isHovered()||isFocused();
                c.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),focus?0xff416274:0xff263e4d);
                c.fill(getX(),getY()+getHeight()-2,getX()+getWidth(),getY()+getHeight(),focus?0xff9ee6d5:0xff548a88);
                c.drawCenteredTextWithShadow(textRenderer,getMessage(),getX()+getWidth()/2,getY()+6,0xfff2f6f2);
            }
        });
    }
    @Override protected void init(){
        panelWidth=Math.min(790,width-16);left=(width-panelWidth)/2;bottom=height-38;
        rail=panelWidth>=520?146:0;articleX=left+rail;articleWidth=panelWidth-rail;
        navigation("guide.electron.previous",left,76,()->select(page-1));
        navigation("guide.electron.next",left+82,76,()->select(page+1));
        navigation("guide.electron.close",left+panelWidth-76,76,this::close);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){
        if(rail>0&&x<articleX)contentsScroll=(int)Math.clamp(contentsScroll-v*24,0,Math.max(0,PAGES.length*25-(bottom-64)));
        else scroll=(int)Math.clamp(scroll-v*24,0,maxScroll);
        return true;
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(zoom){zoom=false;return true;}
        if(rail>0&&x>=left&&x<articleX&&y>=64&&y<bottom-8){int n=(int)((y-64+contentsScroll)/25);if(n>=0&&n<PAGES.length)select(n);return true;}
        if(ILLUSTRATIONS[page]!=null&&x>articleX+16&&x<left+panelWidth-16&&y>90-scroll&&y<90-scroll+(articleWidth-32)/3&&y>=86&&y<bottom-8){zoom=true;return true;}
        return super.mouseClicked(x,y,button);
    }
    private void illustration(DrawContext c,int x,int y,int w){
        c.setShaderColor(1,1,1,1);float scale=w/960f;c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(scale,scale,1);
        c.drawTexture(Electron.id("textures/gui/tutorial/"+Text.translatable("guide.electron.image_locale").getString()+"/"+ILLUSTRATIONS[page]+".png"),0,0,0f,0f,960,320,960,320);c.getMatrices().pop();
    }
    @Override public void render(DrawContext c,int mouseX,int mouseY,float delta){
        c.setShaderColor(1,1,1,1);
        c.fill(0,0,width,height,0xff0e1720);
        c.fill(left,10,left+panelWidth,bottom,0xffeef1ed);c.fill(left,10,left+panelWidth,13,0xff65b8ab);
        if(rail>0){
            c.fill(left,13,articleX,bottom,0xff172733);c.drawText(textRenderer,Text.translatable("guide.electron.contents"),left+12,29,0x9fe0d2,false);
            c.drawText(textRenderer,"CIRCUIT FOUNDRY",left+12,44,0x91a6b4,false);c.enableScissor(left,64,articleX-4,bottom-8);
            for(int i=0;i<PAGES.length;i++){int y=64+i*25-contentsScroll;
                if(i==page){c.fill(left+5,y,articleX-5,y+23,0xff31505e);c.fill(left+5,y,left+8,y+23,0xff9fe0d2);}
                else if(mouseX>=left&&mouseX<articleX&&mouseY>=y&&mouseY<y+23)c.fill(left+5,y,articleX-5,y+23,0xff243b49);
                String value=String.format("%02d  ",i+1)+Text.translatable("guide.electron."+PAGES[i]+".title").getString();
                c.drawText(textRenderer,textRenderer.trimToWidth(value,rail-25),left+13,y+7,i==page?0xf5f6f8:0xb5bdc9,false);
            }c.disableScissor();
        }
        c.drawText(textRenderer,Text.translatable("guide.electron.brand"),articleX+16,26,0x357b78,false);
        c.drawText(textRenderer,Text.translatable("guide.electron."+PAGES[page]+".title"),articleX+16,44,0x172c39,false);
        String subtitle=Text.translatable("guide.electron."+PAGES[page]+".subtitle").getString();
        c.drawText(textRenderer,textRenderer.trimToWidth(subtitle,articleWidth-32),articleX+16,63,0x566b72,false);c.fill(articleX+16,79,left+panelWidth-16,80,0xffc5d3cc);
        c.enableScissor(articleX+8,85,left+panelWidth-8,bottom-8);int y=90-scroll;
        if(ILLUSTRATIONS[page]!=null){illustration(c,articleX+16,y,articleWidth-32);y+=(articleWidth-32)/3+8;c.drawText(textRenderer,Text.translatable("guide.electron.zoom"),articleX+16,y,0x357b78,false);y+=23;}
        for(int i=0;i<4;i++){
            var lines=textRenderer.wrapLines(Text.translatable("guide.electron."+PAGES[page]+".p"+i),articleWidth-68);int h=lines.size()*13+20;
            c.fill(articleX+16,y+1,left+panelWidth-16,y+h+1,0xffd5ded8);
            c.fill(articleX+16,y,left+panelWidth-16,y+h,0xfffcfcf7);
            c.fill(articleX+16,y,articleX+18,y+h,0xff7eb5a5);
            c.drawText(textRenderer,String.format("%02d",i+1),articleX+23,y+9,0x438376,false);
            int ty=y+10;for(var line:lines){c.drawText(textRenderer,line,articleX+47,ty,0x263944,false);ty+=13;}y+=h+10;
        }
        maxScroll=Math.max(0,y+scroll-(bottom-8));c.disableScissor();
        scroll=Math.min(scroll,maxScroll);
        if(maxScroll>0){int span=bottom-96,thumb=Math.max(12,span*span/(span+maxScroll));int yy=88+(span-thumb)*scroll/maxScroll;c.fill(left+panelWidth-5,88,left+panelWidth-3,bottom-8,0xffd4ded7);c.fill(left+panelWidth-5,yy,left+panelWidth-3,yy+thumb,0xff438376);}
        c.drawText(textRenderer,(page+1)+" / "+PAGES.length,left+panelWidth/2-15,height-23,0xb7cad4,false);super.render(c,mouseX,mouseY,delta);
        if(zoom&&ILLUSTRATIONS[page]!=null){c.getMatrices().push();c.getMatrices().translate(0,0,300);c.fill(0,0,width,height,0xf012151b);int w=Math.min(width-24,(height-50)*3);illustration(c,(width-w)/2,(height-w/3)/2,w);c.drawCenteredTextWithShadow(textRenderer,Text.translatable("guide.electron.zoom_close"),width/2,height-25,0xffffff);c.getMatrices().pop();}
        c.setShaderColor(1,1,1,1);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(zoom){if(key==256)zoom=false;return true;}
        if(key==263||key==262){select(page+(key==262?1:-1));return true;}
        if(key==266||key==267){scroll=Math.clamp(scroll+(key==267?1:-1)*Math.max(30,bottom-110),0,maxScroll);return true;}
        if(key==268||key==269){scroll=key==268?0:maxScroll;return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean shouldPause(){return false;}
}
