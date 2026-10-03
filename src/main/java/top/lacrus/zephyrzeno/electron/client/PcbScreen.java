package top.lacrus.zephyrzeno.electron.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.pcb.*;

/** Top-down antistatic mat: drag miniature packages from trays or reposition existing packages. */
public final class PcbScreen extends HandledScreen<PcbScreenHandler> {
    private int tool,turn,start=-1,dragType=-1,dragPart=-1,dragRevision;
    private static final int GRID=12;
    private double viewScale=1;
    public PcbScreen(PcbScreenHandler h,PlayerInventory p,Text title){super(h,p,title);backgroundWidth=354;backgroundHeight=330;playerInventoryTitleY=237;}
    private void button(String key,int bx,int by,int w,Runnable action){addDrawableChild(ButtonWidget.builder(Text.translatable("pcb_ui.electron."+key),b->action.run()).dimensions(x+bx,y+by,w,18).build());}
    @Override protected void init(){
        viewScale=Math.min(1,Math.min(width/366d,height/342d));
        width=(int)(width/viewScale);height=(int)(height/viewScale);
        super.init();button("move",8,23,46,()->{tool=0;start=-1;});button("track",57,23,48,()->{tool=1;start=-1;});button("outline",108,23,48,()->{tool=2;start=-1;});button("erase",159,23,48,()->{tool=3;start=-1;});
        button("size",236,23,48,()->{var l=handler.layout();int[][] sizes={{16,12},{14,10},{12,8},{10,8}};int next=0;for(int i=0;i<4;i++)if(sizes[i][0]==l.width())next=(i+1)%4;send(8,sizes[next][0],sizes[next][1],0,0);});button("clear",287,23,57,()->send(3,0,0,0,0));
        button("example",236,178,108,()->send(4,0,0,0,0));button("load",236,200,68,()->send(7,0,0,0,0));button("make",236,229,68,()->send(5,0,0,0,0));button("stop",307,229,37,()->send(6,0,0,0,0));}
    private void send(int op,int a,int b,int c,int d){ClientPlayNetworking.send(new PcbNetworking.Action(handler.syncId,handler.value(2),op,a,b,c,d));}
    @Override protected void applyBlur(float delta){}
    private int gx(double mx){return (int)Math.floor((mx-x-18+GRID/2)/GRID);}
    private int gy(double my){return (int)Math.floor((my-y-62+GRID/2)/GRID);}
    private boolean grid(double mx,double my){return mx>=x+12&&mx<x+210&&my>=y+56&&my<y+200;}
    @Override public boolean mouseClicked(double mx,double my,int button){
        mx/=viewScale;my/=viewScale;
        if(button==1&&grid(mx,my)){start=-1;turn=(turn+1)&3;return true;}
        if(button==0){
            for(int i=0;i<8;i++)if(mx>=x+16+i*25&&mx<x+38+i*25&&my>=y+217&&my<y+235){dragType=i;dragPart=-1;dragRevision=handler.value(2);tool=0;return true;}
            if(grid(mx,my)){int u=gx(mx),v=gy(my);var l=handler.layout();
                if(tool==1){if(!l.contains(u,v))return true;int at=PcbLayout.point(u,v);if(start<0)start=at;else{send(1,start,at,hasShiftDown()?1:0,0);start=at;}return true;}
                if(tool==2){send(9,u,v,0,0);return true;}
                if(tool==3){send(2,u,v,0,0);return true;}
                for(int i=0;i<l.parts().size();i++){var part=l.parts().get(i);if(part.contains(u,v)){dragPart=i;dragType=part.type().ordinal();turn=part.turn();dragRevision=handler.value(2);return true;}}
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){mx/=viewScale;my/=viewScale;if(button==0&&dragType>=0){if(grid(mx,my)&&handler.value(2)==dragRevision){int u=gx(mx)-1,v=gy(my)-1;if(dragPart>=0)send(10,dragPart,u,v,turn);else send(0,dragType,u,v,turn);}dragType=dragPart=-1;return true;}return super.mouseReleased(mx,my,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return dragType>=0||super.mouseDragged(mx/viewScale,my/viewScale,button,dx/viewScale,dy/viewScale);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==82){turn=(turn+1)&3;return true;}return super.keyPressed(key,scan,modifiers);}
    private void segment(DrawContext c,int a,int b,int color){int ax=x+18+PcbLayout.x(a)*GRID,ay=y+62+PcbLayout.y(a)*GRID,bx=x+18+PcbLayout.x(b)*GRID,by=y+62+PcbLayout.y(b)*GRID;c.fill(Math.min(ax,bx)-1,Math.min(ay,by)-1,Math.max(ax,bx)+2,Math.max(ay,by)+2,color);}
    @Override protected void drawBackground(DrawContext c,float dt,int mx,int my){
        c.setShaderColor(1,1,1,1);c.fill(x,y,x+354,y+330,0xff242c35);c.fill(x,y,x+354,y+3,0xffa8becf);
        c.drawTexture(Electron.id("textures/gui/precision_mat.png"),x+8,y+47,0,0,218,157,218,157);
        var layout=handler.layout();int[] costs=handler.slots.get(0).getStack().isOf(PcbFabrication.GOLD)?Pcb.costs(layout):new int[]{1,0,1,0,0,0,0,0,0,0,0};
        for(int v=0;v<layout.height();v++)for(int u=0;u<layout.width();u++)if(layout.contains(u,v)){
            int px=x+18+u*GRID,py=y+62+v*GRID;c.fill(px-5,py-5,px+7,py+7,0xff315b52);c.fill(px,py,px+1,py+1,0xff66847b);
        }
        for(var t:layout.tracks())segment(c,t.a(),t.b(),0xffd6ad65);
        for(var p:layout.parts()){
            int px=x+18+(p.x()+1)*GRID,py=y+62+(p.y()+1)*GRID;c.fill(px-9,py-6,px+9,py+6,p.type().logic==null?0xffaa9276:0xff29333f);
            c.getMatrices().push();c.getMatrices().translate(px,py,0);c.getMatrices().scale(.65f,.65f,1);c.drawCenteredTextWithShadow(textRenderer,p.type().name(),0,-3,0xf1ece2);c.getMatrices().pop();
            for(int j=0;j<p.type().pins();j++){int at=p.pin(j),ax=x+18+PcbLayout.x(at)*GRID,ay=y+62+PcbLayout.y(at)*GRID;c.fill(ax-2,ay-2,ax+3,ay+3,0xffe0d2ae);}
        }
        String[] ports={"+","−","A","B","Y","Z"};var pins=layout.ports();for(int i=0;i<6;i++){int px=x+18+PcbLayout.x(pins[i])*GRID,py=y+62+PcbLayout.y(pins[i])*GRID;c.fill(px-3,py-3,px+4,py+4,0xffdfb96b);c.drawText(textRenderer,ports[i],px-2,py-12,0xf3e3be,false);}
        if(start>=0){int px=x+18+PcbLayout.x(start)*GRID,py=y+62+PcbLayout.y(start)*GRID;c.drawBorder(px-4,py-4,9,9,0xffe7f3f7);}
        for(var slot:handler.slots){c.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff7d8993);c.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xff343e49);}
        for(int i=0;i<11;i++){
            var slot=handler.slots.get(i);var item=i==0?handler.slots.get(0).getStack().isOf(PcbFabrication.GOLD)?PcbFabrication.GOLD:PcbFabrication.FILM:i==2?net.minecraft.item.Items.BLACK_DYE:Pcb.MATERIALS.get(i);
            if(!slot.hasStack()){c.setShaderColor(1,1,1,.35f);c.drawItem(new ItemStack(item),x+slot.x,y+slot.y);c.setShaderColor(1,1,1,1);}
            c.drawText(textRenderer,Text.translatable("pcb_ui.electron.need",costs[i]),x+slot.x,y+slot.y+18,slot.getStack().getCount()>=costs[i]?0xaebfb8:0xe5af98,false);
        }
        c.drawText(textRenderer,Text.translatable("pcb_ui.electron.tray"),x+236,y+51,0xd7dee5,false);
        c.drawText(textRenderer,Text.translatable("pcb_ui.electron.dimensions",layout.width(),layout.height(),turn*90),x+12,y+206,0xd7dfeb,false);
        for(int i=0;i<8;i++){c.fill(x+14+i*25,y+215,x+36+i*25,y+237,0xff3f4b55);c.drawItem(new ItemStack(Pcb.PARTS.get(PcbLayout.Type.values()[i])),x+17+i*25,y+218);}
        c.fill(x+10,y+242,x+85,y+247,0xff10191f);c.fill(x+10,y+242,x+10+75*handler.value(0)/1000,y+247,0xffb1c4d3);
        wrapped(c,Text.translatable(handler.value(3)>0?"pcb_ui.electron.error_"+handler.value(3):"pcb_ui.electron.state_"+handler.value(1)),x+10,y+253,78,0xe2d1b4);
        wrapped(c,Text.translatable("pcb_ui.electron.help_"+tool),x+270,y+252,73,0xb7c8d8);
    }
    private void wrapped(DrawContext c,Text text,int x,int y,int width,int color){for(var line:textRenderer.wrapLines(text,width)){c.drawText(textRenderer,line,x,y,color,false);y+=10;}}
    @Override protected void drawForeground(DrawContext c,int mx,int my){c.drawText(textRenderer,title,8,8,0xf0f4f7,false);c.drawText(textRenderer,playerInventoryTitle,97,237,0xd0d9e0,false);}
    @Override public void render(DrawContext c,int mx,int my,float dt){mx=(int)(mx/viewScale);my=(int)(my/viewScale);c.getMatrices().push();c.getMatrices().scale((float)viewScale,(float)viewScale,1);super.render(c,mx,my,dt);if(dragType>=0)c.drawItem(new ItemStack(Pcb.PARTS.get(PcbLayout.Type.values()[dragType])),mx-8,my-8);else drawMouseoverTooltip(c,mx,my);
        if(grid(mx,my)&&dragType<0){int u=gx(mx),v=gy(my);for(var p:handler.layout().parts())for(int i=0;i<p.type().pins();i++)if(p.pin(i)==PcbLayout.point(u,v))c.drawTooltip(textRenderer,Text.literal(p.type().name()+" / "+(p.type().logic==null?(i==0?"A":"K/B"):new String[]{"VCC","GND","A","B","Y"}[i])),mx,my);}
        c.getMatrices().pop();
    }
}
