package top.lacrus.zephyrzeno.electron.multiblock;

import java.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import top.lacrus.zephyrzeno.electron.foundry.ComponentBlock.Kind;

/** Shared world-space mesh and cell-local collision geometry, in blocks relative to the controller. */
public final class AssemblyGeometry {
    public record Piece(Vec3d a,Vec3d b,double radius,String texture,boolean beam,int sides) {
        public Piece(Vec3d a,Vec3d b,double radius,String texture,boolean beam){this(a,b,radius,texture,beam,beam?4:0);}
        public Box bounds(){return beam?new Box(a,b).expand(radius):new Box(a,b);}
    }
    public record Model(List<Piece> pieces,Map<BlockPos,VoxelShape> cells) {}
    private static final Map<String,Model> CACHE=new HashMap<>();
    private final List<Piece> parts=new ArrayList<>();
    private void box(double x,double y,double z,double X,double Y,double Z,String t){parts.add(new Piece(new Vec3d(x,y,z),new Vec3d(X,Y,Z),0,t,false));}
    private void beam(double x,double y,double z,double X,double Y,double Z,double r,String t){parts.add(new Piece(new Vec3d(x,y,z),new Vec3d(X,Y,Z),r,t,true));}
    private void cylinder(double x,double y,double z,double r,double h,String t){parts.add(new Piece(new Vec3d(x,y,z),new Vec3d(x,y+h,z),r,t,true,12));}
    public static double baseRadius(int height){return Math.min(2.15,.65+height*.065);}
    public static Vec3d terminal(int pin){return new Vec3d(pin<4?-1.85:2.85,.82-1.35*Math.min(pin%4,2),pin%4==3?.7:.4);}
    public static synchronized Model model(Kind kind,int height,boolean tower){
        return model(kind,height,tower,0);
    }
    public static Vec3d rotate(Vec3d p,int turn){return switch(turn&3){case 1->new Vec3d(1-p.z,p.y,p.x);case 2->new Vec3d(1-p.x,p.y,1-p.z);case 3->new Vec3d(p.z,p.y,1-p.x);default->p;};}
    public static synchronized Model model(Kind kind,int height,boolean tower,int turn){
        int rotation=tower?turn&3:0;
        String key=kind+":"+height+":"+tower+":"+rotation;
        return CACHE.computeIfAbsent(key,k->{
            var g=new AssemblyGeometry();g.make(kind,height,tower);
            if(rotation!=0)g.parts.replaceAll(p->new Piece(rotate(p.a,rotation),rotate(p.b,rotation),p.radius,p.texture,p.beam,p.sides));
            var m=g.finish();
            for(int i=1;i<=height;i++)m.cells.put(new BlockPos(0,-i,0),m.cells.getOrDefault(new BlockPos(0,-i,0),VoxelShapes.empty()));
            // Collision disk protects the rotating blades throughout their swept volume.
            if(kind==Kind.WIND)for(int x=-2;x<=2;x++)for(int y=0;y<=4;y++){
                var disk=new BitSetVoxelSet(16,16,16);
                for(int a=0;a<16;a++)for(int b=0;b<16;b++){
                    double dx=x+(a+.5)/16-.5,dy=y+(b+.5)/16-2.5;
                    if(dx*dx+dy*dy<2.26*2.26)for(int z=2;z<7;z++)disk.set(a,b,z);
                }
                if(!disk.isEmpty())m.cells.put(new BlockPos(x,y,-1),VoxelShapes.union(m.cells.getOrDefault(new BlockPos(x,y,-1),VoxelShapes.empty()),new CellShape(disk).simplify()));
            }
            return new Model(m.pieces,Collections.unmodifiableMap(m.cells));
        });
    }
    private void lattice(int height){
        for(int level=0;level<height;level++){
            double t=level/(double)height,u=(level+1d)/height,r=baseRadius(height)*(1-t)+.23*t,R=baseRadius(height)*(1-u)+.23*u;
            double y=level-height+.055,Y=y+1;
            for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1}){
                double x=.5+sx*r,z=.5+sz*r,X=.5+sx*R,Z=.5+sz*R;
                beam(x,y,z,X,Y,Z,.052,"galvanized");
                beam(x,y,z,.5-sx*R,Y,Z,.024,"galvanized");
                beam(x,y,z,X,Y,.5-sz*R,.024,"galvanized");
                beam(x,y,z,.5-sx*r,y,z,.032,"galvanized");
                if(level==0)box(x-.15,y,z-.15,x+.15,y+.12,z+.15,"cast_base");
            }
        }
    }
    private void make(Kind kind,int height,boolean tower){
        if(height>0)lattice(height);
        // This service panel keeps the existing eight/six/two electrical pin coordinates accessible.
        box(.03,0,.03,.97,.88,.97,"control_case");
        box(.04,.88,.04,.96,.9375,.96,"service_panel");
        if(tower){
            for(int side:new int[]{-1,1}){
                double tip=side<0?-1.85:2.85;
                for(int tier=0;tier<3;tier++){
                    double y=-1.35*tier;
                    beam(.5,.28+y,.5,tip,1.32+y,.5,.05,"galvanized");
                    beam(.5,1.32+y,.5,tip,1.32+y,.5,.05,"galvanized");
                    beam(.5,1.32+y,.5,tip,.95+y,.5,.025,"galvanized");
                }
                for(int i=0;i<4;i++){
                    double z=i==3?.7:.4,y=-1.35*Math.min(i,2);
                    beam(tip,1.32+y,.5,tip,1.32+y,z,.026,"galvanized");
                    for(int j=0;j<5;j++)box(tip-.075,.88+j*.075+y,z-.075,tip+.075,.92+j*.075+y,z+.075,"porcelain");
                    box(tip-.04,.77+y,z-.04,tip+.04,.88+y,z+.04,"copper_bus");
                }
            }
            return;
        }
        switch(kind){
            case WIND -> {
                box(.4,.8,.66,.6,2.36,.86,"turbine_white");
                // Nacelle, service hatch, hub and a swept rotor envelope.
                box(.11,2.13,-.34,.89,2.86,1.3,"turbine_white");
                box(.2,2.86,.3,.8,2.94,1.05,"galvanized");
                box(.32,2.3,-.68,.68,2.67,-.34,"cast_base");
            }
            case LIGHTNING -> {
                for(int side:new int[]{-1,1}){
                    double x=.5+side*.95;
                    box(x-.25,0,.12,x+.25,1.75,.88,"capacitor_bank");
                    for(int i=0;i<6;i++)box(x-.3,1.78+i*.1,.28,x+.3,1.83+i*.1,.72,"porcelain");
                    beam(.5,.85,.5,x,2.4,.5,.04,"copper_bus");
                }
            }
            case THERMAL -> {
                tank(.5,.15,-.5,.66,1.6,"boiler");
                box(-.95,0,.12,-.08,.88,.88,"cast_base");
                for(int i=0;i<5;i++)box(-.95,.15+i*.12,.08,-.08,.2+i*.12,.94,"copper_bus");
                box(.23,1.75,-.77,.77,3.25,-.23,"soot_stack");
                box(.12,3.18,-.88,.88,3.34,-.12,"cast_base");
                beam(-.5,.68,.5,-.5,1.3,-.5,.07,"copper_bus");
                beam(-.5,1.3,-.5,.5,1.3,-.5,.07,"copper_bus");
                box(.15,0,1.02,.85,.65,1.86,"boiler");
            }
            case HYDRO -> {
                tank(.5,.04,-.5,.46,1.28,"hydro_blue");
                box(.2,.15,1.08,.8,1.05,1.86,"cast_base");
                beam(.5,.6,-.5,.5,.6,1.5,.13,"galvanized");
                for(int i=0;i<6;i++)box(.12,.16+i*.13,1.13,.88,.22+i*.13,1.82,"hydro_blue");
            }
            case NUCLEAR -> {
                tank(.5,.03,-.5,.68,2.3,"reactor_shell");
                for(int i=0;i<5;i++){double r=.62-i*.095;cylinder(.5,2.3+i*.11,-.5,r,.12,"reactor_shell");}
                for(int side:new int[]{-1,1}){
                    double x=.5+side;
                    tank(x,.05,.5,.4,1.85,"cast_base");
                    beam(x,1.5,.5,x,1.5,-.5,.075,"copper_bus");
                    beam(x,1.5,-.5,.5,1.5,-.5,.075,"copper_bus");
                }
                box(.1,0,1.07,.9,1.04,1.9,"reactor_shell");
                box(.3,1.04,1.22,.7,1.34,1.62,"control_case");
            }
            case SUBSTATION -> {
                box(-.25,.1,-.97,1.25,1.5,-.08,"transformer_oil");
                for(int i=0;i<9;i++)for(int side:new int[]{-1,1}){
                    double x=side<0?-.48:1.3;box(x,.18,-.93+i*.1,x+.18,1.3,-.89+i*.1,"galvanized");
                }
                for(double x:new double[]{.13,.87})for(int i=0;i<7;i++)box(x-.13,1.5+i*.09,-.62,x+.13,1.55+i*.09,-.35,"porcelain");
                for(double x:new double[]{-.5,1.5})box(x-.32,0,.12,x+.32,.7,.88,"transformer_oil");
                box(.08,0,1.08,.92,.85,1.92,"control_case");
            }
            case MAINS -> {
                box(.02,0,-.98,.98,1.95,-.12,"control_case");
                box(.08,.22,-1,.92,1.8,-.97,"service_panel");
                box(.75,.75,-1.08,.8,1.22,-.99,"galvanized");
            }
            default -> {}
        }
    }
    private void tank(double x,double y,double z,double r,double h,String texture){
        cylinder(x,y,z,r,h,texture);
        for(double Y:new double[]{y+.08,y+h-.12})cylinder(x,Y,z,r+.02,.07,"galvanized");
    }
    private Model finish(){
        Map<BlockPos,List<Box>> boxes=new LinkedHashMap<>();
        for(var p:parts){
            if(p.sides>4){
                for(int i=0;i<24;i++){double z0=-p.radius+2*p.radius*i/24,z1=-p.radius+2*p.radius*(i+1)/24;double w=Math.sqrt(Math.max(0,p.radius*p.radius-Math.pow((z0+z1)/2,2)));clip(boxes,new Box(p.a.x-w,p.a.y,p.a.z+z0,p.a.x+w,p.b.y,p.a.z+z1));}
            }
            else if(p.beam){int n=Math.max(1,(int)Math.ceil(p.a.distanceTo(p.b)*7));for(int i=0;i<n;i++)clip(boxes,new Box(p.a.lerp(p.b,i/(double)n),p.a.lerp(p.b,(i+1d)/n)).expand(p.radius));}
            else clip(boxes,p.bounds());
        }
        Map<BlockPos,VoxelShape> cells=new LinkedHashMap<>();
        boxes.forEach((pos,list)->{
            var voxels=new BitSetVoxelSet(16,16,16);
            for(var b:list)for(int x=Math.max(0,(int)Math.floor(b.minX*16));x<Math.min(16,(int)Math.ceil(b.maxX*16));x++)
                for(int y=Math.max(0,(int)Math.floor(b.minY*16));y<Math.min(16,(int)Math.ceil(b.maxY*16));y++)
                    for(int z=Math.max(0,(int)Math.floor(b.minZ*16));z<Math.min(16,(int)Math.ceil(b.maxZ*16));z++)voxels.set(x,y,z);
            cells.put(pos,new CellShape(voxels).simplify());
        });
        return new Model(List.copyOf(parts),cells);
    }
    private static final class CellShape extends ArrayVoxelShape {
        private static final double[] GRID=java.util.stream.IntStream.rangeClosed(0,16).mapToDouble(i->i/16d).toArray();
        CellShape(BitSetVoxelSet set){super(set,GRID,GRID,GRID);}
    }
    private static void clip(Map<BlockPos,List<Box>> cells,Box b){
        for(var q:BlockPos.iterate(BlockPos.ofFloored(b.minX,b.minY,b.minZ),BlockPos.ofFloored(b.maxX-1e-7,b.maxY-1e-7,b.maxZ-1e-7))){
            var box=b.intersection(new Box(q));if(box.maxX-box.minX>1e-7&&box.maxY-box.minY>1e-7&&box.maxZ-box.minZ>1e-7)cells.computeIfAbsent(q.toImmutable(),k->new ArrayList<>()).add(box.offset(-q.getX(),-q.getY(),-q.getZ()));
        }
    }
    private AssemblyGeometry(){}
}
