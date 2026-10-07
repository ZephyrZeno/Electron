package top.lacrus.zephyrzeno.electron.client;

import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import top.lacrus.zephyrzeno.electron.pcb.*;

/** Real client rendering / event dispatch, no desktop input automation and no player world. */
public final class InterfaceQa implements ClientModInitializer {
    private int ticks,stage;
    private PcbScreen pcb;
    private final java.util.Map<String,int[]> canvasBounds=new java.util.LinkedHashMap<>();
    private final Path output=Path.of(System.getProperty("electron.interface.output"));
    @Override public void onInitializeClient(){
        ClientLifecycleEvents.CLIENT_STARTED.register(client->org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getHandle()));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            try{step(client);}catch(Throwable error){
                try{Files.createDirectories(output);Files.writeString(output.resolve("failure.txt"),error.toString());}catch(Exception ignored){}
                error.printStackTrace();client.scheduleStop();
            }
        });
    }
    private void step(MinecraftClient c)throws Exception{
        if(stage==0){
            if(c.currentScreen==null||c.getOverlay()!=null)return;
            Files.createDirectories(output);Files.deleteIfExists(output.resolve("failure.txt"));Files.deleteIfExists(output.resolve("verified.json"));
            org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().getHandle());
            c.options.pauseOnLostFocus=false;c.options.getMaxFps().setValue(60);
            c.options.getGuiScale().setValue(2);c.onResolutionChanged();
            c.setScreen(new EngineeringGuideScreen());stage=1;ticks=0;return;
        }
        if(++ticks<35)return;ticks=0;
        System.out.println("Electron interface QA stage "+stage);
        if(stage==1){capture(c,"guide.png");var inv=new PlayerInventory(null);var handler=new PcbScreenHandler(1,inv);handler.snapshot(SingleBoardCalculator.layout().encode());pcb=new PcbScreen(handler,inv,Text.translatable("block.electron.pcb_design_station"));c.setScreen(new CanvasHost(pcb));stage=2;return;}
        if(stage==2){capture(c,"pcb-fit.png");checkDragAndZoom(c);stage=3;return;}
        if(stage==3){capture(c,"pcb-panned.png");c.options.getGuiScale().setValue(3);c.onResolutionChanged();checkDragAndZoom(c);stage=4;return;}
        if(stage==4){capture(c,"pcb-scaled.png");stage=5;return;}
        if(stage==5){
            if(!Files.exists(output.resolve("screenshots/pcb-scaled.png")))return;
            Files.writeString(output.resolve("canvas-bounds.json"),new com.google.gson.Gson().toJson(canvasBounds));
            Files.writeString(output.resolve("verified.json"),"{\"manual_rendered\":true,\"pcb_left_drag\":true,\"pcb_middle_drag\":true,\"zoom_at_pointer\":true,\"resize_preserves_view\":true,\"gui_scales\":[2,3],\"computer_use\":false}\n");
            c.scheduleStop();stage=6;
        }
    }
    private void capture(MinecraftClient c,String name)throws Exception{
        if(name.startsWith("pcb-")){
            double scale=(double)field(pcb,PcbScreen.class,"viewScale"),gui=c.getWindow().getScaleFactor();
            int x=(int)field(pcb,HandledScreen.class,"x"),y=(int)field(pcb,HandledScreen.class,"y");
            canvasBounds.put(name,new int[]{(int)(Math.floor((x+122)*scale)*gui),(int)(Math.floor((y+48)*scale)*gui),
                    (int)(Math.ceil((x+394)*scale)*gui),(int)(Math.ceil((y+259)*scale)*gui)});
        }
        ScreenshotRecorder.saveScreenshot(output.toFile(),name,c.getFramebuffer(),message->{});
    }
    private static Object field(Object object,Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void checkDragAndZoom(MinecraftClient c)throws Exception{
        var view=(PcbViewport)field(pcb,PcbScreen.class,"viewport");double scale=(double)field(pcb,PcbScreen.class,"viewScale");
        int x=(int)field(pcb,HandledScreen.class,"x"),y=(int)field(pcb,HandledScreen.class,"y");
        double mx=(x+125)*scale,my=(y+51)*scale;
        for(int button:new int[]{0,2}){
            double before=view.screenX(20),beforeY=view.screenY(20);
            if(!pcb.mouseClicked(mx,my,button))throw new AssertionError("Pan click rejected");
            pcb.mouseDragged(mx+18*scale,my+11*scale,button,18*scale,11*scale);
            pcb.mouseReleased(-50,-50,button);pcb.keyReleased(32,0,0);
            if(Math.abs(view.screenX(20)-before-18)>.001||Math.abs(view.screenY(20)-beforeY-11)>.001||view.dragging())throw new AssertionError("Drag transform or outside release failed");
        }
        double column=view.column(128),row=view.row(101);
        pcb.mouseScrolled((x+250)*scale,(y+149)*scale,0,3);
        if(Math.abs(column-view.column(128))>.001||Math.abs(row-view.row(101))>.001)throw new AssertionError("Zoom drifted from cursor");
        double before=view.screenX(20),beforeY=view.screenY(20);
        pcb.resize(c,c.getWindow().getScaledWidth(),c.getWindow().getScaledHeight());
        if(before!=view.screenX(20)||beforeY!=view.screenY(20))throw new AssertionError("Resize recentered board");
    }
    // HandledScreen.tick needs a live player. The host renders the real screen without inventing a world/player.
    private static final class CanvasHost extends Screen {
        private final PcbScreen child;
        CanvasHost(PcbScreen child){super(Text.literal("Interface QA"));this.child=child;}
        @Override protected void init(){child.init(client,width,height);}
        @Override public void render(DrawContext c,int x,int y,float dt){child.render(c,-100,-100,dt);}
        @Override public void renderBackground(DrawContext c,int x,int y,float dt){}
    }
}
