package top.lacrus.zephyrzeno.electron.foundry;

import net.minecraft.block.Block;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import top.lacrus.zephyrzeno.electron.circuit.SurfacePoint;

/** Geometry shared by selection, wiring and the animated client renderer. Units are 1/16 block. */
public final class ComponentGeometry {
    private static final java.util.Map<Integer,VoxelShape> SHAPES=new java.util.concurrent.ConcurrentHashMap<>();
    public static int inset(ComponentBlock.Kind kind){return kind.large()?1:13;}
    public static double[] rotate(double u,double v,int turn){
        return switch(turn&3){case 1->new double[]{16-v,u};case 2->new double[]{16-u,16-v};case 3->new double[]{v,16-u};default->new double[]{u,v};};
    }
    public static SurfacePoint rotate(SurfacePoint p,int turn){var uv=rotate(p.u(),p.v(),turn);return new SurfacePoint(p.host(),p.face(),(int)uv[0],(int)uv[1],p.layer());}
    /** d is measured inward from the outer block face, before applying the thin housing inset. */
    public static Vec3d local(Direction face,int turn,double u,double v,double d){
        var uv=rotate(u,v,turn);u=uv[0]/16;v=uv[1]/16;d/=16;
        return switch(face){case NORTH->new Vec3d(u,v,d);case SOUTH->new Vec3d(u,v,1-d);case WEST->new Vec3d(d,v,u);case EAST->new Vec3d(1-d,v,u);case UP->new Vec3d(u,1-d,v);case DOWN->new Vec3d(u,d,v);};
    }
    public static VoxelShape shape(ComponentBlock.Kind kind,Direction face,int turn){
        return SHAPES.computeIfAbsent(kind.ordinal()*24+face.getId()*4+(turn&3),k->createShape(kind,face,turn));
    }
    private static VoxelShape createShape(ComponentBlock.Kind kind,Direction face,int turn){
        if(kind.large())return box(face,turn,0,0,0,16,16,16);
        var plate=box(face,turn,0,0,13,16,16,16);
        double front=inset(kind)-1;
        var body=(kind==ComponentBlock.Kind.RESISTOR||kind==ComponentBlock.Kind.DIODE||kind==ComponentBlock.Kind.FUSE)?box(face,turn,4.5,5.8,front,9.5,8.2,13):(kind==ComponentBlock.Kind.BATTERY||kind==ComponentBlock.Kind.BOOST||kind==ComponentBlock.Kind.BUCK||kind==ComponentBlock.Kind.RELAY)?box(face,turn,5,5,front,9,11,13):kind==ComponentBlock.Kind.TERMINAL_STRIP?VoxelShapes.empty():box(face,turn,4.5,9,front,11,15,13);
        VoxelShape shape=VoxelShapes.union(plate,body);
        for(int p=0;p<ComponentPins.count(kind);p++){int u=ComponentPins.u(kind,p),v=ComponentPins.v(kind,p);shape=VoxelShapes.union(shape,box(face,turn,u-.75,v-.75,12.35,u+.75,v+.75,13));}
        return shape;
    }
    private static VoxelShape box(Direction face,int turn,double u0,double v0,double d0,double u1,double v1,double d1){
        Vec3d a=local(face,turn,u0,v0,d0).multiply(16),b=local(face,turn,u1,v1,d1).multiply(16);
        return Block.createCuboidShape(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z),Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z));
    }
    public static boolean traceClear(ComponentBlock.Kind kind,int u,int v,int layer){
        // Keep the conductor off the IC, moving handle, resistor body and LED lens.
        if(layer==1)return true;
        if(kind==ComponentBlock.Kind.TERMINAL_STRIP||kind==ComponentBlock.Kind.DISTRIBUTION)return true;
        if(kind==ComponentBlock.Kind.MOTOR||kind==ComponentBlock.Kind.GENERATOR||kind==ComponentBlock.Kind.LAMP)return !(u>=5&&u<=9&&v>=9&&v<=13);
        if(kind==ComponentBlock.Kind.BATTERY||kind==ComponentBlock.Kind.BOOST||kind==ComponentBlock.Kind.BUCK||kind==ComponentBlock.Kind.RELAY)return !(u>=5&&u<=9&&v>=5&&v<=11);
        if(kind==ComponentBlock.Kind.CRANK)return !(u>=5&&u<=11&&v>=9&&v<=15);
        if(kind==ComponentBlock.Kind.RESISTOR||kind==ComponentBlock.Kind.DIODE||kind==ComponentBlock.Kind.FUSE)return !(u>=5&&u<=9&&v==7);
        return !(u>=5&&u<=9&&v>=9&&v<=13);
    }
    private ComponentGeometry(){}
}
