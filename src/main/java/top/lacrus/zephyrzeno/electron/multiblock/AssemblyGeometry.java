package top.lacrus.zephyrzeno.electron.multiblock;

import java.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.*;
import top.lacrus.zephyrzeno.electron.foundry.ComponentBlock.Kind;

/** Shared world-space mesh and cell-local collision geometry, in blocks relative to the controller. */
public final class AssemblyGeometry {
    public record Piece(Vec3d a,Vec3d b,double radius,String texture,boolean beam,int sides,double topRadius) {
        public Piece(Vec3d a,Vec3d b,double radius,String texture,boolean beam,int sides){this(a,b,radius,texture,beam,sides,radius);}
        public Piece(Vec3d a,Vec3d b,double radius,String texture,boolean beam){this(a,b,radius,texture,beam,beam?4:0);}
        public Box bounds(){return beam?new Box(a,b).expand(radius):new Box(a,b);}
    }
    public record Model(List<Piece> pieces,Map<BlockPos,VoxelShape> cells) {}
    private static final Map<String,Model> CACHE=new HashMap<>();
    private final List<Piece> parts=new ArrayList<>();
    private void box(double x,double y,double z,double X,double Y,double Z,String t){parts.add(new Piece(new Vec3d(x,y,z),new Vec3d(X,Y,Z),0,t,false));}
    private void beam(double x,double y,double z,double X,double Y,double Z,double r,String t){parts.add(new Piece(new Vec3d(x,y,z),new Vec3d(X,Y,Z),r,t,true));}
    private void cone(double x,double y,double z,double r,double R,double h,String t){parts.add(new Piece(new Vec3d(x,y,z),new Vec3d(x,y+h,z),r,t,true,20,R));}
    private void cylinder(double x,double y,double z,double r,double h,String t){parts.add(new Piece(new Vec3d(x,y,z),new Vec3d(x,y+h,z),r,t,true,12));}
    public static double baseRadius(int height){return Math.min(2.15,.65+height*.065);}
    public static Vec3d terminal(int pin){return new Vec3d(pin<4?-1.85:2.85,.82-1.35*Math.min(pin%4,2),pin%4==3?.7:.4);}
    public static synchronized Model model(Kind kind,int height,boolean tower){
        return model(kind,height,tower,0);
    }
    public static Vec3d rotate(Vec3d p,int turn){return switch(turn&3){case 1->new Vec3d(1-p.z,p.y,p.x);case 2->new Vec3d(1-p.x,p.y,1-p.z);case 3->new Vec3d(p.z,p.y,1-p.x);default->p;};}
    public static synchronized Model model(Kind kind,int height,boolean tower,int turn){
        int rotation=turn&3;
        String key=kind+":"+height+":"+tower+":"+rotation;
        return CACHE.computeIfAbsent(key,k->{
            var g=new AssemblyGeometry();g.make(kind,height,tower);
            if(rotation!=0)g.parts.replaceAll(p->new Piece(rotate(p.a,rotation),rotate(p.b,rotation),p.radius,p.texture,p.beam,p.sides,p.topRadius));
            var m=g.finish();
            for(int i=1;i<=height;i++)m.cells.put(new BlockPos(0,-i,0),m.cells.getOrDefault(new BlockPos(0,-i,0),VoxelShapes.empty()));
            // Collision disk protects the rotating blades throughout their swept volume.
            if(kind==Kind.WIND)for(int x=-5;x<=5;x++)for(int y=-3;y<=7;y++){
                var disk=new BitSetVoxelSet(16,16,16);
                for(int a=0;a<16;a++)for(int b=0;b<16;b++){
                    double dx=x+(a+.5)/16-.5,dy=y+(b+.5)/16-2.5;
                    if(dx*dx+dy*dy<4.9*4.9)for(int z=1;z<7;z++)disk.set(a,b,z);
                }
                if(!disk.isEmpty()){
                    var cell=new BlockPos(x,y,-1);var shape=new CellShape(disk).simplify();
                    if(rotation!=0){var rotated=BlockPos.ofFloored(rotate(Vec3d.ofCenter(cell),rotation));var boxes=shape.getBoundingBoxes();shape=VoxelShapes.empty();for(var box:boxes){var aa=rotate(new Vec3d(box.minX+x,box.minY+y,box.minZ-1),rotation).subtract(Vec3d.of(rotated));var bb=rotate(new Vec3d(box.maxX+x,box.maxY+y,box.maxZ-1),rotation).subtract(Vec3d.of(rotated));shape=VoxelShapes.union(shape,VoxelShapes.cuboid(new Box(aa,bb)));}cell=rotated;}
                    m.cells.put(cell,VoxelShapes.union(m.cells.getOrDefault(cell,VoxelShapes.empty()),shape));
                }
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
        if(height>0&&kind!=Kind.WIND)lattice(height);
        if(kind==Kind.WIND){
            for(int i=0;i<height;i++){double r=.58-.31*i/Math.max(1d,height),R=.58-.31*(i+1)/Math.max(1d,height);cone(.5,i-height,.5,r,R,1,"turbine_white");if(i%3==0)cylinder(.5,i-height,.5,r+.018,.055,"galvanized");}
            box(.12,-height,.0,.88,-height+1.25,.04,"service_panel");
            for(int i=0;i<12;i++){double a=i*Math.PI/6;box(.5+.59*Math.cos(a)-.035,-height,.5+.59*Math.sin(a)-.035,.5+.59*Math.cos(a)+.035,-height+.08,.5+.59*Math.sin(a)+.035,"cast_base");}
        }
        // This service panel keeps the existing eight/six/two electrical pin coordinates accessible.
        if(kind!=Kind.WIND){box(.03,0,.03,.97,.88,.97,"control_case");box(.04,.88,.04,.96,.9375,.96,"service_panel");}
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
                cone(.5,0,.5,.27,.23,2.4,"turbine_white");
                // Nacelle, service hatch, hub and a swept rotor envelope.
                box(-.02,2.04,-.34,1.02,2.96,1.7,"turbine_white");
                for(int i=0;i<6;i++)box(.13,2.95,.25+i*.18,.87,2.98,.32+i*.18,"cast_base");
                beam(.5,3,1.2,.5,3.48,1.2,.026,"galvanized");
                box(.2,2.86,.3,.8,2.94,1.05,"galvanized");
                box(.32,2.3,-.68,.68,2.67,-.34,"cast_base");
                for(int j=0;j<8;j++){double a=j*Math.PI/4;beam(.5+.25*Math.cos(a),2.5+.25*Math.sin(a),-.35,.5+.25*Math.cos(a),2.5+.25*Math.sin(a),-.4,.028,"galvanized");}
                box(.9,2.23,.8,1.04,2.8,1.4,"service_panel");
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
                // Boiler drum, firebox, chimney and flanged steam path occupy a seven-by-seven site.
                box(-.85,0,-4.85,1.85,1.15,-2.7,"cast_base");tank(.5,1.1,-3.5,1.13,2.8,"boiler");
                tank(.5,3.9,-4.5,.35,3.1,"soot_stack");cylinder(.5,6.9,-4.5,.46,.15,"cast_base");
                box(-.48,.25,-2.72,1.48,.9,-2.66,"service_panel");
                tank(2.5,.12,-2.5,.67,1.25,"hydro_blue");tank(2.5,.12,-.5,.59,1.2,"cast_base");
                beam(2.5,.72,-2.5,2.5,.72,-.5,.16,"galvanized");
                for(int i=0;i<8;i++)box(1.85,.28,-1.02+i*.12,3.15,1.17,-.96+i*.12,"galvanized");
                box(-2.95,.08,-4.1,-.55,1.65,-2.9,"transformer_oil");
                for(int i=0;i<10;i++)box(-2.96,.2,-4.03+i*.105,-.5,1.52,-3.99+i*.105,"copper_bus");
                pipe(.5,3.6,-3.5,2.5,3.6,-3.5,.09);pipe(2.5,3.6,-3.5,2.5,1,-2.5,.09);
                pipe(2.5,1,-2.5,-1.6,1,-3.5,.09);pipe(-1.6,1,-3.5,-1.6,.4,-.5,.08);
                rail(-1.35,3.0,-4.8,2,3.0,-4.8);rail(-1.35,3.0,-4.8,-1.35,3.0,-2.3);
                // Refractory firebox, ash doors, supported catwalk, guarded ladder and relief piping.
                box(-.92,.05,-4.9,-.86,1.14,-2.66,"firebrick");box(1.86,.05,-4.9,1.92,1.14,-2.66,"firebrick");
                for(double x:new double[]{-.15,.85}){box(x,.23,-2.65,x+.55,.78,-2.58,"hatch");beam(x+.15,.48,-2.56,x+.43,.48,-2.56,.025,"galvanized");}
                box(-1.65,2.88,-4.9,2.12,3,-4.66,"grating");box(-1.65,2.88,-4.9,-.9,3,-2.15,"grating");
                for(double z:new double[]{-4.8,-2.3})beam(-1.3,.04,z,-1.3,2.88,z,.055,"galvanized");
                ladder(-1.25,.15,-2.06,3.05,false);pipe(.5,3.95,-3.5,1.6,4.15,-3.5,.07);pipe(1.6,4.15,-3.5,1.6,4.95,-3.5,.07);
                wheel(1.6,4.3,-3.5,.24);wheel(-1.6,1,-1.7,.22);
                for(int band=0;band<6;band++)cylinder(.5,4.08+band*.5,-4.5,.38,.055,"galvanized");
                for(double x:new double[]{-.32,1.32})for(double y:new double[]{.3,.78})box(x,y,-2.6,x+.055,y+.055,-2.53,"galvanized");
                box(2.14,1.4,-2.75,2.86,1.7,-2.25,"service_panel");
            }
            case HYDRO -> {
                tank(.5,.04,-.5,.46,1.28,"hydro_blue");
                box(.2,.15,1.08,.8,1.05,1.86,"cast_base");
                beam(.5,.6,-.5,.5,.6,1.5,.13,"galvanized");
                for(int i=0;i<6;i++)box(.12,.16+i*.13,1.13,.88,.22+i*.13,1.82,"hydro_blue");
            }
            case NUCLEAR -> {
                // Thirty-six-by-thirty-six site with a walk-in control room and outside intake.
                for(int x=-15;x<21;x+=3){rail(x,0,-35,x+3,0,-35);if((x<-3||x>2)&&x!=18)rail(x,0,.94,x+3,0,.94);}
                rail(-14.94,0,-35,-14.94,0,1);rail(20.94,0,-35,20.94,0,-4);rail(20.94,0,-1,20.94,0,1);
                tank(-2.5,0,-19.5,5.45,10,"reactor_shell");
                for(int i=0;i<12;i++){double y=i/12d*5.4,Y=(i+1)/12d*5.4;cone(-2.5,10+y,-19.5,Math.sqrt(Math.max(.005,5.45*5.45-y*y)),Math.sqrt(Math.max(.005,5.45*5.45-Y*Y)),Y-y,"reactor_shell");}
                for(int i=0;i<20;i++){double y=i*.9,r=3.8-1.6*Math.sin(Math.PI*i/27),R=3.8-1.6*Math.sin(Math.PI*(i+1)/27);cone(9.5,y,-22.5,r,R,.9,"cooling_concrete");}
                cylinder(9.5,18,-22.5,2.67,.2,"cooling_concrete");
                // Separate rectangular condenser hall with visible turbine housings and overhead service rails.
                box(4,.0,-10,13,.18,-1,"cast_base");
                for(double x:new double[]{4.25,12.75})for(double z:new double[]{-9.5,-5.5,-1.5})beam(x,.15,z,x,4.8,z,.1,"galvanized");
                box(4,4.8,-10,13,5.02,-1,"roof_panels");
                for(int i=0;i<8;i++)beam(4,4.9,-9.5+i,13,4.9,-9.5+i,.06,"galvanized");
                for(double x:new double[]{7.5,10.5}){tank(x,.2,-6.5,1.05,2.0,"hydro_blue");for(int i=0;i<7;i++)cylinder(x,.45+i*.2,-6.5,1.1,.05,"galvanized");}
                tank(7.5,.2,-2.5,.9,1.75,"cast_base");pipe(7.5,1.1,-6.5,7.5,1.1,-2.5,.23);
                // Cooling-water reservoir has real storage at its accessible front service hatch.
                box(-14,0,-12,-8.7,.2,-5.1,"cooling_concrete");
                box(-14,0,-12,-13.7,2.4,-5.1,"cooling_concrete");box(-9,0,-12,-8.7,2.4,-5.1,"cooling_concrete");
                box(-14,0,-12,-8.7,2.4,-11.7,"cooling_concrete");box(-14,0,-5.4,-8.7,2.4,-5.1,"cooling_concrete");
                // A locked steel grille has a full thin collision lid, with cutout mesh revealing the water.
                box(-13.7,2.4,-11.7,-9,2.48,-5.4,"pool_grille");
                rail(-14,2.4,-12,-8.7,2.4,-12);rail(-14,2.4,-5.1,-8.7,2.4,-5.1);rail(-14,2.4,-12,-14,2.4,-5.1);
                pipe(-11.2,1.7,-11.4,-11.2,1.7,-19.5,.16);pipe(-11.2,1.7,-19.5,-7.8,1.7,-19.5,.16);
                pipe(2.8,5,-19.5,6.5,5,-19.5,.19);pipe(6.5,5,-19.5,6.5,2,-6.5,.19);
                pipe(10.5,1.4,-6.5,10.5,1.4,-15.5,.16);pipe(10.5,1.4,-15.5,9.5,1.4,-19.2,.16);
                controlRoom();
                rail(-8.7,2.4,-12,-8.7,2.4,-5.1);
                // Containment ribs, accessible service ring, personnel airlock and pipe penetrations.
                for(int i=0;i<24;i++){double a=i*Math.PI/12,x=-2.5+5.5*Math.cos(a),z=-19.5+5.5*Math.sin(a);beam(x,.25,z,x,9.85,z,.055,"reactor_rib");}
                for(double y:new double[]{3.0,6.0,9.8})cylinder(-2.5,y,-19.5,5.5,.075,"reactor_rib");
                box(-4,.0,-14.1,-1,2.85,-12.9,"reactor_shell");box(-3.62,.06,-12.91,-1.4,2.58,-12.8,"hatch");
                pipe(-5.8,3.0,-14.9,-5.8,3,-10.8,.14);pipe(-5.8,3,-10.8,4.2,3,-10.8,.14);wheel(-5.8,3,-12.5,.34);
                // Cooling tower is hollow at the crown, with separate louvres and ring columns.
                for(int i=0;i<24;i++){double a=i*Math.PI/12,x=9.5+3.6*Math.cos(a),z=-22.5+3.6*Math.sin(a);beam(x,.05,z,x,.95,z,.1,"reactor_rib");}
                for(int i=0;i<10;i++)cylinder(9.5,.25+i*.08,-22.5,3.83,.025,"grating");
                ladder(13.65,.2,-22.5,18.3,true);
                for(double y=1.1;y<18;y+=1.8){double r=3.8-1.6*Math.sin(Math.PI*(y/.9)/27);beam(9.5+r,y,-22.5,13.65,y,-22.5,.04,"galvanized");}
                rail(6.0,5.03,-10,12.8,5.03,-10);
                // Turbine hall: trusses, bridge crane, cable trays, vents, flanges and maintenance hatches.
                for(int i=0;i<5;i++){double z=-9.5+i*1.8;beam(4.3,3.4,z,8.5,4.76,z,.065,"galvanized");beam(8.5,4.76,z,12.7,3.4,z,.065,"galvanized");}
                for(double x:new double[]{4.65,12.35})box(x,3.88,-9.75,x+.15,4.03,-1.15,"safety_yellow");
                box(4.6,4.04,-6.9,12.55,4.28,-6.45,"safety_yellow");box(8.05,3.5,-6.95,8.8,4.03,-6.35,"cast_base");beam(8.4,3.5,-6.65,8.4,2.25,-6.65,.024,"galvanized");
                for(double x:new double[]{7.5,10.5}){box(x-.44,2.2,-6.9,x+.44,2.46,-6.1,"service_panel");wheel(x,1.5,-5.2,.28);}
                for(int i=0;i<3;i++){double z=-17+i*2.3;box(11.5,.12,z,13.8,.72,z+1.2,"transformer_oil");for(double x:new double[]{12,13.3})for(int j=0;j<5;j++)cylinder(x,.72+j*.11,z+.6,.17,.05,"porcelain");}
                pipe(-13.4,.75,-10.8,-13.4,.75,-5.6,.10);wheel(-13.4,.75,-8.5,.24);
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
    private void pipe(double x,double y,double z,double X,double Y,double Z,double r){beam(x,y,z,X,Y,Z,r,"steel_pipe");var a=new Vec3d(x,y,z);var b=new Vec3d(X,Y,Z);int n=Math.max(1,(int)a.distanceTo(b));for(int i=0;i<=n;i++){var q=a.lerp(b,i/(double)n);box(q.x-r*1.35,q.y-r*1.35,q.z-r*1.35,q.x+r*1.35,q.y+r*1.35,q.z+r*1.35,"galvanized");}}
    private void controlRoom(){
        // Interior is empty and reachable through a two-block-wide front opening.
        box(14,0,-7,21,.08,-.1,"floor_tiles");box(14,0,-7,14.18,3.55,-.1,"room_wall");
        box(14,0,-7,21,3.55,-6.82,"room_wall");
        box(20.82,0,-7,21,3.55,-3,"room_wall");box(20.82,0,-2,21,3.55,-.1,"room_wall");box(20.82,2,-3,21,3.55,-2,"room_wall");
        box(14,0,-.28,17,3.55,-.1,"room_wall");box(19,0,-.28,21,3.55,-.1,"room_wall");box(17,2.65,-.28,19,3.55,-.1,"room_wall");
        box(13.9,3.55,-7.1,21.08,3.75,0,"roof_panels");
        for(double x:new double[]{14.5,19.4})box(x,1.05,-.095,x+1.1,2.55,-.07,"instrument_glass");
        box(17,2.67,-.31,19,2.9,-.07,"room_sign");
        // Desks leave the functional console at (16,0,-3) completely exposed.
        box(14.45,0,-6.4,15.35,2.65,-5.65,"control_case");box(14.5,1.3,-5.66,15.3,2.5,-5.62,"console_panel");
        box(17.3,.05,-5.95,19.85,.72,-4.9,"control_case");box(17.2,.72,-6.0,20,.86,-4.85,"console_panel");
        for(double x:new double[]{14.8,18.5})box(x,3.76,-5.9,x+1.2,4.08,-4.6,"vent");
        box(20.82,2.08,-3,21.04,2.6,-2,"intake_sign");
    }
    private void ladder(double x,double y,double z,double top,boolean alongZ){
        for(int side:new int[]{-1,1}){double dx=alongZ?0:side*.3,dz=alongZ?side*.3:0;beam(x+dx,y,z+dz,x+dx,top,z+dz,.035,"galvanized");}
        for(double Y=y+.15;Y<top;Y+=.3)beam(x-(alongZ?0:.3),Y,z-(alongZ?.3:0),x+(alongZ?0:.3),Y,z+(alongZ?.3:0),.027,"galvanized");
    }
    private void wheel(double x,double y,double z,double radius){for(int i=0;i<12;i++){double a=i*Math.PI/6,b=(i+1)*Math.PI/6;beam(x+radius*Math.cos(a),y+radius*Math.sin(a),z,x+radius*Math.cos(b),y+radius*Math.sin(b),z,.025,"safety_yellow");if(i%3==0)beam(x,y,z,x+radius*Math.cos(a),y+radius*Math.sin(a),z,.018,"galvanized");}}
    private void rail(double x,double y,double z,double X,double Y,double Z){beam(x,y+1,z,X,Y+1,Z,.035,"safety_yellow");beam(x,y+.55,z,X,Y+.55,Z,.025,"galvanized");var a=new Vec3d(x,y,z);var b=new Vec3d(X,Y,Z);int n=Math.max(1,(int)Math.ceil(a.distanceTo(b)/2));for(int i=0;i<=n;i++){var q=a.lerp(b,i/(double)n);beam(q.x,q.y+.04,q.z,q.x,q.y+1.04,q.z,.032,"galvanized");}}
    private void tank(double x,double y,double z,double r,double h,String texture){
        cylinder(x,y,z,r,h,texture);
        for(double Y:new double[]{y+.08,y+h-.12})cylinder(x,Y,z,r+.02,.07,"galvanized");
    }
    private Model finish(){
        Map<BlockPos,List<Box>> boxes=new LinkedHashMap<>();
        for(var p:parts){
            if(p.sides>4){
                if(p.texture.equals("cooling_concrete")){
                    double rr=Math.max(p.radius,p.topRadius),inner=Math.max(0,Math.min(p.radius,p.topRadius)-.15);
                    for(int x=0;x<32;x++)for(int z=0;z<32;z++){double dx=-rr+(x+.5)*rr/16,dz=-rr+(z+.5)*rr/16,d=Math.hypot(dx,dz);if(d<=rr+.12&&d>=inner-.08)clip(boxes,new Box(p.a.x-rr+x*rr/16,p.a.y,p.a.z-rr+z*rr/16,p.a.x-rr+(x+1)*rr/16,p.b.y,p.a.z-rr+(z+1)*rr/16));}continue;
                }
                for(int i=0;i<24;i++){double rr=Math.max(p.radius,p.topRadius),z0=-rr+2*rr*i/24,z1=-rr+2*rr*(i+1)/24;double w=Math.sqrt(Math.max(0,rr*rr-Math.pow((z0+z1)/2,2)));clip(boxes,new Box(p.a.x-w,p.a.y,p.a.z+z0,p.a.x+w,p.b.y,p.a.z+z1));}
            }
            else if(p.beam){int n=Math.max(1,(int)Math.ceil(p.a.distanceTo(p.b)*7));for(int i=0;i<n;i++)clip(boxes,beamBounds(p.a.lerp(p.b,i/(double)n),p.a.lerp(p.b,(i+1d)/n),p.radius));}
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
    private static Box beamBounds(Vec3d a,Vec3d b,double radius){
        var axis=b.subtract(a).normalize();var u=axis.crossProduct(new Vec3d(0,1,0));if(u.lengthSquared()<1e-8)u=axis.crossProduct(new Vec3d(1,0,0));u=u.normalize().multiply(radius);var v=axis.crossProduct(u).normalize().multiply(radius);
        return new Box(a,b).expand(Math.abs(u.x)+Math.abs(v.x),Math.abs(u.y)+Math.abs(v.y),Math.abs(u.z)+Math.abs(v.z));
    }
    private static void clip(Map<BlockPos,List<Box>> cells,Box b){
        for(var q:BlockPos.iterate(BlockPos.ofFloored(b.minX,b.minY,b.minZ),BlockPos.ofFloored(b.maxX-1e-7,b.maxY-1e-7,b.maxZ-1e-7))){
            var box=b.intersection(new Box(q));if(box.maxX-box.minX>1e-7&&box.maxY-box.minY>1e-7&&box.maxZ-box.minZ>1e-7)cells.computeIfAbsent(q.toImmutable(),k->new ArrayList<>()).add(box.offset(-q.getX(),-q.getY(),-q.getZ()));
        }
    }
    private AssemblyGeometry(){}
}
