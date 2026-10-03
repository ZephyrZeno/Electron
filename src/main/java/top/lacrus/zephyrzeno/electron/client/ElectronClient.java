package top.lacrus.zephyrzeno.electron.client;
import top.lacrus.zephyrzeno.electron.item.CircuitToolItem;

import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import top.lacrus.zephyrzeno.electron.circuit.*;
import top.lacrus.zephyrzeno.electron.circuit.CircuitParts.Endpoint;
import top.lacrus.zephyrzeno.electron.foundry.Foundry;
import top.lacrus.zephyrzeno.electron.foundry.ComponentBlock;
import top.lacrus.zephyrzeno.electron.block.LogicGateBlock;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public final class ElectronClient implements ClientModInitializer {
    private static CircuitState state=new CircuitState();private static String dimension="";
    private static final CircuitSnapshots.Receiver snapshots=new CircuitSnapshots.Receiver();
    private static List<SurfacePoint> preview=List.of();private static long previewUntil;
    private static Text routeStatus;
    private record PathPreview(String start,SurfacePoint target,List<SurfacePoint> path,Endpoint end,Text status,long tick){}
    private static PathPreview cachedRoute;private static long nextRouteUpdate;
    @Override public void onInitializeClient() {
        MagnifierZoom.register();
        ClientPlayNetworking.registerGlobalReceiver(top.lacrus.zephyrzeno.electron.power.FaultEffects.Event.ID,(payload,context)->context.client().execute(()->ArcRenderer.receive(payload.data())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->ArcRenderer.clear());
        WorldRenderEvents.AFTER_ENTITIES.register(ArcRenderer::render);
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(top.lacrus.zephyrzeno.electron.industrial.Industrial.SCREEN,IndustrialScreen::new);
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(top.lacrus.zephyrzeno.electron.industrial.Industrial.PLANT_SCREEN,PlantScreen::new);
        BlockEntityRendererFactories.register(top.lacrus.zephyrzeno.electron.industrial.Industrial.FLUID_ENTITY,FluidRenderer::new);
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(),top.lacrus.zephyrzeno.electron.industrial.Industrial.GLASS_PIPE,top.lacrus.zephyrzeno.electron.industrial.Industrial.THERMAL_TANK,top.lacrus.zephyrzeno.electron.industrial.Industrial.NUCLEAR_TANK);
        Foundry.CREATIVE_READINGS=()->MinecraftClient.getInstance().player!=null&&MinecraftClient.getInstance().player.isCreative();
        BlockEntityRendererFactories.register(Foundry.COMPONENT_ENTITY,ComponentRenderer::new);
        BlockEntityRendererFactories.register(Foundry.MECHANICAL_ENTITY,MechanicalRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(CircuitNetworking.Snapshot.ID,(payload,context)->context.client().execute(()->snapshots.accept(payload.data()).ifPresent(data->{state=CircuitState.read(data);dimension=data.getString("dimension");cachedRoute=null;nextRouteUpdate=0;})));
        ClientPlayNetworking.registerGlobalReceiver(CircuitNetworking.Meter.ID,(payload,context)->context.client().execute(()->MeterHud.receive(payload.data())));
        ClientPlayNetworking.registerGlobalReceiver(CircuitNetworking.Preview.ID,(payload,context)->context.client().execute(()->{List<SurfacePoint> points=new ArrayList<>();for(var tag:payload.data().getList("points",NbtElement.COMPOUND_TYPE))points.add(SurfacePoint.read((NbtCompound)tag));preview=points;previewUntil=System.currentTimeMillis()+10000;}));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{state=new CircuitState();dimension="";preview=List.of();cachedRoute=null;nextRouteUpdate=0;snapshots.clear();MeterHud.clear();});
        UseItemCallback.EVENT.register((player,world,hand)->{if(world.isClient&&player.getStackInHand(hand).isOf(Foundry.HANDBOOK)){MinecraftClient.getInstance().setScreen(new EngineeringGuideScreen());return TypedActionResult.success(player.getStackInHand(hand));}return TypedActionResult.pass(player.getStackInHand(hand));});
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{if(world.isClient&&player.getStackInHand(hand).isOf(Foundry.HANDBOOK)){MinecraftClient.getInstance().setScreen(new EngineeringGuideScreen());return ActionResult.SUCCESS;}return ActionResult.PASS;});
        WorldRenderEvents.AFTER_ENTITIES.register(ElectronClient::render);
        HudRenderCallback.EVENT.register((context,tick)->{
            MeterHud.render(context);
            var client=MinecraftClient.getInstance();if(client.world==null||client.player==null||client.currentScreen!=null||!dimension.equals(client.world.getRegistryKey().getValue().toString()))return;
            if(client.crosshairTarget instanceof BlockHitResult hit){
                var block=client.world.getBlockState(hit.getBlockPos());boolean tool=client.player.getMainHandStack().getItem() instanceof CircuitToolItem;
                if(block.getBlock() instanceof ComponentBlock||block.getBlock() instanceof LogicGateBlock){
                    Text title=block.getBlock().getName();Text subtitle;
                    if(block.getBlock() instanceof ComponentBlock c)subtitle=block.get(ComponentBlock.BROKEN)?Text.translatable("hud.electron.broken"):c.kind==ComponentBlock.Kind.GATE?Text.translatable("hud.electron.gate"):Text.translatable("hud.electron.face",faceName(block.get(ComponentBlock.FACING)));
                    else subtitle=Text.translatable("hud.electron.redstone",faceName(block.get(LogicGateBlock.FACING)));
                    int w=Math.max(client.textRenderer.getWidth(title),client.textRenderer.getWidth(subtitle));context.fill(8,28,20+w,62,0xee10212b);context.drawTextWithShadow(client.textRenderer,title,14,33,0xffffff);context.drawTextWithShadow(client.textRenderer,subtitle,14,49,0x9ac8bb);
                }
                if(tool){var stack=client.player.getMainHandStack();int layer=CircuitToolItem.data(stack).getInt("layer")&1;var p=CircuitPicking.pick(client.world,state,hit,layer);
                    Text text=Text.translatable("hud.electron.target",layer,state.contactAt(p).map(pin->pin.name()).orElseGet(()->Text.translatable(state.wires.values().stream().anyMatch(w->w.path().contains(p))?"hud.electron.wire_tap":"hud.electron.free_node")));
                    context.fill(8,66,20+client.textRenderer.getWidth(text),83,0xee10212b);context.drawTextWithShadow(client.textRenderer,text,14,70,0x8de5c6);
                    if(CircuitToolItem.wireSpool(stack)&&routeStatus!=null)context.drawTextWithShadow(client.textRenderer,routeStatus,14,88,0xc4dbd2);
                }
            }
        });
    }
    private static Text faceName(Direction f){return Text.translatable("direction.electron."+f.asString());}
    private static void render(WorldRenderContext context) {
        var client=MinecraftClient.getInstance();if(client.world==null||!dimension.equals(client.world.getRegistryKey().getValue().toString())||context.consumers()==null||context.matrixStack()==null)return;
        Vec3d camera=context.camera().getPos();Matrix4f matrix=context.matrixStack().peek().getPositionMatrix();var buffer=context.consumers().getBuffer(RenderLayer.getDebugQuads());
        var endpoints=state.endpoints();
        for(var w:state.wires.values())if(Vec3d.ofCenter(w.path().getFirst().host()).squaredDistanceTo(camera)<128*128){
            WireRenderer.draw(context.consumers(),matrix,camera,state,w);
        }
        buffer=context.consumers().getBuffer(RenderLayer.getDebugQuads());
        for(var core:state.cores.values())if(Vec3d.ofCenter(core.host()).squaredDistanceTo(camera)<96*96)for(int side=0;side<2;side++) {
            var first=core.endpoint(side,0).point();var second=core.endpoint(side,1).point();Vec3d mid=first.position().add(second.position()).multiply(.5);
            plate(buffer,matrix,camera,mid,first.face(),.21,.15,.025,0x344453);
            for(int channel=0;channel<2;channel++){var p=core.endpoint(side,channel).point();plate(buffer,matrix,camera,p.position().add(Vec3d.of(p.face().getVector()).multiply(.028)),p.face(),.045,.045,.022,channel==0?0xd89552:0x97aaba);}
        }
        for(var junction:state.junctions.values())if(Vec3d.ofCenter(junction.point().host()).squaredDistanceTo(camera)<96*96) {
            var p=junction.point();double radius=junction.wireNode()?.027:.063;plate(buffer,matrix,camera,state.position(p),p.face(),radius,radius,.016,0x304256);plate(buffer,matrix,camera,state.position(p).add(Vec3d.of(p.face().getVector()).multiply(.016)),p.face(),junction.wireNode()?.013:.026,junction.wireNode()?.013:.026,.012,0xe6bc72);
        }
        for(var bridge:state.bridges.values())if(Vec3d.ofCenter(bridge.host()).squaredDistanceTo(camera)<96*96){
            for(var part:BridgeGeometry.parts(state,bridge))cube(buffer,matrix,camera,part.bounds(),part.color(),255);
        }
        routeStatus=null;
        if(client.player!=null&&CircuitToolItem.wireSpool(client.player.getMainHandStack())&&client.crosshairTarget instanceof BlockHitResult hit){
            var data=CircuitToolItem.data(client.player.getMainHandStack());var start=state.endpoints().get(data.getString("start"));
            var p=CircuitPicking.pick(client.world,state,hit,data.getInt("layer")&1);plate(buffer,matrix,camera,state.position(p),p.face(),.026,.026,.012,0x7cffe0);
            if(start!=null&&dimension.equals(data.getString("dimension"))){
                long now=client.world.getTime();boolean same=cachedRoute!=null&&cachedRoute.start.equals(start.id())&&cachedRoute.target.equals(p);
                if((!same||now-cachedRoute.tick>=10)&&now>=nextRouteUpdate){
                    nextRouteUpdate=now+2;
                    try{var copy=state.wiringCopy();boolean cable=CircuitToolItem.cable(client.player.getMainHandStack());var end=copy.joinAt(client.world,p,cable);var path=start.id().equals(end.id())?List.<SurfacePoint>of():copy.connectionRoute(client.world,start,end,data.getInt("layer")&1,cable);
                        cachedRoute=new PathPreview(start.id(),p,path,end,path.isEmpty()?Text.translatable("hud.electron.finish"):Text.translatable("hud.electron.preview",VerticalWire.connection(copy,start,end)?VerticalWire.units(copy,path):OverheadCable.connection(copy,start,end,cable)?OverheadCable.units(copy,path):copy.lengthUnits(path,start,end)),now);
                    }catch(IllegalArgumentException error){cachedRoute=new PathPreview(start.id(),p,List.of(),null,CircuitError.text(error),now);}
                }
                if(cachedRoute!=null&&cachedRoute.start.equals(start.id())&&cachedRoute.target.equals(p)){
                    routeStatus=cachedRoute.status;if(cachedRoute.end!=null){
                        boolean cable=CircuitToolItem.cable(client.player.getMainHandStack());
                        if(VerticalWire.connection(state,start,cachedRoute.end))suspended(buffer,matrix,camera,VerticalWire.vertices(state,cachedRoute.path),0x7cffe0,170);
                        else if(OverheadCable.connection(state,start,cachedRoute.end,cable))suspended(buffer,matrix,camera,OverheadCable.vertices(state,cachedRoute.path),0x7cffe0,170);
                        else wire(buffer,matrix,camera,cachedRoute.path,0x7cffe0,170,start,cachedRoute.end,cable?OverheadCable.RADIUS:.010);
                    }
                    else plate(buffer,matrix,camera,state.position(p),p.face(),.034,.034,.014,0xff6b67);
                }
            }
        }
    }
    private static void wire(VertexConsumer out,Matrix4f m,Vec3d camera,List<SurfacePoint> points,int color,int alpha,Endpoint from,Endpoint to,double radius) {
        if(points.size()<2)return;
        List<Vec3d> visible=WireGeometry.vertices(points,state::inset);
        Vec3d start=visible.getFirst(),previous=start;int axis=-1;
        for(int i=1;i<visible.size();i++){Vec3d next=visible.get(i);int a=next.x!=previous.x?0:next.y!=previous.y?1:2;if(axis!=-1&&axis!=a){bar(out,m,camera,start,previous,radius,color,alpha);start=previous;}axis=a;previous=next;}
        bar(out,m,camera,start,previous,radius,color,alpha);
        for(int i=0;i<2;i++){var p=i==0?points.getFirst():points.getLast();var endpoint=i==0?from:to;var contact=endpoint==null?p.layer(0):endpoint.point();
            if(contact.layer()!=p.layer())bar(out,m,camera,state.position(contact),state.position(p),radius,color,alpha);Vec3d end=state.position(contact);double pad=Math.max(.014,radius);cube(out,m,camera,new Box(end.add(-pad,-pad,-pad),end.add(pad,pad,pad)),0xd7ad72,alpha);}
    }
    private static void plate(VertexConsumer v,Matrix4f m,Vec3d camera,Vec3d center,Direction face,double a,double b,double depth,int color) {
        double x=face.getAxis()==Direction.Axis.X?depth:a,y=face.getAxis()==Direction.Axis.Y?depth:b,z=face.getAxis()==Direction.Axis.Z?depth:face.getAxis()==Direction.Axis.X?a:b;
        cube(v,m,camera,new Box(center.add(-x,-y,-z),center.add(x,y,z)),color,255);
    }
    private static void suspended(VertexConsumer out,Matrix4f matrix,Vec3d camera,List<Vec3d> points,int color,int alpha){
        for(int i=2;i<points.size()+2;i+=2){int end=Math.min(i,points.size()-1);Vec3d a=points.get(i-2).subtract(camera),b=points.get(end).subtract(camera);
            Vec3d axis=b.subtract(a).normalize(),u=axis.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-8)u=axis.crossProduct(new Vec3d(1,0,0));u=u.normalize().multiply(OverheadCable.RADIUS);Vec3d v=axis.crossProduct(u).normalize().multiply(OverheadCable.RADIUS);
            Vec3d[] p={a.add(u).add(v),a.subtract(u).add(v),a.subtract(u).subtract(v),a.add(u).subtract(v),b.add(u).add(v),b.subtract(u).add(v),b.subtract(u).subtract(v),b.add(u).subtract(v)};
            for(int[] face:new int[][]{{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7},{0,3,2,1},{4,5,6,7}})for(int index:face){var vertex=p[index];out.vertex(matrix,(float)vertex.x,(float)vertex.y,(float)vertex.z).color((color>>16)&255,(color>>8)&255,color&255,alpha);}
            if(end==points.size()-1)break;
        }
    }
    private static void bar(VertexConsumer v,Matrix4f m,Vec3d camera,Vec3d a,Vec3d b,double radius,int color,int alpha){cube(v,m,camera,new Box(a,b).expand(radius),color,alpha);}
    private static void cube(VertexConsumer v,Matrix4f m,Vec3d camera,Box box,int color,int alpha) {
        float x0=(float)(box.minX-camera.x),y0=(float)(box.minY-camera.y),z0=(float)(box.minZ-camera.z),x1=(float)(box.maxX-camera.x),y1=(float)(box.maxY-camera.y),z1=(float)(box.maxZ-camera.z);
        float[][] p={{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};float[] shade={.85f,.72f,.78f,.92f,1,.60f};
        for(int i=0;i<6;i++)for(int j:faces[i])v.vertex(m,p[j][0],p[j][1],p[j][2]).color((int)(((color>>16)&255)*shade[i]),(int)(((color>>8)&255)*shade[i]),(int)((color&255)*shade[i]),alpha);
    }
}
