package top.lacrus.zephyrzeno.electron.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import top.lacrus.zephyrzeno.electron.Electron;
import top.lacrus.zephyrzeno.electron.foundry.*;

/** Cosmetic motion only. Electricity and mechanical input remain server authoritative. */
public final class ComponentRenderer implements BlockEntityRenderer<ComponentEntity> {
    private static final Identifier METAL=Electron.id("textures/block/silver.png"),GRIP=Electron.id("textures/block/wood.png"),BUTTON=Electron.id("textures/block/button_cap.png"),GLOW=Electron.id("textures/block/led_glow.png");
    private final TextRenderer text;
    public ComponentRenderer(BlockEntityRendererFactory.Context context){text=context.getTextRenderer();}
    @Override public void render(ComponentEntity entity,float tickDelta,MatrixStack matrices,VertexConsumerProvider consumers,int light,int overlay){
        if(entity.getWorld()==null||!(entity.getCachedState().getBlock() instanceof ComponentBlock block))return;
        var state=entity.getCachedState();Direction face=state.get(ComponentBlock.FACING);int turn=state.get(ComponentBlock.TURN),inset=ComponentGeometry.inset(block.kind);
        double now=entity.getWorld().getTime()+tickDelta,dt=Double.isNaN(entity.lastRenderTime)?0:Math.clamp(now-entity.lastRenderTime,0,4);entity.lastRenderTime=now;
        double target=state.get(ComponentBlock.BROKEN)?0:state.get(ComponentBlock.LEVEL)/15d;
        entity.displayedLight+=(target-entity.displayedLight)*(1-Math.exp(-dt*.8));
        double switchTarget=state.get(ComponentBlock.ENABLED)?1:0;
        entity.displayedSwitch+=(switchTarget-entity.displayedSwitch)*(1-Math.exp(-dt*.9));
        Matrix4f matrix=matrices.peek().getPositionMatrix();
        if(top.lacrus.zephyrzeno.electron.multiblock.Assembly.formed(state)){
            AssemblyRenderer.draw(entity,block,consumers,matrix,dt,light,overlay);
            for(int i=0;i<(block.kind==ComponentBlock.Kind.WIND?0:ComponentPins.count(block.kind));i++)label(consumers,matrix,Direction.UP,turn,ComponentPins.u(block.kind,i),ComponentPins.v(block.kind,i)-1.4,.82,ComponentPins.label(block.kind,i),.007f,0xe4eeee,light);
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
        if(block.kind==ComponentBlock.Kind.LAMP&&entity.displayedLight>.002)glow(consumers,matrix,face,turn,7,11,inset-1.02,5,(float)entity.displayedLight,1,.9f,.65f);
        double sinceFailure=now-entity.failureStart;if(sinceFailure>=0&&sinceFailure<5)glow(consumers,matrix,face,turn,7,10,inset-1.6,6,(float)(1-sinceFailure/5),1,.7f,.3f);
        if(block.kind==ComponentBlock.Kind.GATE){
            label(consumers,matrix,face,turn,7,10.5,inset-.83,block.logic.name().equals("BUFFER")?"BUF":block.logic.name(),.009f,0xf1eee0,light);
            int[] u={3,11,3,11,7},v={13,13,3,3,7};String[] names={"VCC","GND","A",block.logic.unary()?"NC":"B","Y"};
            for(int i=0;i<5;i++)label(consumers,matrix,face,turn,u[i],v[i]-1.55,inset-.13,names[i],.0062f,i==0?0xffd498:0xe4eeee,light);
        }else if(block.kind==ComponentBlock.Kind.RESISTOR)label(consumers,matrix,face,turn,8,12.2,inset-.25,(int)block.resistance+" Ω",.012f,0xf4f5eb,light);
        else if(block.kind==ComponentBlock.Kind.DIODE||block.kind==ComponentBlock.Kind.FUSE)label(consumers,matrix,face,turn,8,12.2,inset-.25,block.kind==ComponentBlock.Kind.DIODE?"A >| K":"25 mA",.011f,0xf4f5eb,light);
        else if(block.kind==ComponentBlock.Kind.CRANK)label(consumers,matrix,face,turn,7,3.1,inset-.15,"5 V DC",.010f,0xf4f5eb,light);
        else if(block.kind==ComponentBlock.Kind.CAPACITOR)label(consumers,matrix,face,turn,7,3.1,inset-.15,"100 mF / 10 V",.008f,0xf4f5eb,light);
        else if(block.kind==ComponentBlock.Kind.BUTTON)label(consumers,matrix,face,turn,7,3.1,inset-.15,"1 s / NO",.010f,0xf4f5eb,light);
        else if(block.kind==ComponentBlock.Kind.GENERATOR||block.kind==ComponentBlock.Kind.LAMP||block.kind==ComponentBlock.Kind.MOTOR||block.kind==ComponentBlock.Kind.BREAKER)label(consumers,matrix,face,turn,7,3.1,inset-.15,block.kind==ComponentBlock.Kind.BREAKER?"1 A DC":"24 V DC",.010f,0xf4f5eb,light);
        else if(top.lacrus.zephyrzeno.electron.power.GridDevices.handles(block.kind))label(consumers,matrix,face,turn,7.5,11.2,inset-.65,block.kind==ComponentBlock.Kind.SUBSTATION?"240 / 24 V":"240 V DC",.008f,0xf4f5eb,light);
        else if(block.kind==ComponentBlock.Kind.MOSFET)label(consumers,matrix,face,turn,7,10.5,inset-.92,"N-MOS",.008f,0xf4f5eb,light);
        else if(block.kind==ComponentBlock.Kind.RELAY)label(consumers,matrix,face,turn,7,9.5,inset-.92,"5 V",.009f,0xf4f5eb,light);
        else if(block.kind==ComponentBlock.Kind.BOOST||block.kind==ComponentBlock.Kind.BUCK)label(consumers,matrix,face,turn,7,9.5,inset-.92,String.format(java.util.Locale.ROOT,"%s %.1f V",block.kind==ComponentBlock.Kind.BOOST?"UP":"DN",block.resistance),.007f,0xf4f5eb,light);
        else if(block.kind==ComponentBlock.Kind.BATTERY){
            label(consumers,matrix,face,turn,7,8.3,inset-.98,"5 V",.010f,0xf4f5eb,light);
            for(int i=0;i<5;i++)glow(consumers,matrix,face,turn,9.75,6.27+i,inset-.24,1.1,(float)Math.clamp(entity.displayedLight*5-i,0,1),.15f,1,.55f);
        }else if(block.kind==ComponentBlock.Kind.REDSTONE_SWITCH){
            label(consumers,matrix,face,turn,7,10.5,inset-.88,"5 V",.009f,0xf4f5eb,light);
            label(consumers,matrix,face,turn,10,1.3,inset-.13,"RS IN",.007f,0xf7b498,light);
            label(consumers,matrix,face,turn,10,15,inset-.13,"RS OUT",.007f,0xf7b498,light);
        }
        if(block.kind!=ComponentBlock.Kind.GATE&&ComponentPins.count(block.kind)>2){
            for(int i=block==top.lacrus.zephyrzeno.electron.industrial.Industrial.WIND_BASE?2:0;i<ComponentPins.count(block.kind);i++)label(consumers,matrix,face,turn,ComponentPins.u(block.kind,i),ComponentPins.v(block.kind,i)-1.55,inset-.13,ComponentPins.label(block.kind,i),.0062f,0xe4eeee,light);
        }
    }
    private void label(VertexConsumerProvider out,Matrix4f parent,Direction face,int turn,double u,double v,double d,String value,float scale,int color,int light){
        Vec3d origin=ComponentGeometry.local(face,turn,u,v,d),a=ComponentGeometry.local(face,turn,u+1,v,d).subtract(origin).multiply(16),b=ComponentGeometry.local(face,turn,u,v+1,d).subtract(origin).multiply(16),n=Vec3d.of(face.getVector());
        if(a.crossProduct(b).dotProduct(n)<0)a=a.negate();
        Matrix4f frame=new Matrix4f().setColumn(0,new Vector4f((float)a.x,(float)a.y,(float)a.z,0)).setColumn(1,new Vector4f((float)b.x,(float)b.y,(float)b.z,0)).setColumn(2,new Vector4f((float)n.x,(float)n.y,(float)n.z,0)).setColumn(3,new Vector4f((float)origin.x,(float)origin.y,(float)origin.z,1));
        Matrix4f m=new Matrix4f(parent).mul(frame).scale(scale,-scale,scale);
        text.draw(value,-text.getWidth(value)/2f,-4,0xff000000|color,false,m,out,TextRenderer.TextLayerType.POLYGON_OFFSET,0,LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }
    private static Vec3d point(Direction face,int turn,int inset,double u,double v,double d,double angle){
        if(angle!=0){double x=u-7,y=v-11.5;u=7+x*Math.cos(angle)-y*Math.sin(angle);v=11.5+x*Math.sin(angle)+y*Math.cos(angle);}
        return ComponentGeometry.local(face,turn,u,v,inset+d);
    }
    private static void cuboid(VertexConsumer out,Matrix4f m,Direction face,int turn,int inset,double u0,double v0,double d0,double u1,double v1,double d1,double angle,int light,int overlay){
        Vec3d[] p={point(face,turn,inset,u0,v0,d0,angle),point(face,turn,inset,u1,v0,d0,angle),point(face,turn,inset,u1,v1,d0,angle),point(face,turn,inset,u0,v1,d0,angle),point(face,turn,inset,u0,v0,d1,angle),point(face,turn,inset,u1,v0,d1,angle),point(face,turn,inset,u1,v1,d1,angle),point(face,turn,inset,u0,v1,d1,angle)};
        int[][] sides={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};float[][] uv={{0,0},{0,1},{1,1},{1,0}};
        for(var side:sides){Vec3d normal=p[side[1]].subtract(p[side[0]]).crossProduct(p[side[2]].subtract(p[side[0]])).normalize();
            for(int i=0;i<4;i++){var a=p[side[i]];out.vertex(m,(float)a.x,(float)a.y,(float)a.z).color(255,255,255,255).texture(uv[i][0],uv[i][1]).overlay(overlay).light(light).normal((float)normal.x,(float)normal.y,(float)normal.z);}
        }
    }
    private static void glow(VertexConsumerProvider consumers,Matrix4f matrix,Direction face,int turn,double u,double v,double depth,double width,float alpha,float r,float g,float b){
        var out=consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(GLOW));double h=width/2;double[][] coords={{u-h,v+h,0,0},{u-h,v-h,0,1},{u+h,v-h,1,1},{u+h,v+h,1,0}};
        for(var c:coords){var p=ComponentGeometry.local(face,turn,c[0],c[1],depth);out.vertex(matrix,(float)p.x,(float)p.y,(float)p.z).color(r,g,b,alpha*.75f).texture((float)c[2],(float)c[3]).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(face.getOffsetX(),face.getOffsetY(),face.getOffsetZ());}
    }
    @Override public boolean rendersOutsideBoundingBox(ComponentEntity entity){return top.lacrus.zephyrzeno.electron.multiblock.Assembly.formed(entity.getCachedState())||entity.getCachedState().isOf(Foundry.WIND);}
    static void centeredBox(VertexConsumer out,Matrix4f m,double x0,double y0,double z0,double x1,double y1,double z1,int light,int overlay){
        cuboid(out,m,Direction.NORTH,0,0,x0*16,y0*16,z0*16,x1*16,y1*16,z1*16,0,light,overlay);
    }
}
