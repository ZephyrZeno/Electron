package top.lacrus.zephyrzeno.electron.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.block.BlockState;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import top.lacrus.zephyrzeno.electron.foundry.*;
import top.lacrus.zephyrzeno.electron.industrial.Industrial;

/** Test-only inspection of actual baked world models and dynamic cabinet surfaces. */
final class SurfaceQaScreen extends Screen {
    private final boolean profile;
    private static final String[] DEVICES={"wire_and_gate","resistor_220","power_mosfet","momentary_button","terminal_strip","tower_terminal"};
    SurfaceQaScreen(boolean profile){super(Text.literal("Surface material review"));this.profile=profile;}
    @Override public void renderBackground(DrawContext c,int x,int y,float dt){}
    @Override public void render(DrawContext c,int mx,int my,float dt){
        c.fill(0,0,width,height,0xff101b25);
        c.drawText(textRenderer,"ELECTRON / SOLID SURFACE MATERIALS",12,10,0xffb7e2d7,false);
        c.drawText(textRenderer,profile?"LOW ANGLE / ROTATED MOUNTS":"TOP / CABINET INLAYS",12,23,0xff92a4b5,false);
        int columns=3,cellW=(width-24)/columns,cellH=(height-42)/3;
        for(int index=0;index<9;index++){
            int x=12+index%columns*cellW,y=40+index/columns*cellH;
            c.fill(x+2,y+2,x+cellW-2,y+cellH-2,0xff1b2a36);
            BlockState state=index<6?Foundry.DEVICES.get(DEVICES[index]).getDefaultState():index==6?Foundry.NUCLEAR.getDefaultState():Industrial.WIND_BASE.getDefaultState();
            state=state.with(ComponentBlock.FACING,index==8?Direction.SOUTH:Direction.UP).with(ComponentBlock.TURN,profile?1:0);
            c.drawText(textRenderer,state.getBlock().getName(),x+6,y+cellH-14,0xffe3ebee,false);
            c.draw();var matrices=c.getMatrices();matrices.push();
            matrices.translate(x+cellW*.5,y+cellH*.56,180);
            float scale=Math.min(cellW*.60f,cellH*(index>=5?.64f:.73f));matrices.scale(scale,-scale,scale);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(profile?10:48));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-24));
            matrices.translate(-.5,index==5||index==8?-.5:index>=6?-.8:-.14,index==8?-.8:-.5);
            var consumers=client.getBufferBuilders().getEntityVertexConsumers();
            RenderSystem.enableDepthTest();DiffuseLighting.enableGuiDepthLighting();
            if(index<6)client.getBlockRenderManager().renderBlockAsEntity(state,matrices,consumers,LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV);
            else{var entity=new ComponentEntity(BlockPos.ORIGIN,state);entity.cabinetOpen=index!=7;CabinetRenderer.draw(entity,consumers,matrices.peek().getPositionMatrix(),LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV);}
            consumers.draw();matrices.pop();DiffuseLighting.disableGuiDepthLighting();
        }
    }
}
