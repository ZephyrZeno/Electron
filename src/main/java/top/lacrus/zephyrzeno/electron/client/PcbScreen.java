package top.lacrus.zephyrzeno.electron.client;

import java.util.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.pcb.*;

/** Separate searchable parts tray, editable mat and paid manufacturing inventory. */
public final class PcbScreen extends HandledScreen<PcbScreenHandler> {
    private int tool,turn,start=-1,dragType=-1,dragPart=-1,selected=-1,dragRevision,grabX=1,grabY=1,category,paletteOffset,layer,port;
    private double viewScale=1;
    private final PcbViewport viewport=new PcbViewport();
    private boolean space;
    private TextFieldWidget search;
    private final List<ButtonWidget> editing=new ArrayList<>();
    private ButtonWidget undo,redo,make,categoryButton,layerButton,portButton,pageButton;
    public PcbScreen(PcbScreenHandler h,PlayerInventory p,Text title){super(h,p,title);backgroundWidth=536;backgroundHeight=380;playerInventoryTitleY=284;}
    private Text tr(String key){return Text.translatable("pcb_ui.electron."+key);}
    private ButtonWidget button(String key,int bx,int by,int w,Runnable action,boolean edit){
        var b=addDrawableChild(new ButtonWidget(x+bx,y+by,w,18,tr(key),v->action.run(),message->message.get()){
            @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
                boolean focus=active&&(isHovered()||isFocused());
                c.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),!active?0xff29343d:focus?0xff4b6b72:0xff374c59);
                c.fill(getX(),getY()+getHeight()-1,getX()+getWidth(),getY()+getHeight(),!active?0xff40505a:focus?0xffa6dfce:0xff719a9a);
                // Vanilla scrolling labels use an unscaled scissor, outside the mat's transform.
                String label=getMessage().getString();int available=getWidth()-6;
                if(textRenderer.getWidth(label)>available)label=textRenderer.trimToWidth(label,available-textRenderer.getWidth("…"))+"…";
                c.drawCenteredTextWithShadow(textRenderer,label,getX()+getWidth()/2,getY()+5,active?0xffedf3ef:0xff8999a3);
            }
        });
        if(edit)editing.add(b);return b;
    }
    @Override protected void init(){
        viewScale=Math.min(1,Math.min(width/548d,height/392d));width=(int)(width/viewScale);height=(int)(height/viewScale);super.init();editing.clear();
        search=addDrawableChild(new TextFieldWidget(textRenderer,x+12,y+47,96,18,tr("search")));search.setMaxLength(40);search.setChangedListener(value->paletteOffset=0);search.setPlaceholder(tr("search"));
        categoryButton=button("category",12,70,96,()->{category=(category+1)%3;paletteOffset=0;},true);
        for(int i=0;i<4;i++){int t=i;button(new String[]{"move","track","outline","erase"}[i],122+i*48,24,46,()->{tool=t;start=-1;},true);}
        undo=button("undo",316,24,37,()->send(11,0,0,0,0),true);redo=button("redo",355,24,37,()->send(12,0,0,0,0),true);
        button("size",12,219,46,()->{var l=handler.layout();int[][] sizes={{32,24},{64,48},{80,88},{96,72},{128,96},{16,12}};int n=0;for(int i=0;i<sizes.length;i++)if(sizes[i][0]==l.width()&&sizes[i][1]==l.height())n=(i+1)%sizes.length;send(8,sizes[n][0],sizes[n][1],0,0);},true);
        button("layers",62,219,46,()->send(15,handler.layout().layers()%4+1,0,0,0),true);
        button("example",12,242,46,()->send(4,hasControlDown()?3:hasShiftDown()?1:0,0,0,0),true);button("clear",62,242,46,()->send(3,0,0,0,0),true);button("calculator",12,264,96,()->send(4,2,0,0,0),true);
        button("load",410,204,110,()->send(7,0,0,0,0),false);make=button("export",410,255,74,()->send(5,0,0,0,0),false);button("stop",488,255,32,()->send(6,0,0,0,0),false);
        button("fit",122,263,36,this::fit,false);
        button("pan",160,263,36,()->{tool=6;start=-1;},false);
        button("zoom_out",198,263,18,()->viewport.zoomAt(136,105.5,-1),false);
        button("zoom_in",218,263,18,()->viewport.zoomAt(136,105.5,1),false);
        layerButton=button("layer",240,263,48,()->{layer=(layer+1)%handler.layout().layers();start=-1;},false);
        button("via",290,263,30,()->{tool=4;start=-1;},true);button("port",322,263,30,()->{tool=5;start=-1;},true);
        portButton=button("port",354,263,38,()->port=(port+1)%6,true);
        pageButton=button("materials_page",410,173,110,()->{handler.materialPage=(handler.materialPage+1)%((Pcb.INPUT_SLOTS+11)/12);send(18,handler.materialPage,0,0,0);},false);
        viewport.boardSize(handler.layout().width(),handler.layout().height());viewport.cancelPan();space=false;
    }
    private void fit(){viewport.fit();}
    private void send(int op,int a,int b,int c,int d){ClientPlayNetworking.send(new PcbNetworking.Action(handler.syncId,handler.value(2),op,a,b,c,d));}
    @Override protected void applyBlur(float delta){}
    private int px(int u){return x+122+(int)Math.round(viewport.screenX(u));}
    private int py(int v){return y+48+(int)Math.round(viewport.screenY(v));}
    private int gx(double mx){return (int)Math.floor(viewport.column(mx-x-122)+.5);}
    private int gy(double my){return (int)Math.floor(viewport.row(my-y-48)+.5);}
    private boolean grid(double mx,double my){return mx>=x+122&&mx<x+394&&my>=y+48&&my<y+259;}
    private List<PcbLayout.Type> palette(){String q=search==null?"":search.getText().toLowerCase(Locale.ROOT);return Arrays.stream(PcbLayout.Type.values()).filter(t->category==0||category==1&&!t.digital()||category==2&&t.digital()).filter(t->Pcb.PARTS.get(t).getName().getString().toLowerCase(Locale.ROOT).contains(q)||t.name().toLowerCase(Locale.ROOT).contains(q)).toList();}
    private PcbLayout.Type paletteAt(double mx,double my){var list=palette();for(int i=paletteOffset;i<Math.min(list.size(),paletteOffset+12);i++){int j=i-paletteOffset;int a=x+12+j%3*33,b=y+95+j/3*29;if(mx>=a&&mx<a+30&&my>=b&&my<b+26)return list.get(i);}return null;}
    @Override public boolean mouseClicked(double mx,double my,int button){
        mx/=viewScale;my/=viewScale;
        if(grid(mx,my)&&dragType<0&&(button==2||button==0&&(space||tool==6||handler.assembly()))){beginPan(button,mx,my);return true;}
        if(button==1&&grid(mx,my)){if(dragType>=0)turn=(turn+1)&3;else start=-1;return true;}
        if(button==0&&editable()){
            var type=paletteAt(mx,my);if(type!=null){dragType=type.ordinal();dragPart=-1;grabX=grabY=1;dragRevision=handler.value(2);tool=0;search.setFocused(false);return true;}
            if(grid(mx,my)){search.setFocused(false);int u=gx(mx),v=gy(my);var l=handler.layout();
                if(tool==1){if(!l.contains(u,v))return true;int at=PcbLayout.point(u,v,layer);if(start<0)start=at;else{send(1,start,at,hasShiftDown()?1:0,0);start=at;}return true;}
                if(tool==4){send(16,u,v,0,0);return true;}if(tool==5){send(17,port,u,v,0);return true;}if(tool==2){send(9,u,v,0,0);return true;}if(tool==3){send(2,u,v,layer,0);return true;}
                selected=-1;for(int i=0;i<l.parts().size();i++){var p=l.parts().get(i);if(p.contains(u,v)){selected=dragPart=i;dragType=p.type().ordinal();turn=p.turn();grabX=u-p.x();grabY=v-p.y();dragRevision=handler.value(2);return true;}}
                // In selection mode, dragging empty mat/board space grabs the viewport.
                beginPan(button,mx,my);return true;
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    private void beginPan(int button,double mx,double my){search.setFocused(false);setFocused(null);setDragging(true);viewport.beginPan(button,mx,my);}
    @Override public void mouseMoved(double mx,double my){super.mouseMoved(mx/viewScale,my/viewScale);}
    @Override public boolean mouseReleased(double mx,double my,int button){mx/=viewScale;my/=viewScale;if(viewport.endPan(button)){setDragging(false);return true;}if(button==0&&dragType>=0){if(grid(mx,my)&&handler.value(2)==dragRevision&&validGhost(gx(mx)-grabX,gy(my)-grabY)){if(dragPart>=0)send(10,dragPart,gx(mx)-grabX,gy(my)-grabY,turn);else send(0,dragType,gx(mx)-grabX,gy(my)-grabY,turn);}dragType=dragPart=-1;return true;}return super.mouseReleased(mx,my,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(viewport.drag(button,mx/viewScale,my/viewScale))return true;return dragType>=0||super.mouseDragged(mx/viewScale,my/viewScale,button,dx/viewScale,dy/viewScale);}
    @Override public boolean mouseScrolled(double mx,double my,double h,double v){mx/=viewScale;my/=viewScale;if(mx>=x+12&&mx<x+110&&my>=y+95&&my<y+214){paletteOffset=Math.clamp(paletteOffset-(int)v*3,0,Math.max(0,((palette().size()+2)/3-4)*3));return true;}if(grid(mx,my)){if(h!=0)viewport.pan(h*20,0);if(hasShiftDown())viewport.pan(0,v*20);else viewport.zoomAt(mx-x-122,my-y-48,v);return true;}return super.mouseScrolled(mx,my,h,v);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(search.isFocused())return super.keyPressed(key,scan,modifiers);if(key==32){space=true;return true;}if(key==70&&!hasControlDown()){fit();return true;}if(key==256&&(start>=0||dragType>=0)){start=dragType=dragPart=-1;return true;}if(editable()){if(hasControlDown()&&(key==90||key==89)){send(key==89||hasShiftDown()?12:11,0,0,0,0);return true;}if(key==82){if(dragType>=0)turn=(turn+1)&3;else if(selected>=0)send(13,selected,0,0,0);return true;}if(key==261&&selected>=0){send(14,selected,0,0,0);selected=-1;return true;}}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean keyReleased(int key,int scan,int modifiers){if(key==32)space=false;return super.keyReleased(key,scan,modifiers);}
    private boolean editable(){return !handler.assembly()&&(handler.value(1)==0||handler.value(1)==4);}
    private boolean validGhost(int u,int v){try{if(dragPart>=0)handler.layout().move(dragPart,u,v,turn);else handler.layout().place(PcbLayout.Type.values()[dragType],u,v,turn);return true;}catch(IllegalArgumentException|IndexOutOfBoundsException e){return false;}}
    private void segment(DrawContext c,int a,int b,int color){double zoom=viewport.zoom();int ax=px(PcbLayout.x(a)),ay=py(PcbLayout.y(a)),bx=px(PcbLayout.x(b)),by=py(PcbLayout.y(b));c.fill(Math.min(ax,bx),Math.min(ay,by),Math.max(ax,bx)+Math.max(1,(int)(zoom*.07)),Math.max(ay,by)+Math.max(1,(int)(zoom*.07)),color);}
    private void part(DrawContext c,PcbLayout.Part p,int color){double zoom=viewport.zoom();int cx=px(p.x()+1),cy=py(p.y()+1),r=(int)(zoom*.62);c.fill(cx-r,cy-r/2,cx+r,cy+r/2,color);for(int j=0;j<p.type().pins();j++){int at=p.pin(j),ax=px(PcbLayout.x(at)),ay=py(PcbLayout.y(at));int pad=Math.max(1,(int)(zoom*.12));c.fill(ax-pad,ay-pad,ax+pad,ay+pad,0xffe7d6b4);}if(zoom>=12){c.getMatrices().push();c.getMatrices().translate(cx,cy,0);c.getMatrices().scale(.65f,.65f,1);c.drawCenteredTextWithShadow(textRenderer,p.type().name().replace("HALF_ADDER","HA").replace("DISPLAY","7S"),0,-3,0xe8edf1);c.getMatrices().pop();}}
    @Override protected void drawBackground(DrawContext c,float dt,int mx,int my){
        viewport.boardSize(handler.layout().width(),handler.layout().height());double zoom=viewport.zoom();layer=Math.min(layer,handler.layout().layers()-1);layerButton.setMessage(Text.translatable("pcb_ui.electron.layer_value",layer+1,handler.layout().layers()));portButton.setMessage(Text.literal(new String[]{"VCC","GND","A","B","Y","Z"}[port]));pageButton.setMessage(Text.translatable("pcb_ui.electron.materials_page_value",handler.materialPage+1,(Pcb.INPUT_SLOTS+11)/12));boolean editable=editable();categoryButton.setMessage(tr("category_"+category));for(var b:editing)b.active=editable;undo.active=editable&&handler.value(199)>0;redo.active=editable&&handler.value(200)>0;search.active=!handler.assembly();make.setMessage(tr(handler.assembly()?"assemble":"export"));
        c.setShaderColor(1,1,1,1);c.fill(x,y,x+536,y+380,0xff202933);c.fill(x,y,x+536,y+3,0xff9eb3c3);c.fill(x+6,y+23,x+115,y+287,0xff2a3540);c.fill(x+405,y+46,x+528,y+278,0xff293540);
        c.drawText(textRenderer,tr(handler.assembly()?"locked_design":"parts"),x+12,y+28,0xdfe8ed,false);
        c.drawTexture(Electron.id("textures/gui/precision_mat.png"),x+122,y+48,0,0,272,211,272,211);
        // Scissors are GUI-screen coordinates; unlike vertices, they do not use the matrix stack.
        c.enableScissor((int)Math.floor((x+122)*viewScale),(int)Math.floor((y+48)*viewScale),
                (int)Math.ceil((x+394)*viewScale),(int)Math.ceil((y+259)*viewScale));var l=handler.layout();
        for(int v=Math.max(0,(int)Math.floor(viewport.row(0)));v<Math.min(l.height(),(int)Math.ceil(viewport.row(211))+1);v++)for(int u=Math.max(0,(int)Math.floor(viewport.column(0)));u<Math.min(l.width(),(int)Math.ceil(viewport.column(272))+1);u++)if(l.contains(u,v)){int half=(int)Math.ceil(zoom/2);c.fill(px(u)-half,py(v)-half,px(u)+half,py(v)+half,0xff315b52);c.fill(px(u),py(v),px(u)+1,py(v)+1,0xff708e82);}
        for(var t:l.tracks())if(PcbLayout.layer(t.a())==layer)segment(c,t.a(),t.b(),new int[]{0xffdbb775,0xff73b5df,0xffd68fa7,0xffa9ce88}[layer]);for(int via:l.vias()){int ax=px(PcbLayout.x(via)),ay=py(PcbLayout.y(via)),r=Math.max(2,(int)(zoom*.18));c.fill(ax-r,ay-r,ax+r,ay+r,0xffdaa85e);c.fill(ax-r+1,ay-r+1,ax+r-1,ay+r-1,0xff17352e);}for(int i=0;i<l.parts().size();i++)if(i!=dragPart)part(c,l.parts().get(i),i==selected?0xff50667b:0xff25323b);
        if(start>=0&&grid(mx,my)&&l.contains(gx(mx),gy(my))){try{var route=new PcbLayout(List.of(),List.of(),l.width(),l.height(),Set.of()).withLayers(l.layers()).route(start,PcbLayout.point(gx(mx),gy(my),layer),hasShiftDown());for(var t:route.tracks())segment(c,t.a(),t.b(),0xff89d8bc);}catch(IllegalArgumentException ignored){}}
        if(dragType>=0&&grid(mx,my)){int u=gx(mx)-grabX,v=gy(my)-grabY;part(c,new PcbLayout.Part(PcbLayout.Type.values()[dragType],u,v,turn),validGhost(u,v)?0xff447e68:0xff9f4a47);}
        String[] ports={"+","−","A","B","Y","Z"};int[] pins=l.ports();for(int i=0;i<6;i++){int ax=px(PcbLayout.x(pins[i])),ay=py(PcbLayout.y(pins[i]));c.fill(ax-3,ay-3,ax+4,ay+4,0xffdfb96b);c.drawText(textRenderer,ports[i],ax-2,ay-12,0xf3e3be,false);}c.disableScissor();
        if(!handler.assembly()){var list=palette();for(int i=paletteOffset;i<Math.min(list.size(),paletteOffset+12);i++){int j=i-paletteOffset;int a=x+12+j%3*33,b=y+95+j/3*29;c.fill(a,b,a+30,b+26,0xff43515d);c.drawItem(new ItemStack(Pcb.PARTS.get(list.get(i))),a+7,b+5);}}else wrapped(c,tr("assembly_hint"),x+12,y+96,96,0xc8d5df);
        for(var slot:handler.slots)if(slot.isEnabled()){c.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff768897);c.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff35424f);}
        int[] costs=handler.assembly()?Pcb.costs(l):new int[Pcb.INPUT_SLOTS];if(!handler.assembly()){costs[0]=costs[2]=l.laminateCost();}
        for(int i=0;i<Pcb.INPUT_SLOTS;i++){costs[i]=Math.max(0,costs[i]-handler.value(8+i));var slot=handler.slots.get(i);if(!slot.isEnabled())continue;if(!slot.hasStack()&&costs[i]>0)c.drawItem(new ItemStack(handler.inputItem(i)),x+slot.x,y+slot.y);if(costs[i]>0)c.drawText(textRenderer,"×"+costs[i],x+slot.x,y+slot.y+18,slot.getStack().getCount()>=costs[i]?0xaebfb8:0xe9aa91,false);}
        if(!handler.assembly())c.drawText(textRenderer,tr("reference"),x+440,y+86,0xaebfce,false);
        if(tool==6)c.fill(x+160,y+281,x+196,y+283,0xff9ad2c2);if(tool<4)c.fill(x+122+tool*48,y+43,x+168+tool*48,y+45,0xff9ad2c2);
        c.drawText(textRenderer,tr("tray"),x+410,y+51,0xd7dee5,false);c.drawText(textRenderer,tr("output"),x+410,y+235,0xcbd6df,false);c.drawText(textRenderer,Text.translatable("pcb_ui.electron.dimensions",l.width(),l.height(),turn*90),x+122,y+282,0xd3dce3,false);
        c.fill(x+410,y+192,x+520,y+197,0xff121c24);c.fill(x+410,y+192,x+410+110*handler.value(0)/1000,y+197,0xff9db9c8);
        wrapped(c,Text.translatable(handler.value(3)>0?"pcb_ui.electron.error_"+handler.value(3):"pcb_ui.electron.state_"+handler.value(1)),x+410,y+290,108,0xe2d1b4);wrapped(c,tr("shortcuts"),x+12,y+300,156,0xb6c6d5);wrapped(c,tr("help_"+tool),x+410,y+325,108,0xb6c6d5);
    }
    private void wrapped(DrawContext c,Text text,int a,int b,int w,int color){for(var line:textRenderer.wrapLines(text,w)){c.drawText(textRenderer,line,a,b,color,false);b+=10;}}
    @Override protected void drawForeground(DrawContext c,int mx,int my){c.drawText(textRenderer,title,12,9,0xf0f4f7,false);}
    @Override public void render(DrawContext c,int mx,int my,float dt){mx=(int)(mx/viewScale);my=(int)(my/viewScale);c.getMatrices().push();c.getMatrices().scale((float)viewScale,(float)viewScale,1);super.render(c,mx,my,dt);if(dragType>=0&&!grid(mx,my))c.drawItem(new ItemStack(Pcb.PARTS.get(PcbLayout.Type.values()[dragType])),mx-8,my-8);else if(dragType<0){drawMouseoverTooltip(c,mx,my);var type=paletteAt(mx,my);if(type!=null&&!handler.assembly())c.drawTooltip(textRenderer,Pcb.PARTS.get(type).getName(),mx,my);if(grid(mx,my)&&start<0&&!viewport.dragging()){int u=gx(mx),v=gy(my);for(var p:handler.layout().parts())for(int i=0;i<p.type().pins();i++)if(p.pin(i)==PcbLayout.point(u,v))c.drawTooltip(textRenderer,Text.translatable("pcb_ui.electron.pad",Pcb.PARTS.get(p.type()).getName(),p.type().pinName(i)),mx,my);}}c.getMatrices().pop();}
}
