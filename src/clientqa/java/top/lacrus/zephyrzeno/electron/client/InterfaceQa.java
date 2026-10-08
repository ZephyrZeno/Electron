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
        if(stage==4){capture(c,"pcb-scaled.png");prepareRegion(c);stage=5;return;}
        if(stage==5){
            capture(c,"pcb-region.png");checkCancelAndTray(c);stage=6;return;
        }
        if(stage==6){capture(c,"pcb-sequential.png");for(int i=0;i<3;i++)click(60,78);if((int)field(pcb,PcbScreen.class,"category")!=6)throw new AssertionError("Analog tray inaccessible");pcb.getScreenHandler().snapshot(PcbExamples.timer().encode());pcb.keyPressed(70,0,0);stage=7;return;}
        if(stage==7){capture(c,"pcb-analog.png");double scale=(double)field(pcb,PcbScreen.class,"viewScale");int left=(int)field(pcb,HandledScreen.class,"x"),top=(int)field(pcb,HandledScreen.class,"y");pcb.mouseScrolled((left+60)*scale,(top+130)*scale,0,-1);pcb.getScreenHandler().snapshot(PcbExamples.regulated().encode());pcb.keyPressed(70,0,0);stage=8;return;}
        if(stage==8){capture(c,"pcb-buck.png");c.options.getGuiScale().setValue(2);c.onResolutionChanged();c.setScreen(new SurfaceQaScreen(false));stage=9;return;}
        if(stage==9){capture(c,"surfaces-top.png");c.setScreen(new SurfaceQaScreen(true));stage=10;return;}
        if(stage==10){capture(c,"surfaces-profile.png");stage=11;return;}
        if(stage==11){
            if(!Files.exists(output.resolve("screenshots/surfaces-profile.png")))return;
            Files.writeString(output.resolve("canvas-bounds.json"),new com.google.gson.Gson().toJson(canvasBounds));
            Files.writeString(output.resolve("verified.json"),"{\"manual_rendered\":true,\"pcb_left_drag\":true,\"pcb_middle_drag\":true,\"zoom_at_pointer\":true,\"resize_preserves_view\":true,\"region_selection\":true,\"copy_preview\":true,\"tool_switch_cancels_paste\":true,\"select_all_cancels_paste\":true,\"sequential_tray\":true,\"analog_tray\":true,\"analog_samples\":true,\"gui_scales\":[2,3],\"computer_use\":false}\n");
            Files.writeString(output.resolve("surface-rendering.json"),"{\"baked_world_models\":6,\"cabinet_variants\":3,\"views\":[\"top\",\"profile\"],\"computer_use\":false}\n");
            c.scheduleStop();stage=12;
        }
    }
    private void click(int x,int y)throws Exception{
        double scale=(double)field(pcb,PcbScreen.class,"viewScale");int left=(int)field(pcb,HandledScreen.class,"x"),top=(int)field(pcb,HandledScreen.class,"y");
        pcb.mouseClicked((left+x)*scale,(top+y)*scale,0);pcb.mouseReleased((left+x)*scale,(top+y)*scale,0);
    }
    private double[] canvasPoint(int u,int v)throws Exception{
        var view=(PcbViewport)field(pcb,PcbScreen.class,"viewport");double scale=(double)field(pcb,PcbScreen.class,"viewScale");
        int x=(int)field(pcb,HandledScreen.class,"x"),y=(int)field(pcb,HandledScreen.class,"y");return new double[]{(x+122+view.screenX(u))*scale,(y+48+view.screenY(v))*scale};
    }
    private void prepareRegion(MinecraftClient c)throws Exception{
        var sample=new PcbLayout(java.util.List.of(),java.util.List.of(),64,48,java.util.Set.of()).withLayers(2)
                .place(PcbLayout.Type.NOT,4,4,0).place(PcbLayout.Type.DFF,8,4,0)
                .route(PcbLayout.point(4,4),PcbLayout.point(8,4),false).via(6,4)
                .route(PcbLayout.point(6,4,1),PcbLayout.point(6,8,1),false);
        pcb.getScreenHandler().snapshot(sample.encode());pcb.resize(c,c.getWindow().getScaledWidth(),c.getWindow().getScaledHeight());
        click(215,32);double[] a=canvasPoint(3,3),b=canvasPoint(11,9);
        pcb.mouseClicked(a[0],a[1],0);pcb.mouseDragged(b[0],b[1],0,b[0]-a[0],b[1]-a[1]);pcb.mouseReleased(b[0],b[1],0);
        var region=(PcbSelection.Area)field(pcb,PcbScreen.class,"region");
        if(region==null||sample.parts().stream().filter(region::contains).count()!=2)throw new AssertionError("Region selection failed");
        pcb.keyPressed(67,0,2);pcb.keyPressed(86,0,2);
        if(!(boolean)field(pcb,PcbScreen.class,"pasteMode"))throw new AssertionError("Clipboard preview not entered");
        var validate=PcbScreen.class.getDeclaredMethod("validRegionGhost",int.class,int.class);validate.setAccessible(true);
        if(!(boolean)validate.invoke(pcb,18,0)||(boolean)validate.invoke(pcb,0,0))throw new AssertionError("Copy preview accepted overlap or rejected free area");
        double[] destination=canvasPoint(21,3);var host=(CanvasHost)c.currentScreen;host.mouseX=(int)destination[0];host.mouseY=(int)destination[1];
    }
    private void checkCancelAndTray(MinecraftClient c)throws Exception{
        click(302,271);if((boolean)field(pcb,PcbScreen.class,"pasteMode"))throw new AssertionError("Via tool kept stale paste");
        // There is no network connection here: an accidental edit packet fails this check.
        double[] outside=canvasPoint(64,4);pcb.mouseClicked(outside[0],outside[1],0);pcb.mouseReleased(outside[0],outside[1],0);
        pcb.keyPressed(86,0,2);pcb.keyPressed(65,0,2);if((boolean)field(pcb,PcbScreen.class,"pasteMode"))throw new AssertionError("Select all kept stale paste");
        pcb.keyPressed(256,0,0);
        for(int i=0;i<3;i++)click(60,78);
        if((int)field(pcb,PcbScreen.class,"category")!=3)throw new AssertionError("Sequential category inaccessible");
        var host=(CanvasHost)c.currentScreen;host.mouseX=host.mouseY=-100;
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
        private int mouseX=-100,mouseY=-100;
        CanvasHost(PcbScreen child){super(Text.literal("Interface QA"));this.child=child;}
        @Override protected void init(){child.init(client,width,height);}
        @Override public void render(DrawContext c,int x,int y,float dt){child.render(c,mouseX,mouseY,dt);}
        @Override public void renderBackground(DrawContext c,int x,int y,float dt){}
    }
}
