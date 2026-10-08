package top.lacrus.zephyrzeno.electron.client;

import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;

/** Cosmetic motion only. Electricity and mechanical input remain server authoritative. */
public final class ComponentRenderer implements BlockEntityRenderer<ComponentEntity> {
    private static final Identifier METAL=Electron.id("textures/block/silver.png"),GRIP=Electron.id("textures/block/wood.png"),BUTTON=Electron.id("textures/block/button_cap.png"),GLOW=Electron.id("textures/block/led_glow.png");
    public ComponentRenderer(BlockEntityRendererFactory.Context context){}
    @Override public void render(ComponentEntity entity,float tickDelta,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){
        if(entity.getWorld()==null||!(entity.getCachedState().getBlock() instanceof ComponentBlock block))return;
        var state=entity.getCachedState();Direction face=state.get(ComponentBlock.FACING);int turn=state.get(ComponentBlock.TURN),inset=ComponentGeometry.inset(block.kind);
        double now=entity.getWorld().getTime()+tickDelta,dt=Double.isNaN(entity.lastRenderTime)?0:Math.clamp(now-entity.lastRenderTime,0,4);entity.lastRenderTime=now;
        double target=state.get(ComponentBlock.BROKEN)?0:state.get(ComponentBlock.LEVEL)/15d;
        entity.displayedLight+=(target-entity.displayedLight)*(1-Math.exp(-dt*.8));
        double switchTarget=state.get(ComponentBlock.ENABLED)?1:0;
        entity.displayedSwitch+=(switchTarget-entity.displayedSwitch)*(1-Math.exp(-dt*.9));
        Matrix4f matrix=matrices.peek().getPositionMatrix();
        if(block==top.lacrus.zephyrzeno.electron.industrial.Industrial.WIND_BASE){
            AssemblyRenderer.windFoot(entity,consumers,matrix,light,overlay);
            return;
        }
        if(top.lacrus.zephyrzeno.electron.multiblock.Assembly.formed(state)){
            AssemblyRenderer.draw(entity,block,consumers,matrix,dt,light,overlay);
            return;
        }
        if(block instanceof top.lacrus.zephyrzeno.electron.appliance.ApplianceBlock)return;
        if(entity instanceof top.lacrus.zephyrzeno.electron.display.DisplayEntity display){DisplayRenderer.draw(display,consumers,matrix,light,overlay);return;}
        if(entity instanceof top.lacrus.zephyrzeno.electron.pcb.PcbSocketEntity pcb){PcbRenderer.draw(pcb,consumers,matrix,light,overlay);return;}
        if(entity instanceof top.lacrus.zephyrzeno.electron.pcb.PcbBenchEntity)return;
        // Sensor housings are complete baked models, not the relay mechanism they simulate.
        if(block.kind==ComponentBlock.Kind.RELAY&&top.lacrus.zephyrzeno.electron.industrial.Automation.handles(block))return;
        if(block instanceof top.lacrus.zephyrzeno.electron.industrial.ProcessingBlock machine){
            if(machine.rearPowerPorts())WorkshopMechanismRenderer.draw(entity,machine,matrices,consumers,dt,light,overlay);else processing(entity,machine,consumers,matrix,dt,light,overlay);
            return;
        }
        if(block.kind==ComponentBlock.Kind.CRANK||block.kind==ComponentBlock.Kind.GENERATOR){
            double phase=Math.clamp((now-entity.animationStart)/18,0,1),angle=2*Math.PI*(phase*phase*(3-2*phase));
            cuboid(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(METAL)),matrix,face,turn,inset,6.5,11,-.7,9.5,12,-.4,angle,light,overlay);
            cuboid(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(GRIP)),matrix,face,turn,inset,9,10.8,-1,10,12.2,-.7,angle,light,overlay);
        }else if(block.kind==ComponentBlock.Kind.SWITCH||block.kind==ComponentBlock.Kind.BREAKER){
            double v=9+2*entity.displayedSwitch;cuboid(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(METAL)),matrix,face,turn,inset,6,v,-1,8,v+2,-.4,0,light,overlay);
        }else if(block.kind==ComponentBlock.Kind.BUTTON&&!state.get(ComponentBlock.BROKEN)){
            double depth=-1+.5*entity.displayedSwitch;
            cuboid(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(BUTTON)),matrix,face,turn,inset,5.8,9.8,depth,8.2,12.2,depth+.45,0,light,overlay);
        }else if(block.kind==ComponentBlock.Kind.REDSTONE_SWITCH&&!state.get(ComponentBlock.BROKEN)){
            double v=12.5+.8*entity.displayedSwitch;
            cuboid(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(METAL)),matrix,face,turn,inset,6,v,-.9,8,v+.4,-.55,0,light,overlay);
        }else if(block.kind==ComponentBlock.Kind.RELAY&&!state.get(ComponentBlock.BROKEN)){
            double v=5.6+.9*entity.displayedLight;cuboid(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(METAL)),matrix,face,turn,inset,5.5,v,-.9,8.5,v+.4,-.6,0,light,overlay);
        }else if(block.kind==ComponentBlock.Kind.MOTOR&&!state.get(ComponentBlock.BROKEN)){
            entity.rotorAngle+=dt*.6*entity.displayedLight*(state.get(ComponentBlock.ENABLED)?1:-1);
            var rotor=new Matrix4f(matrix);var center=ComponentGeometry.local(face,turn,8,8,0);
            rotor.translate((float)center.x,(float)center.y,(float)center.z).rotate((float)entity.rotorAngle,face.getOffsetX(),face.getOffsetY(),face.getOffsetZ()).translate((float)-center.x,(float)-center.y,(float)-center.z);
            cuboid(consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(METAL)),rotor,face,turn,inset,6,7.5,-1,10,8.5,1,0,light,overlay);
        }
        if(block.kind==ComponentBlock.Kind.LED&&entity.displayedLight>.002)glow(consumers,matrix,face,turn,7,11,inset-1.05,4.7,(float)entity.displayedLight,.9f,.22f,.1f);
        if(block.kind==ComponentBlock.Kind.LAMP&&!top.lacrus.zephyrzeno.electron.industrial.Lighting.fixture(block)&&entity.displayedLight>.002)glow(consumers,matrix,face,turn,7,11,inset-1.02,5,(float)entity.displayedLight,1,.9f,.65f);
        double sinceFailure=now-entity.failureStart;if(sinceFailure>=0&&sinceFailure<5)glow(consumers,matrix,face,turn,7,10,inset-1.6,6,(float)(1-sinceFailure/5),1,.7f,.3f);
        // Names and pin legends are baked into the solid model's surface materials.
        if(block.kind==ComponentBlock.Kind.BATTERY){
            for(int i=0;i<5;i++)glow(consumers,matrix,face,turn,9.75,6.27+i,inset-.24,1.1,(float)Math.clamp(entity.displayedLight*5-i,0,1),.15f,1,.55f);
        }
    }
    private static void processing(ComponentEntity e,top.lacrus.zephyrzeno.electron.industrial.ProcessingBlock b,VertexConsumerProvider out,Matrix4f parent,double dt,int light,int overlay){
        e.rotorAngle+=dt*.24*e.displayedLight;
        var m=new Matrix4f(parent).translate(.5f,0,.5f).rotateY(-e.getCachedState().get(ComponentBlock.TURN)*(float)Math.PI/2).translate(-.5f,0,-.5f);
        var metal=out.getBuffer(RenderLayer.getEntityCutoutNoCull(METAL));double a=e.rotorAngle;
        if(b==top.lacrus.zephyrzeno.electron.industrial.Processing.FABRICATOR){
            double y=7.8-3.5*(1-Math.cos(a))*.5;
            modelBox(metal,m,7,y+.7,10.5,9,13.5,12.5,light,overlay);modelBox(metal,m,5.9,y,9.3,10.1,y+.7,13.5,light,overlay);
        }else if(b==top.lacrus.zephyrzeno.electron.industrial.Processing.CENTRIFUGE){
            var rotor=new Matrix4f(m).translate(.5f,5f/16,6.6f/16).rotateZ((float)a).translate(-.5f,-5f/16,-6.6f/16);
            modelBox(metal,rotor,6.7,4.85,6.55,9.3,5.15,6.68,light,overlay);modelBox(metal,rotor,7.85,3.7,6.55,8.15,6.3,6.68,light,overlay);
        }else if(b==top.lacrus.zephyrzeno.electron.industrial.Processing.CRUSHER){
            for(double x:new double[]{6.4,9}){var rotor=new Matrix4f(m).translate((float)x/16,0,11f/16).rotateY((float)(x<8?a:-a)).translate((float)-x/16,0,-11f/16);modelBox(metal,rotor,x-1.25,7.2,10.85,x+1.25,12,11.15,light,overlay);}
        }else if(e.displayedLight>.01){
            // The heating indicator sits flush in the front door; it does not emit block light.
            var glow=out.getBuffer(RenderLayer.getEntityCutoutNoCull(Electron.id("textures/block/copper.png")));
            modelBox(glow,m,6.2,6.2,4.02,9.8,6.55,4.08,LightmapTextureManager.MAX_LIGHT_COORDINATE,overlay);
        }
    }
    private static void modelBox(VertexConsumer out,Matrix4f m,double x0,double y0,double z0,double x1,double y1,double z1,int light,int overlay){centeredBox(out,m,x0/16,y0/16,z0/16,x1/16,y1/16,z1/16,light,overlay);}
    private static Vec3d point(Direction face,int turn,int inset,double u,double v,double d,double angle){
        if(angle!=0){double x=u-7,y=v-11.5;u=7+x*Math.cos(angle)-y*Math.sin(angle);v=11.5+x*Math.sin(angle)+y*Math.cos(angle);}
        return ComponentGeometry.local(face,turn,u,v,inset+d);
    }
    static void cuboid(VertexConsumer out,Matrix4f m,Direction face,int turn,int inset,double u0,double v0,double d0,double u1,double v1,double d1,double angle,int light,int overlay){
        Vec3d[] p={point(face,turn,inset,u0,v0,d0,angle),point(face,turn,inset,u1,v0,d0,angle),point(face,turn,inset,u1,v1,d0,angle),point(face,turn,inset,u0,v1,d0,angle),point(face,turn,inset,u0,v0,d1,angle),point(face,turn,inset,u1,v0,d1,angle),point(face,turn,inset,u1,v1,d1,angle),point(face,turn,inset,u0,v1,d1,angle)};
        int[][] sides={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};float[][] uv={{0,0},{0,1},{1,1},{1,0}};
        var normals=new org.joml.Matrix3f(m).invert().transpose();
        for(var side:sides){Vec3d normal=p[side[1]].subtract(p[side[0]]).crossProduct(p[side[2]].subtract(p[side[0]])).normalize();var n=normals.transform(new org.joml.Vector3f((float)normal.x,(float)normal.y,(float)normal.z)).normalize();
            for(int i=0;i<4;i++){var a=p[side[i]];out.vertex(m,(float)a.x,(float)a.y,(float)a.z).color(255,255,255,255).texture(uv[i][0],uv[i][1]).overlay(overlay).light(light).normal(n.x,n.y,n.z);}
        }
    }
    private static void glow(VertexConsumerProvider consumers,Matrix4f matrix,Direction face,int turn,double u,double v,double depth,double width,float alpha,float r,float g,float b){
        var out=consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(GLOW));double h=width/2;double[][] coords={{u-h,v+h,0,0},{u-h,v-h,0,1},{u+h,v-h,1,1},{u+h,v+h,1,0}};
        for(var c:coords){var p=ComponentGeometry.local(face,turn,c[0],c[1],depth);out.vertex(matrix,(float)p.x,(float)p.y,(float)p.z).color(r,g,b,alpha*.75f).texture((float)c[2],(float)c[3]).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ());}
    }
    @Override public boolean rendersOutsideBoundingBox(ComponentEntity entity){return entity instanceof top.lacrus.zephyrzeno.electron.pcb.PcbSocketEntity||entity instanceof top.lacrus.zephyrzeno.electron.display.DisplayEntity||top.lacrus.zephyrzeno.electron.multiblock.Assembly.formed(entity.getCachedState())||entity.getCachedState().isOf(Foundry.WIND);}
    static void centeredBox(VertexConsumer out,Matrix4f m,double x0,double y0,double z0,double x1,double y1,double z1,int light,int overlay){
        cuboid(out,m,Direction.NORTH,0,0,x0*16,y0*16,z0*16,x1*16,y1*16,z1*16,0,light,overlay);
    }
}
