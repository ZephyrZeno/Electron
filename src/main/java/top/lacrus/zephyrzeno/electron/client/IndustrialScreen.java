package top.lacrus.zephyrzeno.electron.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import top.lacrus.zephyrzeno.electron.industrial.*;

public final class IndustrialScreen extends HandledScreen<IndustrialScreenHandler> {
    private int selected;
    public IndustrialScreen(IndustrialScreenHandler h,PlayerInventory i,Text t){super(h,i,t);backgroundWidth=356;backgroundHeight=222;playerInventoryTitleY=126;}
    @Override protected void init(){super.init();
        for(int n=0;n<AssemblyRecipes.recipes().size();n++){int index=n;addDrawableChild(ButtonWidget.builder(Text.translatable("assembly.electron."+AssemblyRecipes.recipes().get(n).id()),b->{selected=index;}).dimensions(x+184,y+24+n*20,164,18).build());}
        addDrawableChild(ButtonWidget.builder(Text.translatable("assembly.electron.build"),b->client.interactionManager.clickButton(handler.syncId,selected)).dimensions(x+184,y+190,164,22).build());
    }
    @Override protected void applyBlur(float delta){}
    @Override protected void drawBackground(DrawContext c,float delta,int mx,int my){
        c.fill(x,y,x+backgroundWidth,y+backgroundHeight,0xff152832);c.fill(x,y,x+backgroundWidth,y+3,0xff66d4b5);
        for(var slot:handler.slots)c.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xff46616b);
        var recipe=AssemblyRecipes.recipes().get(selected);int xx=x+8;
        for(var ingredient:recipe.inputs()){var stack=new net.minecraft.item.ItemStack(ingredient.item(),ingredient.count());c.drawItem(stack,xx,y+92);c.drawItemInSlot(textRenderer,stack,xx,y+92);if(mx>=xx&&mx<xx+16&&my>=y+92&&my<y+108)c.drawItemTooltip(textRenderer,stack,mx,my);xx+=22;}
        c.drawText(textRenderer,Text.translatable("assembly.electron.inputs"),x+8,y+114,0xb9d1d5,false);
        c.fill(x+182,y+24+selected*20,x+184,y+42+selected*20,0xff66d4b5);
    }
    @Override protected void drawForeground(DrawContext c,int mx,int my){c.drawText(textRenderer,title,8,7,0xe2f0ed,false);c.drawText(textRenderer,playerInventoryTitle,8,126,0xa9c8cc,false);}
    @Override public void render(DrawContext c,int mx,int my,float d){super.render(c,mx,my,d);drawMouseoverTooltip(c,mx,my);}
}
