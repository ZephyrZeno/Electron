package top.lacrus.zephyrzeno.electroncalculator.BluePrint;

import java.util.*;
import java.nio.file.*;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.CompactBlueprint.*;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.DecimalCircuit.Module;
import top.lacrus.zephyrzeno.electroncalculator.BluePrint.PhysicalNetlist.*;

/** Places fixed arithmetic modules and routes only their external signal pins. */
public final class ModularLayout {
    private final CompactBlueprint fixed;
    private final Map<Integer,List<Pos>> sinks=new LinkedHashMap<>();
    private final Map<Integer,Module> colors=new HashMap<>();
    private final Set<Pos> guards=new HashSet<>();
    private final List<boolean[][]> floors=new ArrayList<>();
    private final DecimalCircuit circuit;
    private final PhysicalNetlist netlist;
    private final int columns,rows,originX;
    private static final int PITCH_X=Integer.getInteger("electron.modulePitchX",12),PITCH_Z=Integer.getInteger("electron.modulePitchZ",8),PITCH_Y=Integer.getInteger("electron.modulePitchY",20);
    private ModularLayout(DecimalCircuit circuit) {
        this(circuit,CompactBlueprint.panel(circuit),20,24,0);
    }
    private ModularLayout(DecimalCircuit circuit,CompactBlueprint.FixedPanel panel,int columns,int rows,int originX) {
        this.columns=columns;this.rows=rows;this.originX=originX;
        this.circuit=circuit;netlist=PhysicalNetlist.of(circuit);
        fixed=panel.plan();guards.addAll(panel.keepOut());
        panel.sinks().forEach((id,ports)->sinks.put(id,new ArrayList<>(ports)));
        for(int i=0;i<circuit.nodes.size();i++)colors.put(i,circuit.nodes.get(i).module());
    }
    private Pos allocate(int width,int depth,Unit unit,CompactBlueprint template) {
        int w=(width+PITCH_X-1)/PITCH_X,d=(depth+PITCH_Z-1)/PITCH_Z;
        if(Boolean.parseBoolean(System.getProperty("electron.localPlacement","true"))) {
            int bestFloor=-1,bestX=0,bestZ=0;long best=Long.MAX_VALUE;
            for(int floor=0;floor<=floors.size();floor++) {
                if(floor>=12)break;
                boolean[][] occupied=floor==floors.size()?new boolean[rows][columns]:floors.get(floor);
                for(int z=0;z<=rows-d;z++)for(int x=0;x<=columns-w;x++) {
                    boolean clear=true;
                    for(int dz=0;dz<d&&clear;dz++)for(int dx=0;dx<w;dx++)if(occupied[z+dz][x+dx]){clear=false;break;}
                    if(!clear)continue;
                    Pos origin=new Pos(originX+6+x*PITCH_X,2+floor*PITCH_Y,66+z*PITCH_Z);long cost=0;int connections=0;
                    for(var pin:template.inputs.entrySet()) {
                        int id=input(unit,pin.getKey());if(id<=1)continue;
                        Pos from=fixed.sources.get(id);if(from==null)continue;
                        Pos at=origin.add(pin.getValue().x(),pin.getValue().y(),pin.getValue().z());
                        cost+=Math.abs(at.x()-from.x())+2*Math.abs(at.y()-from.y())+Math.abs(at.z()-from.z());connections++;
                    }
                    cost=cost*100/Math.max(1,connections)+floor*5+z+x;
                    if(cost<best){best=cost;bestFloor=floor;bestX=x;bestZ=z;}
                }
            }
            if(bestFloor<0)throw new IllegalStateException("Module packing exceeded height limit");
            if(bestFloor==floors.size())floors.add(new boolean[rows][columns]);
            for(int z=0;z<d;z++)for(int x=0;x<w;x++)floors.get(bestFloor)[bestZ+z][bestX+x]=true;
            return new Pos(originX+6+bestX*PITCH_X,2+bestFloor*PITCH_Y,66+bestZ*PITCH_Z);
        }
        for(int floor=0;;floor++) {
            if(floor==floors.size())floors.add(new boolean[rows][columns]);
            boolean[][] occupied=floors.get(floor);
            for(int z=0;z<=rows-d;z++)for(int x=0;x<=columns-w;x++) {
                boolean clear=true;
                for(int dz=0;dz<d&&clear;dz++)for(int dx=0;dx<w;dx++)if(occupied[z+dz][x+dx]){clear=false;break;}
                if(!clear)continue;
                for(int dz=0;dz<d;dz++)for(int dx=0;dx<w;dx++)occupied[z+dz][x+dx]=true;
                return new Pos(originX+6+x*PITCH_X,2+floor*PITCH_Y,66+z*PITCH_Z);
            }
        }
    }
    private static int input(Unit unit,String name) {
        if(unit.type()!=Type.ADDER)return name.equals("A")?unit.inputs()[0]:unit.inputs().length>1?unit.inputs()[1]:0;
        int bits=(unit.inputs().length-1)/2;
        return unit.inputs()[name.equals("CIN")?bits*2:(name.charAt(0)=='B'?bits:0)+Integer.parseInt(name.substring(1))];
    }
    private static void put(CompactBlueprint p,Pos pos,Part part,int direction) {
        Tile tile=new Tile(part,Module.CONTROL,direction,-1),old=p.blocks.putIfAbsent(pos,tile);
        if(old!=null&&!old.equals(tile))throw new IllegalStateException("Template overlap "+pos);
    }
    private static void component(CompactBlueprint p,Pos pos,Part part,int direction) {
        p.blocks.putIfAbsent(pos.add(0,-1,0),new Tile(Part.WOOL,Module.CONTROL,0,-1));put(p,pos,part,direction);
    }
    private static void box(Set<Pos> result,Pos origin,int x0,int x1,int y0,int y1,int z0,int z1) {
        for(int x=x0;x<=x1;x++)for(int y=y0;y<=y1;y++)for(int z=z0;z<=z1;z++)result.add(origin.add(x,y,z));
    }
    private record Template(CompactBlueprint plan,Set<Pos> guards) {}
    public static CompactBlueprint moduleFixture(Type type) {
        CompactBlueprint p=small(type).plan();
        for(var entry:p.inputs.entrySet()) {
            Pos pin=entry.getValue();Pos lever=entry.getKey().equals("A")||type==Type.AND||type==Type.XOR?pin.add(-1,0,0):pin.add(0,0,type==Type.OR?-1:1);
            component(p,lever,Part.LEVER,0);entry.setValue(lever);
        }
        for(var entry:p.outputs.entrySet()) {
            Pos lamp=entry.getValue().add(0,-1,0);p.blocks.put(lamp,new Tile(Part.LAMP,Module.DISPLAY,0,-1));entry.setValue(lamp);
        }
        return p;
    }
    private static Template small(Type type) {
        CompactBlueprint p=new CompactBlueprint();Set<Pos> guards=new HashSet<>();
        Pos c=new Pos(5,3,3);
        if(type==Type.AND||type==Type.XOR) {
            // Quan Nguyen's standard cell geometry; see META-INF/licenses/pershing.txt.
            String[][] shape=type==Type.AND?new String[][]{{"w#  "," #>w","w#  "},{" t  "," w  "," t  "}}:
                new String[][]{{"w#>w#>"," ##> w","w#>w#>"},{" t  w "," ww   "," t  w "}};
            Pos o=new Pos(4,3,3);int length=shape[0][0].length();
            for(int y=0;y<shape.length;y++)for(int z=0;z<3;z++)for(int x=0;x<length;x++) {
                char ch=shape[y][z].charAt(x);if(ch==' ')continue;
                Part part=switch(ch){case '#'->Part.WOOL;case 'w'->Part.WIRE;case 't'->Part.STANDING_TORCH;default->Part.TORCH;};
                if(part==Part.WOOL||part==Part.TORCH)put(p,o.add(x,y,z),part,0);else component(p,o.add(x,y,z),part,0);
            }
            for(int z:new int[]{0,2}) {
                component(p,o.add(-1,0,z),Part.REPEATER,0);component(p,o.add(-2,0,z),Part.WIRE,0);p.inputs.put(z==0?"A":"B",o.add(-2,0,z));
            }
            component(p,o.add(length,0,1),Part.REPEATER,0);component(p,o.add(length+1,0,1),Part.WIRE,0);p.outputs.put("Q",o.add(length+1,0,1));
            box(guards,o,-1,length,-1,2,-1,3);p.width=length+7;
        } else {
            component(p,c.add(-2,0,0),Part.WIRE,0);component(p,c.add(-1,0,0),Part.REPEATER,0);p.inputs.put("A",c.add(-2,0,0));
            if(type==Type.OR) {
                component(p,c,Part.WIRE,0);component(p,c.add(0,0,-1),Part.REPEATER,1);component(p,c.add(0,0,-2),Part.WIRE,0);p.inputs.put("B",c.add(0,0,-2));
                component(p,c.add(1,0,0),Part.REPEATER,0);component(p,c.add(2,0,0),Part.WIRE,0);p.outputs.put("Q",c.add(2,0,0));
                box(guards,c,-1,1,-1,1,-1,1);
            } else {
                component(p,c.add(0,0,1),Part.REPEATER,3);component(p,c.add(0,0,2),Part.WIRE,0);component(p,c.add(0,0,3),Part.WIRE,0);p.inputs.put("B",c.add(0,0,3));
                if(type==Type.REGISTER) {
                    component(p,c,Part.REPEATER,0);component(p,c.add(1,0,0),Part.REPEATER,0);
                } else {put(p,c,Part.WOOL,0);put(p,c.add(1,0,0),Part.TORCH,0);}
                component(p,c.add(2,0,0),Part.REPEATER,0);component(p,c.add(3,0,0),Part.WIRE,0);p.outputs.put("Q",c.add(3,0,0));
                box(guards,c,-1,2,-1,1,-1,1);
            }
            p.width=11;
        }
        p.height=8;p.depth=8;return new Template(p,guards);
    }
    private void port(Pos pos,int signal,boolean source) {
        // The buffer next to a pin belongs to the same net. Internal logic retains private IDs.
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++) {
            if(Math.abs(dx)+Math.abs(dy)+Math.abs(dz)>1)continue;
            Pos p=pos.add(dx,dy,dz);Tile t=fixed.blocks.get(p);
            if(t!=null&&(t.part()==Part.WIRE||t.part()==Part.REPEATER))fixed.blocks.put(p,new Tile(t.part(),t.module(),t.direction(),signal));
        }
        if(source)fixed.sources.put(signal,pos);else sinks.computeIfAbsent(signal,k->new ArrayList<>()).add(pos);
    }
    private void constant(Pos pos,int value) {
        if(value==0)return;
        for(int dx:new int[]{-1,1}) {
            Pos torch=pos.add(dx,0,0),below=torch.add(0,-1,0);
            if(fixed.blocks.containsKey(torch))continue;
            Tile support=fixed.blocks.get(below);if(support!=null&&support.part()!=Part.WOOL)continue;
            fixed.blocks.putIfAbsent(below,new Tile(Part.WOOL,Module.CONTROL,0,-1));
            fixed.blocks.put(torch,new Tile(Part.STANDING_TORCH,Module.CONTROL,0,-1));box(guards,torch,-1,1,-1,2,-1,1);return;
        }
        throw new IllegalStateException("Constant pin blocked "+pos);
    }
    private void place(Unit unit) {
        if(unit.type()==Type.INPUT)return;
        boolean adder=unit.type()==Type.ADDER;int bits=adder?(unit.inputs().length-1)/2:0;
        Template template;
        if(adder) {
            CompactBlueprint p=VerticalFullAdder.template(bits);Set<Pos> keepOut=new HashSet<>();
            box(keepOut,new Pos(0,0,0),5,17,5,14,4,p.depth-1);template=new Template(p,keepOut);
        } else template=small(unit.type());
        CompactBlueprint p=template.plan();Pos offset=allocate(p.width,p.depth,unit,p);
        p.blocks.forEach((pos,t)->{
            Pos at=offset.add(pos.x(),pos.y(),pos.z());Tile tile=new Tile(t.part(),unit.module(),t.direction(),-1);
            if(fixed.blocks.putIfAbsent(at,tile)!=null)throw new IllegalStateException("Module placement overlap "+at);
        });
        template.guards().forEach(pos->guards.add(offset.add(pos.x(),pos.y(),pos.z())));
        for(var entry:p.inputs.entrySet()) {
            int signal=input(unit,entry.getKey());
            Pos pin=entry.getValue(),world=offset.add(pin.x(),pin.y(),pin.z());
            if(signal<=1)constant(world,signal);else port(world,signal,false);
        }
        for(var entry:p.outputs.entrySet()) {
            int signal=unit.outputs()[adder?(entry.getKey().equals("COUT")?bits:Integer.parseInt(entry.getKey().substring(1))):0];
            if(!netlist.owns(unit,signal))continue;
            Pos pin=entry.getValue();port(offset.add(pin.x(),pin.y(),pin.z()),signal,true);
        }
    }
    private static ModularLayout placed(DecimalCircuit circuit) {
        ModularLayout layout=new ModularLayout(circuit);
        for(Unit unit:layout.netlist.units)layout.place(unit);
        return layout;
    }
    public static Set<Pos> keepOut(DecimalCircuit circuit) {
        return new HashSet<>(placed(circuit).guards);
    }
    /** Pins and fixed cells that must survive pruning obsolete panel branches. */
    public static Map<Pos,Tile> moduleBlocks(DecimalCircuit circuit) {
        Map<Pos,Tile> result=new HashMap<>();
        placed(circuit).fixed.blocks.forEach((p,t)->{if(p.z()>=60)result.put(p,t);});
        return result;
    }
    public static Set<Pos> keepOut(DecimalCircuit circuit,CompactBlueprint.FixedPanel panel,int columns,int rows,int originX) {
        ModularLayout layout=new ModularLayout(circuit,panel,columns,rows,originX);
        for(Unit unit:layout.netlist.units)layout.place(unit);
        return new HashSet<>(layout.guards);
    }
    public static CompactBlueprint create(DecimalCircuit circuit) {
        ModularLayout layout=placed(circuit);
        return routed(layout);
    }
    public static CompactBlueprint create(DecimalCircuit circuit,CompactBlueprint.FixedPanel panel,int columns,int rows,int originX) {
        ModularLayout layout=new ModularLayout(circuit,panel,columns,rows,originX);
        for(Unit unit:layout.netlist.units)layout.place(unit);
        return routed(layout);
    }
    private static CompactBlueprint routed(ModularLayout layout) {
        System.out.println("MODULES fixed="+layout.fixed.blocks.size()+" floors="+layout.floors.size()+" nets="+layout.sinks.size());
        CompactBlueprint result=CompactBlueprint.connectModules(layout.fixed,layout.sinks,layout.colors,layout.guards,layout.originX+layout.columns*PITCH_X+30,layout.floors.size()*PITCH_Y+40,layout.rows*PITCH_Z+90);
        result.estimatedTicks=BlueprintVerifier.verifyModuleRoutes(result,layout.sinks,layout.netlist);return result;
    }
    public static int verify(CompactBlueprint plan,DecimalCircuit circuit) {
        ModularLayout layout=placed(circuit);
        if(!plan.sources.equals(layout.fixed.sources)||!plan.inputs.equals(layout.fixed.inputs)||!plan.outputs.equals(layout.fixed.outputs))
            throw new IllegalStateException("Blueprint module interfaces do not match the circuit");
        for(var block:layout.fixed.blocks.entrySet())if(!block.getValue().equals(plan.blocks.get(block.getKey())))
            throw new IllegalStateException("Fixed module changed at "+block.getKey());
        return BlueprintVerifier.verifyModuleRoutes(plan,layout.sinks,layout.netlist);
    }
    public static void main(String[] args)throws Exception {
        if(args.length==2&&args[0].equals("--verify")) {
            CompactBlueprint plan=CompactBlueprint.read(Files.newInputStream(Path.of(args[1])));
            int ticks=verify(plan,plan.inputs.containsKey("AT0")?DecimalCircuit.create():DecimalCircuit.smoke());
            System.out.println("Verified module blueprint: "+plan.blocks.size()+" blocks, propagation budget "+ticks+" game ticks");return;
        }
        CompactBlueprint p=create(args.length>1&&args[1].equals("smoke")?DecimalCircuit.smoke():DecimalCircuit.create());p.write(Path.of(args.length==0?"build/modular-calculator.ebp":args[0]));
        System.out.println("MODULAR CALCULATOR "+p.width+"x"+p.height+"x"+p.depth+" blocks="+p.blocks.size());
    }
}
