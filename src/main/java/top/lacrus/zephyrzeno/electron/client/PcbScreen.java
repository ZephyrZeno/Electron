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
    private int tool,turn,start=-1,dragType=-1,dragPart=-1,selected=-1,dragRevision,grabX=1,grabY=1,category;
    private double viewScale=1,zoom=15,panX=18,panY=21;
    private boolean panning,space;
    private TextFieldWidget search;
    private final List<ButtonWidget> editing=new ArrayList<>();
    private ButtonWidget undo,redo,make,categoryButton;
    public PcbScreen(PcbScreenHandler h,PlayerInventory p,Text title){super(h,p,title);backgroundWidth=536;backgroundHeight=380;playerInventoryTitleY=284;}
    private Text tr(String key){return Text.translatable("pcb_ui.electron."+key);}
    private ButtonWidget button(String key,int bx,int by,int w,Runnable action,boolean edit){var b=addDrawableChild(ButtonWidget.builder(tr(key),v->action.run()).dimensions(x+bx,y+by,w,18).build());if(edit)editing.add(b);return b;}
    @Override protected void init(){
        viewScale=Math.min(1,Math.min(width/548d,height/392d));width=(int)(width/viewScale);height=(int)(height/viewScale);super.init();editing.clear();
        search=addDrawableChild(new TextFieldWidget(textRenderer,x+12,y+47,96,18,tr("search")));search.setMaxLength(40);search.setPlaceholder(tr("search"));
        categoryButton=button("category",12,70,96,()->category=(category+1)%3,true);
        for(int i=0;i<4;i++){int t=i;button(new String[]{"move","track","outline","erase"}[i],122+i*48,24,46,()->{tool=t;start=-1;},true);}
        undo=button("undo",316,24,37,()->send(11,0,0,0,0),true);redo=button("redo",355,24,37,()->send(12,0,0,0,0),true);
        button("size",12,219,46,()->{var l=handler.layout();int[][] sizes={{16,12},{14,10},{12,8},{10,8}};int n=0;for(int i=0;i<4;i++)if(sizes[i][0]==l.width())n=(i+1)%4;send(8,sizes[n][0],sizes[n][1],0,0);},true);
        button("clear",62,219,46,()->send(3,0,0,0,0),true);
        button("example",12,242,96,()->send(4,0,0,0,0),true);button("half_adder",12,264,96,()->send(4,1,0,0,0),true);
        button("load",410,204,110,()->send(7,0,0,0,0),false);make=button("export",410,255,74,()->send(5,0,0,0,0),false);button("stop",488,255,32,()->send(6,0,0,0,0),false);
        button("fit",122,263,46,()->{zoom=15;panX=18;panY=21;},false);
    }
    private void send(int op,int a,int b,int c,int d){ClientPlayNetworking.send(new PcbNetworking.Action(handler.syncId,handler.value(2),op,a,b,c,d));}
    @Override protected void applyBlur(float delta){}
    private int px(int u){return x+122+(int)Math.round(panX+u*zoom);}
    private int py(int v){return y+48+(int)Math.round(panY+v*zoom);}
    private int gx(double mx){return (int)Math.floor((mx-x-122-panX)/zoom+.5);}
    private int gy(double my){return (int)Math.floor((my-y-48-panY)/zoom+.5);}
    private boolean grid(double mx,double my){return mx>=x+122&&mx<x+394&&my>=y+48&&my<y+259;}
    private List<PcbLayout.Type> palette(){String q=search==null?"":search.getText().toLowerCase(Locale.ROOT);return Arrays.stream(PcbLayout.Type.values()).filter(t->category==0||category==1&&!t.digital()||category==2&&t.digital()).filter(t->Pcb.PARTS.get(t).getName().getString().toLowerCase(Locale.ROOT).contains(q)||t.name().toLowerCase(Locale.ROOT).contains(q)).toList();}
    private PcbLayout.Type paletteAt(double mx,double my){var list=palette();for(int i=0;i<list.size();i++){int a=x+12+i%3*33,b=y+95+i/3*29;if(mx>=a&&mx<a+30&&my>=b&&my<b+26)return list.get(i);}return null;}
    @Override public boolean mouseClicked(double mx,double my,int button){
        mx/=viewScale;my/=viewScale;
        if(grid(mx,my)&&(button==2||button==0&&space)){panning=true;return true;}
        if(button==1&&grid(mx,my)){if(dragType>=0)turn=(turn+1)&3;else start=-1;return true;}
        if(button==0&&editable()){
            var type=paletteAt(mx,my);if(type!=null){dragType=type.ordinal();dragPart=-1;grabX=grabY=1;dragRevision=handler.value(2);tool=0;search.setFocused(false);return true;}
            if(grid(mx,my)){search.setFocused(false);int u=gx(mx),v=gy(my);var l=handler.layout();
                if(tool==1){if(!l.contains(u,v))return true;int at=PcbLayout.point(u,v);if(start<0)start=at;else{send(1,start,at,hasShiftDown()?1:0,0);start=at;}return true;}
                if(tool==2){send(9,u,v,0,0);return true;}if(tool==3){send(2,u,v,0,0);return true;}
                selected=-1;for(int i=0;i<l.parts().size();i++){var p=l.parts().get(i);if(p.contains(u,v)){selected=dragPart=i;dragType=p.type().ordinal();turn=p.turn();grabX=u-p.x();grabY=v-p.y();dragRevision=handler.value(2);return true;}}
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){mx/=viewScale;my/=viewScale;if(panning){panning=false;return true;}if(button==0&&dragType>=0){if(grid(mx,my)&&handler.value(2)==dragRevision&&validGhost(gx(mx)-grabX,gy(my)-grabY)){if(dragPart>=0)send(10,dragPart,gx(mx)-grabX,gy(my)-grabY,turn);else send(0,dragType,gx(mx)-grabX,gy(my)-grabY,turn);}dragType=dragPart=-1;return true;}return super.mouseReleased(mx,my,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(panning){panX=Math.clamp(panX+dx/viewScale,-16*zoom+40,230);panY=Math.clamp(panY+dy/viewScale,-12*zoom+30,180);return true;}return dragType>=0||super.mouseDragged(mx/viewScale,my/viewScale,button,dx/viewScale,dy/viewScale);}
    @Override public boolean mouseScrolled(double mx,double my,double h,double v){mx/=viewScale;my/=viewScale;if(grid(mx,my)){double old=zoom;zoom=Math.clamp(zoom+v*2,8,28);panX=(mx-x-122)-(mx-x-122-panX)*zoom/old;panY=(my-y-48)-(my-y-48-panY)*zoom/old;return true;}return super.mouseScrolled(mx,my,h,v);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(search.isFocused())return super.keyPressed(key,scan,modifiers);if(key==32){space=true;return true;}if(key==256&&(start>=0||dragType>=0)){start=dragType=dragPart=-1;return true;}if(editable()){if(hasControlDown()&&(key==90||key==89)){send(key==89||hasShiftDown()?12:11,0,0,0,0);return true;}if(key==82){if(dragType>=0)turn=(turn+1)&3;else if(selected>=0)send(13,selected,0,0,0);return true;}if(key==261&&selected>=0){send(14,selected,0,0,0);selected=-1;return true;}}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean keyReleased(int key,int scan,int modifiers){if(key==32)space=false;return super.keyReleased(key,scan,modifiers);}
    private boolean editable(){return !handler.assembly()&&(handler.value(1)==0||handler.value(1)==4);}
    private boolean validGhost(int u,int v){try{if(dragPart>=0)handler.layout().move(dragPart,u,v,turn);else handler.layout().place(PcbLayout.Type.values()[dragType],u,v,turn);return true;}catch(IllegalArgumentException|IndexOutOfBoundsException e){return false;}}
    private void segment(DrawContext c,int a,int b,int color){int ax=px(PcbLayout.x(a)),ay=py(PcbLayout.y(a)),bx=px(PcbLayout.x(b)),by=py(PcbLayout.y(b));c.fill(Math.min(ax,bx)-1,Math.min(ay,by)-1,Math.max(ax,bx)+2,Math.max(ay,by)+2,color);}
    private void part(DrawContext c,PcbLayout.Part p,int color){int cx=px(p.x()+1),cy=py(p.y()+1),r=(int)(zoom*.62);c.fill(cx-r,cy-r/2,cx+r,cy+r/2,color);for(int j=0;j<p.type().pins();j++){int at=p.pin(j),ax=px(PcbLayout.x(at)),ay=py(PcbLayout.y(at));c.fill(ax-2,ay-2,ax+3,ay+3,0xffe7d6b4);}if(zoom>=12){c.getMatrices().push();c.getMatrices().translate(cx,cy,0);c.getMatrices().scale(.65f,.65f,1);c.drawCenteredTextWithShadow(textRenderer,p.type().name().replace("HALF_ADDER","HA").replace("DISPLAY","7S"),0,-3,0xe8edf1);c.getMatrices().pop();}}
    @Override protected void drawBackground(DrawContext c,float dt,int mx,int my){
        boolean editable=editable();categoryButton.setMessage(tr("category_"+category));for(var b:editing)b.active=editable;undo.active=editable&&handler.value(199)>0;redo.active=editable&&handler.value(200)>0;search.active=!handler.assembly();make.setMessage(tr(handler.assembly()?"assemble":"export"));
        c.setShaderColor(1,1,1,1);c.fill(x,y,x+536,y+380,0xff202933);c.fill(x,y,x+536,y+3,0xff9eb3c3);c.fill(x+6,y+23,x+115,y+287,0xff2a3540);c.fill(x+405,y+46,x+528,y+278,0xff293540);
        c.drawText(textRenderer,tr(handler.assembly()?"locked_design":"parts"),x+12,y+28,0xdfe8ed,false);
        c.drawTexture(Electron.id("textures/gui/precision_mat.png"),x+122,y+48,0,0,272,211,272,211);c.enableScissor(x+122,y+48,x+394,y+259);var l=handler.layout();
        for(int v=0;v<l.height();v++)for(int u=0;u<l.width();u++)if(l.contains(u,v)){int half=(int)Math.ceil(zoom/2);c.fill(px(u)-half,py(v)-half,px(u)+half,py(v)+half,0xff315b52);c.fill(px(u),py(v),px(u)+1,py(v)+1,0xff708e82);}
        for(var t:l.tracks())segment(c,t.a(),t.b(),0xffdbb775);for(int i=0;i<l.parts().size();i++)if(i!=dragPart)part(c,l.parts().get(i),i==selected?0xff50667b:0xff25323b);
        if(start>=0&&grid(mx,my)&&l.contains(gx(mx),gy(my))){try{var route=PcbLayout.empty().route(start,PcbLayout.point(gx(mx),gy(my)),hasShiftDown());for(var t:route.tracks())segment(c,t.a(),t.b(),0xff89d8bc);}catch(IllegalArgumentException ignored){}}
        if(dragType>=0&&grid(mx,my)){int u=gx(mx)-grabX,v=gy(my)-grabY;part(c,new PcbLayout.Part(PcbLayout.Type.values()[dragType],u,v,turn),validGhost(u,v)?0xff447e68:0xff9f4a47);}
        String[] ports={"+","−","A","B","Y","Z"};int[] pins=l.ports();for(int i=0;i<6;i++){int ax=px(PcbLayout.x(pins[i])),ay=py(PcbLayout.y(pins[i]));c.fill(ax-3,ay-3,ax+4,ay+4,0xffdfb96b);c.drawText(textRenderer,ports[i],ax-2,ay-12,0xf3e3be,false);}c.disableScissor();
        if(!handler.assembly()){var list=palette();for(int i=0;i<list.size();i++){int a=x+12+i%3*33,b=y+95+i/3*29;c.fill(a,b,a+30,b+26,0xff43515d);c.drawItem(new ItemStack(Pcb.PARTS.get(list.get(i))),a+7,b+5);}}else wrapped(c,tr("assembly_hint"),x+12,y+96,96,0xc8d5df);
        for(var slot:handler.slots){c.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff768897);c.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff35424f);}
        int[] costs=handler.assembly()?Pcb.costs(l):new int[Pcb.INPUT_SLOTS];if(!handler.assembly()){costs[0]=costs[2]=1;}
        for(int i=0;i<Pcb.INPUT_SLOTS;i++){var slot=handler.slots.get(i);if(!slot.hasStack()&&costs[i]>0)c.drawItem(new ItemStack(handler.inputItem(i)),x+slot.x,y+slot.y);if(costs[i]>0)c.drawText(textRenderer,"×"+costs[i],x+slot.x,y+slot.y+18,slot.getStack().getCount()>=costs[i]?0xaebfb8:0xe9aa91,false);}
        if(!handler.assembly())c.drawText(textRenderer,tr("reference"),x+440,y+86,0xaebfce,false);
        c.fill(x+122+tool*48,y+43,x+168+tool*48,y+45,0xff9ad2c2);
        c.drawText(textRenderer,tr("tray"),x+410,y+51,0xd7dee5,false);c.drawText(textRenderer,tr("output"),x+410,y+235,0xcbd6df,false);c.drawText(textRenderer,Text.translatable("pcb_ui.electron.dimensions",l.width(),l.height(),turn*90),x+174,y+266,0xd3dce3,false);
        c.fill(x+410,y+192,x+520,y+197,0xff121c24);c.fill(x+410,y+192,x+410+110*handler.value(0)/1000,y+197,0xff9db9c8);
        wrapped(c,Text.translatable(handler.value(3)>0?"pcb_ui.electron.error_"+handler.value(3):"pcb_ui.electron.state_"+handler.value(1)),x+410,y+290,108,0xe2d1b4);wrapped(c,tr("shortcuts"),x+12,y+300,156,0xb6c6d5);wrapped(c,tr("help_"+tool),x+410,y+325,108,0xb6c6d5);
    }
    private void wrapped(DrawContext c,Text text,int a,int b,int w,int color){for(var line:textRenderer.wrapLines(text,w)){c.drawText(textRenderer,line,a,b,color,false);b+=10;}}
    @Override protected void drawForeground(DrawContext c,int mx,int my){c.drawText(textRenderer,title,12,9,0xf0f4f7,false);c.drawText(textRenderer,playerInventoryTitle,184,284,0xd0d9e0,false);}
    @Override public void render(DrawContext c,int mx,int my,float dt){mx=(int)(mx/viewScale);my=(int)(my/viewScale);c.getMatrices().push();c.getMatrices().scale((float)viewScale,(float)viewScale,1);super.render(c,mx,my,dt);if(dragType>=0&&!grid(mx,my))c.drawItem(new ItemStack(Pcb.PARTS.get(PcbLayout.Type.values()[dragType])),mx-8,my-8);else if(dragType<0){drawMouseoverTooltip(c,mx,my);var type=paletteAt(mx,my);if(type!=null&&!handler.assembly())c.drawTooltip(textRenderer,Pcb.PARTS.get(type).getName(),mx,my);if(grid(mx,my)&&start<0){int u=gx(mx),v=gy(my);for(var p:handler.layout().parts())for(int i=0;i<p.type().pins();i++)if(p.pin(i)==PcbLayout.point(u,v))c.drawTooltip(textRenderer,Text.translatable("pcb_ui.electron.pad",Pcb.PARTS.get(p.type()).getName(),p.type().pinName(i)),mx,my);}}c.getMatrices().pop();}
}
